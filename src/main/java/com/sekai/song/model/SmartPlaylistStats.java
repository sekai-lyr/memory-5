package com.sekai.song.model;

public class SmartPlaylistStats {
    private int totalSongs;
    private int localAudioSongs;
    private int localVideoSongs;
    private int bilibiliSongs;
    private int neteaseSongs;
    private int qqSongs;
    private int favoriteSongs;
    private int thirdPartyOnlySongs;

    public int getTotalSongs() {
        return totalSongs;
    }

    public void setTotalSongs(int totalSongs) {
        this.totalSongs = totalSongs;
    }

    public int getLocalAudioSongs() {
        return localAudioSongs;
    }

    public void setLocalAudioSongs(int localAudioSongs) {
        this.localAudioSongs = localAudioSongs;
    }

    public int getLocalVideoSongs() {
        return localVideoSongs;
    }

    public void setLocalVideoSongs(int localVideoSongs) {
        this.localVideoSongs = localVideoSongs;
    }

    public int getBilibiliSongs() {
        return bilibiliSongs;
    }

    public void setBilibiliSongs(int bilibiliSongs) {
        this.bilibiliSongs = bilibiliSongs;
    }

    public int getNeteaseSongs() {
        return neteaseSongs;
    }

    public void setNeteaseSongs(int neteaseSongs) {
        this.neteaseSongs = neteaseSongs;
    }

    public int getQqSongs() {
        return qqSongs;
    }

    public void setQqSongs(int qqSongs) {
        this.qqSongs = qqSongs;
    }

    public int getFavoriteSongs() {
        return favoriteSongs;
    }

    public void setFavoriteSongs(int favoriteSongs) {
        this.favoriteSongs = favoriteSongs;
    }

    public int getThirdPartyOnlySongs() {
        return thirdPartyOnlySongs;
    }

    public void setThirdPartyOnlySongs(int thirdPartyOnlySongs) {
        this.thirdPartyOnlySongs = thirdPartyOnlySongs;
    }
}
