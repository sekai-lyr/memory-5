package com.sekai.song.service.impl;

import com.sekai.song.model.DeliveryReadinessReport;
import com.sekai.song.model.DeliveryRisk;
import com.sekai.song.model.DeliverySourceSummary;
import com.sekai.song.model.Playlist;
import com.sekai.song.model.Song;
import com.sekai.song.model.User;
import com.sekai.song.service.DeliveryReadinessService;
import com.sekai.song.service.PlaylistService;
import com.sekai.song.service.SongService;
import com.sekai.song.util.UploadPathUtil;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

@Service
public class DeliveryReadinessServiceImpl implements DeliveryReadinessService {
    private static final int RISK_LIMIT = 120;
    private static final Path UPLOAD_ROOT = UploadPathUtil.uploadRoot();
    private static final Path EXTERNAL_UPLOAD_ROOT = externalUploadRoot();

    @Resource
    private SongService songService;

    @Resource
    private PlaylistService playlistService;

    @Override
    public DeliveryReadinessReport buildReport(User user) {
        DeliveryReadinessReport report = new DeliveryReadinessReport();
        report.setUploadRoot(UPLOAD_ROOT.toString());
        report.setExternalUploadRoot(EXTERNAL_UPLOAD_ROOT.toString());
        if (user == null || user.getId() == null) {
            finishReport(report);
            return report;
        }

        List<Song> songs = songService.listByUserId(user.getId());
        List<Playlist> playlists = playlistService.listByUserId(user.getId());
        report.setSongCount(songs.size());
        report.setPlaylistCount(playlists.size());
        Set<Long> songsInPlaylists = scanPlaylists(user, report, playlists);
        for (Song song : songs) {
            analyzeSong(song, report);
            if (song.getId() != null && !songsInPlaylists.contains(song.getId())) {
                report.setOrphanSongCount(report.getOrphanSongCount() + 1);
                addRisk(report, new DeliveryRisk(
                        "info",
                        "未归档歌曲",
                        compactSongName(song),
                        "这首歌在歌曲库里，但没有出现在任何歌单中，交付给客户时不容易找到。",
                        "加入对应歌单或用智能歌单工厂整理",
                        song.getId(),
                        null));
            }
        }
        UploadStat uploadStat = scanUploadFiles();
        report.setUploadFileCount(uploadStat.fileCount());
        report.setUploadBytes(uploadStat.bytes());
        finishReport(report);
        return report;
    }

    @Override
    public String renderTextReport(User user) {
        DeliveryReadinessReport report = buildReport(user);
        StringBuilder text = new StringBuilder();
        text.append("Sekai Song 客户交付体检报告\n");
        text.append("生成时间：").append(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").format(report.getGeneratedAt())).append("\n");
        text.append("账号：").append(user == null ? "未知" : safe(user.getUserName())).append("\n\n");

        text.append("一、交付评分\n");
        text.append("- 就绪评分：").append(report.getReadinessScore()).append("/100（").append(report.getReadinessLabel()).append("）\n");
        text.append("- 建议：").append(report.getReadinessHint()).append("\n\n");

        text.append("二、核心数据\n");
        text.append("- 歌曲总数：").append(report.getSongCount()).append("\n");
        text.append("- 歌单数量：").append(report.getPlaylistCount()).append("\n");
        text.append("- 歌单内引用：").append(report.getPlaylistSongCount()).append("\n");
        text.append("- 本地可播放：").append(report.getLocalReadyCount()).append("\n");
        text.append("- 本地音频 / 本地视频：").append(report.getLocalAudioCount()).append(" / ").append(report.getLocalVideoCount()).append("\n");
        text.append("- 第三方音频风险：").append(report.getThirdPartyOnlyCount()).append("\n");
        text.append("- B站在线播放风险：").append(report.getBilibiliOnlineCount()).append("\n");
        text.append("- 缺失本地文件：").append(report.getMissingLocalCount()).append("\n");
        text.append("- 完全不可播放：").append(report.getUnplayableCount()).append("\n");
        text.append("- 未归档歌曲：").append(report.getOrphanSongCount()).append("\n\n");

        text.append("三、上传文件\n");
        text.append("- 项目上传目录：").append(report.getUploadRoot()).append("\n");
        text.append("- 外部上传目录：").append(report.getExternalUploadRoot()).append("\n");
        text.append("- 文件数量：").append(report.getUploadFileCount()).append("\n");
        text.append("- 文件体积：").append(report.getUploadSizeLabel()).append("\n\n");

        text.append("四、交付检查清单\n");
        for (int i = 0; i < report.getChecklist().size(); i++) {
            text.append(i + 1).append(". ").append(report.getChecklist().get(i)).append("\n");
        }
        text.append("\n");

        text.append("五、风险清单\n");
        if (report.getRisks().isEmpty()) {
            text.append("- 暂无明显风险。\n");
        } else {
            for (DeliveryRisk risk : report.getRisks()) {
                text.append("- [").append(risk.getSeverity()).append("] ")
                        .append(risk.getType()).append("：")
                        .append(risk.getTitle()).append("。")
                        .append(risk.getDetail()).append(" 建议：")
                        .append(risk.getActionHint()).append("\n");
            }
        }
        return text.toString();
    }

    private Set<Long> scanPlaylists(User user, DeliveryReadinessReport report, List<Playlist> playlists) {
        Set<Long> songIds = new HashSet<>();
        for (Playlist playlist : playlists) {
            List<Song> playlistSongs = playlistService.listSongs(playlist.getId(), user.getId());
            report.setPlaylistSongCount(report.getPlaylistSongCount() + playlistSongs.size());
            if (playlistSongs.isEmpty()) {
                report.setEmptyPlaylistCount(report.getEmptyPlaylistCount() + 1);
                addRisk(report, new DeliveryRisk(
                        "info",
                        "空歌单",
                        playlist.getName(),
                        "这个歌单没有任何歌曲，客户打开会感觉项目没有整理好。",
                        "删除空歌单或导入歌曲",
                        null,
                        playlist.getId()));
            }
            playlistSongs.stream().map(Song::getId).forEach(songIds::add);
        }
        return songIds;
    }

    private void analyzeSong(Song song, DeliveryReadinessReport report) {
        boolean netease = contains(song.getAudioUrl(), "music.163.com");
        boolean qq = contains(song.getAudioUrl(), "y.qq.com");
        boolean bilibili = contains(song.getAudioUrl(), "bilibili.com");
        boolean hasLocalAudio = hasText(song.getLocalAudioUrl());
        boolean hasLocalVideo = hasText(song.getLocalVideoUrl());
        boolean localAudioExists = hasLocalAudio && localFileExists(song.getLocalAudioUrl());
        boolean localVideoExists = hasLocalVideo && localFileExists(song.getLocalVideoUrl());

        if (netease) {
            report.setNeteaseCount(report.getNeteaseCount() + 1);
        } else if (qq) {
            report.setQqCount(report.getQqCount() + 1);
        } else if (bilibili) {
            report.setBilibiliCount(report.getBilibiliCount() + 1);
        } else {
            report.setManualCount(report.getManualCount() + 1);
        }

        if (hasLocalAudio) {
            report.setLocalAudioCount(report.getLocalAudioCount() + 1);
            if (!localAudioExists) {
                report.setMissingLocalCount(report.getMissingLocalCount() + 1);
                addRisk(report, missingRisk("本地音频缺失", song, "数据库绑定了本地音频，但 uploads 里找不到对应文件。", "重新上传或批量绑定本地音频"));
            }
        }
        if (hasLocalVideo) {
            report.setLocalVideoCount(report.getLocalVideoCount() + 1);
            if (!localVideoExists) {
                report.setMissingLocalCount(report.getMissingLocalCount() + 1);
                addRisk(report, missingRisk("本地视频缺失", song, "数据库绑定了本地视频，但 uploads 里找不到对应文件。", "重新绑定本地视频"));
            }
        }

        boolean localReady = localAudioExists || localVideoExists;
        if (localReady) {
            report.setLocalReadyCount(report.getLocalReadyCount() + 1);
            return;
        }

        if ((netease || qq) && !hasLocalAudio) {
            report.setThirdPartyOnlyCount(report.getThirdPartyOnlyCount() + 1);
            addRisk(report, new DeliveryRisk(
                    "warning",
                    "第三方音频风险",
                    compactSongName(song),
                    "仍依赖网易云或 QQ 音乐链接，VIP/版权歌曲在客户电脑上可能无法播放。",
                    "下载客户自己的音频后批量绑定本地音频",
                    song.getId(),
                    null));
            return;
        }

        if (bilibili && !hasLocalVideo) {
            report.setBilibiliOnlineCount(report.getBilibiliOnlineCount() + 1);
            addRisk(report, new DeliveryRisk(
                    "warning",
                    "B站在线播放风险",
                    compactSongName(song),
                    "仍依赖 B 站在线播放，客户网络差时可能卡顿或加载失败。",
                    "下载视频后绑定本地视频",
                    song.getId(),
                    null));
            return;
        }

        if (!hasText(song.getAudioUrl())) {
            report.setUnplayableCount(report.getUnplayableCount() + 1);
            addRisk(report, new DeliveryRisk(
                    "danger",
                    "不可播放",
                    compactSongName(song),
                    "没有本地文件，也没有第三方播放链接。",
                    "上传音频文件或删除无效歌曲",
                    song.getId(),
                    null));
        }
    }

    private DeliveryRisk missingRisk(String type, Song song, String detail, String actionHint) {
        return new DeliveryRisk("danger", type, compactSongName(song), detail, actionHint, song.getId(), null);
    }

    private void finishReport(DeliveryReadinessReport report) {
        report.setUploadSizeLabel(formatBytes(report.getUploadBytes()));
        int total = Math.max(report.getSongCount(), 1);
        report.getSourceSummaries().add(new DeliverySourceSummary("本地就绪", "已经绑定并能在项目内播放", "local", report.getLocalReadyCount(), total));
        report.getSourceSummaries().add(new DeliverySourceSummary("网易云", "导入自网易云链接或 ID", "netease", report.getNeteaseCount(), total));
        report.getSourceSummaries().add(new DeliverySourceSummary("QQ 音乐", "导入自 QQ 音乐链接或 songmid", "qq", report.getQqCount(), total));
        report.getSourceSummaries().add(new DeliverySourceSummary("B 站", "视频音乐条目", "bilibili", report.getBilibiliCount(), total));
        report.getSourceSummaries().add(new DeliverySourceSummary("手动/本地", "手动添加或本地文件导入", "manual", report.getManualCount(), total));

        int localRate = Math.round(report.getLocalReadyCount() * 100f / total);
        int noMissingRate = Math.round((total - Math.min(report.getMissingLocalCount(), total)) * 100f / total);
        int noStreamRiskRate = Math.round((total - Math.min(report.getThirdPartyOnlyCount() + report.getBilibiliOnlineCount(), total)) * 100f / total);
        int score = Math.round(localRate * 0.58f + noMissingRate * 0.22f + noStreamRiskRate * 0.2f);
        if (report.getSongCount() == 0) {
            score = 0;
        }
        report.setReadinessScore(Math.max(0, Math.min(100, score)));
        applyReadinessText(report);
        buildChecklist(report);
    }

    private void applyReadinessText(DeliveryReadinessReport report) {
        int score = report.getReadinessScore();
        if (score >= 90) {
            report.setReadinessLabel("可以交付");
            report.setReadinessHint("本地化和缺文件情况都很好，可以先导出报告再给客户验收。");
        } else if (score >= 70) {
            report.setReadinessLabel("基本可交付");
            report.setReadinessHint("还有一些在线播放或未归档风险，建议先处理高优先级清单。");
        } else if (score >= 45) {
            report.setReadinessLabel("需要整理");
            report.setReadinessHint("本地文件绑定不足，客户电脑上可能出现无法播放或卡顿。");
        } else {
            report.setReadinessLabel("不建议交付");
            report.setReadinessHint("先完成本地音频/视频绑定和缺文件修复，再拿去给客户使用。");
        }
    }

    private void buildChecklist(DeliveryReadinessReport report) {
        report.getChecklist().add("确认 MySQL 数据库已经导入，并且客户账号能正常登录。");
        report.getChecklist().add("复制项目 uploads 文件夹到客户电脑同一项目目录，保持 /uploads/audio 和 /uploads/video 路径不变。");
        report.getChecklist().add("打开 VIP 高级维护页面，确认缺失本地文件为 0。");
        report.getChecklist().add("打开 2-3 个客户核心歌单，测试顺序播放、随机播放和单曲循环。");
        report.getChecklist().add("如果是付费交付，先在授权中心给客户激活 VIP 或永久授权码。");
        if (report.getThirdPartyOnlyCount() > 0) {
            report.getChecklist().add("仍有 " + report.getThirdPartyOnlyCount() + " 首网易云/QQ 第三方链接歌曲，建议绑定客户自己下载的本地音频。");
        }
        if (report.getBilibiliOnlineCount() > 0) {
            report.getChecklist().add("仍有 " + report.getBilibiliOnlineCount() + " 个 B站视频在线播放，建议绑定本地视频降低卡顿。");
        }
        if (report.getMissingLocalCount() > 0) {
            report.getChecklist().add("有 " + report.getMissingLocalCount() + " 个本地文件缺失，交付前必须重新绑定。");
        }
    }

    private UploadStat scanUploadFiles() {
        long bytes = 0;
        int count = 0;
        for (Path root : List.of(UPLOAD_ROOT, EXTERNAL_UPLOAD_ROOT)) {
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (Stream<Path> paths = Files.walk(root, 10)) {
                List<Path> files = paths.filter(Files::isRegularFile).toList();
                count += files.size();
                for (Path file : files) {
                    try {
                        bytes += Files.size(file);
                    } catch (IOException ignored) {
                        // Size is only used for a delivery estimate, so unreadable files are skipped.
                    }
                }
            } catch (IOException ignored) {
                // Keep report generation available even when one upload folder is unreadable.
            }
        }
        return new UploadStat(count, bytes);
    }

    private boolean localFileExists(String url) {
        Path path = resolveLocalFile(url);
        return path != null && Files.isRegularFile(path);
    }

    private Path resolveLocalFile(String url) {
        if (!hasText(url)) {
            return null;
        }
        String clean = url.trim().replace('\\', '/');
        if (clean.startsWith("/uploads/")) {
            return UPLOAD_ROOT.resolve(clean.substring("/uploads/".length())).normalize();
        }
        if (clean.startsWith("uploads/")) {
            return UPLOAD_ROOT.resolve(clean.substring("uploads/".length())).normalize();
        }
        if (clean.startsWith("/external-uploads/")) {
            return EXTERNAL_UPLOAD_ROOT.resolve(clean.substring("/external-uploads/".length())).normalize();
        }
        if (clean.startsWith("external-uploads/")) {
            return EXTERNAL_UPLOAD_ROOT.resolve(clean.substring("external-uploads/".length())).normalize();
        }
        if (clean.matches("^[A-Za-z]:/.*")) {
            return Paths.get(clean).toAbsolutePath().normalize();
        }
        return null;
    }

    private void addRisk(DeliveryReadinessReport report, DeliveryRisk risk) {
        if (report.getRisks().size() < RISK_LIMIT) {
            report.getRisks().add(risk);
        }
    }

    private boolean contains(String value, String marker) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(marker.toLowerCase(Locale.ROOT));
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String compactSongName(Song song) {
        if (song == null) {
            return "未知歌曲";
        }
        if (!hasText(song.getArtist())) {
            return hasText(song.getTitle()) ? song.getTitle() : "未知歌曲";
        }
        return song.getArtist() + " - " + song.getTitle();
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "未知" : value.trim();
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        double value = bytes;
        String[] units = {"KB", "MB", "GB", "TB"};
        int unitIndex = -1;
        do {
            value = value / 1024d;
            unitIndex++;
        } while (value >= 1024 && unitIndex < units.length - 1);
        return String.format(Locale.ROOT, "%.1f %s", value, units[unitIndex]);
    }

    private static Path externalUploadRoot() {
        String configured = System.getProperty("sekai.externalUploadRoot");
        if (configured == null || configured.isBlank()) {
            configured = System.getenv("SEKAI_EXTERNAL_UPLOAD_ROOT");
        }
        if (configured == null || configured.isBlank()) {
            configured = "D:/SekaiSongExternalUploads/memory-5/uploads";
        }
        return Paths.get(configured).toAbsolutePath().normalize();
    }

    private record UploadStat(int fileCount, long bytes) {
    }
}
