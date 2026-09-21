package com.sekai.song.service;

import com.sekai.song.model.LyricLine;
import com.sekai.song.model.Song;

import java.util.List;

public interface SongService {
    boolean add(Song song);

    boolean update(Song song, Long userId);

    boolean delete(Long id, Long userId);

    boolean toggleFavorite(Long id, Long userId);

    boolean updateLocalAudioUrl(Long id, Long userId, String localAudioUrl);

    boolean updateLocalAudioMetadata(Long id, Long userId, String localAudioUrl, String lyricText, Integer durationSeconds);

    boolean updateLocalVideoUrl(Long id, Long userId, String localVideoUrl);

    boolean updateCoverUrl(Long id, Long userId, String coverUrl);

    boolean updateLyricMetadata(Long id, Long userId, String lyricText);

    Song getByIdAndUserId(Long id, Long userId);

    List<Song> listByUserId(Long userId, String keyword, int page, int pageSize);

    List<Song> listByUserId(Long userId);

    List<Song> listLibraryByUserId(Long userId, String keyword, int page, int pageSize);

    List<Song> listLibraryByUserId(Long userId);

    List<Song> listFavoritesByUserId(Long userId);

    int countByUserId(Long userId, String keyword);

    int countLibraryByUserId(Long userId, String keyword);

    List<LyricLine> parseLyrics(String lyricText);
}
