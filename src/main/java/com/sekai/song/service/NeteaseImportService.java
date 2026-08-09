package com.sekai.song.service;

import com.sekai.song.model.NeteaseImportResult;

public interface NeteaseImportService {
    NeteaseImportResult importPlaylist(String link, Long userId);

    NeteaseImportResult importSongs(String songIds, Long playlistId, Long userId);
}
