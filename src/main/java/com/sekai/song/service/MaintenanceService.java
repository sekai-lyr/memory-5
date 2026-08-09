package com.sekai.song.service;

import com.sekai.song.model.MaintenanceReport;
import com.sekai.song.model.User;

public interface MaintenanceService {
    MaintenanceReport buildReport(User user);

    int deduplicatePlaylist(Long playlistId, User user);

    int deduplicateAll(User user);
}
