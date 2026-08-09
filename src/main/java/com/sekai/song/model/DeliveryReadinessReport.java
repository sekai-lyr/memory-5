package com.sekai.song.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class DeliveryReadinessReport {
    private LocalDateTime generatedAt = LocalDateTime.now();
    private int readinessScore;
    private String readinessLabel;
    private String readinessHint;
    private int songCount;
    private int playlistCount;
    private int playlistSongCount;
    private int emptyPlaylistCount;
    private int orphanSongCount;
    private int localReadyCount;
    private int localAudioCount;
    private int localVideoCount;
    private int thirdPartyOnlyCount;
    private int bilibiliOnlineCount;
    private int missingLocalCount;
    private int unplayableCount;
    private int neteaseCount;
    private int qqCount;
    private int bilibiliCount;
    private int manualCount;
    private int uploadFileCount;
    private long uploadBytes;
    private String uploadSizeLabel;
    private String uploadRoot;
    private String externalUploadRoot;
    private final List<DeliverySourceSummary> sourceSummaries = new ArrayList<>();
    private final List<DeliveryRisk> risks = new ArrayList<>();
    private final List<String> checklist = new ArrayList<>();

    public LocalDateTime getGeneratedAt() {
        return generatedAt;
    }

    public void setGeneratedAt(LocalDateTime generatedAt) {
        this.generatedAt = generatedAt;
    }

    public int getReadinessScore() {
        return readinessScore;
    }

    public void setReadinessScore(int readinessScore) {
        this.readinessScore = readinessScore;
    }

    public String getReadinessLabel() {
        return readinessLabel;
    }

    public void setReadinessLabel(String readinessLabel) {
        this.readinessLabel = readinessLabel;
    }

    public String getReadinessHint() {
        return readinessHint;
    }

    public void setReadinessHint(String readinessHint) {
        this.readinessHint = readinessHint;
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

    public int getPlaylistSongCount() {
        return playlistSongCount;
    }

    public void setPlaylistSongCount(int playlistSongCount) {
        this.playlistSongCount = playlistSongCount;
    }

    public int getEmptyPlaylistCount() {
        return emptyPlaylistCount;
    }

    public void setEmptyPlaylistCount(int emptyPlaylistCount) {
        this.emptyPlaylistCount = emptyPlaylistCount;
    }

    public int getOrphanSongCount() {
        return orphanSongCount;
    }

    public void setOrphanSongCount(int orphanSongCount) {
        this.orphanSongCount = orphanSongCount;
    }

    public int getLocalReadyCount() {
        return localReadyCount;
    }

    public void setLocalReadyCount(int localReadyCount) {
        this.localReadyCount = localReadyCount;
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

    public int getBilibiliOnlineCount() {
        return bilibiliOnlineCount;
    }

    public void setBilibiliOnlineCount(int bilibiliOnlineCount) {
        this.bilibiliOnlineCount = bilibiliOnlineCount;
    }

    public int getMissingLocalCount() {
        return missingLocalCount;
    }

    public void setMissingLocalCount(int missingLocalCount) {
        this.missingLocalCount = missingLocalCount;
    }

    public int getUnplayableCount() {
        return unplayableCount;
    }

    public void setUnplayableCount(int unplayableCount) {
        this.unplayableCount = unplayableCount;
    }

    public int getNeteaseCount() {
        return neteaseCount;
    }

    public void setNeteaseCount(int neteaseCount) {
        this.neteaseCount = neteaseCount;
    }

    public int getQqCount() {
        return qqCount;
    }

    public void setQqCount(int qqCount) {
        this.qqCount = qqCount;
    }

    public int getBilibiliCount() {
        return bilibiliCount;
    }

    public void setBilibiliCount(int bilibiliCount) {
        this.bilibiliCount = bilibiliCount;
    }

    public int getManualCount() {
        return manualCount;
    }

    public void setManualCount(int manualCount) {
        this.manualCount = manualCount;
    }

    public int getUploadFileCount() {
        return uploadFileCount;
    }

    public void setUploadFileCount(int uploadFileCount) {
        this.uploadFileCount = uploadFileCount;
    }

    public long getUploadBytes() {
        return uploadBytes;
    }

    public void setUploadBytes(long uploadBytes) {
        this.uploadBytes = uploadBytes;
    }

    public String getUploadSizeLabel() {
        return uploadSizeLabel;
    }

    public void setUploadSizeLabel(String uploadSizeLabel) {
        this.uploadSizeLabel = uploadSizeLabel;
    }

    public String getUploadRoot() {
        return uploadRoot;
    }

    public void setUploadRoot(String uploadRoot) {
        this.uploadRoot = uploadRoot;
    }

    public String getExternalUploadRoot() {
        return externalUploadRoot;
    }

    public void setExternalUploadRoot(String externalUploadRoot) {
        this.externalUploadRoot = externalUploadRoot;
    }

    public List<DeliverySourceSummary> getSourceSummaries() {
        return sourceSummaries;
    }

    public List<DeliveryRisk> getRisks() {
        return risks;
    }

    public List<String> getChecklist() {
        return checklist;
    }
}
