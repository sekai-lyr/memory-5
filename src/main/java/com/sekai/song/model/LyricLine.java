package com.sekai.song.model;

public class LyricLine {
    private double seconds;
    private String timeLabel;
    private String text;

    public LyricLine(double seconds, String timeLabel, String text) {
        this.seconds = seconds;
        this.timeLabel = timeLabel;
        this.text = text;
    }

    public double getSeconds() {
        return seconds;
    }

    public void setSeconds(double seconds) {
        this.seconds = seconds;
    }

    public String getTimeLabel() {
        return timeLabel;
    }

    public void setTimeLabel(String timeLabel) {
        this.timeLabel = timeLabel;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }
}
