package com.sekai.song.model;

public class MaintenanceIssue {
    private String type;
    private String severity;
    private String playlistName;
    private Long playlistId;
    private Long songId;
    private String title;
    private String detail;
    private String actionHint;

    public MaintenanceIssue() {
    }

    public MaintenanceIssue(String type, String severity, String playlistName, Long playlistId,
                            Long songId, String title, String detail, String actionHint) {
        this.type = type;
        this.severity = severity;
        this.playlistName = playlistName;
        this.playlistId = playlistId;
        this.songId = songId;
        this.title = title;
        this.detail = detail;
        this.actionHint = actionHint;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public String getPlaylistName() {
        return playlistName;
    }

    public void setPlaylistName(String playlistName) {
        this.playlistName = playlistName;
    }

    public Long getPlaylistId() {
        return playlistId;
    }

    public void setPlaylistId(Long playlistId) {
        this.playlistId = playlistId;
    }

    public Long getSongId() {
        return songId;
    }

    public void setSongId(Long songId) {
        this.songId = songId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }

    public String getActionHint() {
        return actionHint;
    }

    public void setActionHint(String actionHint) {
        this.actionHint = actionHint;
    }
}
