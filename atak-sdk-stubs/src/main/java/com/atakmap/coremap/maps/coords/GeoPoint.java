/* Compile-only stub mirroring the ATAK 4.8 SDK API. Replaced by the host ATAK app at runtime. */
package com.atakmap.coremap.maps.coords;

public class GeoPoint {
    public static final double UNKNOWN = Double.NaN;

    private final double lat;
    private final double lon;
    private final double alt;

    public GeoPoint(double latitude, double longitude) {
        this(latitude, longitude, UNKNOWN);
    }

    public GeoPoint(double latitude, double longitude, double altitude) {
        this.lat = latitude;
        this.lon = longitude;
        this.alt = altitude;
    }

    public double getLatitude() {
        return lat;
    }

    public double getLongitude() {
        return lon;
    }

    public double getAltitude() {
        return alt;
    }

    public boolean isValid() {
        return !Double.isNaN(lat) && !Double.isNaN(lon) && Math.abs(lat) <= 90 && Math.abs(lon) <= 180;
    }
}
