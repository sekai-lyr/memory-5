package com.sekai.song.service;

import com.sekai.song.model.Result;
import com.sekai.song.model.User;

public interface UserService {
    Result<User> register(User user);

    Result<User> login(String userName, String password);
}
