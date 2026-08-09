package com.sekai.song.model;

import java.time.LocalDateTime;

public class Song {
    private Long id;
    private Long userId;
    private String title;
    private String artist;
    private String animeTitle;
    private String albumName;
    private String coverUrl;
    private String audioUrl;
    private String localAudioUrl;
    private String localVideoUrl;
    private String skinUrl;
    private String tags;
    private String mood;
    private String lyricText;
    private Integer durationSeconds;
    private Boolean favorite;
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

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getArtist() {
        return artist;
    }

    public void setArtist(String artist) {
        this.artist = artist;
    }

    public String getAnimeTitle() {
        return animeTitle;
    }

    public void setAnimeTitle(String animeTitle) {
        this.animeTitle = animeTitle;
    }

    public String getAlbumName() {
        return albumName;
    }

    public void setAlbumName(String albumName) {
        this.albumName = albumName;
    }

    public String getCoverUrl() {
        return coverUrl;
    }

    public void setCoverUrl(String coverUrl) {
        this.coverUrl = coverUrl;
    }

    public String getAudioUrl() {
        return audioUrl;
    }

    public void setAudioUrl(String audioUrl) {
        this.audioUrl = audioUrl;
    }

    public String getLocalAudioUrl() {
        return localAudioUrl;
    }

    public void setLocalAudioUrl(String localAudioUrl) {
        this.localAudioUrl = localAudioUrl;
    }

    public String getLocalVideoUrl() {
        return localVideoUrl;
    }

    public void setLocalVideoUrl(String localVideoUrl) {
        this.localVideoUrl = localVideoUrl;
    }

    public String getSkinUrl() {
        return skinUrl;
    }

    public void setSkinUrl(String skinUrl) {
        this.skinUrl = skinUrl;
    }

    public String getTags() {
        return tags;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }

    public String getMood() {
        return mood;
    }

    public void setMood(String mood) {
        this.mood = mood;
    }

    public String getLyricText() {
        return lyricText;
    }

    public void setLyricText(String lyricText) {
        this.lyricText = lyricText;
    }

    public Integer getDurationSeconds() {
        return durationSeconds;
    }

    public void setDurationSeconds(Integer durationSeconds) {
        this.durationSeconds = durationSeconds;
    }

    public Boolean getFavorite() {
        return favorite;
    }

    public void setFavorite(Boolean favorite) {
        this.favorite = favorite;
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

    public String getNeteaseSongId() {
        if (audioUrl == null || audioUrl.isBlank() || !audioUrl.contains("music.163.com")) {
            return null;
        }
        String[] markers = {"id=", "/song/"};
        for (String marker : markers) {
            int markerIndex = audioUrl.indexOf(marker);
            if (markerIndex >= 0) {
                int start = markerIndex + marker.length();
                StringBuilder id = new StringBuilder();
                while (start < audioUrl.length() && Character.isDigit(audioUrl.charAt(start))) {
                    id.append(audioUrl.charAt(start));
                    start++;
                }
                if (!id.isEmpty()) {
                    return id.toString();
                }
            }
        }
        return null;
    }

    public String getQqSongMid() {
        if (audioUrl == null || audioUrl.isBlank() || !audioUrl.contains("y.qq.com")) {
            return null;
        }
        String[] markers = {"/songDetail/", "songmid=", "song_mid="};
        for (String marker : markers) {
            int markerIndex = audioUrl.indexOf(marker);
            if (markerIndex >= 0) {
                int start = markerIndex + marker.length();
                StringBuilder id = new StringBuilder();
                while (start < audioUrl.length() && Character.isLetterOrDigit(audioUrl.charAt(start))) {
                    id.append(audioUrl.charAt(start));
                    start++;
                }
                if (!id.isEmpty()) {
                    return id.toString();
                }
            }
        }
        return null;
    }

    public String getBilibiliBvid() {
        if (audioUrl == null || audioUrl.isBlank() || !audioUrl.contains("bilibili.com")) {
            return null;
        }
        int markerIndex = audioUrl.indexOf("BV");
        if (markerIndex < 0) {
            markerIndex = audioUrl.indexOf("bv");
        }
        if (markerIndex < 0) {
            return null;
        }
        StringBuilder id = new StringBuilder();
        while (markerIndex < audioUrl.length() && Character.isLetterOrDigit(audioUrl.charAt(markerIndex))) {
            id.append(audioUrl.charAt(markerIndex));
            markerIndex++;
        }
        return id.length() >= 12 ? id.toString() : null;
    }

    public String getBilibiliAid() {
        if (audioUrl == null || audioUrl.isBlank() || !audioUrl.contains("bilibili.com")) {
            return null;
        }
        String[] markers = {"/video/av", "aid=", "avid="};
        for (String marker : markers) {
            int markerIndex = audioUrl.indexOf(marker);
            if (markerIndex >= 0) {
                int start = markerIndex + marker.length();
                StringBuilder id = new StringBuilder();
                while (start < audioUrl.length() && Character.isDigit(audioUrl.charAt(start))) {
                    id.append(audioUrl.charAt(start));
                    start++;
                }
                if (!id.isEmpty()) {
                    return id.toString();
                }
            }
        }
        return null;
    }

    public String getBilibiliPage() {
        String page = getBilibiliQueryValue("p");
        return page == null ? getBilibiliQueryValue("page") : page;
    }

    public String getBilibiliCid() {
        return getBilibiliQueryValue("cid");
    }

    private String getBilibiliQueryValue(String key) {
        if (audioUrl == null || audioUrl.isBlank() || !audioUrl.contains("bilibili.com")) {
            return null;
        }
        int queryIndex = audioUrl.indexOf('?');
        if (queryIndex < 0 || queryIndex >= audioUrl.length() - 1) {
            return null;
        }
        String[] parts = audioUrl.substring(queryIndex + 1).split("&");
        for (String part : parts) {
            int equalsIndex = part.indexOf('=');
            if (equalsIndex <= 0) {
                continue;
            }
            String queryKey = part.substring(0, equalsIndex);
            if (!key.equals(queryKey)) {
                continue;
            }
            String value = part.substring(equalsIndex + 1);
            StringBuilder number = new StringBuilder();
            for (int i = 0; i < value.length() && Character.isDigit(value.charAt(i)); i++) {
                number.append(value.charAt(i));
            }
            return number.isEmpty() ? null : number.toString();
        }
        return null;
    }

    public String getBilibiliEmbedParam() {
        String bvid = getBilibiliBvid();
        StringBuilder param = new StringBuilder();
        if (bvid != null) {
            param.append("bvid=").append(bvid);
        } else {
            String aid = getBilibiliAid();
            if (aid == null) {
                return null;
            }
            param.append("aid=").append(aid);
        }
        String cid = getBilibiliCid();
        if (cid != null) {
            param.append("&cid=").append(cid);
        }
        String page = getBilibiliPage();
        if (page != null) {
            param.append("&page=").append(page);
        }
        return param.toString();
    }

    public String getBilibiliVideoUrl() {
        if (audioUrl != null && !audioUrl.isBlank() && audioUrl.contains("bilibili.com")) {
            return audioUrl;
        }
        String bvid = getBilibiliBvid();
        if (bvid != null) {
            return "https://www.bilibili.com/video/" + bvid;
        }
        String aid = getBilibiliAid();
        return aid == null ? null : "https://www.bilibili.com/video/av" + aid;
    }
}
