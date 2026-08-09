package com.sekai.song.model;

public class SmartPlaylistResult {
    private final boolean success;
    private final Long playlistId;
    private final int addedCount;
    private final String message;

    private SmartPlaylistResult(boolean success, Long playlistId, int addedCount, String message) {
        this.success = success;
        this.playlistId = playlistId;
        this.addedCount = addedCount;
        this.message = message;
    }

    public static SmartPlaylistResult success(Long playlistId, int addedCount, String message) {
        return new SmartPlaylistResult(true, playlistId, addedCount, message);
    }

    public static SmartPlaylistResult fail(String message) {
        return new SmartPlaylistResult(false, null, 0, message);
    }

    public boolean isSuccess() {
        return success;
    }

    public Long getPlaylistId() {
        return playlistId;
    }

    public int getAddedCount() {
        return addedCount;
    }

    public String getMessage() {
        return message;
    }
}
