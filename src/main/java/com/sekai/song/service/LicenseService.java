package com.sekai.song.service;

import com.sekai.song.model.LicenseCode;
import com.sekai.song.model.LicenseStatus;
import com.sekai.song.model.Result;
import com.sekai.song.model.User;

import java.util.List;

public interface LicenseService {
    LicenseStatus statusOf(User user);

    Result<User> activate(Long userId, String code);

    List<LicenseCode> generateCodes(String plan, Integer durationDays, Integer count, Integer maxActivations, String note);

    List<LicenseCode> listCodes();
}
