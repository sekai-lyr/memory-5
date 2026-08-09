package com.sekai.song.model;

public class InsightAction {
    private String level;
    private String title;
    private String detail;
    private String actionText;
    private String actionUrl;

    public InsightAction() {
    }

    public InsightAction(String level, String title, String detail, String actionText, String actionUrl) {
        this.level = level;
        this.title = title;
        this.detail = detail;
        this.actionText = actionText;
        this.actionUrl = actionUrl;
    }

    public String getLevel() {
        return level;
    }

    public void setLevel(String level) {
        this.level = level;
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

    public String getActionText() {
        return actionText;
    }

    public void setActionText(String actionText) {
        this.actionText = actionText;
    }

    public String getActionUrl() {
        return actionUrl;
    }

    public void setActionUrl(String actionUrl) {
        this.actionUrl = actionUrl;
    }
}
