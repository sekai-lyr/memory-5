package com.sekai.song.model;

public class InsightMetric {
    private String name;
    private String detail;
    private String cssClass;
    private int count;
    private int percent;

    public InsightMetric() {
    }

    public InsightMetric(String name, String detail, String cssClass, int count, int total) {
        this.name = name;
        this.detail = detail;
        this.cssClass = cssClass;
        this.count = count;
        this.percent = total <= 0 ? 0 : Math.round(count * 100f / total);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }

    public String getCssClass() {
        return cssClass;
    }

    public void setCssClass(String cssClass) {
        this.cssClass = cssClass;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }

    public int getPercent() {
        return percent;
    }

    public void setPercent(int percent) {
        this.percent = percent;
    }
}
