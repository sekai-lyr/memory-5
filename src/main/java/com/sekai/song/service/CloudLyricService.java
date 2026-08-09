package com.sekai.song.service;

public interface CloudLyricService {
    String fetchNeteaseLyric(String songId);

    String fetchQqLyric(String songMid, Long songId);

    String fetchBestLyric(String title, String artist);
}
