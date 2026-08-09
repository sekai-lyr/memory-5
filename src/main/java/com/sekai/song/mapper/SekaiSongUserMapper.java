package com.sekai.song.mapper;

import com.sekai.song.model.User;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

public interface SekaiSongUserMapper {
    int insert(User user);

    User findByUserName(@Param("userName") String userName);

    User findById(@Param("id") Long id);

    int updateLicense(@Param("id") Long id,
                      @Param("licensePlan") String licensePlan,
                      @Param("licenseExpireTime") LocalDateTime licenseExpireTime,
                      @Param("licenseCode") String licenseCode);
}
