package com.sekai.song.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sekai.song.service.CloudLyricService;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

@Service
public class CloudLyricServiceImpl implements CloudLyricService {
    private static final String NETEASE_LYRIC_API = "https://music.163.com/api/song/lyric?id=%s&lv=1&kv=1&tv=-1";
    private static final String NETEASE_SEARCH_API = "https://music.163.com/api/search/get/web?s=%s&type=1&limit=8&offset=0";
    private static final String QQ_MUSICU_API = "https://u.y.qq.com/cgi-bin/musicu.fcg?data=";
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String fetchNeteaseLyric(String songId) {
        if (isBlank(songId)) {
            return null;
        }
        try {
            HttpResponse<String> response = HTTP_CLIENT.send(
                    request(String.format(NETEASE_LYRIC_API, songId), "https://music.163.com/"),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (!ok(response)) {
                return null;
            }
            JsonNode root = objectMapper.readTree(response.body());
            return cleanLyric(firstText(root.path("lrc"), "lyric"));
        } catch (IOException | InterruptedException | IllegalArgumentException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return null;
        }
    }

    @Override
    public String fetchQqLyric(String songMid, Long songId) {
        if (!isBlank(songMid) && songMid.chars().allMatch(Character::isDigit) && (songId == null || songId <= 0)) {
            songId = Long.parseLong(songMid);
            songMid = null;
        }
        if (isBlank(songMid) && (songId == null || songId <= 0)) {
            return null;
        }
        String lyric = fetchQqLyricByMusicu(songMid, songId);
        if (!isBlank(lyric)) {
            return lyric;
        }
        return fetchQqLyricByLegacyApi(songMid, songId);
    }

    @Override
    public String fetchBestLyric(String title, String artist) {
        if (isBlank(title)) {
            return null;
        }
        String lyric = fetchBestNeteaseLyric(title, artist);
        if (!isBlank(lyric)) {
            return lyric;
        }
        return fetchBestQqLyric(title, artist);
    }

    private String fetchBestNeteaseLyric(String title, String artist) {
        try {
            String query = joinQuery(title, artist);
            HttpResponse<String> response = HTTP_CLIENT.send(
                    request(String.format(NETEASE_SEARCH_API, URLEncoder.encode(query, StandardCharsets.UTF_8)), "https://music.163.com/"),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (!ok(response)) {
                return null;
            }
            JsonNode songs = objectMapper.readTree(response.body()).path("result").path("songs");
            if (!songs.isArray()) {
                return null;
            }
            JsonNode best = null;
            int bestScore = 0;
            for (JsonNode song : songs) {
                int score = matchScore(title, artist, firstText(song, "name"), joinArtists(song.path("artists")));
                if (score > bestScore) {
                    best = song;
                    bestScore = score;
                }
            }
            if (best == null || bestScore < 55) {
                return null;
            }
            long id = best.path("id").asLong(0);
            return id > 0 ? fetchNeteaseLyric(String.valueOf(id)) : null;
        } catch (IOException | InterruptedException | IllegalArgumentException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return null;
        }
    }

    private String fetchBestQqLyric(String title, String artist) {
        try {
            String payload = qqSearchPayload(joinQuery(title, artist));
            HttpResponse<String> response = HTTP_CLIENT.send(
                    request(QQ_MUSICU_API + URLEncoder.encode(payload, StandardCharsets.UTF_8), "https://y.qq.com/"),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (!ok(response)) {
                return null;
            }
            JsonNode list = objectMapper.readTree(response.body())
                    .path("search")
                    .path("data")
                    .path("body")
                    .path("song")
                    .path("list");
            if (!list.isArray()) {
                return null;
            }
            JsonNode best = null;
            int bestScore = 0;
            for (JsonNode song : list) {
                int score = matchScore(title, artist, firstText(song, "title", "name"), joinArtists(song.path("singer")));
                if (score > bestScore) {
                    best = song;
                    bestScore = score;
                }
            }
            if (best == null || bestScore < 55) {
                return null;
            }
            String mid = firstText(best, "mid", "songmid");
            long id = best.path("id").asLong(0);
            return fetchQqLyric(mid, id > 0 ? id : null);
        } catch (IOException | InterruptedException | IllegalArgumentException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return null;
        }
    }

    private String fetchQqLyricByMusicu(String songMid, Long songId) {
        try {
            String payload = qqLyricPayload(songMid, songId);
            HttpResponse<String> response = HTTP_CLIENT.send(
                    request(QQ_MUSICU_API + URLEncoder.encode(payload, StandardCharsets.UTF_8), "https://y.qq.com/"),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (!ok(response)) {
                return null;
            }
            JsonNode data = objectMapper.readTree(response.body()).path("lyric").path("data");
            return firstDecodedLyric(data, "lyric", "trans");
        } catch (IOException | InterruptedException | IllegalArgumentException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return null;
        }
    }

    private String fetchQqLyricByLegacyApi(String songMid, Long songId) {
        try {
            StringBuilder url = new StringBuilder("https://c.y.qq.com/lyric/fcgi-bin/fcg_query_lyric_new.fcg?format=json&nobase64=0");
            if (!isBlank(songMid)) {
                url.append("&songmid=").append(URLEncoder.encode(songMid, StandardCharsets.UTF_8));
            }
            if (songId != null && songId > 0) {
                url.append("&songid=").append(songId);
            }
            HttpResponse<String> response = HTTP_CLIENT.send(
                    request(url.toString(), "https://y.qq.com/"),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (!ok(response)) {
                return null;
            }
            JsonNode root = objectMapper.readTree(response.body());
            return firstDecodedLyric(root, "lyric", "trans");
        } catch (IOException | InterruptedException | IllegalArgumentException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return null;
        }
    }

    private String qqLyricPayload(String songMid, Long songId) throws IOException {
        ObjectNode root = objectMapper.createObjectNode();
        ObjectNode comm = root.putObject("comm");
        comm.put("ct", 24);
        comm.put("cv", 0);
        ObjectNode lyric = root.putObject("lyric");
        lyric.put("method", "GetPlayLyricInfo");
        lyric.put("module", "music.musichallSong.PlayLyricInfo");
        ObjectNode param = lyric.putObject("param");
        param.put("songMID", isBlank(songMid) ? "" : songMid);
        param.put("songID", songId == null ? 0 : songId);
        param.put("qrc", 0);
        param.put("roma", 0);
        param.put("trans", 1);
        return objectMapper.writeValueAsString(root);
    }

    private String qqSearchPayload(String keyword) throws IOException {
        ObjectNode root = objectMapper.createObjectNode();
        ObjectNode comm = root.putObject("comm");
        comm.put("ct", 24);
        comm.put("cv", 0);
        ObjectNode search = root.putObject("search");
        search.put("method", "DoSearchForQQMusicDesktop");
        search.put("module", "music.search.SearchCgiService");
        ObjectNode param = search.putObject("param");
        param.put("query", keyword);
        param.put("page_num", 1);
        param.put("num_per_page", 8);
        param.put("search_type", 0);
        return objectMapper.writeValueAsString(root);
    }

    private String firstDecodedLyric(JsonNode node, String... fields) {
        for (String field : fields) {
            String value = firstText(node, field);
            if (!isBlank(value)) {
                String decoded = decodeBase64IfNeeded(value);
                String cleaned = cleanLyric(decoded);
                if (!isBlank(cleaned)) {
                    return cleaned;
                }
            }
        }
        return null;
    }

    private String decodeBase64IfNeeded(String value) {
        String trimmed = value.trim();
        if (trimmed.contains("[") && trimmed.contains("]")) {
            return trimmed;
        }
        try {
            return new String(Base64.getDecoder().decode(trimmed), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return trimmed;
        }
    }

    private String cleanLyric(String value) {
        if (isBlank(value)) {
            return null;
        }
        String lyric = value.replace("\r\n", "\n").replace('\r', '\n').trim();
        return lyric.contains("[") && lyric.contains("]") ? lyric : null;
    }

    private String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode value = node.path(field);
            if (!value.isMissingNode() && !value.isNull() && !isBlank(value.asText(null))) {
                return value.asText(null);
            }
        }
        return null;
    }

    private String joinQuery(String title, String artist) {
        return isBlank(artist) ? title.trim() : title.trim() + " " + artist.trim();
    }

    private String joinArtists(JsonNode artists) {
        if (!artists.isArray()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (JsonNode artist : artists) {
            String name = firstText(artist, "name", "title");
            if (!isBlank(name)) {
                if (!builder.isEmpty()) {
                    builder.append(' ');
                }
                builder.append(name);
            }
        }
        return builder.toString();
    }

    private int matchScore(String expectedTitle, String expectedArtist, String actualTitle, String actualArtist) {
        String expectedTitleKey = normalize(expectedTitle);
        String actualTitleKey = normalize(actualTitle);
        if (expectedTitleKey.isEmpty() || actualTitleKey.isEmpty()) {
            return 0;
        }
        int score = 0;
        if (expectedTitleKey.equals(actualTitleKey)) {
            score += 80;
        } else if (expectedTitleKey.contains(actualTitleKey) || actualTitleKey.contains(expectedTitleKey)) {
            score += 62;
        } else {
            score += overlapScore(expectedTitleKey, actualTitleKey);
        }
        String expectedArtistKey = normalize(expectedArtist);
        String actualArtistKey = normalize(actualArtist);
        if (!expectedArtistKey.isEmpty() && !actualArtistKey.isEmpty()) {
            if (expectedArtistKey.equals(actualArtistKey)) {
                score += 25;
            } else if (expectedArtistKey.contains(actualArtistKey) || actualArtistKey.contains(expectedArtistKey)) {
                score += 16;
            } else if (overlapScore(expectedArtistKey, actualArtistKey) >= 18) {
                score += 8;
            }
        }
        return score;
    }

    private int overlapScore(String left, String right) {
        int common = 0;
        int max = Math.min(left.length(), right.length());
        for (int size = max; size >= 2; size--) {
            for (int start = 0; start + size <= left.length(); start++) {
                if (right.contains(left.substring(start, start + size))) {
                    common = Math.max(common, size);
                }
            }
            if (common > 0) {
                break;
            }
        }
        return common == 0 ? 0 : Math.min(50, common * 50 / Math.max(left.length(), right.length()));
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        String lower = value.toLowerCase();
        StringBuilder builder = new StringBuilder(lower.length());
        for (int i = 0; i < lower.length(); i++) {
            char ch = lower.charAt(i);
            if (Character.isLetterOrDigit(ch)) {
                builder.append(ch);
            }
        }
        return builder.toString();
    }

    private HttpRequest request(String url, String referer) {
        return HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header("User-Agent", "Mozilla/5.0 SekaiSong/1.0")
                .header("Referer", referer)
                .header("Origin", referer.endsWith("/") ? referer.substring(0, referer.length() - 1) : referer)
                .GET()
                .build();
    }

    private boolean ok(HttpResponse<String> response) {
        return response.statusCode() >= 200 && response.statusCode() < 300;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
