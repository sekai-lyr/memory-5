package com.sekai.song.service.impl;

import com.sekai.song.mapper.SekaiSongUserMapper;
import com.sekai.song.model.Result;
import com.sekai.song.model.User;
import com.sekai.song.service.UserService;
import com.sekai.song.util.PasswordHashUtil;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

@Service
public class UserServiceImpl implements UserService {

    @Resource
    private SekaiSongUserMapper userMapper;

    @Override
    public Result<User> register(User user) {
        if (user == null || isBlank(user.getUserName()) || isBlank(user.getPassword())) {
            return Result.fail("用户名和密码不能为空");
        }
        if (userMapper.findByUserName(user.getUserName().trim()) != null) {
            return Result.fail("这个用户名已经被占用");
        }
        user.setUserName(user.getUserName().trim());
        if (isBlank(user.getNickName())) {
            user.setNickName(user.getUserName());
        }
        user.setPassword(PasswordHashUtil.hash(user.getPassword()));
        userMapper.insert(user);
        user.setPassword(null);
        return Result.success(user);
    }

    @Override
    public Result<User> login(String userName, String password) {
        if (isBlank(userName) || isBlank(password)) {
            return Result.fail("请输入用户名和密码");
        }
        User user = userMapper.findByUserName(userName.trim());
        if (user == null || !PasswordHashUtil.matches(password, user.getPassword())) {
            return Result.fail("用户名或密码不正确");
        }
        user.setPassword(null);
        return Result.success(user);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
