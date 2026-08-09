package com.sekai.song.mapper;

import com.sekai.song.model.Playlist;
import com.sekai.song.model.Song;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface SekaiSongPlaylistMapper {
    int insert(Playlist playlist);

    int update(Playlist playlist);

    int deleteByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    Playlist findByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    Playlist findByNameAndUserId(@Param("name") String name, @Param("userId") Long userId);

    List<Playlist> listByUserId(@Param("userId") Long userId);

    int addSong(@Param("playlistId") Long playlistId, @Param("songId") Long songId);

    int removeSong(@Param("playlistId") Long playlistId, @Param("songId") Long songId);

    int existsSong(@Param("playlistId") Long playlistId, @Param("songId") Long songId);

    List<Song> listSongs(@Param("playlistId") Long playlistId, @Param("userId") Long userId);
}
