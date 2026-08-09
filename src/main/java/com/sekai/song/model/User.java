package com.sekai.song.model;

import java.time.LocalDateTime;

public class User {
    private Long id;
    private String userName;
    private String password;
    private String nickName;
    private String avatarUrl;
    private String licensePlan;
    private LocalDateTime licenseExpireTime;
    private String licenseCode;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getNickName() {
        return nickName;
    }

    public void setNickName(String nickName) {
        this.nickName = nickName;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    public String getLicensePlan() {
        return licensePlan;
    }

    public void setLicensePlan(String licensePlan) {
        this.licensePlan = licensePlan;
    }

    public LocalDateTime getLicenseExpireTime() {
        return licenseExpireTime;
    }

    public void setLicenseExpireTime(LocalDateTime licenseExpireTime) {
        this.licenseExpireTime = licenseExpireTime;
    }

    public String getLicenseCode() {
        return licenseCode;
    }

    public void setLicenseCode(String licenseCode) {
        this.licenseCode = licenseCode;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }
}
