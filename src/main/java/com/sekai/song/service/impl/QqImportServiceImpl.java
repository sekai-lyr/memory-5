package com.sekai.song.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sekai.song.mapper.SekaiSongPlaylistMapper;
import com.sekai.song.mapper.SekaiSongTrackMapper;
import com.sekai.song.model.MusicImportResult;
import com.sekai.song.model.Playlist;
import com.sekai.song.model.Song;
import com.sekai.song.service.CloudLyricService;
import com.sekai.song.service.QqImportService;
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

@Service
public class QqImportServiceImpl implements QqImportService {

    private static final String SONG_DETAIL_API = "https://u.y.qq.com/cgi-bin/musicu.fcg?data=";
    private static final int QQ_PLAYLIST_PAGE_SIZE = 100;
    private static final int QQ_IMPORT_RETRY_TIMES = 3;
    private static final int QQ_IMPORT_MAX_PLAYLIST_SONGS = 5000;
    private static final Pattern QQ_LINK = Pattern.compile("https?://[^\\s\"'<>]+");
    private static final Pattern PLAYLIST_PATH = Pattern.compile("(?i)/(?:n/ryqq(?:_v2)?/playlist|n/yqq/playlist)/(\\d{5,})(?:\\.html)?");
    private static final Pattern TAOGE_LINK_ID = Pattern.compile("(?i)/taoge\\.html[^\\s\"'<>]*[?&#]id=(\\d{5,})");
    private static final Pattern DISSTID_PARAM = Pattern.compile("(?i)[?&#](?:disstid|dissid)=([0-9A-Za-z]{5,})");
    private static final Pattern PLAYLIST_ID_FIELD = Pattern.compile("(?i)(?:disstid|dissid|playlistId|playlist_id)[\"']?\\s*[:=]\\s*[\"']?([0-9A-Za-z]{5,})");
    private static final String FIXED_IMPORT_PLAYLIST_NAME = "QQ音乐歌曲导入";
    private static final Pattern SONG_DETAIL_PATH = Pattern.compile("(?i)/(?:n/ryqq/songDetail|n/yqq/song)/([0-9A-Za-z]{8,})(?:\\.html)?");
    private static final Pattern SONG_MID_FIELD = Pattern.compile("(?i)(?:songmid|song_mid|songMid)[\"']?\\s*[:=]\\s*[\"']?([0-9A-Za-z]{8,})");
    private static final Pattern SONG_ID_FIELD = Pattern.compile("(?i)(?:songid|song_id|songId)[\"']?\\s*[:=]\\s*[\"']?([0-9]{5,})");
    private static final Pattern TRACK_INFO_MID = Pattern.compile("(?i)\"track_info\"\\s*:\\s*\\{[^{}]*\"mid\"\\s*:\\s*\"([0-9A-Za-z]{8,})\"");
    private static final Pattern MID_LINE = Pattern.compile("^[0-9A-Za-z]{8,}$");
    private static final Pattern NUMBER_LINE = Pattern.compile("^\\D*(\\d{5,})\\D*$");
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
    public MusicImportResult importSongs(String songIds, Long playlistId, Long userId) {
        MusicImportResult result = new MusicImportResult();
        if (userId == null) {
            return fail(result, "请先登录后再导入歌曲");
        }
        String expandedText = expandShareLinks(songIds);
        List<QqSongKey> keys = extractSongKeys(expandedText);
        List<String> playlistIds = extractPlaylistIds(expandedText);
        if (keys.isEmpty() && playlistIds.isEmpty()) {
            return fail(result, "请粘贴 QQ 音乐 songmid、songid、单曲链接，或网络检查里复制出来的歌曲字段");
        }

        Playlist playlist = targetPlaylist(playlistId, userId);
        if (playlist == null || playlist.getId() == null) {
            return fail(result, "目标歌单不可用，导入失败");
        }

        List<JsonNode> directTrackNodes = fetchSongDetails(keys);
        List<JsonNode> trackNodes = new ArrayList<>(directTrackNodes);
        int failedPlaylists = 0;
        for (String remotePlaylistId : playlistIds) {
            List<JsonNode> playlistTracks = fetchPlaylistTracks(remotePlaylistId);
            if (playlistTracks.isEmpty()) {
                failedPlaylists++;
            }
            trackNodes.addAll(playlistTracks);
        }
        List<JsonNode> uniqueTracks = deduplicateTracks(trackNodes);
        if (!keys.isEmpty() && uniqueTracks.isEmpty()) {
            return fail(result, "QQ 音乐这次没有返回歌曲数据。已识别 " + keys.size()
                    + " 个歌曲标识，可能是接口限流或网络波动，请稍后重试，或把一次导入数量减少到 100 首以内。");
        }

        ImportCount count = importTracks(uniqueTracks, playlist.getId(), userId);
        int failed = Math.max(keys.size() - directTrackNodes.size(), 0) + failedPlaylists;
        result.setSuccess(true);
        result.setPlaylistId(playlist.getId());
        result.setPlaylistName(playlist.getName());
        result.setImportedCount(count.imported);
        result.setSkippedCount(count.existing + count.skipped);
        String message = "QQ 音乐导入完成：识别 " + keys.size() + " 个歌曲标识，读取 " + uniqueTracks.size()
                + " 首，新增 " + count.imported + " 首，已存在 " + count.existing
                + " 首，加入当前歌单 " + count.addedToPlaylist + " 首，未读取 "
                + Math.max(failed, 0) + " 首，无效/跳过 " + count.skipped + " 首";
        if (count.imported == 0 && count.addedToPlaylist == 0 && count.existing > 0) {
            message += "。这些歌曲之前已经导入过，所以第二次不会重复新增。";
        }
        result.setMessage(message);
        return result;
    }

    private Playlist targetPlaylist(Long playlistId, Long userId) {
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
            playlist.setDescription("未选择目标歌单时，QQ 音乐歌曲会导入到这里。");
            playlist.setCoverUrl("/images/login-music-room.png");
            playlistMapper.insert(playlist);
        }
        return playlist;
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

    private List<JsonNode> fetchSongDetails(List<QqSongKey> keys) {
        List<JsonNode> songs = new ArrayList<>();
        for (QqSongKey key : keys) {
            JsonNode trackInfo = fetchSongDetail(key);
            if (trackInfo != null && !trackInfo.isMissingNode() && !trackInfo.isNull()) {
                songs.add(trackInfo);
            }
        }
        return songs;
    }

    private JsonNode fetchSongDetail(QqSongKey key) {
        for (int attempt = 0; attempt < QQ_IMPORT_RETRY_TIMES; attempt++) {
            JsonNode trackInfo = fetchSongDetailOnce(key);
            if (trackInfo != null && !trackInfo.isMissingNode() && !trackInfo.isNull()) {
                return trackInfo;
            }
            sleepBeforeRetry(attempt);
        }
        return null;
    }

    private JsonNode fetchSongDetailOnce(QqSongKey key) {
        try {
            HttpResponse<String> response = HTTP_CLIENT.send(
                    request(SONG_DETAIL_API + URLEncoder.encode(songDetailPayload(key), StandardCharsets.UTF_8)),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return null;
            }
            JsonNode trackInfo = objectMapper.readTree(response.body())
                    .path("songinfo")
                    .path("data")
                    .path("track_info");
            return trackInfo.isMissingNode() ? null : trackInfo;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return null;
        }
    }

    private List<JsonNode> fetchPlaylistTracks(String playlistId) {
        List<JsonNode> songs = new ArrayList<>();
        for (int begin = 0; begin < QQ_IMPORT_MAX_PLAYLIST_SONGS; begin += QQ_PLAYLIST_PAGE_SIZE) {
            JsonNode data = fetchPlaylistData(playlistId, begin, QQ_PLAYLIST_PAGE_SIZE);
            if (data == null) {
                break;
            }
            JsonNode songList = data.path("songlist");
            if (!songList.isArray() || songList.isEmpty()) {
                break;
            }
            for (JsonNode songNode : songList) {
                JsonNode trackNode = normalizePlaylistSongNode(songNode);
                if (trackNode != null && !trackNode.isMissingNode() && !trackNode.isNull()) {
                    songs.add(trackNode);
                }
            }
            int total = firstPositiveInt(data, "total_song_num", "total", "song_count");
            if (songList.size() < QQ_PLAYLIST_PAGE_SIZE || (total > 0 && songs.size() >= total)) {
                break;
            }
        }
        return songs;
    }

    private JsonNode fetchPlaylistData(String playlistId, int begin, int size) {
        for (int attempt = 0; attempt < QQ_IMPORT_RETRY_TIMES; attempt++) {
            JsonNode data = fetchPlaylistDataOnce(playlistId, begin, size);
            if (data != null && !data.isMissingNode() && !data.isNull()) {
                return data;
            }
            sleepBeforeRetry(attempt);
        }
        return null;
    }

    private JsonNode fetchPlaylistDataOnce(String playlistId, int begin, int size) {
        try {
            HttpResponse<String> response = HTTP_CLIENT.send(
                    request(SONG_DETAIL_API + URLEncoder.encode(playlistDetailPayload(playlistId, begin, size), StandardCharsets.UTF_8)),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return null;
            }
            JsonNode data = objectMapper.readTree(response.body())
                    .path("req_0")
                    .path("data");
            return data.isMissingNode() ? null : data;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return null;
        }
    }

    private void sleepBeforeRetry(int attempt) {
        try {
            Thread.sleep(300L * (attempt + 1));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private JsonNode normalizePlaylistSongNode(JsonNode songNode) {
        JsonNode trackInfo = songNode.path("track_info");
        if (!trackInfo.isMissingNode() && !trackInfo.isNull()) {
            return trackInfo;
        }
        JsonNode songInfo = songNode.path("songInfo");
        if (!songInfo.isMissingNode() && !songInfo.isNull()) {
            return songInfo;
        }
        return songNode;
    }

    private String songDetailPayload(QqSongKey key) throws IOException {
        ObjectNode root = objectMapper.createObjectNode();
        ObjectNode comm = root.putObject("comm");
        comm.put("ct", 24);
        comm.put("cv", 0);
        ObjectNode songInfo = root.putObject("songinfo");
        songInfo.put("method", "get_song_detail_yqq");
        songInfo.put("module", "music.pf_song_detail_svr");
        ObjectNode param = songInfo.putObject("param");
        param.put("song_type", 0);
        param.put("song_mid", key.mid == null ? "" : key.mid);
        param.put("song_id", key.songId == null ? 0 : key.songId);
        return objectMapper.writeValueAsString(root);
    }

    private String playlistDetailPayload(String playlistId, int begin, int size) throws IOException {
        ObjectNode root = objectMapper.createObjectNode();
        ObjectNode comm = root.putObject("comm");
        comm.put("ct", 24);
        comm.put("cv", 0);
        ObjectNode playlistInfo = root.putObject("req_0");
        playlistInfo.put("method", "uniform_get_Dissinfo");
        playlistInfo.put("module", "music.srfDissInfo.aiDissInfo");
        ObjectNode param = playlistInfo.putObject("param");
        param.put("disstid", playlistId);
        param.put("dirid", 0);
        param.put("tag", 1);
        param.put("userinfo", 1);
        param.put("song_begin", begin);
        param.put("song_num", size);
        return objectMapper.writeValueAsString(root);
    }

    private HttpRequest request(String url) {
        return HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header("User-Agent", "Mozilla/5.0 SekaiSong/1.0")
                .header("Referer", "https://y.qq.com/")
                .header("Origin", "https://y.qq.com")
                .GET()
                .build();
    }

    private List<QqSongKey> extractSongKeys(String rawText) {
        if (isBlank(rawText)) {
            return List.of();
        }
        String songText = removePlaylistMatches(rawText);
        List<QqSongKey> keys = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        addMidMatches(keys, seen, SONG_DETAIL_PATH.matcher(songText));
        addMidMatches(keys, seen, SONG_MID_FIELD.matcher(songText));
        addMidMatches(keys, seen, TRACK_INFO_MID.matcher(songText));
        addIdMatches(keys, seen, SONG_ID_FIELD.matcher(songText));
        for (String token : songText.split("[\\s,，;；]+")) {
            String value = token.trim();
            if (value.isEmpty()) {
                continue;
            }
            if (MID_LINE.matcher(value).matches() && value.chars().anyMatch(Character::isLetter)) {
                addKey(keys, seen, QqSongKey.byMid(value));
                continue;
            }
            Matcher numberMatcher = NUMBER_LINE.matcher(value);
            if (numberMatcher.matches()) {
                addKey(keys, seen, QqSongKey.bySongId(Long.parseLong(numberMatcher.group(1))));
            }
        }
        return keys;
    }

    private List<String> extractPlaylistIds(String rawText) {
        if (isBlank(rawText)) {
            return List.of();
        }
        Set<String> ids = new LinkedHashSet<>();
        addTextMatches(ids, PLAYLIST_PATH.matcher(rawText));
        addTextMatches(ids, TAOGE_LINK_ID.matcher(rawText));
        addTextMatches(ids, DISSTID_PARAM.matcher(rawText));
        addTextMatches(ids, PLAYLIST_ID_FIELD.matcher(rawText));
        return new ArrayList<>(ids);
    }

    private String removePlaylistMatches(String rawText) {
        String text = PLAYLIST_PATH.matcher(rawText).replaceAll(" ");
        text = TAOGE_LINK_ID.matcher(text).replaceAll(" ");
        text = DISSTID_PARAM.matcher(text).replaceAll(" ");
        return PLAYLIST_ID_FIELD.matcher(text).replaceAll(" ");
    }

    private String expandShareLinks(String rawText) {
        if (isBlank(rawText)) {
            return "";
        }
        StringBuilder expanded = new StringBuilder(rawText).append('\n');
        Set<String> seenLinks = new LinkedHashSet<>();
        Matcher matcher = QQ_LINK.matcher(rawText);
        while (matcher.find()) {
            String link = trimUrl(matcher.group());
            if (!link.contains("qq.com") || !seenLinks.add(link)) {
                continue;
            }
            try {
                HttpResponse<String> response = HTTP_CLIENT.send(request(link),
                        HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                expanded.append(response.uri()).append('\n');
                String body = response.body();
                if (body != null && !body.isBlank()) {
                    expanded.append(body, 0, Math.min(body.length(), 30000)).append('\n');
                }
            } catch (IOException | InterruptedException | IllegalArgumentException e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        return expanded.toString();
    }

    private String trimUrl(String url) {
        return url.replaceAll("[,，。)）\\]>]+$", "");
    }

    private List<JsonNode> deduplicateTracks(List<JsonNode> trackNodes) {
        List<JsonNode> uniqueTracks = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (JsonNode trackNode : trackNodes) {
            String key = qqTrackKey(trackNode);
            if (key != null && seen.add(key)) {
                uniqueTracks.add(trackNode);
            }
        }
        return uniqueTracks;
    }

    private String qqTrackKey(JsonNode trackNode) {
        String mid = text(trackNode, "mid", null);
        if (!isBlank(mid)) {
            return "mid:" + mid;
        }
        long id = trackNode.path("id").asLong(0);
        return id > 0 ? "id:" + id : null;
    }

    private int firstPositiveInt(JsonNode node, String... fields) {
        for (String field : fields) {
            int value = node.path(field).asInt(0);
            if (value > 0) {
                return value;
            }
        }
        return 0;
    }

    private void addTextMatches(Set<String> values, Matcher matcher) {
        while (matcher.find()) {
            values.add(matcher.group(1));
        }
    }

    private void addMidMatches(List<QqSongKey> keys, Set<String> seen, Matcher matcher) {
        while (matcher.find()) {
            addKey(keys, seen, QqSongKey.byMid(matcher.group(1)));
        }
    }

    private void addIdMatches(List<QqSongKey> keys, Set<String> seen, Matcher matcher) {
        while (matcher.find()) {
            addKey(keys, seen, QqSongKey.bySongId(Long.parseLong(matcher.group(1))));
        }
    }

    private void addKey(List<QqSongKey> keys, Set<String> seen, QqSongKey key) {
        String uniqueKey = key.uniqueKey();
        if (seen.add(uniqueKey)) {
            keys.add(key);
        }
    }

    private Song buildSong(JsonNode trackNode, Long userId) {
        String songMid = text(trackNode, "mid", null);
        long songId = trackNode.path("id").asLong(0);
        String title = firstText(trackNode, "title", "name");
        if (isBlank(title) || (isBlank(songMid) && songId <= 0)) {
            return null;
        }

        JsonNode albumNode = trackNode.path("album");
        Song song = new Song();
        song.setUserId(userId);
        song.setTitle(title);
        song.setArtist(artists(trackNode));
        song.setAnimeTitle("QQ音乐导入");
        song.setAlbumName(firstText(albumNode, "title", "name"));
        song.setCoverUrl(albumCover(albumNode));
        song.setSkinUrl(song.getCoverUrl());
        song.setAudioUrl("https://y.qq.com/n/ryqq/songDetail/" + (isBlank(songMid) ? songId : songMid));
        song.setTags("QQ音乐");
        song.setMood("歌曲导入");
        song.setLyricText(cloudLyricService.fetchQqLyric(songMid, songId > 0 ? songId : null));
        song.setFavorite(false);
        song.setDurationSeconds(duration(trackNode));
        return song;
    }

    private String artists(JsonNode trackNode) {
        JsonNode singers = trackNode.path("singer");
        StringJoiner joiner = new StringJoiner(" / ");
        if (singers.isArray()) {
            for (JsonNode singer : singers) {
                String name = firstText(singer, "title", "name");
                if (!isBlank(name)) {
                    joiner.add(name);
                }
            }
        }
        String value = joiner.toString();
        return value.isBlank() ? "未知歌手" : value;
    }

    private String albumCover(JsonNode albumNode) {
        String albumMid = firstText(albumNode, "pmid", "mid");
        if (isBlank(albumMid)) {
            return null;
        }
        int suffix = albumMid.indexOf('_');
        if (suffix > 0) {
            albumMid = albumMid.substring(0, suffix);
        }
        return "https://y.qq.com/music/photo_new/T002R300x300M000" + albumMid + ".jpg?max_age=2592000";
    }

    private Integer duration(JsonNode trackNode) {
        int seconds = trackNode.path("interval").asInt(0);
        return seconds > 0 ? seconds : null;
    }

    private String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            String value = text(node, field, null);
            if (!isBlank(value)) {
                return value;
            }
        }
        return "";
    }

    private String text(JsonNode node, String field, String fallback) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? fallback : value.asText(fallback);
    }

    private MusicImportResult fail(MusicImportResult result, String message) {
        result.setSuccess(false);
        result.setMessage(message);
        return result;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private record QqSongKey(String mid, Long songId) {
        static QqSongKey byMid(String mid) {
            return new QqSongKey(mid, null);
        }

        static QqSongKey bySongId(Long songId) {
            return new QqSongKey(null, songId);
        }

        String uniqueKey() {
            return mid == null ? "id:" + songId : "mid:" + mid;
        }
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
