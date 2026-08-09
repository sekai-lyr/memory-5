package com.sekai.song.model;

public class DeliverySourceSummary {
    private String name;
    private String description;
    private String cssClass;
    private int count;
    private int percent;

    public DeliverySourceSummary() {
    }

    public DeliverySourceSummary(String name, String description, String cssClass, int count, int total) {
        this.name = name;
        this.description = description;
        this.cssClass = cssClass;
        this.count = count;
        this.percent = total <= 0 ? 0 : Math.min(100, Math.round(count * 100f / total));
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
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
