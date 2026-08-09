package com.sekai.song.model;

public class VipUsage {
    private int songCount;
    private int playlistCount;
    private int songLimit;
    private int playlistLimit;
    private boolean unlimited;
    private boolean vip;
    private String message;

    public int getSongCount() {
        return songCount;
    }

    public void setSongCount(int songCount) {
        this.songCount = songCount;
    }

    public int getPlaylistCount() {
        return playlistCount;
    }

    public void setPlaylistCount(int playlistCount) {
        this.playlistCount = playlistCount;
    }

    public int getSongLimit() {
        return songLimit;
    }

    public void setSongLimit(int songLimit) {
        this.songLimit = songLimit;
    }

    public int getPlaylistLimit() {
        return playlistLimit;
    }

    public void setPlaylistLimit(int playlistLimit) {
        this.playlistLimit = playlistLimit;
    }

    public boolean isUnlimited() {
        return unlimited;
    }

    public void setUnlimited(boolean unlimited) {
        this.unlimited = unlimited;
    }

    public boolean isVip() {
        return vip;
    }

    public void setVip(boolean vip) {
        this.vip = vip;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public int getSongPercent() {
        if (songLimit <= 0) {
            return 100;
        }
        return Math.min(100, (int) Math.round(songCount * 100.0 / songLimit));
    }

    public int getPlaylistPercent() {
        if (playlistLimit <= 0) {
            return 100;
        }
        return Math.min(100, (int) Math.round(playlistCount * 100.0 / playlistLimit));
    }

    public String getSongLimitLabel() {
        return unlimited ? "不限" : String.valueOf(songLimit);
    }

    public String getPlaylistLimitLabel() {
        return unlimited ? "不限" : String.valueOf(playlistLimit);
    }
}
