package com.sekai.song.model;

public class PlaylistInsight {
    private Long playlistId;
    private String playlistName;
    private String actionHint;
    private int songCount;
    private int localReadyCount;
    private int lyricCount;
    private int thirdPartyOnlyCount;
    private int missingMetadataCount;
    private int qualityScore;

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

    public String getActionHint() {
        return actionHint;
    }

    public void setActionHint(String actionHint) {
        this.actionHint = actionHint;
    }

    public int getSongCount() {
        return songCount;
    }

    public void setSongCount(int songCount) {
        this.songCount = songCount;
    }

    public int getLocalReadyCount() {
        return localReadyCount;
    }

    public void setLocalReadyCount(int localReadyCount) {
        this.localReadyCount = localReadyCount;
    }

    public int getLyricCount() {
        return lyricCount;
    }

    public void setLyricCount(int lyricCount) {
        this.lyricCount = lyricCount;
    }

    public int getThirdPartyOnlyCount() {
        return thirdPartyOnlyCount;
    }

    public void setThirdPartyOnlyCount(int thirdPartyOnlyCount) {
        this.thirdPartyOnlyCount = thirdPartyOnlyCount;
    }

    public int getMissingMetadataCount() {
        return missingMetadataCount;
    }

    public void setMissingMetadataCount(int missingMetadataCount) {
        this.missingMetadataCount = missingMetadataCount;
    }

    public int getQualityScore() {
        return qualityScore;
    }

    public void setQualityScore(int qualityScore) {
        this.qualityScore = qualityScore;
    }

    public int getLocalReadyPercent() {
        return percent(localReadyCount, songCount);
    }

    public int getLyricPercent() {
        return percent(lyricCount, songCount);
    }

    private int percent(int count, int total) {
        return total <= 0 ? 0 : Math.round(count * 100f / total);
    }
}
