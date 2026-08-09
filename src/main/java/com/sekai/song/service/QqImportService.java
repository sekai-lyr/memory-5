package com.sekai.song.service;

import com.sekai.song.model.MusicImportResult;

public interface QqImportService {
    MusicImportResult importSongs(String songIds, Long playlistId, Long userId);
}
