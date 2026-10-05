package dev.wick.core;

public final class Packed {
    private Packed() {
    }

    public static int withDynamic(int coords, int dynamic) {
        if (dynamic <= (coords & 0xFFFF)) {
            return coords;
        }
        return (coords & 0xFFFF0000) | dynamic;
    }
}
