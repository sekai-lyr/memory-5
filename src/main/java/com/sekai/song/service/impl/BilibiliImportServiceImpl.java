package com.sekai.song.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sekai.song.mapper.SekaiSongPlaylistMapper;
import com.sekai.song.mapper.SekaiSongTrackMapper;
import com.sekai.song.model.MusicImportResult;
import com.sekai.song.model.Playlist;
import com.sekai.song.model.Song;
import com.sekai.song.service.BilibiliImportService;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class BilibiliImportServiceImpl implements BilibiliImportService {

    private static final String VIDEO_DETAIL_API = "https://api.bilibili.com/x/web-interface/view?";
    private static final String FIXED_IMPORT_PLAYLIST_NAME = "B站视频音乐";
    private static final int BILIBILI_IMPORT_RETRY_TIMES = 3;
    private static final int BILIBILI_COLLECTION_PAGE_SIZE = 30;
    private static final int BILIBILI_MAX_COLLECTION_PAGES = 200;
    private static final Pattern BVID = Pattern.compile("(?i)\\b(BV[0-9A-Za-z]{10})\\b");
    private static final Pattern AID = Pattern.compile("(?i)(?:/video/av|\\bav)(\\d{5,})");
    private static final Pattern AID_FIELD = Pattern.compile("(?i)(?:aid|avid)[\"']?\\s*[:=]\\s*[\"']?(\\d{5,})");
    private static final Pattern PURE_AID_LINE = Pattern.compile("^\\d{5,}$");
    private static final Pattern B23_SHORT_LINK = Pattern.compile("https?://b23\\.tv/[0-9A-Za-z_\\-]+");
    private static final Pattern BILIBILI_LIST_LINK = Pattern.compile("(?i)bilibili\\.com/list/");
    private static final Pattern SPACE_LIST = Pattern.compile("(?i)space\\.bilibili\\.com/(\\d+)/lists/(\\d+)([^\\s\"']*)?");
    private static final Pattern SPACE_SERIES_DETAIL = Pattern.compile("(?i)space\\.bilibili\\.com/(\\d+)/channel/seriesdetail\\?[^\\s\"']*\\bsid=(\\d+)");
    private static final Pattern MID_FIELD = Pattern.compile("(?i)\\bmid[\"']?\\s*[:=]\\s*[\"']?(\\d{5,})");
    private static final Pattern SERIES_ID_FIELD = Pattern.compile("(?i)(?:series_id|seriesId)[\"']?\\s*[:=]\\s*[\"']?(\\d{1,})");
    private static final Pattern SEASON_ID_FIELD = Pattern.compile("(?i)(?:season_id|seasonId)[\"']?\\s*[:=]\\s*[\"']?(\\d{1,})");
    private static final Pattern SID_FIELD = Pattern.compile("(?i)\\bsid[\"']?\\s*[:=]\\s*[\"']?(\\d{1,})");
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    @Resource
    private SekaiSongPlaylistMapper playlistMapper;

    @Resource
    private SekaiSongTrackMapper songMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    @Transactional
    public MusicImportResult importVideos(String videoIds, Long playlistId, Long userId) {
        MusicImportResult result = new MusicImportResult();
        if (userId == null) {
            return fail(result, "请先登录后再导入 B 站视频");
        }
        String expandedText = expandShortLinks(videoIds);
        List<BilibiliVideoKey> keys = extractVideoKeys(expandedText);
        List<BilibiliCollectionKey> collectionKeys = extractCollectionKeys(expandedText);
        if (keys.isEmpty() && collectionKeys.isEmpty()) {
            return fail(result, "请粘贴 B 站视频链接、作品集链接、BV 号、av 号，或网络检查里复制出的 bvid/aid 字段");
        }

        Playlist playlist = targetPlaylist(playlistId, userId);
        if (playlist == null || playlist.getId() == null) {
            return fail(result, "目标歌单不可用，导入失败");
        }

        List<JsonNode> directVideoNodes = fetchVideoDetails(keys);
        List<JsonNode> videoNodes = new ArrayList<>();
        for (JsonNode directVideoNode : directVideoNodes) {
            List<JsonNode> seasonVideos = shouldExpandUgcSeason(expandedText) ? videosFromUgcSeason(directVideoNode) : List.of();
            if (seasonVideos.isEmpty()) {
                videoNodes.add(directVideoNode);
            } else {
                videoNodes.addAll(seasonVideos);
            }
        }
        int failedCollections = 0;
        for (BilibiliCollectionKey collectionKey : collectionKeys) {
            List<JsonNode> collectionVideos = fetchCollectionVideos(collectionKey);
            if (collectionVideos.isEmpty()) {
                failedCollections++;
            }
            videoNodes.addAll(collectionVideos);
        }
        List<JsonNode> uniqueVideos = deduplicateVideos(videoNodes);
        if ((!keys.isEmpty() || !collectionKeys.isEmpty()) && uniqueVideos.isEmpty()) {
            return fail(result, "B 站这次没有返回视频数据。已识别 " + keys.size() + " 个视频和 "
                    + collectionKeys.size() + " 个作品集，可能是接口限流、作品集权限限制或网络波动，请稍后重试。");
        }

        ImportCount count = importVideos(uniqueVideos, playlist.getId(), userId);
        int failed = Math.max(keys.size() - directVideoNodes.size(), 0) + failedCollections;
        result.setSuccess(true);
        result.setPlaylistId(playlist.getId());
        result.setPlaylistName(playlist.getName());
        result.setImportedCount(count.imported);
        result.setSkippedCount(count.existing + count.skipped);
        String message = "B 站视频音乐导入完成：识别 " + keys.size() + " 个视频、"
                + collectionKeys.size() + " 个作品集，读取 " + uniqueVideos.size()
                + " 个视频，新增 " + count.imported + " 首，已存在 " + count.existing
                + " 首，加入当前歌单 " + count.addedToPlaylist + " 首，未读取 "
                + Math.max(failed, 0) + " 项，无效/跳过 " + count.skipped + " 项";
        if (count.imported == 0 && count.addedToPlaylist == 0 && count.existing > 0) {
            message += "。这些视频之前已经导入过，所以第二次不会重复新增。";
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
            playlist.setDescription("未选择目标歌单时，B 站视频音乐会导入到这里。");
            playlist.setCoverUrl("/images/login-music-room.png");
            playlistMapper.insert(playlist);
        }
        return playlist;
    }

    private ImportCount importVideos(List<JsonNode> videoNodes, Long playlistId, Long userId) {
        ImportCount count = new ImportCount();
        for (JsonNode videoNode : videoNodes) {
            count.read++;
            List<Song> songs = buildSongs(videoNode, userId);
            if (songs.isEmpty()) {
                count.skipped++;
                continue;
            }
            for (Song song : songs) {
                Song existing = songMapper.findByAudioUrlAndUserId(song.getAudioUrl(), userId);
                if (existing != null) {
                    song = existing;
                    count.existing++;
                } else if (songMapper.insert(song) > 0) {
                    count.imported++;
                } else {
                    count.skipped++;
                    continue;
                }
                if (song.getId() != null && playlistMapper.existsSong(playlistId, song.getId()) <= 0) {
                    playlistMapper.addSong(playlistId, song.getId());
                    count.addedToPlaylist++;
                }
            }
        }
        return count;
    }

    private List<JsonNode> fetchVideoDetails(List<BilibiliVideoKey> keys) {
        List<JsonNode> videos = new ArrayList<>();
        for (BilibiliVideoKey key : keys) {
            JsonNode video = fetchVideoDetail(key);
            if (video != null && !video.isMissingNode() && !video.isNull()) {
                videos.add(video);
            }
        }
        return videos;
    }

    private JsonNode fetchVideoDetail(BilibiliVideoKey key) {
        for (int attempt = 0; attempt < BILIBILI_IMPORT_RETRY_TIMES; attempt++) {
            JsonNode video = fetchVideoDetailOnce(key);
            if (video != null && !video.isMissingNode() && !video.isNull()) {
                return video;
            }
            sleepBeforeRetry(attempt);
        }
        return null;
    }

    private JsonNode fetchVideoDetailOnce(BilibiliVideoKey key) {
        try {
            HttpResponse<String> response = HTTP_CLIENT.send(request(videoDetailUrl(key)),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return null;
            }
            JsonNode root = objectMapper.readTree(response.body());
            if (root.path("code").asInt(-1) != 0) {
                return null;
            }
            JsonNode data = root.path("data");
            return data.isMissingNode() ? null : data;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return null;
        }
    }

    private String videoDetailUrl(BilibiliVideoKey key) {
        if (key.bvid != null) {
            return VIDEO_DETAIL_API + "bvid=" + URLEncoder.encode(key.bvid, StandardCharsets.UTF_8);
        }
        return VIDEO_DETAIL_API + "aid=" + key.aid;
    }

    private HttpRequest request(String url) {
        return HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header("User-Agent", "Mozilla/5.0 SekaiSong/1.0")
                .header("Referer", "https://www.bilibili.com/")
                .GET()
                .build();
    }

    private List<BilibiliVideoKey> extractVideoKeys(String rawText) {
        if (isBlank(rawText)) {
            return List.of();
        }
        List<BilibiliVideoKey> keys = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        addBvidMatches(keys, seen, BVID.matcher(rawText));
        addAidMatches(keys, seen, AID.matcher(rawText));
        addAidMatches(keys, seen, AID_FIELD.matcher(rawText));
        for (String line : rawText.split("\\R")) {
            String value = line.trim();
            if (PURE_AID_LINE.matcher(value).matches()) {
                addKey(keys, seen, BilibiliVideoKey.byAid(Long.parseLong(value)));
            }
        }
        return keys;
    }

    private List<BilibiliCollectionKey> extractCollectionKeys(String rawText) {
        if (isBlank(rawText)) {
            return List.of();
        }
        List<BilibiliCollectionKey> keys = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        Matcher listMatcher = SPACE_LIST.matcher(rawText);
        while (listMatcher.find()) {
            Long mid = Long.parseLong(listMatcher.group(1));
            Long id = Long.parseLong(listMatcher.group(2));
            String tail = listMatcher.group(3) == null ? "" : listMatcher.group(3).toLowerCase();
            String type = tail.contains("type=season") ? "season" : tail.contains("type=series") ? "series" : "unknown";
            addCollectionKey(keys, seen, new BilibiliCollectionKey(mid, id, type));
        }
        Matcher seriesDetailMatcher = SPACE_SERIES_DETAIL.matcher(rawText);
        while (seriesDetailMatcher.find()) {
            addCollectionKey(keys, seen, new BilibiliCollectionKey(
                    Long.parseLong(seriesDetailMatcher.group(1)),
                    Long.parseLong(seriesDetailMatcher.group(2)),
                    "series"));
        }
        Long mid = firstLong(MID_FIELD.matcher(rawText));
        if (mid != null) {
            Long seasonId = firstLong(SEASON_ID_FIELD.matcher(rawText));
            if (seasonId != null) {
                addCollectionKey(keys, seen, new BilibiliCollectionKey(mid, seasonId, "season"));
            }
            Long seriesId = firstLong(SERIES_ID_FIELD.matcher(rawText));
            if (seriesId != null) {
                addCollectionKey(keys, seen, new BilibiliCollectionKey(mid, seriesId, "series"));
            }
            Long sid = firstLong(SID_FIELD.matcher(rawText));
            if (sid != null) {
                addCollectionKey(keys, seen, new BilibiliCollectionKey(mid, sid, "unknown"));
            }
        }
        return keys;
    }

    private Long firstLong(Matcher matcher) {
        return matcher.find() ? Long.parseLong(matcher.group(1)) : null;
    }

    private String expandShortLinks(String rawText) {
        if (isBlank(rawText)) {
            return "";
        }
        StringBuilder expanded = new StringBuilder();
        expanded.append(rawText).append('\n');
        Matcher matcher = B23_SHORT_LINK.matcher(rawText);
        while (matcher.find()) {
            try {
                HttpResponse<Void> response = HTTP_CLIENT.send(request(matcher.group()),
                        HttpResponse.BodyHandlers.discarding());
                response.uri();
                expanded.append(response.uri()).append('\n');
            } catch (IOException | InterruptedException | IllegalArgumentException e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        return expanded.toString();
    }

    private List<JsonNode> fetchCollectionVideos(BilibiliCollectionKey key) {
        List<JsonNode> videos = List.of();
        if (!"series".equals(key.type)) {
            videos = fetchSeasonVideos(key.mid, key.id);
        }
        if (videos.isEmpty() && !"season".equals(key.type)) {
            videos = fetchSeriesVideos(key.mid, key.id);
        }
        return videos;
    }

    private List<JsonNode> fetchSeasonVideos(Long mid, Long seasonId) {
        List<JsonNode> videos = new ArrayList<>();
        for (int page = 1; page <= BILIBILI_MAX_COLLECTION_PAGES; page++) {
            String url = "https://api.bilibili.com/x/polymer/web-space/seasons_archives_list"
                    + "?mid=" + mid + "&season_id=" + seasonId + "&sort_reverse=false&page_num=" + page + "&page_size=" + BILIBILI_COLLECTION_PAGE_SIZE;
            JsonNode data = fetchData(url);
            if (data == null) {
                break;
            }
            JsonNode archives = data.path("archives");
            if (!archives.isArray() || archives.isEmpty()) {
                break;
            }
            archives.forEach(videos::add);
            int total = data.path("page").path("total").asInt(0);
            if (archives.size() < BILIBILI_COLLECTION_PAGE_SIZE || (total > 0 && videos.size() >= total)) {
                break;
            }
        }
        return fetchDetailedVideos(videos);
    }

    private List<JsonNode> fetchSeriesVideos(Long mid, Long seriesId) {
        List<JsonNode> videos = new ArrayList<>();
        for (int page = 1; page <= BILIBILI_MAX_COLLECTION_PAGES; page++) {
            String url = "https://api.bilibili.com/x/series/archives"
                    + "?mid=" + mid + "&series_id=" + seriesId + "&only_normal=true&sort=desc&pn=" + page + "&ps=" + BILIBILI_COLLECTION_PAGE_SIZE;
            JsonNode data = fetchData(url);
            if (data == null) {
                break;
            }
            JsonNode archives = data.path("archives");
            if (!archives.isArray() || archives.isEmpty()) {
                break;
            }
            archives.forEach(videos::add);
            int total = data.path("page").path("total").asInt(0);
            if (archives.size() < BILIBILI_COLLECTION_PAGE_SIZE || (total > 0 && videos.size() >= total)) {
                break;
            }
        }
        return fetchDetailedVideos(videos);
    }

    private List<JsonNode> fetchDetailedVideos(List<JsonNode> archives) {
        List<JsonNode> detailedVideos = new ArrayList<>();
        for (JsonNode archive : archives) {
            BilibiliVideoKey key = videoKeyOf(archive);
            JsonNode detail = key == null ? null : fetchVideoDetail(key);
            detailedVideos.add(detail == null ? archive : detail);
        }
        return detailedVideos;
    }

    private BilibiliVideoKey videoKeyOf(JsonNode node) {
        String bvid = text(node, "bvid", null);
        if (isBlank(bvid)) {
            bvid = text(node, "bv_id", null);
        }
        if (!isBlank(bvid)) {
            return BilibiliVideoKey.byBvid(bvid);
        }
        long aid = node.path("aid").asLong(0);
        if (aid <= 0) {
            aid = node.path("avid").asLong(0);
        }
        if (aid <= 0) {
            aid = node.path("arc").path("aid").asLong(0);
        }
        return aid > 0 ? BilibiliVideoKey.byAid(aid) : null;
    }

    private boolean shouldExpandUgcSeason(String rawText) {
        return rawText != null && BILIBILI_LIST_LINK.matcher(rawText).find();
    }

    private List<JsonNode> videosFromUgcSeason(JsonNode videoNode) {
        JsonNode sections = videoNode.path("ugc_season").path("sections");
        if (!sections.isArray() || sections.isEmpty()) {
            return List.of();
        }
        List<JsonNode> videos = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (JsonNode section : sections) {
            JsonNode episodes = section.path("episodes");
            if (!episodes.isArray()) {
                continue;
            }
            for (JsonNode episode : episodes) {
                BilibiliVideoKey key = videoKeyOf(episode);
                if (key == null || !seen.add(key.uniqueKey())) {
                    continue;
                }
                JsonNode detail = fetchVideoDetail(key);
                videos.add(detail == null ? episode : detail);
            }
        }
        return videos;
    }

    private JsonNode fetchData(String url) {
        for (int attempt = 0; attempt < BILIBILI_IMPORT_RETRY_TIMES; attempt++) {
            JsonNode data = fetchDataOnce(url);
            if (data != null && !data.isMissingNode() && !data.isNull()) {
                return data;
            }
            sleepBeforeRetry(attempt);
        }
        return null;
    }

    private JsonNode fetchDataOnce(String url) {
        try {
            HttpResponse<String> response = HTTP_CLIENT.send(request(url),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return null;
            }
            JsonNode root = objectMapper.readTree(response.body());
            if (root.path("code").asInt(-1) != 0) {
                return null;
            }
            JsonNode data = root.path("data");
            return data.isMissingNode() ? null : data;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return null;
        }
    }

    private List<JsonNode> deduplicateVideos(List<JsonNode> videoNodes) {
        List<JsonNode> uniqueVideos = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (JsonNode videoNode : videoNodes) {
            BilibiliVideoKey key = videoKeyOf(videoNode);
            String uniqueKey = key == null ? null : key.uniqueKey();
            if (uniqueKey != null && seen.add(uniqueKey)) {
                uniqueVideos.add(videoNode);
            }
        }
        return uniqueVideos;
    }

    private void sleepBeforeRetry(int attempt) {
        try {
            Thread.sleep(300L * (attempt + 1));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void addBvidMatches(List<BilibiliVideoKey> keys, Set<String> seen, Matcher matcher) {
        while (matcher.find()) {
            addKey(keys, seen, BilibiliVideoKey.byBvid(matcher.group(1)));
        }
    }

    private void addAidMatches(List<BilibiliVideoKey> keys, Set<String> seen, Matcher matcher) {
        while (matcher.find()) {
            addKey(keys, seen, BilibiliVideoKey.byAid(Long.parseLong(matcher.group(1))));
        }
    }

    private void addKey(List<BilibiliVideoKey> keys, Set<String> seen, BilibiliVideoKey key) {
        String uniqueKey = key.uniqueKey();
        if (seen.add(uniqueKey)) {
            keys.add(key);
        }
    }

    private void addCollectionKey(List<BilibiliCollectionKey> keys, Set<String> seen, BilibiliCollectionKey key) {
        String uniqueKey = key.uniqueKey();
        if (seen.add(uniqueKey)) {
            keys.add(key);
        }
    }

    private List<Song> buildSongs(JsonNode videoNode, Long userId) {
        JsonNode pages = videoNode.path("pages");
        if (pages.isArray() && pages.size() > 1) {
            List<Song> songs = new ArrayList<>();
            for (JsonNode pageNode : pages) {
                Song song = buildSong(videoNode, pageNode, userId);
                if (song != null) {
                    songs.add(song);
                }
            }
            return songs;
        }
        Song song = buildSong(videoNode, null, userId);
        return song == null ? List.of() : List.of(song);
    }

    private Song buildSong(JsonNode videoNode, JsonNode pageNode, Long userId) {
        String bvid = firstText(videoNode, "bvid", "bv_id");
        long aid = videoNode.path("aid").asLong(0);
        if (aid <= 0) {
            aid = videoNode.path("avid").asLong(0);
        }
        if (aid <= 0) {
            aid = videoNode.path("arc").path("aid").asLong(0);
        }
        String baseTitle = firstText(videoNode, "title", "name");
        if (isBlank(baseTitle)) {
            baseTitle = firstText(videoNode.path("arc"), "title", "name");
        }
        if (isBlank(baseTitle) || (isBlank(bvid) && aid <= 0)) {
            return null;
        }

        int page = pageNode == null ? 0 : pageNode.path("page").asInt(0);
        long cid = pageNode == null ? 0 : pageNode.path("cid").asLong(0);
        String pageTitle = pageNode == null ? null : text(pageNode, "part", null);

        Song song = new Song();
        song.setUserId(userId);
        song.setTitle(isBlank(pageTitle) ? baseTitle : pageTitle);
        String artist = text(videoNode.path("owner"), "name", null);
        if (isBlank(artist)) {
            artist = text(videoNode.path("arc").path("author"), "name", null);
        }
        song.setArtist(isBlank(artist) ? text(videoNode, "author", "Bilibili") : artist);
        song.setAnimeTitle("B\u7ad9\u89c6\u9891\u97f3\u4e50");
        song.setAlbumName(baseTitle);
        String coverUrl = pageNode == null ? null : text(pageNode, "first_frame", null);
        if (isBlank(coverUrl)) {
            coverUrl = text(videoNode, "pic", null);
        }
        if (isBlank(coverUrl)) {
            coverUrl = text(videoNode.path("arc"), "pic", null);
        }
        song.setCoverUrl(normalizeImageUrl(coverUrl));
        song.setSkinUrl(song.getCoverUrl());
        song.setAudioUrl(videoUrl(bvid, aid, page, cid));
        song.setTags("B\u7ad9\u89c6\u9891");
        song.setMood("\u89c6\u9891\u97f3\u4e50");
        song.setFavorite(false);
        int duration = pageNode == null ? 0 : pageNode.path("duration").asInt(0);
        if (duration <= 0) {
            duration = videoNode.path("duration").asInt(0);
        }
        if (duration <= 0) {
            duration = videoNode.path("arc").path("duration").asInt(0);
        }
        song.setDurationSeconds(duration > 0 ? duration : null);
        return song;
    }

    private String videoUrl(String bvid, long aid, int page, long cid) {
        StringBuilder url = new StringBuilder("https://www.bilibili.com/video/");
        url.append(isBlank(bvid) ? "av" + aid : bvid);
        List<String> query = new ArrayList<>();
        if (page > 0) {
            query.add("p=" + page);
        }
        if (cid > 0) {
            query.add("cid=" + cid);
        }
        if (!query.isEmpty()) {
            url.append('?').append(String.join("&", query));
        }
        return url.toString();
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

    private Song buildSong(JsonNode videoNode, Long userId) {
        String bvid = text(videoNode, "bvid", null);
        long aid = videoNode.path("aid").asLong(0);
        String title = text(videoNode, "title", null);
        if (isBlank(title) || (isBlank(bvid) && aid <= 0)) {
            return null;
        }

        String videoUrl = "https://www.bilibili.com/video/" + (isBlank(bvid) ? "av" + aid : bvid);
        Song song = new Song();
        song.setUserId(userId);
        song.setTitle(title);
        String artist = text(videoNode.path("owner"), "name", null);
        song.setArtist(isBlank(artist) ? text(videoNode, "author", "Bilibili") : artist);
        song.setAnimeTitle("B站视频音乐");
        song.setAlbumName(text(videoNode, "tname", "Bilibili"));
        song.setCoverUrl(normalizeImageUrl(text(videoNode, "pic", null)));
        song.setSkinUrl(song.getCoverUrl());
        song.setAudioUrl(videoUrl);
        song.setTags("B站视频");
        song.setMood("视频音乐");
        song.setFavorite(false);
        int duration = videoNode.path("duration").asInt(0);
        song.setDurationSeconds(duration > 0 ? duration : null);
        return song;
    }

    private String normalizeImageUrl(String url) {
        if (isBlank(url)) {
            return null;
        }
        if (url.startsWith("//")) {
            return "https:" + url;
        }
        if (url.startsWith("http://")) {
            return "https://" + url.substring("http://".length());
        }
        return url;
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

    private record BilibiliVideoKey(String bvid, Long aid) {
        static BilibiliVideoKey byBvid(String bvid) {
            return new BilibiliVideoKey(bvid, null);
        }

        static BilibiliVideoKey byAid(Long aid) {
            return new BilibiliVideoKey(null, aid);
        }

        String uniqueKey() {
            return bvid == null ? "aid:" + aid : "bvid:" + bvid.toUpperCase();
        }
    }

    private record BilibiliCollectionKey(Long mid, Long id, String type) {
        String uniqueKey() {
            return mid + ":" + id + ":" + type;
        }
    }

    private static class ImportCount {
        private int read;
        private int imported;
        private int existing;
        private int addedToPlaylist;
        private int skipped;
    }
}
