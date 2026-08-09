package com.sekai.song.control;

import com.sekai.song.model.NeteaseImportResult;
import com.sekai.song.model.LicenseStatus;
import com.sekai.song.model.MusicImportResult;
import com.sekai.song.model.Playlist;
import com.sekai.song.model.Song;
import com.sekai.song.model.User;
import com.sekai.song.service.BilibiliImportService;
import com.sekai.song.service.CloudLyricService;
import com.sekai.song.service.LicenseService;
import com.sekai.song.service.NeteaseImportService;
import com.sekai.song.service.PlaylistService;
import com.sekai.song.service.QqImportService;
import com.sekai.song.service.SongService;
import com.sekai.song.service.VipQuotaService;
import com.sekai.song.util.UploadPathUtil;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpSession;
import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.audio.AudioHeader;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Controller
public class MusicControl {
    private static final Set<String> AUDIO_EXTENSIONS = Set.of(".mp3", ".flac", ".m4a", ".wav", ".ogg", ".aac");
    private static final Set<String> LYRIC_EXTENSIONS = Set.of(".lrc");
    private static final Set<String> VIDEO_EXTENSIONS = Set.of(".mp4", ".webm", ".m4v", ".mov", ".mkv");
    private static final Charset GB18030 = Charset.forName("GB18030");
    private static final Pattern LRC_TIMESTAMP = Pattern.compile("\\[(\\d{1,2}):(\\d{2})(?:\\.(\\d{1,3}))?]");
    private static final Path AUDIO_UPLOAD_ROOT = UploadPathUtil.uploadRoot().resolve("audio").normalize();
    private static final Path VIDEO_UPLOAD_ROOT = UploadPathUtil.uploadRoot().resolve("video").normalize();
    private static final Path EXTERNAL_UPLOAD_ROOT = externalUploadRoot();

    @Resource
    private SongService songService;

    @Resource
    private PlaylistService playlistService;

    @Resource
    private NeteaseImportService neteaseImportService;

    @Resource
    private QqImportService qqImportService;

    @Resource
    private BilibiliImportService bilibiliImportService;

    @Resource
    private CloudLyricService cloudLyricService;

    @Resource
    private LicenseService licenseService;

    @Resource
    private VipQuotaService vipQuotaService;

    @PostMapping("/songs/add")
    public String addSong(Song song, HttpSession session, RedirectAttributes redirectAttributes) {
        User user = requireUser(session);
        if (user == null) {
            return "redirect:/login";
        }
        if (!vipQuotaService.canAddSong(user)) {
            redirectAttributes.addFlashAttribute("error", vipQuotaService.songLimitMessage(user));
            return "redirect:/license";
        }
        song.setUserId(user.getId());
        boolean success = songService.add(song);
        redirectAttributes.addFlashAttribute(success ? "message" : "error",
                success ? "歌曲已经加入你的动漫歌单" : "歌曲名不能为空");
        return "redirect:/songs";
    }

    @PostMapping("/songs/update/{id}")
    public String updateSong(@PathVariable Long id, Song song, HttpSession session, RedirectAttributes redirectAttributes) {
        User user = requireUser(session);
        if (user == null) {
            return "redirect:/login";
        }
        song.setId(id);
        boolean success = songService.update(song, user.getId());
        redirectAttributes.addFlashAttribute(success ? "message" : "error",
                success ? "歌曲信息已更新" : "更新失败，请检查歌曲名");
        return "redirect:/songs";
    }

    @PostMapping("/songs/delete/{id}")
    public String deleteSong(@PathVariable Long id, HttpSession session) {
        User user = requireUser(session);
        if (user == null) {
            return "redirect:/login";
        }
        songService.delete(id, user.getId());
        return "redirect:/songs";
    }

    @PostMapping("/songs/favorite/{id}")
    public String favoriteSong(@PathVariable Long id,
                               HttpSession session,
                               @RequestParam(value = "returnTo", required = false) String returnTo) {
        User user = requireUser(session);
        if (user == null) {
            return "redirect:/login";
        }
        songService.toggleFavorite(id, user.getId());
        return redirectTo(returnTo, "/songs");
    }

    @PostMapping("/songs/audio/{id}")
    public String uploadAudio(@PathVariable Long id,
                              @RequestParam("audioFile") MultipartFile audioFile,
                              @RequestParam(value = "returnTo", required = false) String returnTo,
                              HttpSession session,
                              RedirectAttributes redirectAttributes) {
        User user = requireUser(session);
        if (user == null) {
            return "redirect:/login";
        }
        if (!requireVip(user, redirectAttributes)) {
            return "redirect:/license";
        }
        Song song = songService.getByIdAndUserId(id, user.getId());
        if (song == null) {
            redirectAttributes.addFlashAttribute("error", "歌曲不存在");
            return "redirect:/songs";
        }
        if (audioFile == null || audioFile.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "请选择一个音频文件");
            return redirectTo(returnTo, "/player/" + id);
        }
        String extension = extensionOf(audioFile.getOriginalFilename());
        if (!AUDIO_EXTENSIONS.contains(extension)) {
            redirectAttributes.addFlashAttribute("error", "只支持 mp3、flac、m4a、wav、ogg、aac 音频文件");
            return redirectTo(returnTo, "/player/" + id);
        }
        try {
            Files.createDirectories(AUDIO_UPLOAD_ROOT);
            String fileName = user.getId() + "-" + id + "-" + System.currentTimeMillis() + extension;
            Path target = AUDIO_UPLOAD_ROOT.resolve(fileName).normalize();
            if (!target.startsWith(AUDIO_UPLOAD_ROOT)) {
                redirectAttributes.addFlashAttribute("error", "文件名不安全");
                return redirectTo(returnTo, "/player/" + id);
            }
            Files.copy(audioFile.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
            String audioUrl = "/uploads/audio/" + fileName;
            AudioFileMetadata metadata = readAudioFileMetadata(target);
            metadata = withCloudLyricFallback(song, metadata);
            songService.updateLocalAudioMetadata(id, user.getId(), audioUrl, metadata.lyricText(), metadata.durationSeconds());
            redirectAttributes.addFlashAttribute("message", "本地音频已上传，现在优先使用项目播放器播放");
        } catch (IOException e) {
            redirectAttributes.addFlashAttribute("error", "音频上传失败，请换一个文件再试");
        }
        return redirectTo(returnTo, "/player/" + id);
    }

    @PostMapping("/songs/audio/{id}/metadata/resync")
    public String resyncSongAudioMetadata(@PathVariable Long id,
                                          @RequestParam(value = "returnTo", required = false) String returnTo,
                                          HttpSession session,
                                          RedirectAttributes redirectAttributes) {
        User user = requireUser(session);
        if (user == null) {
            return "redirect:/login";
        }
        if (!requireVip(user, redirectAttributes)) {
            return "redirect:/license";
        }
        Song song = songService.getByIdAndUserId(id, user.getId());
        if (song == null) {
            redirectAttributes.addFlashAttribute("error", "歌曲不存在");
            return "redirect:/songs";
        }
        MetadataSyncResult result = resyncLocalAudioMetadata(List.of(song), user.getId());
        redirectAttributes.addFlashAttribute(result.success() ? "message" : "error", result.message());
        return redirectTo(returnTo, "/player/" + id);
    }

    @PostMapping("/songs/video/{id}")
    public String uploadVideo(@PathVariable Long id,
                              @RequestParam("videoFile") MultipartFile videoFile,
                              @RequestParam(value = "returnTo", required = false) String returnTo,
                              HttpSession session,
                              RedirectAttributes redirectAttributes) {
        User user = requireUser(session);
        if (user == null) {
            return "redirect:/login";
        }
        if (!requireVip(user, redirectAttributes)) {
            return "redirect:/license";
        }
        Song song = songService.getByIdAndUserId(id, user.getId());
        if (song == null) {
            redirectAttributes.addFlashAttribute("error", "歌曲不存在");
            return "redirect:/songs";
        }
        if (!isBilibiliVideoSong(song)) {
            redirectAttributes.addFlashAttribute("error", "只有 B站视频音乐可以绑定本地视频");
            return redirectTo(returnTo, "/player/" + id);
        }
        if (videoFile == null || videoFile.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "请选择一个本地视频文件");
            return redirectTo(returnTo, "/player/" + id);
        }
        String extension = extensionOf(videoFile.getOriginalFilename());
        if (!VIDEO_EXTENSIONS.contains(extension)) {
            redirectAttributes.addFlashAttribute("error", "只支持 mp4、webm、m4v、mov、mkv 视频文件");
            return redirectTo(returnTo, "/player/" + id);
        }
        try {
            Files.createDirectories(VIDEO_UPLOAD_ROOT);
            String fileName = user.getId() + "-" + id + "-" + System.currentTimeMillis() + extension;
            Path target = VIDEO_UPLOAD_ROOT.resolve(fileName).normalize();
            if (!target.startsWith(VIDEO_UPLOAD_ROOT)) {
                redirectAttributes.addFlashAttribute("error", "文件名不安全");
                return redirectTo(returnTo, "/player/" + id);
            }
            Files.copy(videoFile.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
            String videoUrl = "/uploads/video/" + fileName;
            songService.updateLocalVideoUrl(id, user.getId(), videoUrl);
            redirectAttributes.addFlashAttribute("message", "本地视频已绑定，这首 B站视频现在优先使用本地文件播放");
        } catch (IOException e) {
            redirectAttributes.addFlashAttribute("error", "视频上传失败，请换一个文件再试");
        }
        return redirectTo(returnTo, "/player/" + id);
    }

    @PostMapping("/playlists/{playlistId}/audio-folder")
    public String bindAudioFolder(@PathVariable Long playlistId,
                                  @RequestParam("audioFiles") MultipartFile[] audioFiles,
                                  HttpSession session,
                                  RedirectAttributes redirectAttributes) {
        User user = requireUser(session);
        if (user == null) {
            return "redirect:/login";
        }
        if (!requireVip(user, redirectAttributes)) {
            return "redirect:/license";
        }
        Playlist playlist = playlistService.getByIdAndUserId(playlistId, user.getId());
        if (playlist == null) {
            redirectAttributes.addFlashAttribute("error", "歌单不存在");
            return "redirect:/playlists";
        }
        List<Song> songs = playlistService.listSongs(playlistId, user.getId());
        if (songs.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "这个歌单还没有歌曲，先导入歌单后再批量绑定音频");
            return "redirect:/playlists/" + playlistId;
        }
        List<AudioCandidate> candidates = usableAudioFiles(audioFiles);
        if (candidates.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "请选择包含 mp3、flac、m4a、wav、ogg 或 aac 的音乐文件夹");
            return "redirect:/playlists/" + playlistId;
        }
        BulkBindResult bindResult = bindAudioCandidates(songs, candidates, user.getId());
        redirectAttributes.addFlashAttribute(bindResult.success() ? "message" : "error", bindResult.message());
        return "redirect:/playlists/" + playlistId;
    }

    @PostMapping("/playlists/{playlistId}/audio-folder-path")
    public String bindAudioFolderPath(@PathVariable Long playlistId,
                                      @RequestParam(value = "folderPath", required = false) String folderPath,
                                      HttpSession session,
                                      RedirectAttributes redirectAttributes) {
        User user = requireUser(session);
        if (user == null) {
            return "redirect:/login";
        }
        if (!requireVip(user, redirectAttributes)) {
            return "redirect:/license";
        }
        Playlist playlist = playlistService.getByIdAndUserId(playlistId, user.getId());
        if (playlist == null) {
            redirectAttributes.addFlashAttribute("error", "歌单不存在");
            return "redirect:/playlists";
        }
        List<Song> songs = playlistService.listSongs(playlistId, user.getId());
        if (songs.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "这个歌单还没有歌曲，先导入歌单后再批量绑定音频");
            return "redirect:/playlists/" + playlistId;
        }
        List<AudioCandidate> candidates;
        try {
            candidates = scanAudioFolder(folderPath);
        } catch (IOException e) {
            redirectAttributes.addFlashAttribute("error", "读取本机音乐文件夹失败，请确认路径存在且项目有读取权限");
            return "redirect:/playlists/" + playlistId;
        }
        if (candidates.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "这个路径下没有找到可用音频文件");
            return "redirect:/playlists/" + playlistId;
        }
        BulkBindResult bindResult = bindAudioCandidates(songs, candidates, user.getId());
        redirectAttributes.addFlashAttribute(bindResult.success() ? "message" : "error", bindResult.message());
        return "redirect:/playlists/" + playlistId;
    }

    @PostMapping("/playlists/{playlistId}/audio-metadata/resync")
    public String resyncPlaylistAudioMetadata(@PathVariable Long playlistId,
                                             HttpSession session,
                                             RedirectAttributes redirectAttributes) {
        User user = requireUser(session);
        if (user == null) {
            return "redirect:/login";
        }
        if (!requireVip(user, redirectAttributes)) {
            return "redirect:/license";
        }
        Playlist playlist = playlistService.getByIdAndUserId(playlistId, user.getId());
        if (playlist == null) {
            redirectAttributes.addFlashAttribute("error", "歌单不存在");
            return "redirect:/playlists";
        }
        MetadataSyncResult result = resyncLocalAudioMetadata(playlistService.listSongs(playlistId, user.getId()), user.getId());
        redirectAttributes.addFlashAttribute(result.success() ? "message" : "error", result.message());
        return "redirect:/playlists/" + playlistId;
    }

    @PostMapping("/playlists/local-audio-folder")
    public String importLocalAudioFolder(@RequestParam(value = "playlistId", required = false) Long playlistId,
                                         @RequestParam(value = "playlistName", required = false) String playlistName,
                                         @RequestParam(value = "coverUrl", required = false) String coverUrl,
                                         @RequestParam("audioFiles") MultipartFile[] audioFiles,
                                         HttpSession session,
                                         RedirectAttributes redirectAttributes) {
        User user = requireUser(session);
        if (user == null) {
            return "redirect:/login";
        }
        if (!requireVip(user, redirectAttributes)) {
            return "redirect:/license";
        }
        List<AudioCandidate> candidates = usableAudioFiles(audioFiles);
        if (candidates.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "请选择包含 mp3、flac、m4a、wav、ogg 或 aac 的本地音乐文件夹");
            return "redirect:/playlists";
        }
        LocalAudioImportResult result = importAudioCandidatesAsPlaylist(candidates, playlistId, playlistName, coverUrl, user);
        redirectAttributes.addFlashAttribute(result.success() ? "message" : "error", result.message());
        return result.playlistId() == null ? "redirect:/playlists" : "redirect:/playlists/" + result.playlistId();
    }

    @PostMapping("/playlists/local-audio-folder-path")
    public String importLocalAudioFolderPath(@RequestParam(value = "playlistId", required = false) Long playlistId,
                                             @RequestParam(value = "playlistName", required = false) String playlistName,
                                             @RequestParam(value = "coverUrl", required = false) String coverUrl,
                                             @RequestParam(value = "folderPath", required = false) String folderPath,
                                             HttpSession session,
                                             RedirectAttributes redirectAttributes) {
        User user = requireUser(session);
        if (user == null) {
            return "redirect:/login";
        }
        if (!requireVip(user, redirectAttributes)) {
            return "redirect:/license";
        }
        List<AudioCandidate> candidates;
        try {
            candidates = scanAudioFolder(folderPath);
        } catch (IOException e) {
            redirectAttributes.addFlashAttribute("error", "读取本机音乐文件夹失败，请确认路径存在且项目有读取权限");
            return "redirect:/playlists";
        }
        if (candidates.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "这个路径下没有找到可用音频文件");
            return "redirect:/playlists";
        }
        LocalAudioImportResult result = importAudioCandidatesAsPlaylist(candidates, playlistId, playlistName, coverUrl, user);
        redirectAttributes.addFlashAttribute(result.success() ? "message" : "error", result.message());
        return result.playlistId() == null ? "redirect:/playlists" : "redirect:/playlists/" + result.playlistId();
    }

    private BulkBindResult bindAudioCandidates(List<Song> songs, List<AudioCandidate> candidates, Long userId) {
        int matched = 0;
        int syncedLyrics = 0;
        int syncedTimeline = 0;
        List<String> unmatchedSongs = new ArrayList<>();
        Set<AudioCandidate> usedFiles = new HashSet<>();
        try {
            Files.createDirectories(AUDIO_UPLOAD_ROOT);
            for (Song song : songs) {
                Match match = findBestMatch(song, candidates, usedFiles);
                if (match == null) {
                    unmatchedSongs.add(compactSongName(song));
                    continue;
                }
                AudioCandidate candidate = match.candidate();
                String extension = candidate.extension();
                String fileName = userId + "-" + song.getId() + "-" + System.currentTimeMillis() + extension;
                Path target = AUDIO_UPLOAD_ROOT.resolve(fileName).normalize();
                if (!target.startsWith(AUDIO_UPLOAD_ROOT)) {
                    continue;
                }
                copyCandidate(candidate, target);
                AudioCandidate enrichedCandidate = enrichCandidateWithAudioFile(candidate, target);
                AudioFileMetadata syncedMetadata = withCloudLyricFallback(song,
                        new AudioFileMetadata(enrichedCandidate.lyricText(), enrichedCandidate.durationSeconds()));
                boolean updated = songService.updateLocalAudioMetadata(
                        song.getId(),
                        userId,
                        "/uploads/audio/" + fileName,
                        syncedMetadata.lyricText(),
                        syncedMetadata.durationSeconds());
                if (!updated) {
                    Files.deleteIfExists(target);
                    continue;
                }
                usedFiles.add(candidate);
                matched++;
                if (syncedMetadata.lyricText() != null) {
                    syncedLyrics++;
                }
                if (syncedMetadata.durationSeconds() != null) {
                    syncedTimeline++;
                }
            }
        } catch (IOException e) {
            return new BulkBindResult(false, "批量绑定失败，请确认文件夹里的音频文件可以正常读取");
        }

        StringBuilder message = new StringBuilder("已自动绑定 " + matched + " 首本地音频");
        if (syncedLyrics > 0) {
            message.append("\uFF0C\u540C\u6B65\u6B4C\u8BCD ").append(syncedLyrics).append(" \u9996");
        }
        if (syncedTimeline > 0) {
            message.append("\uFF0C\u540C\u6B65\u65F6\u95F4\u8F74 ").append(syncedTimeline).append(" \u9996");
        }
        if (!unmatchedSongs.isEmpty()) {
            message.append("，未匹配 ").append(unmatchedSongs.size()).append(" 首：")
                    .append(String.join("、", unmatchedSongs.stream().limit(8).toList()));
            if (unmatchedSongs.size() > 8) {
                message.append(" 等");
            }
        }
        return new BulkBindResult(matched > 0, message.toString());
    }

    private LocalAudioImportResult importAudioCandidatesAsPlaylist(List<AudioCandidate> candidates,
                                                                   Long playlistId,
                                                                   String playlistName,
                                                                   String coverUrl,
                                                                   User user) {
        Playlist playlist = resolveImportPlaylist(playlistId, playlistName, coverUrl, user);
        if (playlist == null) {
            return new LocalAudioImportResult(false, null, "请选择已有歌单，或填写新歌单名称后再导入");
        }
        int imported = 0;
        int syncedLyrics = 0;
        int syncedTimeline = 0;
        List<String> failedFiles = new ArrayList<>();
        try {
            Files.createDirectories(AUDIO_UPLOAD_ROOT);
            for (AudioCandidate candidate : candidates.stream()
                    .sorted(Comparator.comparing(this::candidateName, String.CASE_INSENSITIVE_ORDER))
                    .toList()) {
                String storedName = user.getId() + "-local-" + System.currentTimeMillis() + "-" + UUID.randomUUID() + candidate.extension();
                Path target = AUDIO_UPLOAD_ROOT.resolve(storedName).normalize();
                if (!target.startsWith(AUDIO_UPLOAD_ROOT)) {
                    failedFiles.add(candidate.originalName());
                    continue;
                }
                copyCandidate(candidate, target);
                AudioCandidate enrichedCandidate = enrichCandidateWithAudioFile(candidate, target);

                Song song = buildLocalAudioSong(enrichedCandidate, playlist, user.getId(), "/uploads/audio/" + storedName);
                if (!songService.add(song)) {
                    Files.deleteIfExists(target);
                    failedFiles.add(enrichedCandidate.originalName());
                    continue;
                }
                playlistService.addSong(playlist.getId(), song.getId(), user.getId());
                imported++;
                if (enrichedCandidate.lyricText() != null) {
                    syncedLyrics++;
                }
                if (enrichedCandidate.durationSeconds() != null) {
                    syncedTimeline++;
                }
            }
        } catch (IOException e) {
            return new LocalAudioImportResult(false, playlist.getId(), "本地音频导入失败，请确认文件可以正常读取");
        }

        StringBuilder message = new StringBuilder("已从本地文件夹导入 " + imported + " 首歌曲到歌单「" + playlist.getName() + "」");
        if (syncedLyrics > 0) {
            message.append("\uFF0C\u540C\u6B65\u6B4C\u8BCD ").append(syncedLyrics).append(" \u9996");
        }
        if (syncedTimeline > 0) {
            message.append("\uFF0C\u540C\u6B65\u65F6\u95F4\u8F74 ").append(syncedTimeline).append(" \u9996");
        }
        if (!failedFiles.isEmpty()) {
            message.append("，失败 ").append(failedFiles.size()).append(" 个文件：")
                    .append(String.join("、", failedFiles.stream().limit(6).map(this::baseName).toList()));
            if (failedFiles.size() > 6) {
                message.append(" 等");
            }
        }
        return new LocalAudioImportResult(imported > 0, playlist.getId(), message.toString());
    }

    private MetadataSyncResult resyncLocalAudioMetadata(List<Song> songs, Long userId) {
        if (songs == null || songs.isEmpty()) {
            return new MetadataSyncResult(false, "没有可扫描的歌曲");
        }
        int scanned = 0;
        int updated = 0;
        int syncedLyrics = 0;
        int syncedDuration = 0;
        int missingFiles = 0;
        for (Song song : songs) {
            if (song == null || song.getId() == null || song.getLocalAudioUrl() == null || song.getLocalAudioUrl().isBlank()) {
                continue;
            }
            Path audioPath = resolveLocalAudioPath(song.getLocalAudioUrl());
            if (audioPath == null || !Files.isRegularFile(audioPath)) {
                missingFiles++;
                continue;
            }
            scanned++;
            AudioFileMetadata metadata = readAudioFileMetadata(audioPath);
            metadata = withCloudLyricFallback(song, metadata);
            if (metadata.lyricText() == null && metadata.durationSeconds() == null) {
                continue;
            }
            boolean success = songService.updateLocalAudioMetadata(
                    song.getId(),
                    userId,
                    song.getLocalAudioUrl(),
                    metadata.lyricText(),
                    metadata.durationSeconds());
            if (success) {
                updated++;
                if (metadata.lyricText() != null) {
                    syncedLyrics++;
                }
                if (metadata.durationSeconds() != null) {
                    syncedDuration++;
                }
            }
        }
        if (updated == 0) {
            String message = scanned == 0
                    ? "没有找到已绑定的本地音频文件"
                    : "已扫描本地音频，但文件里没有可读取的内嵌歌词；请放同名 .lrc 后再批量绑定";
            if (missingFiles > 0) {
                message += "，缺失文件 " + missingFiles + " 个";
            }
            return new MetadataSyncResult(false, message);
        }
        String message = "已重新读取 " + scanned + " 个本地音频，更新 " + updated + " 首";
        if (syncedLyrics > 0) {
            message += "，同步歌词 " + syncedLyrics + " 首";
        }
        if (syncedDuration > 0) {
            message += "，同步时长 " + syncedDuration + " 首";
        }
        if (missingFiles > 0) {
            message += "，缺失文件 " + missingFiles + " 个";
        }
        return new MetadataSyncResult(true, message);
    }

    private Playlist resolveImportPlaylist(Long playlistId, String playlistName, String coverUrl, User user) {
        if (playlistId != null && playlistId > 0) {
            return playlistService.getByIdAndUserId(playlistId, user.getId());
        }
        if (!vipQuotaService.canAddPlaylist(user)) {
            return null;
        }
        String cleanName = playlistName == null || playlistName.isBlank()
                ? "本地音乐导入"
                : playlistName.trim();
        Playlist playlist = new Playlist();
        playlist.setUserId(user.getId());
        playlist.setName(cleanName);
        playlist.setDescription("从本地音频文件夹直接导入");
        playlist.setCoverUrl(coverUrl == null || coverUrl.isBlank() ? "/images/login-music-room.png" : coverUrl.trim());
        return playlistService.add(playlist) ? playlist : null;
    }

    private Song buildLocalAudioSong(AudioCandidate candidate, Playlist playlist, Long userId, String localAudioUrl) {
        AudioMetadata metadata = inferAudioMetadata(candidate.originalName());
        Song song = new Song();
        song.setUserId(userId);
        song.setTitle(fit(metadata.title(), 160));
        song.setArtist(fit(metadata.artist(), 160));
        song.setAnimeTitle("本地音乐");
        song.setAlbumName(fit(playlist.getName(), 160));
        song.setCoverUrl(playlist.getCoverUrl());
        song.setAudioUrl("");
        song.setLocalAudioUrl(localAudioUrl);
        song.setSkinUrl("/images/login-music-room.png");
        song.setTags("本地导入");
        song.setMood("本地");
        song.setLyricText(candidate.lyricText() == null
                ? cloudLyricService.fetchBestLyric(song.getTitle(), song.getArtist())
                : candidate.lyricText());
        song.setDurationSeconds(candidate.durationSeconds());
        song.setFavorite(false);
        return song;
    }

    private AudioFileMetadata withCloudLyricFallback(Song song, AudioFileMetadata metadata) {
        if (metadata == null) {
            metadata = new AudioFileMetadata(null, null);
        }
        if (metadata.lyricText() != null || song == null) {
            return metadata;
        }
        String lyricText = cloudLyricForSong(song);
        return lyricText == null
                ? metadata
                : new AudioFileMetadata(lyricText, metadata.durationSeconds());
    }

    private String cloudLyricForSong(Song song) {
        String neteaseSongId = song.getNeteaseSongId();
        if (neteaseSongId != null && !neteaseSongId.isBlank()) {
            return cloudLyricService.fetchNeteaseLyric(neteaseSongId);
        }
        String qqValue = song.getQqSongMid();
        if (qqValue != null && !qqValue.isBlank()) {
            if (qqValue.chars().allMatch(Character::isDigit)) {
                return cloudLyricService.fetchQqLyric(null, Long.parseLong(qqValue));
            }
            return cloudLyricService.fetchQqLyric(qqValue, null);
        }
        return cloudLyricService.fetchBestLyric(song.getTitle(), song.getArtist());
    }

    private AudioMetadata inferAudioMetadata(String originalName) {
        String name = baseName(originalName).replaceFirst("^\\d{1,3}[.\\-_\\s]+", "").trim();
        String[] separators = {" - ", " – ", " — ", "_-_"};
        for (String separator : separators) {
            int index = name.indexOf(separator);
            if (index > 0 && index < name.length() - separator.length()) {
                String artist = name.substring(0, index).trim();
                String title = name.substring(index + separator.length()).trim();
                return new AudioMetadata(title.isBlank() ? name : title, artist.isBlank() ? "本地文件" : artist);
            }
        }
        return new AudioMetadata(name.isBlank() ? "未命名歌曲" : name, "本地文件");
    }

    @PostMapping("/playlists/add")
    public String addPlaylist(Playlist playlist, HttpSession session, RedirectAttributes redirectAttributes) {
        User user = requireUser(session);
        if (user == null) {
            return "redirect:/login";
        }
        if (!vipQuotaService.canAddPlaylist(user)) {
            redirectAttributes.addFlashAttribute("error", vipQuotaService.playlistLimitMessage(user));
            return "redirect:/license";
        }
        playlist.setUserId(user.getId());
        boolean success = playlistService.add(playlist);
        redirectAttributes.addFlashAttribute(success ? "message" : "error",
                success ? "专属歌单创建完成" : "歌单名称不能为空");
        return "redirect:/playlists";
    }

    @PostMapping("/playlists/update/{id}")
    public String updatePlaylist(@PathVariable Long id,
                                 Playlist playlist,
                                 HttpSession session,
                                 RedirectAttributes redirectAttributes) {
        User user = requireUser(session);
        if (user == null) {
            return "redirect:/login";
        }
        playlist.setId(id);
        boolean success = playlistService.update(playlist, user.getId());
        redirectAttributes.addFlashAttribute(success ? "message" : "error",
                success ? "歌单信息已更新" : "歌单更新失败，请检查歌单名称");
        return success ? "redirect:/playlists/" + id : "redirect:/playlists/" + id + "/edit";
    }

    @PostMapping("/playlists/delete/{id}")
    public String deletePlaylist(@PathVariable Long id, HttpSession session) {
        User user = requireUser(session);
        if (user == null) {
            return "redirect:/login";
        }
        playlistService.delete(id, user.getId());
        return "redirect:/playlists";
    }

    @PostMapping("/playlists/{playlistId}/songs/add")
    public String addSongToPlaylist(@PathVariable Long playlistId,
                                    @RequestParam("songId") Long songId,
                                    HttpSession session) {
        User user = requireUser(session);
        if (user == null) {
            return "redirect:/login";
        }
        playlistService.addSong(playlistId, songId, user.getId());
        return "redirect:/playlists/" + playlistId;
    }

    @PostMapping("/playlists/{playlistId}/songs/remove/{songId}")
    public String removeSongFromPlaylist(@PathVariable Long playlistId,
                                         @PathVariable Long songId,
                                         HttpSession session) {
        User user = requireUser(session);
        if (user == null) {
            return "redirect:/login";
        }
        playlistService.removeSong(playlistId, songId, user.getId());
        return "redirect:/playlists/" + playlistId;
    }

    @PostMapping("/netease/import")
    public String importNeteaseSongs(@RequestParam(value = "songIds", required = false) String songIds,
                                     @RequestParam(value = "link", required = false) String link,
                                     @RequestParam(value = "playlistId", required = false) Long playlistId,
                                     HttpSession session,
                                     RedirectAttributes redirectAttributes) {
        User user = requireUser(session);
        if (user == null) {
            return "redirect:/login";
        }
        if (!requireVip(user, redirectAttributes)) {
            return "redirect:/license";
        }
        if ((songIds == null || songIds.isBlank()) && link != null) {
            songIds = link;
        }
        NeteaseImportResult result = neteaseImportService.importSongs(songIds, playlistId, user.getId());
        if (result.isSuccess()) {
            redirectAttributes.addFlashAttribute("message", result.getMessage());
            return "redirect:/playlists/" + result.getPlaylistId();
        }
        redirectAttributes.addFlashAttribute("error", result.getMessage());
        redirectAttributes.addFlashAttribute("songIds", songIds);
        return "redirect:/netease";
    }

    @PostMapping("/qq/import")
    public String importQqSongs(@RequestParam(value = "songIds", required = false) String songIds,
                                @RequestParam(value = "link", required = false) String link,
                                @RequestParam(value = "playlistId", required = false) Long playlistId,
                                HttpSession session,
                                RedirectAttributes redirectAttributes) {
        User user = requireUser(session);
        if (user == null) {
            return "redirect:/login";
        }
        if (!requireVip(user, redirectAttributes)) {
            return "redirect:/license";
        }
        if ((songIds == null || songIds.isBlank()) && link != null) {
            songIds = link;
        }
        MusicImportResult result = qqImportService.importSongs(songIds, playlistId, user.getId());
        if (result.isSuccess()) {
            redirectAttributes.addFlashAttribute("message", result.getMessage());
            return "redirect:/playlists/" + result.getPlaylistId();
        }
        redirectAttributes.addFlashAttribute("error", result.getMessage());
        redirectAttributes.addFlashAttribute("songIds", songIds);
        return "redirect:/qq";
    }

    @PostMapping("/bilibili/import")
    public String importBilibiliVideos(@RequestParam(value = "videoIds", required = false) String videoIds,
                                       @RequestParam(value = "link", required = false) String link,
                                       @RequestParam(value = "playlistId", required = false) Long playlistId,
                                       HttpSession session,
                                       RedirectAttributes redirectAttributes) {
        User user = requireUser(session);
        if (user == null) {
            return "redirect:/login";
        }
        if (!requireVip(user, redirectAttributes)) {
            return "redirect:/license";
        }
        if ((videoIds == null || videoIds.isBlank()) && link != null) {
            videoIds = link;
        }
        MusicImportResult result = bilibiliImportService.importVideos(videoIds, playlistId, user.getId());
        if (result.isSuccess()) {
            redirectAttributes.addFlashAttribute("message", result.getMessage());
            return "redirect:/playlists/" + result.getPlaylistId();
        }
        redirectAttributes.addFlashAttribute("error", result.getMessage());
        redirectAttributes.addFlashAttribute("videoIds", videoIds);
        return "redirect:/bilibili";
    }

    private String redirectTo(String returnTo, String fallback) {
        if (returnTo != null && returnTo.startsWith("/") && !returnTo.startsWith("//")) {
            return "redirect:" + returnTo;
        }
        return "redirect:" + fallback;
    }

    private String extensionOf(String fileName) {
        if (fileName == null) {
            return "";
        }
        int dot = fileName.lastIndexOf('.');
        return dot >= 0 ? fileName.substring(dot).toLowerCase(Locale.ROOT) : "";
    }

    private List<AudioCandidate> usableAudioFiles(MultipartFile[] files) {
        if (files == null || files.length == 0) {
            return List.of();
        }
        Map<String, MultipartFile> lyricFiles = new HashMap<>();
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                continue;
            }
            String originalName = file.getOriginalFilename();
            if (LYRIC_EXTENSIONS.contains(extensionOf(originalName))) {
                lyricFiles.putIfAbsent(normalizeForMatch(baseName(originalName)), file);
            }
        }
        List<AudioCandidate> usableFiles = new ArrayList<>();
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                continue;
            }
            String originalName = file.getOriginalFilename();
            String extension = extensionOf(originalName);
            if (AUDIO_EXTENSIONS.contains(extension)) {
                String normalizedName = normalizeForMatch(baseName(originalName));
                String lyricText = readLyricSafely(lyricFiles.get(normalizedName));
                usableFiles.add(new AudioCandidate(
                        file,
                        null,
                        originalName,
                        extension,
                        normalizedName,
                        lyricText,
                        inferDurationSeconds(lyricText)));
            }
        }
        return usableFiles;
    }

    private List<AudioCandidate> scanAudioFolder(String folderPath) throws IOException {
        if (folderPath == null || folderPath.isBlank()) {
            return List.of();
        }
        Path root = Paths.get(folderPath.trim()).toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        List<Path> audioPaths = new ArrayList<>();
        Map<String, Path> lyricFiles = new HashMap<>();
        try (Stream<Path> paths = Files.walk(root, 8)) {
            paths.filter(Files::isRegularFile)
                    .forEach(path -> {
                        String fileName = path.getFileName().toString();
                        String extension = extensionOf(fileName);
                        if (LYRIC_EXTENSIONS.contains(extension)) {
                            lyricFiles.putIfAbsent(normalizeForMatch(baseName(fileName)), path.toAbsolutePath().normalize());
                            return;
                        }
                        if (AUDIO_EXTENSIONS.contains(extension) && audioPaths.size() < 5000) {
                            audioPaths.add(path.toAbsolutePath().normalize());
                        }
                    });
        }
        List<AudioCandidate> candidates = new ArrayList<>();
        for (Path audioPath : audioPaths.stream()
                .sorted(Comparator.comparing(path -> path.getFileName().toString(), String.CASE_INSENSITIVE_ORDER))
                .toList()) {
            String fileName = audioPath.getFileName().toString();
            String extension = extensionOf(fileName);
            String normalizedName = normalizeForMatch(baseName(fileName));
            String lyricText = readLyricSafely(lyricFiles.get(normalizedName));
            candidates.add(new AudioCandidate(
                    null,
                    audioPath,
                    fileName,
                    extension,
                    normalizedName,
                    lyricText,
                    inferDurationSeconds(lyricText)));
        }
        return candidates;
    }

    private void copyCandidate(AudioCandidate candidate, Path target) throws IOException {
        if (candidate.file() != null) {
            Files.copy(candidate.file().getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
            return;
        }
        Files.copy(candidate.path(), target, StandardCopyOption.REPLACE_EXISTING);
    }

    private AudioCandidate enrichCandidateWithAudioFile(AudioCandidate candidate, Path audioPath) {
        AudioFileMetadata metadata = readAudioFileMetadata(audioPath);
        String lyricText = firstNonBlank(candidate.lyricText(), metadata.lyricText());
        Integer durationSeconds = candidate.durationSeconds() != null
                ? candidate.durationSeconds()
                : metadata.durationSeconds();
        if (durationSeconds == null) {
            durationSeconds = inferDurationSeconds(lyricText);
        }
        return new AudioCandidate(
                candidate.file(),
                candidate.path(),
                candidate.originalName(),
                candidate.extension(),
                candidate.normalizedName(),
                lyricText,
                durationSeconds);
    }

    private AudioFileMetadata readAudioFileMetadata(Path audioPath) {
        if (audioPath == null || !Files.isRegularFile(audioPath)) {
            return new AudioFileMetadata(null, null);
        }
        String sidecarLyric = readSidecarLyric(audioPath);
        try {
            AudioFile audioFile = AudioFileIO.read(audioPath.toFile());
            String lyricText = firstNonBlank(sidecarLyric, readEmbeddedLyrics(audioFile.getTag()));
            Integer durationSeconds = readDurationSeconds(audioFile.getAudioHeader());
            if (durationSeconds == null) {
                durationSeconds = inferDurationSeconds(lyricText);
            }
            return new AudioFileMetadata(lyricText, durationSeconds);
        } catch (Exception e) {
            return new AudioFileMetadata(sidecarLyric, inferDurationSeconds(sidecarLyric));
        }
    }

    private String readSidecarLyric(Path audioPath) {
        if (audioPath == null || audioPath.getParent() == null) {
            return null;
        }
        String baseName = baseName(audioPath.getFileName().toString());
        for (String extension : LYRIC_EXTENSIONS) {
            String lyricFileName = baseName + extension;
            String upperLyricFileName = baseName + extension.toUpperCase(Locale.ROOT);
            String lowerLyricFileName = baseName + extension.toLowerCase(Locale.ROOT);
            String lyric = firstNonBlank(
                    readLyricSafely(audioPath.getParent().resolve(lyricFileName)),
                    readLyricSafely(audioPath.getParent().resolve(upperLyricFileName)),
                    readLyricSafely(audioPath.getParent().resolve(lowerLyricFileName)));
            if (lyric != null) {
                return lyric;
            }
        }
        return null;
    }

    private String readEmbeddedLyrics(Tag tag) {
        if (tag == null) {
            return null;
        }
        return firstNonBlank(
                safeTagValue(tag, FieldKey.LYRICS),
                safeTagValue(tag, "LYRICS"),
                safeTagValue(tag, "UNSYNCEDLYRICS"),
                safeTagValue(tag, "SYNCEDLYRICS"),
                safeTagValue(tag, "UNSYNCED LYRICS"),
                safeTagValue(tag, "SYNCLYRICS"));
    }

    private String safeTagValue(Tag tag, FieldKey key) {
        try {
            return cleanLyric(tag.getFirst(key));
        } catch (Exception e) {
            return null;
        }
    }

    private String safeTagValue(Tag tag, String key) {
        try {
            return cleanLyric(tag.getFirst(key));
        } catch (Exception e) {
            return null;
        }
    }

    private Integer readDurationSeconds(AudioHeader header) {
        if (header == null) {
            return null;
        }
        double preciseLength = header.getPreciseTrackLength();
        if (Double.isFinite(preciseLength) && preciseLength > 0) {
            return Math.max(1, (int) Math.round(preciseLength));
        }
        int trackLength = header.getTrackLength();
        return trackLength > 0 ? trackLength : null;
    }

    private String readLyricSafely(MultipartFile lyricFile) {
        if (lyricFile == null || lyricFile.isEmpty()) {
            return null;
        }
        try {
            byte[] bytes = lyricFile.getBytes();
            String text = new String(bytes, StandardCharsets.UTF_8);
            if (looksBrokenEncoding(text)) {
                text = new String(bytes, GB18030);
            }
            return cleanLyric(text);
        } catch (IOException e) {
            return null;
        }
    }

    private String readLyricSafely(Path lyricPath) {
        if (lyricPath == null || !Files.isRegularFile(lyricPath)) {
            return null;
        }
        try {
            return cleanLyric(Files.readString(lyricPath, StandardCharsets.UTF_8));
        } catch (IOException e) {
            try {
                return cleanLyric(Files.readString(lyricPath, GB18030));
            } catch (IOException ignored) {
                return null;
            }
        }
    }

    private Integer inferDurationSeconds(String lyricText) {
        if (lyricText == null || lyricText.isBlank()) {
            return null;
        }
        Matcher matcher = LRC_TIMESTAMP.matcher(lyricText);
        int maxSeconds = -1;
        while (matcher.find()) {
            int minutes = Integer.parseInt(matcher.group(1));
            int seconds = Integer.parseInt(matcher.group(2));
            maxSeconds = Math.max(maxSeconds, minutes * 60 + seconds);
        }
        return maxSeconds >= 0 ? Math.min(maxSeconds + 8, 24 * 60 * 60) : null;
    }

    private String cleanLyric(String text) {
        if (text == null) {
            return null;
        }
        String cleaned = text.replace("\uFEFF", "")
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .trim();
        return cleaned.isEmpty() ? null : cleaned;
    }

    private boolean looksBrokenEncoding(String text) {
        return text != null && (text.indexOf('\uFFFD') >= 0 || text.contains("\u951F\u65A4\u62F7"));
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            String cleaned = cleanLyric(value);
            if (cleaned != null) {
                return cleaned;
            }
        }
        return null;
    }

    private Match findBestMatch(Song song, List<AudioCandidate> files, Set<AudioCandidate> usedFiles) {
        String title = normalizeForMatch(song.getTitle());
        String artist = normalizeForMatch(song.getArtist());
        String artistTitle = artist + title;
        String titleArtist = title + artist;
        return files.stream()
                .filter(file -> !usedFiles.contains(file))
                .map(file -> new Match(file, matchScore(file, title, artist, artistTitle, titleArtist)))
                .filter(match -> match.score() >= 70)
                .max(Comparator.comparingInt(Match::score))
                .orElse(null);
    }

    private int matchScore(AudioCandidate file, String title, String artist, String artistTitle, String titleArtist) {
        String name = file.normalizedName();
        if (name.isBlank() || title.isBlank()) {
            return 0;
        }
        int score = 0;
        if (name.equals(artistTitle) || name.equals(titleArtist)) {
            score = 130;
        } else if (name.equals(title)) {
            score = 115;
        } else if (name.contains(title)) {
            score = 75 + Math.min(title.length(), 25);
        } else if (title.contains(name) && name.length() >= 4) {
            score = 70;
        }
        if (!artist.isBlank() && name.contains(artist)) {
            score += 25;
        }
        return score;
    }

    private String baseName(String fileName) {
        if (fileName == null) {
            return "";
        }
        String normalizedPath = fileName.replace('\\', '/');
        String name = normalizedPath.substring(normalizedPath.lastIndexOf('/') + 1);
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(0, dot) : name;
    }

    private Path resolveLocalAudioPath(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        String cleanUrl = url.trim().replace('\\', '/');
        if (cleanUrl.startsWith("/uploads/audio/")) {
            String fileName = cleanUrl.substring("/uploads/audio/".length());
            Path path = AUDIO_UPLOAD_ROOT.resolve(fileName).normalize();
            return path.startsWith(AUDIO_UPLOAD_ROOT) ? path : null;
        }
        if (cleanUrl.startsWith("/external-uploads/audio/")) {
            String fileName = cleanUrl.substring("/external-uploads/audio/".length());
            Path externalAudioRoot = EXTERNAL_UPLOAD_ROOT.resolve("audio").normalize();
            Path path = externalAudioRoot.resolve(fileName).normalize();
            return path.startsWith(externalAudioRoot) ? path : null;
        }
        return null;
    }

    private String candidateName(AudioCandidate candidate) {
        return candidate == null || candidate.originalName() == null ? "" : candidate.originalName();
    }

    private String normalizeForMatch(String value) {
        if (value == null) {
            return "";
        }
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        StringBuilder builder = new StringBuilder(normalized.length());
        for (int i = 0; i < normalized.length(); i++) {
            char ch = normalized.charAt(i);
            if (Character.isLetterOrDigit(ch)) {
                builder.append(ch);
            }
        }
        return builder.toString();
    }

    private String compactSongName(Song song) {
        if (song == null) {
            return "未知歌曲";
        }
        String title = song.getTitle() == null ? "未知歌曲" : song.getTitle();
        if (song.getArtist() == null || song.getArtist().isBlank()) {
            return title;
        }
        return song.getArtist() + " - " + title;
    }

    private String fit(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }

    private boolean isBilibiliVideoSong(Song song) {
        return song != null
                && song.getAudioUrl() != null
                && song.getAudioUrl().contains("bilibili.com");
    }

    private boolean requireVip(User user, RedirectAttributes redirectAttributes) {
        LicenseStatus status = licenseService.statusOf(user);
        if (status.isVip()) {
            return true;
        }
        redirectAttributes.addFlashAttribute("error", "这是 VIP 高级功能，请先激活专业版或永久版授权码。");
        return false;
    }

    private User requireUser(HttpSession session) {
        Object loginUser = session.getAttribute("loginUser");
        if (loginUser instanceof User user) {
            return user;
        }
        return null;
    }

    private static Path externalUploadRoot() {
        String configured = System.getProperty("sekai.externalUploadRoot");
        if (configured == null || configured.isBlank()) {
            configured = System.getenv("SEKAI_EXTERNAL_UPLOAD_ROOT");
        }
        if (configured == null || configured.isBlank()) {
            configured = "D:/SekaiSongExternalUploads/memory-5/uploads";
        }
        return Paths.get(configured).toAbsolutePath().normalize();
    }

    private record AudioCandidate(MultipartFile file,
                                  Path path,
                                  String originalName,
                                  String extension,
                                  String normalizedName,
                                  String lyricText,
                                  Integer durationSeconds) {
    }

    private record Match(AudioCandidate candidate, int score) {
    }

    private record BulkBindResult(boolean success, String message) {
    }

    private record LocalAudioImportResult(boolean success, Long playlistId, String message) {
    }

    private record MetadataSyncResult(boolean success, String message) {
    }

    private record AudioMetadata(String title, String artist) {
    }

    private record AudioFileMetadata(String lyricText, Integer durationSeconds) {
    }
}
