package com.skiarkt.shooter.model;

import android.os.Parcel;

import com.skiarkt.shooter.util.GeoUtils;

import java.util.ArrayList;
import java.util.List;

/** Expected threat ingress route: a centreline polyline with a width. */
public class ThreatCorridor extends BasePlanItem {
    /** Ordered centreline, first point = ingress start, last point = target end. */
    public List<GeoPos> points = new ArrayList<>();
    public double widthMeters = 200.0;
    /** Assumed threat ground speed (m/s) - used by the engagement timeline. */
    public double threatSpeedMps = 20.0;
    /** Assumed threat altitude above ground (meters). */
    public double threatAltitudeAglMeters = 60.0;

    public ThreatCorridor() {
        super("Threat Corridor", PlanColors.THREAT_CORRIDOR);
    }

    protected ThreatCorridor(Parcel in) {
        super(in);
        in.readTypedList(points, GeoPos.CREATOR);
        widthMeters = in.readDouble();
        threatSpeedMps = in.readDouble();
        threatAltitudeAglMeters = in.readDouble();
    }

    public double lengthMeters() {
        double total = 0;
        for (int i = 1; i < points.size(); i++) {
            total += GeoUtils.distanceMeters(points.get(i - 1), points.get(i));
        }
        return total;
    }

    @Override
    public ItemKind getKind() {
        return ItemKind.THREAT_CORRIDOR;
    }

    @Override
    public GeoPos getAnchor() {
        return points.isEmpty() ? null : points.get(0);
    }

    @Override
    public boolean isValid() {
        if (points == null || points.size() < 2 || widthMeters <= 0) return false;
        for (GeoPos p : points) {
            if (p == null || !p.isValid()) return false;
        }
        return true;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        writeBase(dest);
        dest.writeTypedList(points);
        dest.writeDouble(widthMeters);
        dest.writeDouble(threatSpeedMps);
        dest.writeDouble(threatAltitudeAglMeters);
    }

    public static final Creator<ThreatCorridor> CREATOR = new Creator<ThreatCorridor>() {
        @Override
        public ThreatCorridor createFromParcel(Parcel in) {
            return new ThreatCorridor(in);
        }

        @Override
        public ThreatCorridor[] newArray(int size) {
            return new ThreatCorridor[size];
        }
    };
}
