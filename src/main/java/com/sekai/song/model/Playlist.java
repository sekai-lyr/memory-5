package com.sekai.song.model;

import java.time.LocalDateTime;

public class Playlist {
    private Long id;
    private Long userId;
    private String name;
    private String description;
    private String coverUrl;
    private Integer songCount;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCoverUrl() {
        return coverUrl;
    }

    public void setCoverUrl(String coverUrl) {
        this.coverUrl = coverUrl;
    }

    public Integer getSongCount() {
        return songCount;
    }

    public void setSongCount(Integer songCount) {
        this.songCount = songCount;
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

    public String getNeteasePlaylistId() {
        if (description == null || !description.contains("网易云歌单导入：")) {
            return null;
        }
        int start = description.indexOf("网易云歌单导入：") + "网易云歌单导入：".length();
        StringBuilder id = new StringBuilder();
        while (start < description.length() && Character.isDigit(description.charAt(start))) {
            id.append(description.charAt(start));
            start++;
        }
        return id.isEmpty() ? null : id.toString();
    }
}
