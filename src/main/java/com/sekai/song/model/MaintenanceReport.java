package com.sekai.song.model;

import java.util.ArrayList;
import java.util.List;

public class MaintenanceReport {
    private int playlistCount;
    private int totalSongs;
    private int localAudioCount;
    private int localVideoCount;
    private int thirdPartyOnlyCount;
    private int bilibiliVideoCount;
    private int missingLocalAudioCount;
    private int missingLocalVideoCount;
    private int unplayableCount;
    private int duplicateGroupCount;
    private int duplicateSongCount;
    private final List<PlaylistMaintenanceReport> playlists = new ArrayList<>();
    private final List<MaintenanceIssue> issues = new ArrayList<>();

    public int getPlaylistCount() {
        return playlistCount;
    }

    public void setPlaylistCount(int playlistCount) {
        this.playlistCount = playlistCount;
    }

    public int getTotalSongs() {
        return totalSongs;
    }

    public void setTotalSongs(int totalSongs) {
        this.totalSongs = totalSongs;
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

    public List<PlaylistMaintenanceReport> getPlaylists() {
        return playlists;
    }

    public List<MaintenanceIssue> getIssues() {
        return issues;
    }

    public int getBlockingIssueCount() {
        return missingLocalAudioCount + missingLocalVideoCount + unplayableCount;
    }

    public int getOptimizationIssueCount() {
        return thirdPartyOnlyCount + duplicateSongCount;
    }

    public int getHealthPercent() {
        if (totalSongs <= 0) {
            return 100;
        }
        int weightedProblems = getBlockingIssueCount() * 2 + duplicateSongCount + thirdPartyOnlyCount;
        int score = 100 - (int) Math.round(weightedProblems * 100.0 / Math.max(totalSongs * 2, 1));
        return Math.max(0, Math.min(100, score));
    }
}
