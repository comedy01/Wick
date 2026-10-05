package dev.wick.core;

import com.google.gson.annotations.SerializedName;

public enum UpdateSpeed implements Choice {
    @SerializedName("realtime")
    REALTIME("realtime", 1),
    @SerializedName("fast")
    FAST("fast", 2),
    @SerializedName("slow")
    SLOW("slow", 5);

    private final String id;
    private final int interval;

    UpdateSpeed(String id, int interval) {
        this.id = id;
        this.interval = interval;
    }

    @Override
    public String id() {
        return id;
    }

    public int interval() {
        return interval;
    }
}
