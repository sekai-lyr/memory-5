package com.sekai.song.service.impl;

import com.sekai.song.model.InsightAction;
import com.sekai.song.model.InsightMetric;
import com.sekai.song.model.MusicInsightReport;
import com.sekai.song.model.Playlist;
import com.sekai.song.model.PlaylistInsight;
import com.sekai.song.model.Song;
import com.sekai.song.model.User;
import com.sekai.song.service.MusicInsightService;
import com.sekai.song.service.PlaylistService;
import com.sekai.song.service.SongService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class MusicInsightServiceImpl implements MusicInsightService {
    @Resource
    private SongService songService;

    @Resource
    private PlaylistService playlistService;

    @Override
    public MusicInsightReport buildReport(User user) {
        MusicInsightReport report = new MusicInsightReport();
        if (user == null || user.getId() == null) {
            finish(report);
            return report;
        }
        List<Song> songs = songService.listByUserId(user.getId());
        List<Playlist> playlists = playlistService.listByUserId(user.getId());
        report.setSongCount(songs.size());
        report.setPlaylistCount(playlists.size());

        long totalDuration = 0;
        Map<String, Integer> artists = new LinkedHashMap<>();
        int netease = 0;
        int qq = 0;
        int bilibili = 0;
        int manual = 0;
        int localAudio = 0;
        int localVideo = 0;

        for (Song song : songs) {
            boolean hasLocalAudio = hasText(song.getLocalAudioUrl());
            boolean hasLocalVideo = hasText(song.getLocalVideoUrl());
            boolean hasLyrics = hasText(song.getLyricText());
            boolean hasDuration = song.getDurationSeconds() != null && song.getDurationSeconds() > 0;
            boolean isBilibili = isBilibili(song);

            if (hasLocalAudio) {
                localAudio++;
            }
            if (hasLocalVideo) {
                localVideo++;
            }
            if (hasLocalAudio || hasLocalVideo) {
                report.setLocalReadyCount(report.getLocalReadyCount() + 1);
            }
            if (hasLyrics) {
                report.setLyricCount(report.getLyricCount() + 1);
            }
            if (hasDuration) {
                report.setDurationCount(report.getDurationCount() + 1);
                totalDuration += song.getDurationSeconds();
            }
            if (Boolean.TRUE.equals(song.getFavorite())) {
                report.setFavoriteCount(report.getFavoriteCount() + 1);
            }
            if (!hasLyrics || !hasDuration || !hasText(song.getCoverUrl())) {
                report.setMissingMetadataCount(report.getMissingMetadataCount() + 1);
            }
            if (isThirdPartyAudio(song) && !hasLocalAudio) {
                report.setThirdPartyOnlyCount(report.getThirdPartyOnlyCount() + 1);
            }
            if (isBilibili && !hasLocalVideo) {
                report.setBilibiliOnlineCount(report.getBilibiliOnlineCount() + 1);
            }

            if (isNetease(song)) {
                netease++;
            } else if (isQq(song)) {
                qq++;
            } else if (isBilibili) {
                bilibili++;
            } else {
                manual++;
            }
            String artistKey = artistName(song);
            artists.put(artistKey, artists.getOrDefault(artistKey, 0) + 1);
        }

        report.setAverageDurationSeconds(report.getDurationCount() == 0 ? 0 : Math.round((float) totalDuration / report.getDurationCount()));
        report.setTotalDurationLabel(durationLabel(totalDuration));
        addSourceMetrics(report, localAudio, localVideo, netease, qq, bilibili, manual);
        addContentMetrics(report);
        addTopArtists(report, artists);
        addPlaylistInsights(report, user, playlists);
        finish(report);
        return report;
    }

    private void addSourceMetrics(MusicInsightReport report, int localAudio, int localVideo, int netease, int qq, int bilibili, int manual) {
        int total = Math.max(report.getSongCount(), 1);
        report.getSourceMetrics().add(new InsightMetric("本地音频", "可离线播放，适合交付客户", "local", localAudio, total));
        report.getSourceMetrics().add(new InsightMetric("本地视频", "B站视频下载后绑定", "video", localVideo, total));
        report.getSourceMetrics().add(new InsightMetric("网易云", "外链或 ID 导入来源", "netease", netease, total));
        report.getSourceMetrics().add(new InsightMetric("QQ音乐", "QQ 歌曲导入来源", "qq", qq, total));
        report.getSourceMetrics().add(new InsightMetric("B站", "视频音乐来源", "bilibili", bilibili, total));
        report.getSourceMetrics().add(new InsightMetric("手动/本地", "手动创建或本地导入", "manual", manual, total));
    }

    private void addContentMetrics(MusicInsightReport report) {
        int total = Math.max(report.getSongCount(), 1);
        report.getContentMetrics().add(new InsightMetric("本地化率", "不依赖第三方播放器", "strong", report.getLocalReadyCount(), total));
        report.getContentMetrics().add(new InsightMetric("歌词覆盖", "同步歌词和歌词时间点", "lyric", report.getLyricCount(), total));
        report.getContentMetrics().add(new InsightMetric("时长完整", "用于自动播放与进度恢复", "duration", report.getDurationCount(), total));
        report.getContentMetrics().add(new InsightMetric("收藏沉淀", "用户明确喜欢的歌曲", "favorite", report.getFavoriteCount(), total));
    }

    private void addTopArtists(MusicInsightReport report, Map<String, Integer> artists) {
        int total = Math.max(report.getSongCount(), 1);
        artists.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(8)
                .map(entry -> new InsightMetric(entry.getKey(), "用户歌库高频歌手", "artist", entry.getValue(), total))
                .forEach(report.getTopArtists()::add);
    }

    private void addPlaylistInsights(MusicInsightReport report, User user, List<Playlist> playlists) {
        for (Playlist playlist : playlists) {
            List<Song> songs = playlistService.listSongs(playlist.getId(), user.getId());
            PlaylistInsight insight = new PlaylistInsight();
            insight.setPlaylistId(playlist.getId());
            insight.setPlaylistName(playlist.getName());
            insight.setSongCount(songs.size());
            for (Song song : songs) {
                if (hasText(song.getLocalAudioUrl()) || hasText(song.getLocalVideoUrl())) {
                    insight.setLocalReadyCount(insight.getLocalReadyCount() + 1);
                }
                if (hasText(song.getLyricText())) {
                    insight.setLyricCount(insight.getLyricCount() + 1);
                }
                if (isThirdPartyAudio(song) && !hasText(song.getLocalAudioUrl())) {
                    insight.setThirdPartyOnlyCount(insight.getThirdPartyOnlyCount() + 1);
                }
                if (!hasText(song.getLyricText()) || song.getDurationSeconds() == null || song.getDurationSeconds() <= 0) {
                    insight.setMissingMetadataCount(insight.getMissingMetadataCount() + 1);
                }
            }
            int score = qualityScore(insight);
            insight.setQualityScore(score);
            insight.setActionHint(playlistHint(insight));
            report.getPlaylistInsights().add(insight);
        }
        report.getPlaylistInsights().sort(Comparator.comparingInt(PlaylistInsight::getQualityScore)
                .thenComparing(PlaylistInsight::getSongCount, Comparator.reverseOrder()));
    }

    private void finish(MusicInsightReport report) {
        int total = Math.max(report.getSongCount(), 1);
        int score = Math.round(report.getLocalReadyPercent() * 0.38f
                + report.getLyricPercent() * 0.24f
                + report.getDurationPercent() * 0.18f
                + Math.max(0, 100 - Math.round(report.getThirdPartyOnlyCount() * 100f / total)) * 0.12f
                + Math.max(0, 100 - Math.round(report.getBilibiliOnlineCount() * 100f / total)) * 0.08f);
        if (report.getSongCount() == 0) {
            score = 0;
        }
        report.setAssetScore(Math.max(0, Math.min(100, score)));
        applyText(report);
        addActions(report);
        report.setSalesPitch(buildSalesPitch(report));
    }

    private int qualityScore(PlaylistInsight insight) {
        if (insight.getSongCount() <= 0) {
            return 0;
        }
        int local = insight.getLocalReadyPercent();
        int lyric = insight.getLyricPercent();
        int risk = Math.round((insight.getThirdPartyOnlyCount() + insight.getMissingMetadataCount()) * 100f / insight.getSongCount());
        return Math.max(0, Math.min(100, Math.round(local * 0.5f + lyric * 0.28f + Math.max(0, 100 - risk) * 0.22f)));
    }

    private String playlistHint(PlaylistInsight insight) {
        if (insight.getSongCount() == 0) {
            return "空歌单，建议删除或导入歌曲";
        }
        if (insight.getThirdPartyOnlyCount() > 0) {
            return "优先绑定本地音频，减少网易云/QQ 版权风险";
        }
        if (insight.getMissingMetadataCount() > 0) {
            return "补歌词、时长和封面，播放器体验会明显提升";
        }
        if (insight.getQualityScore() >= 90) {
            return "适合作为交付演示歌单";
        }
        return "可继续整理为主题歌单";
    }

    private void applyText(MusicInsightReport report) {
        int score = report.getAssetScore();
        if (score >= 88) {
            report.setAssetLabel("接近商业级");
            report.setAssetHint("本地化、歌词和时长数据比较完整，可以作为客户演示重点。");
        } else if (score >= 70) {
            report.setAssetLabel("可包装销售");
            report.setAssetHint("已经有可展示价值，继续补歌词和本地文件能提升成交感。");
        } else if (score >= 45) {
            report.setAssetLabel("需要产品化整理");
            report.setAssetHint("歌库有内容，但本地化率或元数据不够，客户容易觉得只是普通播放器。");
        } else {
            report.setAssetLabel("还不适合卖");
            report.setAssetHint("先把本地文件、歌词和歌单主题整理好，再去做商品页。");
        }
    }

    private void addActions(MusicInsightReport report) {
        if (report.getSongCount() == 0) {
            report.getActions().add(new InsightAction("danger", "先导入歌曲", "没有内容就没有商品价值。", "去导入", "/netease"));
            return;
        }
        if (report.getLocalReadyPercent() < 80) {
            report.getActions().add(new InsightAction("danger", "提高本地化率", "第三方外链越多，客户电脑上越容易遇到 VIP/版权/网络问题。", "批量绑定本地音频", "/playlists"));
        }
        if (report.getLyricPercent() < 70) {
            report.getActions().add(new InsightAction("warning", "补同步歌词", "同步歌词和可点击时间点是比普通播放器更有感知的卖点。", "查看歌单", "/playlists"));
        }
        if (report.getThirdPartyOnlyCount() > 0) {
            report.getActions().add(new InsightAction("warning", "处理网易云/QQ 风险", "仍有 " + report.getThirdPartyOnlyCount() + " 首只依赖第三方播放器。", "打开维护", "/vip-tools"));
        }
        if (report.getBilibiliOnlineCount() > 0) {
            report.getActions().add(new InsightAction("info", "下载 B站视频", "仍有 " + report.getBilibiliOnlineCount() + " 个视频未本地化，弱网会卡。", "打开维护", "/vip-tools"));
        }
        report.getActions().add(new InsightAction("info", "生成演示歌单", "用本地化、收藏、关键词规则生成一个更适合商品演示的歌单。", "智能歌单", "/smart-playlists"));
    }

    private String buildSalesPitch(MusicInsightReport report) {
        return "已整理 " + report.getSongCount() + " 首音乐、" + report.getPlaylistCount() + " 个歌单，"
                + "本地化率 " + report.getLocalReadyPercent() + "%，歌词覆盖 " + report.getLyricPercent() + "%。"
                + "支持网易云/QQ/B站导入、本地 FLAC/MP3 绑定、同步歌词、自动播放和交付体检，适合做个人专属二次元音乐库。";
    }

    private String durationLabel(long seconds) {
        if (seconds <= 0) {
            return "0 分钟";
        }
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        if (hours > 0) {
            return hours + " 小时 " + minutes + " 分钟";
        }
        return minutes + " 分钟";
    }

    private String artistName(Song song) {
        if (song == null || !hasText(song.getArtist())) {
            return "未知歌手";
        }
        String artist = song.getArtist().split("[,/，、]")[0].trim();
        return artist.isEmpty() ? "未知歌手" : artist;
    }

    private boolean isNetease(Song song) {
        return contains(song == null ? null : song.getAudioUrl(), "music.163.com");
    }

    private boolean isQq(Song song) {
        return contains(song == null ? null : song.getAudioUrl(), "y.qq.com");
    }

    private boolean isBilibili(Song song) {
        return contains(song == null ? null : song.getAudioUrl(), "bilibili.com");
    }

    private boolean isThirdPartyAudio(Song song) {
        return isNetease(song) || isQq(song);
    }

    private boolean contains(String value, String marker) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(marker.toLowerCase(Locale.ROOT));
    }

    private boolean hasText(String value) {
        return value != null && !Normalizer.normalize(value, Normalizer.Form.NFKC).trim().isEmpty();
    }
}
