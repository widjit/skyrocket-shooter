package com.btlrckt.bottlerocket.model;

import android.os.Parcel;
import android.os.Parcelable;

import com.atakmap.coremap.maps.coords.GeoPoint;

import java.util.Locale;

/**
 * Lightweight, Gson/Parcel friendly geographic position (WGS84).
 * Altitude is meters HAE; {@link Double#NaN} means unknown.
 * Converts to/from ATAK {@link GeoPoint} for map and elevation APIs.
 */
public class GeoPos implements Parcelable {
    public double lat;
    public double lon;
    public double altHae = Double.NaN;

    public GeoPos() {
    }

    public GeoPos(double lat, double lon) {
        this(lat, lon, Double.NaN);
    }

    public GeoPos(double lat, double lon, double altHae) {
        this.lat = lat;
        this.lon = lon;
        this.altHae = altHae;
    }

    public GeoPos(GeoPos other) {
        this(other.lat, other.lon, other.altHae);
    }

    protected GeoPos(Parcel in) {
        lat = in.readDouble();
        lon = in.readDouble();
        altHae = in.readDouble();
    }

    public static GeoPos fromGeoPoint(GeoPoint gp) {
        return new GeoPos(gp.getLatitude(), gp.getLongitude(), gp.getAltitude());
    }

    public GeoPoint toGeoPoint() {
        return Double.isNaN(altHae) ? new GeoPoint(lat, lon) : new GeoPoint(lat, lon, altHae);
    }

    public boolean hasAltitude() {
        return !Double.isNaN(altHae);
    }

    public boolean isValid() {
        return !Double.isNaN(lat) && !Double.isNaN(lon) && Math.abs(lat) <= 90.0 && Math.abs(lon) <= 180.0;
    }

    /** CoT "link point" encoding: "lat,lon,hae" (hae omitted when unknown). */
    public String toCotString() {
        return hasAltitude()
                ? String.format(Locale.US, "%.8f,%.8f,%.2f", lat, lon, altHae)
                : String.format(Locale.US, "%.8f,%.8f", lat, lon);
    }

    /** Parses "lat,lon[,hae]"; returns null for malformed input. */
    public static GeoPos fromCotString(String s) {
        if (s == null) return null;
        String[] parts = s.trim().split("\\s*,\\s*");
        if (parts.length < 2) return null;
        try {
            double la = Double.parseDouble(parts[0]);
            double lo = Double.parseDouble(parts[1]);
            double h = parts.length > 2 ? Double.parseDouble(parts[2]) : Double.NaN;
            GeoPos p = new GeoPos(la, lo, h);
            return p.isValid() ? p : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof GeoPos)) return false;
        GeoPos g = (GeoPos) o;
        return Double.compare(lat, g.lat) == 0 && Double.compare(lon, g.lon) == 0
                && Double.compare(altHae, g.altHae) == 0;
    }

    @Override
    public int hashCode() {
        long h = Double.doubleToLongBits(lat) * 31 + Double.doubleToLongBits(lon);
        h = h * 31 + Double.doubleToLongBits(altHae);
        return (int) (h ^ (h >>> 32));
    }

    @Override
    public String toString() {
        return toCotString();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeDouble(lat);
        dest.writeDouble(lon);
        dest.writeDouble(altHae);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<GeoPos> CREATOR = new Creator<GeoPos>() {
        @Override
        public GeoPos createFromParcel(Parcel in) {
            return new GeoPos(in);
        }

        @Override
        public GeoPos[] newArray(int size) {
            return new GeoPos[size];
        }
    };
}
