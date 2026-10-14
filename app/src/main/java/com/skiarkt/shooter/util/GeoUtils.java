package com.skiarkt.shooter.util;

import com.skiarkt.shooter.model.GeoPos;

import java.util.List;

/**
 * Pure-Java spherical geodesy helpers (WGS84 mean radius). Accurate to well under
 * 0.5% over the few-kilometre ranges used in point defense planning, and usable in
 * JVM unit tests without the ATAK runtime.
 */
public final class GeoUtils {
    public static final double EARTH_RADIUS_M = 6371008.8;

    private GeoUtils() {
    }

    /** Great-circle (haversine) distance in meters. */
    public static double distanceMeters(GeoPos a, GeoPos b) {
        double phi1 = Math.toRadians(a.lat), phi2 = Math.toRadians(b.lat);
        double dPhi = phi2 - phi1;
        double dLam = Math.toRadians(b.lon - a.lon);
        double h = Math.sin(dPhi / 2) * Math.sin(dPhi / 2)
                + Math.cos(phi1) * Math.cos(phi2) * Math.sin(dLam / 2) * Math.sin(dLam / 2);
        return 2 * EARTH_RADIUS_M * Math.asin(Math.min(1.0, Math.sqrt(h)));
    }

    /** Initial bearing from a to b, degrees true [0, 360). */
    public static double bearingDeg(GeoPos a, GeoPos b) {
        double phi1 = Math.toRadians(a.lat), phi2 = Math.toRadians(b.lat);
        double dLam = Math.toRadians(b.lon - a.lon);
        double y = Math.sin(dLam) * Math.cos(phi2);
        double x = Math.cos(phi1) * Math.sin(phi2) - Math.sin(phi1) * Math.cos(phi2) * Math.cos(dLam);
        return normalizeDeg(Math.toDegrees(Math.atan2(y, x)));
    }

    /** Destination point given start, bearing (deg true) and distance (m). */
    public static GeoPos destination(GeoPos start, double bearingDeg, double distanceM) {
        double delta = distanceM / EARTH_RADIUS_M;
        double theta = Math.toRadians(bearingDeg);
        double phi1 = Math.toRadians(start.lat), lam1 = Math.toRadians(start.lon);
        double phi2 = Math.asin(Math.sin(phi1) * Math.cos(delta)
                + Math.cos(phi1) * Math.sin(delta) * Math.cos(theta));
        double lam2 = lam1 + Math.atan2(Math.sin(theta) * Math.sin(delta) * Math.cos(phi1),
                Math.cos(delta) - Math.sin(phi1) * Math.sin(phi2));
        return new GeoPos(Math.toDegrees(phi2), normalizeLon(Math.toDegrees(lam2)));
    }

    /** Point at fraction f (0..1) along the great circle from a to b. */
    public static GeoPos interpolate(GeoPos a, GeoPos b, double f) {
        double d = distanceMeters(a, b);
        if (d < 1e-6) return new GeoPos(a.lat, a.lon);
        return destination(a, bearingDeg(a, b), d * f);
    }

    /** Arithmetic centroid of the vertices (adequate for small tactical polygons). */
    public static GeoPos centroid(List<GeoPos> pts) {
        if (pts == null || pts.isEmpty()) return null;
        double lat = 0, lon = 0;
        for (GeoPos p : pts) {
            lat += p.lat;
            lon += p.lon;
        }
        return new GeoPos(lat / pts.size(), lon / pts.size());
    }

    /** Ray-casting point-in-polygon test (polygon implicitly closed). */
    public static boolean pointInPolygon(GeoPos p, List<GeoPos> poly) {
        if (p == null || poly == null || poly.size() < 3) return false;
        boolean inside = false;
        for (int i = 0, j = poly.size() - 1; i < poly.size(); j = i++) {
            GeoPos a = poly.get(i), b = poly.get(j);
            if ((a.lat > p.lat) != (b.lat > p.lat)
                    && p.lon < (b.lon - a.lon) * (p.lat - a.lat) / (b.lat - a.lat) + a.lon) {
                inside = !inside;
            }
        }
        return inside;
    }

    /** Polygon area (m^2) via local equirectangular projection + shoelace formula. */
    public static double polygonAreaSquareMeters(List<GeoPos> poly) {
        if (poly == null || poly.size() < 3) return 0;
        GeoPos c = centroid(poly);
        double cosLat = Math.cos(Math.toRadians(c.lat));
        double sum = 0;
        for (int i = 0; i < poly.size(); i++) {
            GeoPos a = poly.get(i), b = poly.get((i + 1) % poly.size());
            double ax = Math.toRadians(a.lon - c.lon) * EARTH_RADIUS_M * cosLat;
            double ay = Math.toRadians(a.lat - c.lat) * EARTH_RADIUS_M;
            double bx = Math.toRadians(b.lon - c.lon) * EARTH_RADIUS_M * cosLat;
            double by = Math.toRadians(b.lat - c.lat) * EARTH_RADIUS_M;
            sum += ax * by - bx * ay;
        }
        return Math.abs(sum) / 2.0;
    }

    public static double normalizeDeg(double d) {
        double r = d % 360.0;
        return r < 0 ? r + 360.0 : r;
    }

    public static double normalizeLon(double lon) {
        double r = ((lon + 180.0) % 360.0 + 360.0) % 360.0 - 180.0;
        return r == -180.0 && lon > 0 ? 180.0 : r;
    }
}
