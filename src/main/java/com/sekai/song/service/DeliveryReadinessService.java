package com.sekai.song.service;

import com.sekai.song.model.DeliveryReadinessReport;
import com.sekai.song.model.User;

public interface DeliveryReadinessService {
    DeliveryReadinessReport buildReport(User user);

    String renderTextReport(User user);
}
