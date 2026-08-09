package com.sekai.song.service.impl;

import com.sekai.song.model.LicenseStatus;
import com.sekai.song.model.User;
import com.sekai.song.model.VipUsage;
import com.sekai.song.service.LicenseService;
import com.sekai.song.service.PlaylistService;
import com.sekai.song.service.SongService;
import com.sekai.song.service.VipQuotaService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

@Service
public class VipQuotaServiceImpl implements VipQuotaService {
    private static final int TRIAL_SONG_LIMIT = 20;
    private static final int TRIAL_PLAYLIST_LIMIT = 3;
    private static final int VIP_DISPLAY_SONG_LIMIT = 5000;
    private static final int VIP_DISPLAY_PLAYLIST_LIMIT = 300;

    @Resource
    private SongService songService;

    @Resource
    private PlaylistService playlistService;

    @Resource
    private LicenseService licenseService;

    @Override
    public VipUsage usageOf(User user) {
        VipUsage usage = new VipUsage();
        if (user == null || user.getId() == null) {
            usage.setMessage("未登录");
            return usage;
        }
        LicenseStatus status = licenseService.statusOf(user);
        int songCount = songService.countByUserId(user.getId(), null);
        int playlistCount = playlistService.listByUserId(user.getId()).size();
        boolean vip = status.isVip();
        usage.setSongCount(songCount);
        usage.setPlaylistCount(playlistCount);
        usage.setVip(vip);
        usage.setUnlimited(vip);
        usage.setSongLimit(vip ? VIP_DISPLAY_SONG_LIMIT : TRIAL_SONG_LIMIT);
        usage.setPlaylistLimit(vip ? VIP_DISPLAY_PLAYLIST_LIMIT : TRIAL_PLAYLIST_LIMIT);
        usage.setMessage(vip ? "VIP 大容量模式已解锁" : "试用版额度有限，升级 VIP 可解锁大容量歌库");
        return usage;
    }

    @Override
    public boolean canAddSong(User user) {
        VipUsage usage = usageOf(user);
        return usage.isUnlimited() || usage.getSongCount() < usage.getSongLimit();
    }

    @Override
    public boolean canAddPlaylist(User user) {
        VipUsage usage = usageOf(user);
        return usage.isUnlimited() || usage.getPlaylistCount() < usage.getPlaylistLimit();
    }

    @Override
    public String songLimitMessage(User user) {
        VipUsage usage = usageOf(user);
        return "试用版最多添加 " + usage.getSongLimit() + " 首歌曲，升级 VIP 后可继续导入和绑定本地音乐。";
    }

    @Override
    public String playlistLimitMessage(User user) {
        VipUsage usage = usageOf(user);
        return "试用版最多创建 " + usage.getPlaylistLimit() + " 个歌单，升级 VIP 后可继续创建客户歌单。";
    }
}
