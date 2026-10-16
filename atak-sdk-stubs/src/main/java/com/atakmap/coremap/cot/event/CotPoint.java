/* Compile-only stub mirroring the ATAK-CIV 5.6 SDK API. Replaced by the host ATAK app at runtime. */
package com.atakmap.coremap.cot.event;

public class CotPoint {
    private final double lat;
    private final double lon;
    private final double hae;
    private final double ce;
    private final double le;

    public CotPoint(double lat, double lon, double hae, double ce, double le) {
        this.lat = lat;
        this.lon = lon;
        this.hae = hae;
        this.ce = ce;
        this.le = le;
    }

    public double getLat() {
        return lat;
    }

    public double getLon() {
        return lon;
    }

    public double getHae() {
        return hae;
    }

    public double getCe() {
        return ce;
    }

    public double getLe() {
        return le;
    }
}
