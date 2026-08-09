package com.sekai.song.model;

public class BilibiliLink {
    private String type;
    private String id;
    private String message;

    public BilibiliLink(String type, String id, String message) {
        this.type = type;
        this.id = id;
        this.message = message;
    }

    public boolean isVideo() {
        return "video".equals(type);
    }

    public boolean isCollection() {
        return "collection".equals(type);
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
