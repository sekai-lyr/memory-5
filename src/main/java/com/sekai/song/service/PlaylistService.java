package com.sekai.song.service;

import com.sekai.song.model.Playlist;
import com.sekai.song.model.Song;

import java.util.List;

public interface PlaylistService {
    boolean add(Playlist playlist);

    boolean update(Playlist playlist, Long userId);

    boolean delete(Long id, Long userId);

    Playlist getByIdAndUserId(Long id, Long userId);

    List<Playlist> listByUserId(Long userId);

    boolean addSong(Long playlistId, Long songId, Long userId);

    boolean removeSong(Long playlistId, Long songId, Long userId);

    List<Song> listSongs(Long playlistId, Long userId);
}
