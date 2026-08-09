package com.sekai.song.util;

import com.sekai.song.model.NeteaseLink;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class NeteaseMusicLinkUtil {
    private static final Pattern ID_PARAM = Pattern.compile("[?&#]id=(\\d+)");
    private static final Pattern PLAYLIST_PATH = Pattern.compile("/playlist/(\\d+)");
    private static final Pattern SONG_PATH = Pattern.compile("/song/(\\d+)");
    private static final Pattern USER_PATH = Pattern.compile("/user/(\\d+)");

    private NeteaseMusicLinkUtil() {
    }

    public static NeteaseLink parse(String rawLink) {
        if (rawLink == null || rawLink.isBlank()) {
            return new NeteaseLink("empty", null, "请先粘贴网易云音乐链接");
        }
        String link = rawLink.trim();
        if (!link.contains("music.163.com")) {
            return new NeteaseLink("unknown", null, "这看起来不是网易云音乐链接");
        }
        if (link.contains("/playlist") || link.contains("playlist?")) {
            return new NeteaseLink("playlist", findId(link, PLAYLIST_PATH), "已识别为网易云歌单");
        }
        if (link.contains("/song") || link.contains("song?")) {
            return new NeteaseLink("song", findId(link, SONG_PATH), "已识别为网易云单曲");
        }
        if (link.contains("/user") || link.contains("user?")) {
            return new NeteaseLink("user", findId(link, USER_PATH), "这是网易云用户主页，不是具体歌单链接。请进入某个公开歌单后复制分享链接。");
        }
        return new NeteaseLink("unknown", findId(link, null), "暂时只能识别单曲、歌单或用户主页链接");
    }

    private static String findId(String link, Pattern pathPattern) {
        if (pathPattern != null) {
            Matcher pathMatcher = pathPattern.matcher(link);
            if (pathMatcher.find()) {
                return pathMatcher.group(1);
            }
        }
        Matcher paramMatcher = ID_PARAM.matcher(link);
        return paramMatcher.find() ? paramMatcher.group(1) : null;
    }
}
