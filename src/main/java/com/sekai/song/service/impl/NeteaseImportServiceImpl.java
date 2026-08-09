package com.sekai.song.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sekai.song.mapper.SekaiSongPlaylistMapper;
import com.sekai.song.mapper.SekaiSongTrackMapper;
import com.sekai.song.model.NeteaseImportResult;
import com.sekai.song.model.NeteaseLink;
import com.sekai.song.model.Playlist;
import com.sekai.song.model.Song;
import com.sekai.song.service.CloudLyricService;
import com.sekai.song.service.NeteaseImportService;
import com.sekai.song.util.NeteaseMusicLinkUtil;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.StringJoiner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class NeteaseImportServiceImpl implements NeteaseImportService {

    private static final List<String> PLAYLIST_API_TEMPLATES = List.of(
            "https://music.163.com/api/v6/playlist/detail?id=%s&n=100000&s=0",
            "https://music.163.com/api/playlist/detail?id=%s&n=100000&s=0",
            "https://music.163.com/api/playlist/detail?id=%s");
    private static final String SONG_DETAIL_API = "https://music.163.com/api/song/detail?ids=";
    private static final String SONG_DETAIL_API_V3 = "https://music.163.com/api/v3/song/detail?c=";
    private static final String FIXED_IMPORT_PLAYLIST_NAME = "网易云歌曲导入";
    private static final int SONG_DETAIL_BATCH_SIZE = 50;
    private static final int SONG_DETAIL_RETRY_TIMES = 2;
    private static final Pattern PLAYLIST_LINK_ID = Pattern.compile("(?i)(?:playlist\\?id=|/playlist/(?:\\?id=)?|\\b(?:playlistId|playlist_id|pid)[\"']?\\s*[:=]\\s*[\"']?)(\\d{4,})");
    private static final Pattern SONG_LINK_ID = Pattern.compile("(?:song\\?id=|/song/(?:\\?id=)?|\\bid[\"']?\\s*[:=]\\s*[\"']?)(\\d{4,})");
    private static final Pattern NUMBER_LINE = Pattern.compile("^\\D*(\\d{4,})\\D*$");
    private static final Pattern LOOSE_NUMBER_ID = Pattern.compile("(?<![A-Za-z0-9])(\\d{4,})(?![A-Za-z0-9])");
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    @Resource
    private SekaiSongPlaylistMapper playlistMapper;

    @Resource
    private SekaiSongTrackMapper songMapper;

    @Resource
    private CloudLyricService cloudLyricService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    @Transactional
    public NeteaseImportResult importPlaylist(String link, Long userId) {
        NeteaseImportResult result = new NeteaseImportResult();
        if (userId == null) {
            return fail(result, "请先登录后再导入歌单");
        }
        NeteaseLink neteaseLink = NeteaseMusicLinkUtil.parse(link);
        if (!neteaseLink.isPlaylist() || isBlank(neteaseLink.getId())) {
            return fail(result, "请粘贴网易云公开歌单链接，例如 playlist?id=123456");
        }

        JsonNode playlistNode;
        try {
            playlistNode = fetchPlaylist(neteaseLink.getId());
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return fail(result, "网易云歌单读取失败，请确认歌单公开且链接可访问");
        }
        if (playlistNode == null || playlistNode.isMissingNode()) {
            return fail(result, "没有读取到歌单数据");
        }

        Playlist playlist = buildPlaylist(playlistNode, userId, neteaseLink.getId());
        playlistMapper.insert(playlist);
        if (playlist.getId() == null) {
            return fail(result, "本地歌单创建失败");
        }

        ImportCount count = importTracks(tracksOf(playlistNode), playlist.getId(), userId);
        result.setSuccess(true);
        result.setPlaylistId(playlist.getId());
        result.setPlaylistName(playlist.getName());
        result.setImportedCount(count.imported);
        result.setSkippedCount(count.existing + count.skipped);
        result.setMessage("导入完成：读取 " + count.read + " 首，新增 " + count.imported
                + " 首，已存在 " + count.existing + " 首，加入当前歌单 " + count.addedToPlaylist
                + " 首，无效/跳过 " + count.skipped + " 首");
        return result;
    }

    @Override
    @Transactional
    public NeteaseImportResult importSongs(String songIds, Long playlistId, Long userId) {
        NeteaseImportResult result = new NeteaseImportResult();
        if (userId == null) {
            return fail(result, "请先登录后再导入歌曲");
        }
        List<String> ids = extractSongIds(songIds);
        List<String> playlistIds = extractPlaylistIds(songIds);
        if (ids.isEmpty() && playlistIds.isEmpty()) {
            return fail(result, "请粘贴网易云歌曲 ID、单曲链接，或网络检查里复制出来的歌曲 id");
        }

        Playlist playlist = null;
        if (playlistId != null) {
            playlist = playlistMapper.findByIdAndUserId(playlistId, userId);
        }
        if (playlist == null) {
            playlist = playlistMapper.findByNameAndUserId(FIXED_IMPORT_PLAYLIST_NAME, userId);
        }
        if (playlist == null) {
            playlist = new Playlist();
            playlist.setUserId(userId);
            playlist.setName(FIXED_IMPORT_PLAYLIST_NAME);
            playlist.setDescription("未选择目标歌单时，网易云歌曲会导入到这里。");
            playlist.setCoverUrl("/images/login-music-room.png");
            playlistMapper.insert(playlist);
        }
        if (playlist.getId() == null) {
            return fail(result, "目标歌单不可用，导入失败");
        }

        List<JsonNode> directTracks = fetchSongDetails(ids);
        List<JsonNode> trackNodes = new ArrayList<>(directTracks);
        int failedPlaylists = 0;
        for (String remotePlaylistId : playlistIds) {
            JsonNode playlistNode;
            try {
                playlistNode = fetchPlaylist(remotePlaylistId);
            } catch (IOException | InterruptedException e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                failedPlaylists++;
                continue;
            }
            List<JsonNode> remoteTracks = playlistNode == null ? List.of() : tracksOf(playlistNode);
            if (remoteTracks.isEmpty()) {
                failedPlaylists++;
            }
            trackNodes.addAll(remoteTracks);
        }

        List<JsonNode> uniqueTracks = deduplicateTracks(trackNodes);
        if (!ids.isEmpty() && uniqueTracks.isEmpty()) {
            return fail(result, "网易云这次没有返回歌曲数据。已识别 " + ids.size()
                    + " 个 ID，可能是接口限流或网络波动，请稍后重试，或把一次导入数量减少到 50 首以内。");
        }

        ImportCount count = importTracks(uniqueTracks, playlist.getId(), userId);
        int failed = Math.max(ids.size() - directTracks.size(), 0) + failedPlaylists;
        result.setSuccess(true);
        result.setPlaylistId(playlist.getId());
        result.setPlaylistName(playlist.getName());
        result.setImportedCount(count.imported);
        result.setSkippedCount(count.existing + count.skipped);
        String message = "歌曲导入完成：识别 " + ids.size() + " 个 ID，读取 " + uniqueTracks.size()
                + " 首，新增 " + count.imported + " 首，已存在 " + count.existing
                + " 首，加入当前歌单 " + count.addedToPlaylist + " 首，未读取 "
                + Math.max(failed, 0) + " 首，无效/跳过 " + count.skipped + " 首";
        if (count.imported == 0 && count.addedToPlaylist == 0 && count.existing > 0) {
            message += "。这些歌曲之前已经导入过，所以第二次不会重复新增。";
        }
        result.setMessage(message);
        return result;
    }

    private ImportCount importTracks(List<JsonNode> trackNodes, Long playlistId, Long userId) {
        ImportCount count = new ImportCount();
        for (JsonNode trackNode : trackNodes) {
            count.read++;
            Song song = buildSong(trackNode, userId);
            if (song == null) {
                count.skipped++;
                continue;
            }
            Song existing = songMapper.findByAudioUrlAndUserId(song.getAudioUrl(), userId);
            if (existing != null) {
                if (syncMissingLyric(existing, song, userId)) {
                    count.syncedLyrics++;
                }
                song = existing;
                count.existing++;
            } else if (songMapper.insert(song) > 0) {
                count.imported++;
                if (!isBlank(song.getLyricText())) {
                    count.syncedLyrics++;
                }
            } else {
                count.skipped++;
                continue;
            }
            if (song.getId() != null && playlistMapper.existsSong(playlistId, song.getId()) <= 0) {
                playlistMapper.addSong(playlistId, song.getId());
                count.addedToPlaylist++;
            }
        }
        return count;
    }

    private boolean syncMissingLyric(Song existing, Song imported, Long userId) {
        boolean needsLyric = isBlank(existing.getLyricText()) && !isBlank(imported.getLyricText());
        boolean needsDuration = existing.getDurationSeconds() == null && imported.getDurationSeconds() != null;
        if (!needsLyric && !needsDuration) {
            return false;
        }
        songMapper.updateLyricMetadata(existing.getId(), userId,
                needsLyric ? imported.getLyricText() : null,
                needsDuration ? imported.getDurationSeconds() : null);
        return needsLyric;
    }

    private JsonNode fetchPlaylist(String playlistId) throws IOException, InterruptedException {
        for (String template : PLAYLIST_API_TEMPLATES) {
            HttpRequest request = request(String.format(template, playlistId));
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                continue;
            }
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode playlist = root.path("playlist");
            if (playlist.isMissingNode() || playlist.isNull()) {
                playlist = root.path("result");
            }
            if (!playlist.isMissingNode() && !playlist.isNull()) {
                return playlist;
            }
        }
        return null;
    }

    private Playlist buildPlaylist(JsonNode playlistNode, Long userId, String neteasePlaylistId) {
        Playlist playlist = new Playlist();
        playlist.setUserId(userId);
        playlist.setName(text(playlistNode, "name", "网易云歌单 " + neteasePlaylistId));
        playlist.setDescription("网易云歌单导入：" + neteasePlaylistId + "。导入后可在本项目管理歌曲，播放由网易云外链能力兜底。");
        playlist.setCoverUrl(text(playlistNode, "coverImgUrl", null));
        return playlist;
    }

    private List<JsonNode> tracksOf(JsonNode playlistNode) {
        List<JsonNode> tracks = new ArrayList<>();
        JsonNode trackArray = playlistNode.path("tracks");
        if (trackArray.isArray()) {
            trackArray.forEach(tracks::add);
        }

        Set<String> existingIds = tracks.stream()
                .map(track -> String.valueOf(track.path("id").asLong(0)))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        List<String> missingIds = new ArrayList<>();
        JsonNode trackIds = playlistNode.path("trackIds");
        if (trackIds.isArray()) {
            for (JsonNode trackId : trackIds) {
                String id = String.valueOf(trackId.path("id").asLong(0));
                if (!"0".equals(id) && !existingIds.contains(id)) {
                    missingIds.add(id);
                }
            }
        }
        if (!missingIds.isEmpty()) {
            tracks.addAll(fetchSongDetails(missingIds));
        }
        return tracks;
    }

    private List<JsonNode> fetchSongDetails(List<String> songIds) {
        List<JsonNode> songs = new ArrayList<>();
        List<String> cleanIds = songIds.stream()
                .filter(id -> !isBlank(id))
                .collect(Collectors.toCollection(LinkedHashSet::new))
                .stream()
                .toList();
        for (int start = 0; start < cleanIds.size(); start += SONG_DETAIL_BATCH_SIZE) {
            List<String> batch = cleanIds.subList(start, Math.min(start + SONG_DETAIL_BATCH_SIZE, cleanIds.size()));
            songs.addAll(fetchSongDetailsBatch(batch));
        }
        return songs;
    }

    private List<JsonNode> fetchSongDetailsBatch(List<String> batch) {
        for (int attempt = 0; attempt < SONG_DETAIL_RETRY_TIMES; attempt++) {
            for (String url : songDetailUrls(batch)) {
                List<JsonNode> songs = fetchSongDetailsFromUrl(url);
                if (!songs.isEmpty()) {
                    return songs;
                }
            }
            sleepBeforeRetry(attempt);
        }
        return List.of();
    }

    private List<String> songDetailUrls(List<String> batch) {
        String idsJson = batch.stream().collect(Collectors.joining(",", "[", "]"));
        String cJson = batch.stream()
                .map(id -> "{\"id\":" + id + "}")
                .collect(Collectors.joining(",", "[", "]"));
        return List.of(
                SONG_DETAIL_API + URLEncoder.encode(idsJson, StandardCharsets.UTF_8),
                SONG_DETAIL_API_V3 + URLEncoder.encode(cJson, StandardCharsets.UTF_8));
    }

    private List<JsonNode> fetchSongDetailsFromUrl(String url) {
        List<JsonNode> songs = new ArrayList<>();
        try {
            HttpResponse<String> response = HTTP_CLIENT.send(
                    request(url),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                JsonNode songArray = objectMapper.readTree(response.body()).path("songs");
                if (songArray.isArray()) {
                    songArray.forEach(songs::add);
                }
            }
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }
        return songs;
    }

    private void sleepBeforeRetry(int attempt) {
        try {
            Thread.sleep(250L * (attempt + 1));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private List<String> extractSongIds(String rawText) {
        if (isBlank(rawText)) {
            return List.of();
        }
        Set<String> ids = new LinkedHashSet<>();
        String songText = PLAYLIST_LINK_ID.matcher(rawText).replaceAll(" ");
        Matcher linkMatcher = SONG_LINK_ID.matcher(songText);
        while (linkMatcher.find()) {
            ids.add(linkMatcher.group(1));
        }
        for (String line : songText.split("\\R")) {
            if (PLAYLIST_LINK_ID.matcher(line).find()) {
                continue;
            }
            Matcher lineMatcher = NUMBER_LINE.matcher(line.trim());
            if (lineMatcher.matches()) {
                ids.add(lineMatcher.group(1));
            }
        }
        Matcher looseMatcher = LOOSE_NUMBER_ID.matcher(songText);
        while (looseMatcher.find()) {
            ids.add(looseMatcher.group(1));
        }
        return new ArrayList<>(ids);
    }

    private List<String> extractPlaylistIds(String rawText) {
        if (isBlank(rawText)) {
            return List.of();
        }
        Set<String> ids = new LinkedHashSet<>();
        Matcher playlistMatcher = PLAYLIST_LINK_ID.matcher(rawText);
        while (playlistMatcher.find()) {
            ids.add(playlistMatcher.group(1));
        }
        return new ArrayList<>(ids);
    }

    private List<JsonNode> deduplicateTracks(List<JsonNode> trackNodes) {
        List<JsonNode> uniqueTracks = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (JsonNode trackNode : trackNodes) {
            String id = String.valueOf(trackNode.path("id").asLong(0));
            if (!"0".equals(id) && seen.add(id)) {
                uniqueTracks.add(trackNode);
            }
        }
        return uniqueTracks;
    }

    private HttpRequest request(String url) {
        return HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header("User-Agent", "Mozilla/5.0 SekaiSong/1.0")
                .header("Referer", "https://music.163.com/")
                .GET()
                .build();
    }

    private Song buildSong(JsonNode trackNode, Long userId) {
        String songId = String.valueOf(trackNode.path("id").asLong(0));
        String title = text(trackNode, "name", null);
        if ("0".equals(songId) || isBlank(title)) {
            return null;
        }

        JsonNode albumNode = trackNode.path("album");
        if (albumNode.isMissingNode() || albumNode.isNull()) {
            albumNode = trackNode.path("al");
        }

        Song song = new Song();
        song.setUserId(userId);
        song.setTitle(title);
        song.setArtist(artists(trackNode));
        song.setAnimeTitle("网易云导入");
        song.setAlbumName(text(albumNode, "name", ""));
        song.setCoverUrl(firstText(albumNode, "picUrl", "blurPicUrl"));
        song.setSkinUrl(song.getCoverUrl());
        song.setAudioUrl("https://music.163.com/song?id=" + songId);
        song.setTags("网易云歌单");
        song.setMood("歌单导入");
        song.setLyricText(cloudLyricService.fetchNeteaseLyric(songId));
        song.setFavorite(false);
        song.setDurationSeconds(duration(trackNode));
        return song;
    }

    private String artists(JsonNode trackNode) {
        JsonNode artists = trackNode.path("artists");
        if (!artists.isArray()) {
            artists = trackNode.path("ar");
        }
        StringJoiner joiner = new StringJoiner(" / ");
        if (artists.isArray()) {
            for (JsonNode artist : artists) {
                String name = text(artist, "name", null);
                if (!isBlank(name)) {
                    joiner.add(name);
                }
            }
        }
        String value = joiner.toString();
        return value.isBlank() ? "未知歌手" : value;
    }

    private Integer duration(JsonNode trackNode) {
        long durationMillis = firstLong(trackNode, "duration", "dt");
        return durationMillis > 0 ? (int) Math.round(durationMillis / 1000.0) : null;
    }

    private long firstLong(JsonNode node, String... fields) {
        for (String field : fields) {
            long value = node.path(field).asLong(0);
            if (value > 0) {
                return value;
            }
        }
        return 0;
    }

    private String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            String value = text(node, field, null);
            if (!isBlank(value)) {
                return value;
            }
        }
        return null;
    }

    private String text(JsonNode node, String field, String fallback) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? fallback : value.asText(fallback);
    }

    private NeteaseImportResult fail(NeteaseImportResult result, String message) {
        result.setSuccess(false);
        result.setMessage(message);
        return result;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static class ImportCount {
        private int read;
        private int imported;
        private int existing;
        private int addedToPlaylist;
        private int skipped;
        private int syncedLyrics;
    }
}
