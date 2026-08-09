package com.sekai.song.service.impl;

import com.sekai.song.model.Playlist;
import com.sekai.song.model.SmartPlaylistResult;
import com.sekai.song.model.SmartPlaylistStats;
import com.sekai.song.model.Song;
import com.sekai.song.model.User;
import com.sekai.song.service.PlaylistService;
import com.sekai.song.service.SmartPlaylistService;
import com.sekai.song.service.SongService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Service
public class SmartPlaylistServiceImpl implements SmartPlaylistService {
    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 500;

    @Resource
    private SongService songService;

    @Resource
    private PlaylistService playlistService;

    @Override
    public SmartPlaylistStats statsOf(User user) {
        SmartPlaylistStats stats = new SmartPlaylistStats();
        if (user == null || user.getId() == null) {
            return stats;
        }
        List<Song> songs = songService.listByUserId(user.getId());
        stats.setTotalSongs(songs.size());
        stats.setLocalAudioSongs((int) songs.stream().filter(this::hasLocalAudio).count());
        stats.setLocalVideoSongs((int) songs.stream().filter(this::hasLocalVideo).count());
        stats.setBilibiliSongs((int) songs.stream().filter(this::isBilibili).count());
        stats.setNeteaseSongs((int) songs.stream().filter(this::isNetease).count());
        stats.setQqSongs((int) songs.stream().filter(this::isQq).count());
        stats.setFavoriteSongs((int) songs.stream().filter(song -> Boolean.TRUE.equals(song.getFavorite())).count());
        stats.setThirdPartyOnlySongs((int) songs.stream().filter(this::isThirdPartyOnly).count());
        return stats;
    }

    @Override
    public SmartPlaylistResult generate(User user,
                                        String playlistName,
                                        String sourceMode,
                                        String keyword,
                                        boolean favoriteOnly,
                                        boolean deduplicate,
                                        boolean shuffle,
                                        String sortMode,
                                        Integer limit,
                                        String coverUrl) {
        if (user == null || user.getId() == null) {
            return SmartPlaylistResult.fail("请先登录");
        }
        String cleanName = cleanPlaylistName(playlistName);
        List<Song> selected = songService.listByUserId(user.getId()).stream()
                .filter(song -> matchesSource(song, sourceMode))
                .filter(song -> !favoriteOnly || Boolean.TRUE.equals(song.getFavorite()))
                .filter(song -> matchesKeyword(song, keyword))
                .toList();
        if (deduplicate) {
            selected = deduplicate(selected);
        }
        selected = sortSongs(selected, sortMode, shuffle);
        selected = selected.stream().limit(safeLimit(limit)).toList();
        if (selected.isEmpty()) {
            return SmartPlaylistResult.fail("没有找到符合规则的歌曲，请放宽筛选条件再试");
        }

        Playlist playlist = new Playlist();
        playlist.setUserId(user.getId());
        playlist.setName(cleanName);
        playlist.setDescription(buildDescription(sourceMode, keyword, favoriteOnly, deduplicate, shuffle, selected.size()));
        playlist.setCoverUrl(hasText(coverUrl) ? coverUrl.trim() : firstCover(selected));
        if (!playlistService.add(playlist)) {
            return SmartPlaylistResult.fail("智能歌单创建失败，请检查歌单名称");
        }
        int added = 0;
        for (Song song : selected) {
            if (playlistService.addSong(playlist.getId(), song.getId(), user.getId())) {
                added++;
            }
        }
        return SmartPlaylistResult.success(playlist.getId(), added,
                "已生成智能歌单「" + playlist.getName() + "」，加入 " + added + " 首歌曲");
    }

    private List<Song> deduplicate(List<Song> songs) {
        Map<String, Song> unique = new LinkedHashMap<>();
        for (Song song : songs) {
            unique.putIfAbsent(duplicateKey(song), song);
        }
        return new ArrayList<>(unique.values());
    }

    private List<Song> sortSongs(List<Song> songs, String sortMode, boolean shuffle) {
        List<Song> sorted = new ArrayList<>(songs);
        if (shuffle) {
            Collections.shuffle(sorted);
            return sorted;
        }
        String mode = hasText(sortMode) ? sortMode.trim() : "recent";
        Comparator<Song> comparator = switch (mode) {
            case "title" -> Comparator.comparing((Song song) -> blankToLast(song.getTitle()), String.CASE_INSENSITIVE_ORDER);
            case "artist" -> Comparator.comparing((Song song) -> blankToLast(song.getArtist()), String.CASE_INSENSITIVE_ORDER)
                    .thenComparing((Song song) -> blankToLast(song.getTitle()), String.CASE_INSENSITIVE_ORDER);
            case "favorite" -> Comparator.comparing((Song song) -> !Boolean.TRUE.equals(song.getFavorite()))
                    .thenComparing(this::songTime, Comparator.reverseOrder());
            default -> Comparator.comparing(this::songTime, Comparator.reverseOrder());
        };
        sorted.sort(comparator.thenComparing(Song::getId, Comparator.nullsLast(Comparator.reverseOrder())));
        return sorted;
    }

    private boolean matchesSource(Song song, String sourceMode) {
        String mode = hasText(sourceMode) ? sourceMode.trim() : "all";
        return switch (mode) {
            case "local-audio" -> hasLocalAudio(song);
            case "local-video" -> hasLocalVideo(song);
            case "offline" -> hasLocalAudio(song) || hasLocalVideo(song);
            case "bilibili" -> isBilibili(song);
            case "netease" -> isNetease(song);
            case "qq" -> isQq(song);
            case "third-party-only" -> isThirdPartyOnly(song);
            case "missing-local-audio" -> !hasLocalAudio(song) && !isBilibili(song);
            default -> true;
        };
    }

    private boolean matchesKeyword(Song song, String keyword) {
        if (!hasText(keyword)) {
            return true;
        }
        String key = keyword.trim().toLowerCase(Locale.ROOT);
        return contains(key, song.getTitle())
                || contains(key, song.getArtist())
                || contains(key, song.getAnimeTitle())
                || contains(key, song.getAlbumName())
                || contains(key, song.getTags())
                || contains(key, song.getMood());
    }

    private boolean contains(String keyword, String value) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(keyword);
    }

    private String duplicateKey(Song song) {
        String title = normalize(song.getTitle());
        String artist = normalize(song.getArtist());
        if (!title.isBlank() || !artist.isBlank()) {
            return title + "|" + artist;
        }
        if (hasText(song.getAudioUrl())) {
            return "url|" + normalize(song.getAudioUrl());
        }
        if (hasText(song.getLocalAudioUrl())) {
            return "local|" + normalize(song.getLocalAudioUrl());
        }
        return "id|" + Objects.toString(song.getId(), "");
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

    private LocalDateTime songTime(Song song) {
        if (song.getUpdateTime() != null) {
            return song.getUpdateTime();
        }
        return song.getCreateTime() == null ? LocalDateTime.MIN : song.getCreateTime();
    }

    private String buildDescription(String sourceMode, String keyword, boolean favoriteOnly,
                                    boolean deduplicate, boolean shuffle, int count) {
        List<String> parts = new ArrayList<>();
        parts.add("智能歌单工厂生成");
        parts.add("来源：" + sourceLabel(sourceMode));
        if (hasText(keyword)) {
            parts.add("关键词：" + keyword.trim());
        }
        if (favoriteOnly) {
            parts.add("仅收藏");
        }
        if (deduplicate) {
            parts.add("已去重");
        }
        if (shuffle) {
            parts.add("随机排序");
        }
        parts.add(count + " 首");
        return String.join(" · ", parts);
    }

    private String sourceLabel(String sourceMode) {
        String mode = hasText(sourceMode) ? sourceMode.trim() : "all";
        return switch (mode) {
            case "local-audio" -> "本地音频";
            case "local-video" -> "本地视频";
            case "offline" -> "离线可播放";
            case "bilibili" -> "B站视频";
            case "netease" -> "网易云";
            case "qq" -> "QQ音乐";
            case "third-party-only" -> "仅第三方链接";
            case "missing-local-audio" -> "未绑定本地音频";
            default -> "全部歌曲";
        };
    }

    private int safeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private String cleanPlaylistName(String playlistName) {
        if (!hasText(playlistName)) {
            return "智能歌单 " + java.time.LocalDate.now();
        }
        String trimmed = playlistName.trim();
        return trimmed.length() > 120 ? trimmed.substring(0, 120) : trimmed;
    }

    private String firstCover(List<Song> songs) {
        return songs.stream()
                .map(Song::getCoverUrl)
                .filter(this::hasText)
                .findFirst()
                .orElse("/images/login-music-room.png");
    }

    private String blankToLast(String value) {
        return hasText(value) ? value.trim() : "\uffff";
    }

    private boolean hasLocalAudio(Song song) {
        return song != null && hasText(song.getLocalAudioUrl());
    }

    private boolean hasLocalVideo(Song song) {
        return song != null && hasText(song.getLocalVideoUrl());
    }

    private boolean isBilibili(Song song) {
        return song != null && hasText(song.getAudioUrl()) && song.getAudioUrl().contains("bilibili.com");
    }

    private boolean isNetease(Song song) {
        return song != null && hasText(song.getAudioUrl()) && song.getAudioUrl().contains("music.163.com");
    }

    private boolean isQq(Song song) {
        return song != null && hasText(song.getAudioUrl()) && song.getAudioUrl().contains("y.qq.com");
    }

    private boolean isThirdPartyOnly(Song song) {
        return song != null && !hasLocalAudio(song) && !hasLocalVideo(song)
                && (isNetease(song) || isQq(song) || isBilibili(song));
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
