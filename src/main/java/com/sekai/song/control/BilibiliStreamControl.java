package com.sekai.song.control;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sekai.song.model.Song;
import com.sekai.song.model.User;
import com.sekai.song.service.SongService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Controller
public class BilibiliStreamControl {
    private static final String PLAY_URL_API = "https://api.bilibili.com/x/player/playurl?";
    private static final Duration PLAY_URL_TTL = Duration.ofMinutes(20);
    private static final int DEFAULT_QUALITY = 80;
    private static final int STREAM_BUFFER_SIZE = 256 * 1024;
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .version(HttpClient.Version.HTTP_2)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    @Resource
    private SongService songService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, CachedPlayUrl> playUrlCache = new ConcurrentHashMap<>();

    @GetMapping("/bilibili/direct/{id}")
    public ResponseEntity<Void> direct(@PathVariable Long id,
                                       @RequestParam(value = "quality", required = false) Integer quality,
                                       @RequestParam(value = "refresh", required = false) String refresh,
                                       HttpSession session) {
        User user = requireUser(session);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        Song song = songService.getByIdAndUserId(id, user.getId());
        if (!isPlayableBilibiliSong(song)) {
            return ResponseEntity.notFound().build();
        }

        String playUrl = fetchPlayUrl(song, safeQuality(quality), refresh != null);
        if (playUrl == null || playUrl.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setLocation(URI.create(playUrl));
        headers.setCacheControl("private, max-age=300");
        headers.set("Referrer-Policy", "no-referrer");
        return new ResponseEntity<>(headers, HttpStatus.FOUND);
    }

    @GetMapping("/bilibili/stream/{id}")
    public ResponseEntity<StreamingResponseBody> stream(@PathVariable Long id,
                                                        @RequestParam(value = "quality", required = false) Integer quality,
                                                        @RequestParam(value = "refresh", required = false) String refresh,
                                                        HttpSession session,
                                                        HttpServletRequest servletRequest) {
        User user = requireUser(session);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        Song song = songService.getByIdAndUserId(id, user.getId());
        if (!isPlayableBilibiliSong(song)) {
            return ResponseEntity.notFound().build();
        }

        String playUrl = fetchPlayUrl(song, safeQuality(quality), refresh != null);
        if (playUrl == null || playUrl.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
        }

        try {
            HttpResponse<InputStream> upstream = HTTP_CLIENT.send(
                    streamRequest(playUrl, servletRequest.getHeader(HttpHeaders.RANGE), song),
                    HttpResponse.BodyHandlers.ofInputStream());
            if (upstream.statusCode() != 200 && upstream.statusCode() != 206) {
                return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
            }

            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.ACCEPT_RANGES, "bytes");
            headers.setCacheControl("private, max-age=60");
            headers.set("X-Accel-Buffering", "no");
            headers.setContentType(MediaType.parseMediaType(
                    upstream.headers().firstValue(HttpHeaders.CONTENT_TYPE).orElse("video/mp4")));
            upstream.headers().firstValue(HttpHeaders.CONTENT_LENGTH)
                    .ifPresent(value -> headers.set(HttpHeaders.CONTENT_LENGTH, value));
            upstream.headers().firstValue(HttpHeaders.CONTENT_RANGE)
                    .ifPresent(value -> headers.set(HttpHeaders.CONTENT_RANGE, value));

            StreamingResponseBody body = outputStream -> {
                try (InputStream inputStream = upstream.body()) {
                    byte[] buffer = new byte[STREAM_BUFFER_SIZE];
                    int read;
                    while ((read = inputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, read);
                    }
                }
            };
            HttpStatus status = upstream.statusCode() == 206 ? HttpStatus.PARTIAL_CONTENT : HttpStatus.OK;
            return new ResponseEntity<>(body, headers, status);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
        }
    }

    private String fetchPlayUrl(Song song, int quality, boolean refresh) {
        for (int candidateQuality : qualityCandidates(quality)) {
            String playUrl = fetchSinglePlayUrl(song, candidateQuality, refresh);
            if (playUrl != null && !playUrl.isBlank()) {
                return playUrl;
            }
        }
        return null;
    }

    private String fetchSinglePlayUrl(Song song, int quality, boolean refresh) {
        String cacheKey = song.getId() + ":" + song.getBilibiliCid() + ":" + quality;
        CachedPlayUrl cached = playUrlCache.get(cacheKey);
        if (!refresh && cached != null && cached.expiresAt().isAfter(Instant.now())) {
            return cached.url();
        }
        if (refresh) {
            playUrlCache.remove(cacheKey);
        }

        try {
            HttpResponse<String> response = HTTP_CLIENT.send(
                    apiRequest(playUrlApi(song, quality)),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return null;
            }
            JsonNode root = objectMapper.readTree(response.body());
            if (root.path("code").asInt(-1) != 0) {
                return null;
            }
            JsonNode durl = root.path("data").path("durl");
            if (!durl.isArray() || durl.isEmpty()) {
                return null;
            }
            String url = selectFastestUrl(collectPlayUrls(durl), song);
            if (url != null && !url.isBlank()) {
                playUrlCache.put(cacheKey, new CachedPlayUrl(url, Instant.now().plus(PLAY_URL_TTL)));
            }
            return url;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return null;
        }
    }

    private List<String> collectPlayUrls(JsonNode durl) {
        Set<String> urls = new LinkedHashSet<>();
        durl.forEach(item -> {
            String primaryUrl = item.path("url").asText(null);
            if (primaryUrl != null && !primaryUrl.isBlank()) {
                urls.add(primaryUrl);
            }
            JsonNode backupUrls = item.path("backup_url");
            if (backupUrls.isArray()) {
                backupUrls.forEach(backupUrl -> {
                    String value = backupUrl.asText(null);
                    if (value != null && !value.isBlank()) {
                        urls.add(value);
                    }
                });
            }
        });
        return new ArrayList<>(urls);
    }

    private String selectFastestUrl(List<String> urls, Song song) {
        if (urls.isEmpty()) {
            return null;
        }
        if (urls.size() == 1) {
            return urls.get(0);
        }

        String bestUrl = urls.get(0);
        long bestElapsed = Long.MAX_VALUE;
        for (String url : urls.stream().limit(6).toList()) {
            long elapsed = probeUrl(url, song);
            if (elapsed >= 0 && elapsed < bestElapsed) {
                bestElapsed = elapsed;
                bestUrl = url;
            }
        }
        return bestUrl;
    }

    private long probeUrl(String url, Song song) {
        long startedAt = System.nanoTime();
        try {
            HttpResponse<InputStream> response = HTTP_CLIENT.send(
                    streamRequest(url, "bytes=0-65535", song, Duration.ofSeconds(4)),
                    HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200 && response.statusCode() != 206) {
                return -1;
            }
            try (InputStream inputStream = response.body()) {
                byte[] buffer = new byte[8192];
                int read = inputStream.read(buffer);
                if (read <= 0) {
                    return -1;
                }
            }
            return Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
        } catch (IOException | InterruptedException | IllegalArgumentException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return -1;
        }
    }

    private int[] qualityCandidates(int quality) {
        if (quality >= 80) {
            return new int[]{quality, 64, 32, 16};
        }
        if (quality >= 64) {
            return new int[]{quality, 32, 16};
        }
        if (quality >= 32) {
            return new int[]{quality, 16};
        }
        return new int[]{16};
    }

    private String playUrlApi(Song song, int quality) {
        StringBuilder url = new StringBuilder(PLAY_URL_API);
        if (song.getBilibiliBvid() != null) {
            url.append("bvid=").append(URLEncoder.encode(song.getBilibiliBvid(), StandardCharsets.UTF_8));
        } else {
            url.append("avid=").append(song.getBilibiliAid());
        }
        url.append("&cid=").append(URLEncoder.encode(song.getBilibiliCid(), StandardCharsets.UTF_8));
        url.append("&qn=").append(quality).append("&fnval=0&fourk=1");
        return url.toString();
    }

    private HttpRequest apiRequest(String url) {
        return HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header(HttpHeaders.USER_AGENT, "Mozilla/5.0 SekaiSong/1.0")
                .header(HttpHeaders.REFERER, "https://www.bilibili.com/")
                .GET()
                .build();
    }

    private HttpRequest streamRequest(String url, String range, Song song) {
        return streamRequest(url, range, song, Duration.ofMinutes(3));
    }

    private HttpRequest streamRequest(String url, String range, Song song, Duration timeout) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(timeout)
                .header(HttpHeaders.USER_AGENT, "Mozilla/5.0 SekaiSong/1.0")
                .header(HttpHeaders.REFERER, bilibiliReferer(song))
                .GET();
        if (range != null && !range.isBlank()) {
            builder.header(HttpHeaders.RANGE, range);
        }
        return builder.build();
    }

    private boolean isPlayableBilibiliSong(Song song) {
        return song != null && song.getBilibiliEmbedParam() != null && song.getBilibiliCid() != null;
    }

    private int safeQuality(Integer quality) {
        return quality == null || quality <= 0 ? DEFAULT_QUALITY : quality;
    }

    private String bilibiliReferer(Song song) {
        String videoUrl = song.getBilibiliVideoUrl();
        return videoUrl == null ? "https://www.bilibili.com/" : videoUrl;
    }

    private User requireUser(HttpSession session) {
        Object loginUser = session.getAttribute("loginUser");
        if (loginUser instanceof User user) {
            return user;
        }
        return null;
    }

    private record CachedPlayUrl(String url, Instant expiresAt) {
    }
}
