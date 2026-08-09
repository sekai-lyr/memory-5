package com.sekai.song.service;

import com.sekai.song.model.MusicImportResult;

public interface BilibiliImportService {
    MusicImportResult importVideos(String videoIds, Long playlistId, Long userId);
}
