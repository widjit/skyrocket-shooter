/* Compile-only stub mirroring the CivTAK 4.6 SDK API. Replaced by the host ATAK app at runtime. */
package com.atakmap.coremap.maps.time;

public class CoordinatedTime {
    private final long millis;

    public CoordinatedTime() {
        this(System.currentTimeMillis());
    }

    public CoordinatedTime(long millis) {
        this.millis = millis;
    }

    public long getMilliseconds() {
        return millis;
    }

    public CoordinatedTime addMinutes(int minutes) {
        return new CoordinatedTime(millis + minutes * 60000L);
    }

    public CoordinatedTime addDays(int days) {
        return new CoordinatedTime(millis + days * 86400000L);
    }
}
