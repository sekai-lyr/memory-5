package com.sekai.song.model;

public class NeteaseLink {
    private String type;
    private String id;
    private String message;

    public NeteaseLink(String type, String id, String message) {
        this.type = type;
        this.id = id;
        this.message = message;
    }

    public boolean isSong() {
        return "song".equals(type);
    }

    public boolean isPlaylist() {
        return "playlist".equals(type);
    }

    public boolean isUser() {
        return "user".equals(type);
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
