package com.sekai.song.service.impl;

import com.sekai.song.mapper.SekaiSongTrackMapper;
import com.sekai.song.model.LyricLine;
import com.sekai.song.model.Song;
import com.sekai.song.service.SongService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class SongServiceImpl implements SongService {
    private static final Pattern LRC_TIMESTAMP = Pattern.compile("\\[(\\d{1,2}):(\\d{2})(?:\\.(\\d{1,3}))?]");

    @Resource
    private SekaiSongTrackMapper songMapper;

    @Override
    public boolean add(Song song) {
        if (song == null || isBlank(song.getTitle()) || song.getUserId() == null) {
            return false;
        }
        song.setTitle(song.getTitle().trim());
        if (song.getFavorite() == null) {
            song.setFavorite(false);
        }
        return songMapper.insert(song) > 0;
    }

    @Override
    public boolean update(Song song, Long userId) {
        if (song == null || song.getId() == null || userId == null || isBlank(song.getTitle())) {
            return false;
        }
        song.setUserId(userId);
        return songMapper.update(song) > 0;
    }

    @Override
    public boolean delete(Long id, Long userId) {
        return id != null && userId != null && songMapper.deleteByIdAndUserId(id, userId) > 0;
    }

    @Override
    public boolean toggleFavorite(Long id, Long userId) {
        Song song = getByIdAndUserId(id, userId);
        if (song == null) {
            return false;
        }
        return songMapper.updateFavorite(id, userId, !Boolean.TRUE.equals(song.getFavorite())) > 0;
    }

    @Override
    public boolean updateLocalAudioUrl(Long id, Long userId, String localAudioUrl) {
        return updateLocalAudioMetadata(id, userId, localAudioUrl, null, null);
    }

    @Override
    public boolean updateLocalAudioMetadata(Long id, Long userId, String localAudioUrl, String lyricText, Integer durationSeconds) {
        if (id == null || userId == null || isBlank(localAudioUrl)) {
            return false;
        }
        String cleanLyric = isBlank(lyricText) ? null : lyricText.trim();
        return songMapper.updateLocalAudioMetadata(id, userId, localAudioUrl.trim(), cleanLyric, durationSeconds) > 0;
    }

    @Override
    public boolean updateLocalVideoUrl(Long id, Long userId, String localVideoUrl) {
        if (id == null || userId == null || isBlank(localVideoUrl)) {
            return false;
        }
        return songMapper.updateLocalVideoUrl(id, userId, localVideoUrl.trim()) > 0;
    }

    @Override
    public Song getByIdAndUserId(Long id, Long userId) {
        if (id == null || userId == null) {
            return null;
        }
        return songMapper.findByIdAndUserId(id, userId);
    }

    @Override
    public List<Song> listByUserId(Long userId, String keyword, int page, int pageSize) {
        int currentPage = Math.max(page, 1);
        int size = Math.max(pageSize, 1);
        return songMapper.listByUserId(userId, cleanKeyword(keyword), (currentPage - 1) * size, size);
    }

    @Override
    public List<Song> listByUserId(Long userId) {
        return songMapper.listAllByUserId(userId);
    }

    @Override
    public List<Song> listLibraryByUserId(Long userId, String keyword, int page, int pageSize) {
        int currentPage = Math.max(page, 1);
        int size = Math.max(pageSize, 1);
        return songMapper.listLibraryByUserId(userId, cleanKeyword(keyword), (currentPage - 1) * size, size);
    }

    @Override
    public List<Song> listLibraryByUserId(Long userId) {
        return songMapper.listAllLibraryByUserId(userId);
    }

    @Override
    public List<Song> listFavoritesByUserId(Long userId) {
        return songMapper.listFavoritesByUserId(userId);
    }

    @Override
    public int countByUserId(Long userId, String keyword) {
        return songMapper.countByUserId(userId, cleanKeyword(keyword));
    }

    @Override
    public int countLibraryByUserId(Long userId, String keyword) {
        return songMapper.countLibraryByUserId(userId, cleanKeyword(keyword));
    }

    @Override
    public List<LyricLine> parseLyrics(String lyricText) {
        if (isBlank(lyricText)) {
            return List.of();
        }
        List<LyricLine> lines = new ArrayList<>();
        for (String rawLine : lyricText.split("\\R")) {
            String line = rawLine.trim();
            Matcher matcher = LRC_TIMESTAMP.matcher(line);
            boolean matched = false;
            String text = LRC_TIMESTAMP.matcher(line).replaceAll("").trim();
            while (matcher.find()) {
                double seconds = Integer.parseInt(matcher.group(1)) * 60
                        + Integer.parseInt(matcher.group(2))
                        + millis(matcher.group(3)) / 1000.0;
                lines.add(new LyricLine(seconds, timeLabel(matcher.group(1), matcher.group(2), matcher.group(3)), text));
                matched = true;
            }
            if (!matched && !rawLine.isBlank()) {
                lines.add(new LyricLine(lines.size() * 8, "--:--", rawLine.trim()));
            }
        }
        return lines;
    }

    private int millis(String fraction) {
        if (fraction == null || fraction.isBlank()) {
            return 0;
        }
        String padded = (fraction + "00").substring(0, 3);
        return Integer.parseInt(padded);
    }

    private String timeLabel(String minute, String second, String fraction) {
        return minute + ":" + second + (fraction == null || fraction.isBlank() ? "" : "." + fraction);
    }

    private String cleanKeyword(String keyword) {
        return isBlank(keyword) ? null : keyword.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
