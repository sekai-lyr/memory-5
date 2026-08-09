package com.sekai.song.service;

import com.sekai.song.model.User;
import com.sekai.song.model.VipUsage;

public interface VipQuotaService {
    VipUsage usageOf(User user);

    boolean canAddSong(User user);

    boolean canAddPlaylist(User user);

    String songLimitMessage(User user);

    String playlistLimitMessage(User user);
}
