package com.sekai.song.service.impl;

import com.sekai.song.model.MaintenanceIssue;
import com.sekai.song.model.MaintenanceReport;
import com.sekai.song.model.Playlist;
import com.sekai.song.model.PlaylistMaintenanceReport;
import com.sekai.song.model.Song;
import com.sekai.song.model.User;
import com.sekai.song.service.MaintenanceService;
import com.sekai.song.service.PlaylistService;
import com.sekai.song.util.UploadPathUtil;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class MaintenanceServiceImpl implements MaintenanceService {
    private static final int ISSUE_LIMIT = 120;
    private static final Path UPLOAD_ROOT = UploadPathUtil.uploadRoot();
    private static final Path EXTERNAL_UPLOAD_ROOT = externalUploadRoot();

    @Resource
    private PlaylistService playlistService;

    @Override
    public MaintenanceReport buildReport(User user) {
        MaintenanceReport report = new MaintenanceReport();
        if (user == null || user.getId() == null) {
            return report;
        }
        List<Playlist> playlists = playlistService.listByUserId(user.getId());
        report.setPlaylistCount(playlists.size());
        for (Playlist playlist : playlists) {
            PlaylistMaintenanceReport playlistReport = analyzePlaylist(playlist, user.getId(), report);
            report.getPlaylists().add(playlistReport);
        }
        return report;
    }

    @Override
    public int deduplicatePlaylist(Long playlistId, User user) {
        if (playlistId == null || user == null || user.getId() == null) {
            return 0;
        }
        Playlist playlist = playlistService.getByIdAndUserId(playlistId, user.getId());
        if (playlist == null) {
            return 0;
        }
        return removeDuplicateSongs(playlistId, user.getId());
    }

    @Override
    public int deduplicateAll(User user) {
        if (user == null || user.getId() == null) {
            return 0;
        }
        int removed = 0;
        for (Playlist playlist : playlistService.listByUserId(user.getId())) {
            removed += removeDuplicateSongs(playlist.getId(), user.getId());
        }
        return removed;
    }

    private PlaylistMaintenanceReport analyzePlaylist(Playlist playlist, Long userId, MaintenanceReport report) {
        PlaylistMaintenanceReport playlistReport = new PlaylistMaintenanceReport();
        playlistReport.setPlaylistId(playlist.getId());
        playlistReport.setPlaylistName(playlist.getName());
        List<Song> songs = playlistService.listSongs(playlist.getId(), userId);
        playlistReport.setSongCount(songs.size());
        report.setTotalSongs(report.getTotalSongs() + songs.size());

        Map<String, List<Song>> duplicates = new LinkedHashMap<>();
        for (Song song : songs) {
            analyzeSong(playlist, playlistReport, report, song);
            String key = duplicateKey(song);
            if (!key.isBlank()) {
                duplicates.computeIfAbsent(key, ignored -> new ArrayList<>()).add(song);
            }
        }

        for (List<Song> group : duplicates.values()) {
            if (group.size() <= 1) {
                continue;
            }
            int duplicateCount = group.size() - 1;
            playlistReport.setDuplicateGroupCount(playlistReport.getDuplicateGroupCount() + 1);
            playlistReport.setDuplicateSongCount(playlistReport.getDuplicateSongCount() + duplicateCount);
            report.setDuplicateGroupCount(report.getDuplicateGroupCount() + 1);
            report.setDuplicateSongCount(report.getDuplicateSongCount() + duplicateCount);
            addIssue(report, new MaintenanceIssue(
                    "重复歌曲",
                    "warning",
                    playlist.getName(),
                    playlist.getId(),
                    group.get(0).getId(),
                    compactSongName(group.get(0)),
                    "这个歌单里有 " + group.size() + " 条相同歌曲，清理后会保留第一条。",
                    "可一键清理重复项"));
        }
        return playlistReport;
    }

    private void analyzeSong(Playlist playlist, PlaylistMaintenanceReport playlistReport, MaintenanceReport report, Song song) {
        boolean bilibili = isBilibili(song);
        boolean hasLocalAudio = hasText(song.getLocalAudioUrl());
        boolean hasLocalVideo = hasText(song.getLocalVideoUrl());

        if (hasLocalAudio) {
            playlistReport.setLocalAudioCount(playlistReport.getLocalAudioCount() + 1);
            report.setLocalAudioCount(report.getLocalAudioCount() + 1);
            if (!localFileExists(song.getLocalAudioUrl())) {
                playlistReport.setMissingLocalAudioCount(playlistReport.getMissingLocalAudioCount() + 1);
                report.setMissingLocalAudioCount(report.getMissingLocalAudioCount() + 1);
                addIssue(report, new MaintenanceIssue(
                        "本地音频缺失",
                        "danger",
                        playlist.getName(),
                        playlist.getId(),
                        song.getId(),
                        compactSongName(song),
                        "数据库绑定了本地音频，但项目文件夹里找不到对应文件。",
                        "重新上传或批量绑定本地音频"));
            }
        }

        if (bilibili) {
            playlistReport.setBilibiliVideoCount(playlistReport.getBilibiliVideoCount() + 1);
            report.setBilibiliVideoCount(report.getBilibiliVideoCount() + 1);
            if (hasLocalVideo) {
                playlistReport.setLocalVideoCount(playlistReport.getLocalVideoCount() + 1);
                report.setLocalVideoCount(report.getLocalVideoCount() + 1);
                if (!localFileExists(song.getLocalVideoUrl())) {
                    playlistReport.setMissingLocalVideoCount(playlistReport.getMissingLocalVideoCount() + 1);
                    report.setMissingLocalVideoCount(report.getMissingLocalVideoCount() + 1);
                    addIssue(report, new MaintenanceIssue(
                            "本地视频缺失",
                            "danger",
                            playlist.getName(),
                            playlist.getId(),
                            song.getId(),
                            compactSongName(song),
                            "B站视频已绑定本地文件，但文件不存在或路径已失效。",
                            "重新绑定本地视频"));
                }
            } else {
                addIssue(report, new MaintenanceIssue(
                        "B站未本地化",
                        "warning",
                        playlist.getName(),
                        playlist.getId(),
                        song.getId(),
                        compactSongName(song),
                        "仍依赖 B 站在线播放，客户电脑或网络差时可能卡顿。",
                        "下载视频后绑定本地文件"));
            }
            return;
        }

        if (!hasLocalAudio && isThirdPartyAudio(song)) {
            playlistReport.setThirdPartyOnlyCount(playlistReport.getThirdPartyOnlyCount() + 1);
            report.setThirdPartyOnlyCount(report.getThirdPartyOnlyCount() + 1);
            addIssue(report, new MaintenanceIssue(
                    "第三方链接播放",
                    "warning",
                    playlist.getName(),
                    playlist.getId(),
                    song.getId(),
                    compactSongName(song),
                    "这首歌还没有本地音频，网易云/QQ 的 VIP 或版权歌曲可能播放不了。",
                    "上传或批量绑定本地音频"));
        }

        if (!hasLocalAudio && !hasText(song.getAudioUrl())) {
            playlistReport.setUnplayableCount(playlistReport.getUnplayableCount() + 1);
            report.setUnplayableCount(report.getUnplayableCount() + 1);
            addIssue(report, new MaintenanceIssue(
                    "不可播放",
                    "danger",
                    playlist.getName(),
                    playlist.getId(),
                    song.getId(),
                    compactSongName(song),
                    "没有本地音频，也没有可用的第三方链接。",
                    "上传音频文件"));
        }
    }

    private int removeDuplicateSongs(Long playlistId, Long userId) {
        Map<String, Song> kept = new LinkedHashMap<>();
        int removed = 0;
        for (Song song : playlistService.listSongs(playlistId, userId)) {
            String key = duplicateKey(song);
            if (key.isBlank() || !kept.containsKey(key)) {
                kept.put(key, song);
                continue;
            }
            if (playlistService.removeSong(playlistId, song.getId(), userId)) {
                removed++;
            }
        }
        return removed;
    }

    private boolean localFileExists(String url) {
        Path path = resolveLocalFile(url);
        return path != null && Files.isRegularFile(path);
    }

    private Path resolveLocalFile(String url) {
        if (!hasText(url)) {
            return null;
        }
        String clean = url.trim().replace('\\', '/');
        if (clean.startsWith("/uploads/")) {
            return UPLOAD_ROOT.resolve(clean.substring("/uploads/".length())).normalize();
        }
        if (clean.startsWith("uploads/")) {
            return UPLOAD_ROOT.resolve(clean.substring("uploads/".length())).normalize();
        }
        if (clean.startsWith("/external-uploads/")) {
            return EXTERNAL_UPLOAD_ROOT.resolve(clean.substring("/external-uploads/".length())).normalize();
        }
        if (clean.startsWith("external-uploads/")) {
            return EXTERNAL_UPLOAD_ROOT.resolve(clean.substring("external-uploads/".length())).normalize();
        }
        return null;
    }

    private String duplicateKey(Song song) {
        String titleArtist = normalize(song.getTitle()) + "|" + normalize(song.getArtist());
        if (!"|".equals(titleArtist) && titleArtist.length() > 1) {
            return titleArtist;
        }
        if (hasText(song.getAudioUrl())) {
            return "url|" + normalize(song.getAudioUrl());
        }
        if (hasText(song.getLocalAudioUrl())) {
            return "local|" + normalize(song.getLocalAudioUrl());
        }
        return "";
    }

    private String normalize(String value) {
        if (!hasText(value)) {
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

    private boolean isBilibili(Song song) {
        return song != null && hasText(song.getAudioUrl()) && song.getAudioUrl().contains("bilibili.com");
    }

    private boolean isThirdPartyAudio(Song song) {
        return song != null
                && hasText(song.getAudioUrl())
                && (song.getAudioUrl().contains("music.163.com") || song.getAudioUrl().contains("y.qq.com"));
    }

    private String compactSongName(Song song) {
        if (song == null) {
            return "未知歌曲";
        }
        if (!hasText(song.getArtist())) {
            return hasText(song.getTitle()) ? song.getTitle() : "未知歌曲";
        }
        return song.getArtist() + " - " + song.getTitle();
    }

    private void addIssue(MaintenanceReport report, MaintenanceIssue issue) {
        if (report.getIssues().size() < ISSUE_LIMIT) {
            report.getIssues().add(issue);
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
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
}
