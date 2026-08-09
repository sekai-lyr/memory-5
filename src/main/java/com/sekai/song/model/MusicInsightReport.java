package com.sekai.song.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class MusicInsightReport {
    private LocalDateTime generatedAt = LocalDateTime.now();
    private int songCount;
    private int playlistCount;
    private int assetScore;
    private String assetLabel;
    private String assetHint;
    private int localReadyCount;
    private int lyricCount;
    private int durationCount;
    private int favoriteCount;
    private int thirdPartyOnlyCount;
    private int bilibiliOnlineCount;
    private int missingMetadataCount;
    private int averageDurationSeconds;
    private String totalDurationLabel = "0 分钟";
    private String salesPitch;
    private final List<InsightMetric> sourceMetrics = new ArrayList<>();
    private final List<InsightMetric> contentMetrics = new ArrayList<>();
    private final List<InsightMetric> topArtists = new ArrayList<>();
    private final List<InsightAction> actions = new ArrayList<>();
    private final List<PlaylistInsight> playlistInsights = new ArrayList<>();

    public LocalDateTime getGeneratedAt() {
        return generatedAt;
    }

    public void setGeneratedAt(LocalDateTime generatedAt) {
        this.generatedAt = generatedAt;
    }

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

    public int getAssetScore() {
        return assetScore;
    }

    public void setAssetScore(int assetScore) {
        this.assetScore = assetScore;
    }

    public String getAssetLabel() {
        return assetLabel;
    }

    public void setAssetLabel(String assetLabel) {
        this.assetLabel = assetLabel;
    }

    public String getAssetHint() {
        return assetHint;
    }

    public void setAssetHint(String assetHint) {
        this.assetHint = assetHint;
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

    public int getDurationCount() {
        return durationCount;
    }

    public void setDurationCount(int durationCount) {
        this.durationCount = durationCount;
    }

    public int getFavoriteCount() {
        return favoriteCount;
    }

    public void setFavoriteCount(int favoriteCount) {
        this.favoriteCount = favoriteCount;
    }

    public int getThirdPartyOnlyCount() {
        return thirdPartyOnlyCount;
    }

    public void setThirdPartyOnlyCount(int thirdPartyOnlyCount) {
        this.thirdPartyOnlyCount = thirdPartyOnlyCount;
    }

    public int getBilibiliOnlineCount() {
        return bilibiliOnlineCount;
    }

    public void setBilibiliOnlineCount(int bilibiliOnlineCount) {
        this.bilibiliOnlineCount = bilibiliOnlineCount;
    }

    public int getMissingMetadataCount() {
        return missingMetadataCount;
    }

    public void setMissingMetadataCount(int missingMetadataCount) {
        this.missingMetadataCount = missingMetadataCount;
    }

    public int getAverageDurationSeconds() {
        return averageDurationSeconds;
    }

    public void setAverageDurationSeconds(int averageDurationSeconds) {
        this.averageDurationSeconds = averageDurationSeconds;
    }

    public String getTotalDurationLabel() {
        return totalDurationLabel;
    }

    public void setTotalDurationLabel(String totalDurationLabel) {
        this.totalDurationLabel = totalDurationLabel;
    }

    public String getSalesPitch() {
        return salesPitch;
    }

    public void setSalesPitch(String salesPitch) {
        this.salesPitch = salesPitch;
    }

    public List<InsightMetric> getSourceMetrics() {
        return sourceMetrics;
    }

    public List<InsightMetric> getContentMetrics() {
        return contentMetrics;
    }

    public List<InsightMetric> getTopArtists() {
        return topArtists;
    }

    public List<InsightAction> getActions() {
        return actions;
    }

    public List<PlaylistInsight> getPlaylistInsights() {
        return playlistInsights;
    }

    public int getLocalReadyPercent() {
        return percent(localReadyCount);
    }

    public int getLyricPercent() {
        return percent(lyricCount);
    }

    public int getDurationPercent() {
        return percent(durationCount);
    }

    private int percent(int count) {
        return songCount <= 0 ? 0 : Math.round(count * 100f / songCount);
    }
}
