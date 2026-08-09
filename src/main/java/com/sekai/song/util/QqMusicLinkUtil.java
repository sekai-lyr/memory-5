package com.sekai.song.util;

import com.sekai.song.model.QqLink;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class QqMusicLinkUtil {
    private static final Pattern PLAYLIST_PATH = Pattern.compile("(?i)/(?:n/ryqq(?:_v2)?/playlist|n/yqq/playlist)/(\\d{5,})(?:\\.html)?");
    private static final Pattern TAOGE_LINK_ID = Pattern.compile("(?i)/taoge\\.html[^\\s\"'<>]*[?&#]id=(\\d{5,})");
    private static final Pattern DISSTID_PARAM = Pattern.compile("(?i)[?&#](?:disstid|dissid)=([0-9A-Za-z]{5,})");
    private static final Pattern SONG_DETAIL_PATH = Pattern.compile("(?i)/(?:n/ryqq/songDetail|n/yqq/song)/([0-9A-Za-z]{8,})(?:\\.html)?");
    private static final Pattern SONG_MID_PARAM = Pattern.compile("(?i)[?&#](?:songmid|song_mid|mid)=([0-9A-Za-z]{8,})");
    private static final Pattern SONG_ID_PARAM = Pattern.compile("(?i)[?&#](?:songid|song_id)=([0-9]{5,})");

    private QqMusicLinkUtil() {
    }

    public static QqLink parse(String rawLink) {
        if (rawLink == null || rawLink.isBlank()) {
            return new QqLink("empty", null, "请先粘贴 QQ 音乐单曲链接");
        }
        String link = rawLink.trim();
        if (!link.contains("y.qq.com") && !link.contains("music.qq.com")) {
            return new QqLink("unknown", null, "这看起来不是 QQ 音乐链接");
        }
        String playlistId = findPlaylistId(link);
        if (playlistId != null) {
            return new QqLink("playlist", playlistId, "宸茶瘑鍒负 QQ 闊充箰姝屽崟");
        }
        String id = findSongId(link);
        if (id != null) {
            return new QqLink("song", id, "已识别为 QQ 音乐单曲");
        }
        return new QqLink("unknown", null, "暂时只能识别 QQ 音乐单曲链接、songmid 或 songid");
    }

    private static String findSongId(String link) {
        Matcher pathMatcher = SONG_DETAIL_PATH.matcher(link);
        if (pathMatcher.find()) {
            return pathMatcher.group(1);
        }
        Matcher midMatcher = SONG_MID_PARAM.matcher(link);
        if (midMatcher.find()) {
            return midMatcher.group(1);
        }
        Matcher idMatcher = SONG_ID_PARAM.matcher(link);
        return idMatcher.find() ? idMatcher.group(1) : null;
    }

    private static String findPlaylistId(String link) {
        Matcher pathMatcher = PLAYLIST_PATH.matcher(link);
        if (pathMatcher.find()) {
            return pathMatcher.group(1);
        }
        Matcher taogeMatcher = TAOGE_LINK_ID.matcher(link);
        if (taogeMatcher.find()) {
            return taogeMatcher.group(1);
        }
        Matcher disstidMatcher = DISSTID_PARAM.matcher(link);
        return disstidMatcher.find() ? disstidMatcher.group(1) : null;
    }
}
