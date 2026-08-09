package com.sekai.song.model;

public class DeliveryRisk {
    private String severity;
    private String type;
    private String title;
    private String detail;
    private String actionHint;
    private Long songId;
    private Long playlistId;

    public DeliveryRisk() {
    }

    public DeliveryRisk(String severity, String type, String title, String detail,
                        String actionHint, Long songId, Long playlistId) {
        this.severity = severity;
        this.type = type;
        this.title = title;
        this.detail = detail;
        this.actionHint = actionHint;
        this.songId = songId;
        this.playlistId = playlistId;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
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

    public Long getSongId() {
        return songId;
    }

    public void setSongId(Long songId) {
        this.songId = songId;
    }

    public Long getPlaylistId() {
        return playlistId;
    }

    public void setPlaylistId(Long playlistId) {
        this.playlistId = playlistId;
    }
}
