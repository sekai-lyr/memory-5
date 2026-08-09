package com.sekai.song.model;

public class DashboardStats {
    private int songCount;
    private int playlistCount;
    private int favoriteCount;
    private String topAnime;
    private String topMood;

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

    public int getFavoriteCount() {
        return favoriteCount;
    }

    public void setFavoriteCount(int favoriteCount) {
        this.favoriteCount = favoriteCount;
    }

    public String getTopAnime() {
        return topAnime;
    }

    public void setTopAnime(String topAnime) {
        this.topAnime = topAnime;
    }

    public String getTopMood() {
        return topMood;
    }

    public void setTopMood(String topMood) {
        this.topMood = topMood;
    }
}
