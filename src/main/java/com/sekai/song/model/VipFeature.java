package com.sekai.song.model;

public class VipFeature {
    private final String name;
    private final String description;
    private final String tag;
    private final boolean enabled;

    public VipFeature(String name, String description, String tag, boolean enabled) {
        this.name = name;
        this.description = description;
        this.tag = tag;
        this.enabled = enabled;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getTag() {
        return tag;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
