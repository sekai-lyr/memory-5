package com.sekai.song.mapper;

import com.sekai.song.model.Song;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface SekaiSongTrackMapper {
    int insert(Song song);

    int update(Song song);

    int deleteByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    int updateFavorite(@Param("id") Long id, @Param("userId") Long userId, @Param("favorite") boolean favorite);

    int updateLocalAudioUrl(@Param("id") Long id, @Param("userId") Long userId, @Param("localAudioUrl") String localAudioUrl);

    int updateLocalAudioMetadata(@Param("id") Long id,
                                 @Param("userId") Long userId,
                                 @Param("localAudioUrl") String localAudioUrl,
                                 @Param("lyricText") String lyricText,
                                 @Param("durationSeconds") Integer durationSeconds);

    int updateLyricMetadata(@Param("id") Long id,
                            @Param("userId") Long userId,
                            @Param("lyricText") String lyricText,
                            @Param("durationSeconds") Integer durationSeconds);

    int updateLocalVideoUrl(@Param("id") Long id, @Param("userId") Long userId, @Param("localVideoUrl") String localVideoUrl);

    Song findByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    Song findByAudioUrlAndUserId(@Param("audioUrl") String audioUrl, @Param("userId") Long userId);

    List<Song> listByUserId(@Param("userId") Long userId, @Param("keyword") String keyword,
                            @Param("offset") int offset, @Param("pageSize") int pageSize);

    List<Song> listAllByUserId(@Param("userId") Long userId);

    List<Song> listLibraryByUserId(@Param("userId") Long userId, @Param("keyword") String keyword,
                                   @Param("offset") int offset, @Param("pageSize") int pageSize);

    List<Song> listAllLibraryByUserId(@Param("userId") Long userId);

    List<Song> listFavoritesByUserId(@Param("userId") Long userId);

    int countByUserId(@Param("userId") Long userId, @Param("keyword") String keyword);

    int countLibraryByUserId(@Param("userId") Long userId, @Param("keyword") String keyword);
}
