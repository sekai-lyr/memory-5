package com.sekai.song.model;

public class PlaylistMaintenanceReport {
    private Long playlistId;
    private String playlistName;
    private int songCount;
    private int localAudioCount;
    private int localVideoCount;
    private int thirdPartyOnlyCount;
    private int bilibiliVideoCount;
    private int missingLocalAudioCount;
    private int missingLocalVideoCount;
    private int unplayableCount;
    private int duplicateGroupCount;
    private int duplicateSongCount;

    public Long getPlaylistId() {
        return playlistId;
    }

    public void setPlaylistId(Long playlistId) {
        this.playlistId = playlistId;
    }

    public String getPlaylistName() {
        return playlistName;
    }

    public void setPlaylistName(String playlistName) {
        this.playlistName = playlistName;
    }

    public int getSongCount() {
        return songCount;
    }

    public void setSongCount(int songCount) {
        this.songCount = songCount;
    }

    public int getLocalAudioCount() {
        return localAudioCount;
    }

    public void setLocalAudioCount(int localAudioCount) {
        this.localAudioCount = localAudioCount;
    }

    public int getLocalVideoCount() {
        return localVideoCount;
    }

    public void setLocalVideoCount(int localVideoCount) {
        this.localVideoCount = localVideoCount;
    }

    public int getThirdPartyOnlyCount() {
        return thirdPartyOnlyCount;
    }

    public void setThirdPartyOnlyCount(int thirdPartyOnlyCount) {
        this.thirdPartyOnlyCount = thirdPartyOnlyCount;
    }

    public int getBilibiliVideoCount() {
        return bilibiliVideoCount;
    }

    public void setBilibiliVideoCount(int bilibiliVideoCount) {
        this.bilibiliVideoCount = bilibiliVideoCount;
    }

    public int getMissingLocalAudioCount() {
        return missingLocalAudioCount;
    }

    public void setMissingLocalAudioCount(int missingLocalAudioCount) {
        this.missingLocalAudioCount = missingLocalAudioCount;
    }

    public int getMissingLocalVideoCount() {
        return missingLocalVideoCount;
    }

    public void setMissingLocalVideoCount(int missingLocalVideoCount) {
        this.missingLocalVideoCount = missingLocalVideoCount;
    }

    public int getUnplayableCount() {
        return unplayableCount;
    }

    public void setUnplayableCount(int unplayableCount) {
        this.unplayableCount = unplayableCount;
    }

    public int getDuplicateGroupCount() {
        return duplicateGroupCount;
    }

    public void setDuplicateGroupCount(int duplicateGroupCount) {
        this.duplicateGroupCount = duplicateGroupCount;
    }

    public int getDuplicateSongCount() {
        return duplicateSongCount;
    }

    public void setDuplicateSongCount(int duplicateSongCount) {
        this.duplicateSongCount = duplicateSongCount;
    }

    public int getProblemCount() {
        return missingLocalAudioCount + missingLocalVideoCount + unplayableCount + duplicateSongCount;
    }

    public boolean isHasDuplicates() {
        return duplicateSongCount > 0;
    }

    public String getStatusText() {
        if (songCount == 0) {
            return "空歌单";
        }
        if (getProblemCount() == 0 && thirdPartyOnlyCount == 0) {
            return "健康";
        }
        if (getProblemCount() == 0) {
            return "可优化";
        }
        return "需处理";
    }
}
