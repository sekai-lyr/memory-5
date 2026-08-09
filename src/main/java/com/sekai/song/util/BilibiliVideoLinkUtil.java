package com.sekai.song.util;

import com.sekai.song.model.BilibiliLink;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class BilibiliVideoLinkUtil {
    private static final Pattern BVID = Pattern.compile("(?i)\\b(BV[0-9A-Za-z]{10})\\b");
    private static final Pattern AID = Pattern.compile("(?i)(?:/video/av|\\bav)(\\d{5,})");
    private static final Pattern AID_PARAM = Pattern.compile("(?i)[?&#](?:aid|avid)=([0-9]{5,})");
    private static final Pattern SPACE_LIST = Pattern.compile("(?i)space\\.bilibili\\.com/(\\d+)/lists/(\\d+)");
    private static final Pattern SPACE_SERIES_DETAIL = Pattern.compile("(?i)space\\.bilibili\\.com/(\\d+)/channel/seriesdetail\\?[^\\s\"']*\\bsid=(\\d+)");

    private BilibiliVideoLinkUtil() {
    }

    public static BilibiliLink parse(String rawLink) {
        if (rawLink == null || rawLink.isBlank()) {
            return new BilibiliLink("empty", null, "请先粘贴 B 站视频链接、作品集链接、BV 号或 av 号");
        }
        String link = rawLink.trim();
        String collectionId = findCollectionId(link);
        if (collectionId != null) {
            return new BilibiliLink("collection", collectionId, "已识别为 B 站作品集/合集");
        }
        String id = findVideoId(link);
        if (id != null) {
            return new BilibiliLink("video", id, "已识别为 B 站视频");
        }
        if (link.contains("b23.tv")) {
            return new BilibiliLink("short", null, "这是 B 站短链，批量导入时会自动尝试展开");
        }
        if (!link.contains("bilibili.com")) {
            return new BilibiliLink("unknown", null, "这看起来不是 B 站视频链接");
        }
        return new BilibiliLink("unknown", null, "暂时只能识别 B 站视频链接、作品集链接、BV 号或 av 号");
    }

    private static String findVideoId(String text) {
        Matcher bvMatcher = BVID.matcher(text);
        if (bvMatcher.find()) {
            return bvMatcher.group(1);
        }
        Matcher aidMatcher = AID.matcher(text);
        if (aidMatcher.find()) {
            return "av" + aidMatcher.group(1);
        }
        Matcher aidParamMatcher = AID_PARAM.matcher(text);
        return aidParamMatcher.find() ? "av" + aidParamMatcher.group(1) : null;
    }

    private static String findCollectionId(String text) {
        Matcher listMatcher = SPACE_LIST.matcher(text);
        if (listMatcher.find()) {
            return listMatcher.group(1) + ":" + listMatcher.group(2);
        }
        Matcher seriesMatcher = SPACE_SERIES_DETAIL.matcher(text);
        return seriesMatcher.find() ? seriesMatcher.group(1) + ":" + seriesMatcher.group(2) : null;
    }
}
