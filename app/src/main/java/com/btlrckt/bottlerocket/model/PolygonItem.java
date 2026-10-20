package com.btlrckt.bottlerocket.model;

import android.os.Parcel;

import com.btlrckt.bottlerocket.util.GeoUtils;

import java.util.ArrayList;
import java.util.List;

/** Base for items defined by a closed polygon (exclusion zones, defended assets). */
public abstract class PolygonItem extends BasePlanItem {
    public List<GeoPos> points = new ArrayList<>();

    protected PolygonItem(String labelPrefix, int defaultColor) {
        super(labelPrefix, defaultColor);
    }

    protected PolygonItem(Parcel in) {
        super(in);
        in.readTypedList(points, GeoPos.CREATOR);
    }

    protected void writePolygon(Parcel dest) {
        writeBase(dest);
        dest.writeTypedList(points);
    }

    @Override
    public GeoPos getAnchor() {
        return GeoUtils.centroid(points);
    }

    @Override
    public boolean isValid() {
        if (points == null || points.size() < 3) return false;
        for (GeoPos p : points) {
            if (p == null || !p.isValid()) return false;
        }
        return true;
    }

    public boolean contains(GeoPos p) {
        return isValid() && GeoUtils.pointInPolygon(p, points);
    }

    public double areaSquareMeters() {
        return isValid() ? GeoUtils.polygonAreaSquareMeters(points) : 0.0;
    }
}
