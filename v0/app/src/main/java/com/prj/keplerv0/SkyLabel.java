package com.prj.keplerv0;

public class SkyLabel {
    public enum Type {
        STAR,
        CONSTELLATION
    }

    public final String text;
    public final float x;
    public final float y;
    public final Type type;
    public final float magnitude; // For fading, prioritization. If constellation, we can set it to a negative number to prioritize it.

    // Additional info for the tap popup
    public final float distance; // simple formatted string or float
    public final String subtitle;
    public final String mythology;

    public SkyLabel(String text, float x, float y, Type type, float magnitude, String subtitle, String mythology) {
        this.text = text;
        this.x = x;
        this.y = y;
        this.type = type;
        this.magnitude = magnitude;
        this.distance = 0f; // placeholder
        this.subtitle = subtitle != null ? subtitle : "";
        this.mythology = mythology != null ? mythology : "";
    }
}
