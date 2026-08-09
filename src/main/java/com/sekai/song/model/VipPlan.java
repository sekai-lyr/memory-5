package com.sekai.song.model;

public class VipPlan {
    private final String name;
    private final String price;
    private final String plan;
    private final Integer durationDays;
    private final String description;
    private final boolean highlighted;

    public VipPlan(String name, String price, String plan, Integer durationDays, String description, boolean highlighted) {
        this.name = name;
        this.price = price;
        this.plan = plan;
        this.durationDays = durationDays;
        this.description = description;
        this.highlighted = highlighted;
    }

    public String getName() {
        return name;
    }

    public String getPrice() {
        return price;
    }

    public String getPlan() {
        return plan;
    }

    public Integer getDurationDays() {
        return durationDays;
    }

    public String getDescription() {
        return description;
    }

    public boolean isHighlighted() {
        return highlighted;
    }
}
