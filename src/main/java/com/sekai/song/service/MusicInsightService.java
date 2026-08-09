package com.sekai.song.service;

import com.sekai.song.model.MusicInsightReport;
import com.sekai.song.model.User;

public interface MusicInsightService {
    MusicInsightReport buildReport(User user);
}
