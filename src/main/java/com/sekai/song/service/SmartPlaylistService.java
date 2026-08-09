package com.sekai.song.service;

import com.sekai.song.model.SmartPlaylistResult;
import com.sekai.song.model.SmartPlaylistStats;
import com.sekai.song.model.User;

public interface SmartPlaylistService {
    SmartPlaylistStats statsOf(User user);

    SmartPlaylistResult generate(User user,
                                 String playlistName,
                                 String sourceMode,
                                 String keyword,
                                 boolean favoriteOnly,
                                 boolean deduplicate,
                                 boolean shuffle,
                                 String sortMode,
                                 Integer limit,
                                 String coverUrl);
}
