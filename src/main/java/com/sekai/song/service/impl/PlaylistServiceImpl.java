package com.sekai.song.service.impl;

import com.sekai.song.mapper.SekaiSongPlaylistMapper;
import com.sekai.song.mapper.SekaiSongTrackMapper;
import com.sekai.song.model.Playlist;
import com.sekai.song.model.Song;
import com.sekai.song.service.PlaylistService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PlaylistServiceImpl implements PlaylistService {

    @Resource
    private SekaiSongPlaylistMapper playlistMapper;

    @Resource
    private SekaiSongTrackMapper songMapper;

    @Override
    public boolean add(Playlist playlist) {
        if (playlist == null || playlist.getUserId() == null || isBlank(playlist.getName())) {
            return false;
        }
        playlist.setName(playlist.getName().trim());
        return playlistMapper.insert(playlist) > 0;
    }

    @Override
    public boolean update(Playlist playlist, Long userId) {
        if (playlist == null || playlist.getId() == null || userId == null || isBlank(playlist.getName())) {
            return false;
        }
        playlist.setUserId(userId);
        playlist.setName(playlist.getName().trim());
        return playlistMapper.update(playlist) > 0;
    }

    @Override
    public boolean delete(Long id, Long userId) {
        return id != null && userId != null && playlistMapper.deleteByIdAndUserId(id, userId) > 0;
    }

    @Override
    public Playlist getByIdAndUserId(Long id, Long userId) {
        if (id == null || userId == null) {
            return null;
        }
        return playlistMapper.findByIdAndUserId(id, userId);
    }

    @Override
    public List<Playlist> listByUserId(Long userId) {
        return playlistMapper.listByUserId(userId);
    }

    @Override
    public boolean addSong(Long playlistId, Long songId, Long userId) {
        if (!ownsPlaylistAndSong(playlistId, songId, userId) || playlistMapper.existsSong(playlistId, songId) > 0) {
            return false;
        }
        return playlistMapper.addSong(playlistId, songId) > 0;
    }

    @Override
    public boolean removeSong(Long playlistId, Long songId, Long userId) {
        if (!ownsPlaylistAndSong(playlistId, songId, userId)) {
            return false;
        }
        return playlistMapper.removeSong(playlistId, songId) > 0;
    }

    @Override
    public List<Song> listSongs(Long playlistId, Long userId) {
        return playlistMapper.listSongs(playlistId, userId);
    }

    private boolean ownsPlaylistAndSong(Long playlistId, Long songId, Long userId) {
        return playlistMapper.findByIdAndUserId(playlistId, userId) != null
                && songMapper.findByIdAndUserId(songId, userId) != null;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
