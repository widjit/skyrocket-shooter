package com.skiarkt.shooter.model;

import android.os.Parcel;

/** Base for single-location emplacements (sensors and effectors). */
public abstract class PointItem extends BasePlanItem {
    public GeoPos position = new GeoPos();
    /** Emplacement height above ground level (meters). */
    public double aglMeters = 2.0;
    /** Maximum engagement / detection distance (meters). */
    public double maxEngagementDistMeters = 1000.0;
    /**
     * R0 site grouping: uid of the site this emplacement belongs to (null = standalone).
     * Members of a site share a ring overlay and can be moved together.
     */
    public String siteUid;

    protected PointItem(String labelPrefix, int defaultColor) {
        super(labelPrefix, defaultColor);
    }

    protected PointItem(Parcel in) {
        super(in);
        position = in.readParcelable(GeoPos.class.getClassLoader());
        aglMeters = in.readDouble();
        maxEngagementDistMeters = in.readDouble();
        siteUid = in.readString();
    }

    public boolean hasSite() {
        return siteUid != null && !siteUid.trim().isEmpty();
    }

    /** Normalizes a blank site uid to null. */
    public static String normalizeSiteUid(String s) {
        return s == null || s.trim().isEmpty() ? null : s.trim();
    }

    protected void writePoint(Parcel dest, int flags) {
        writeBase(dest);
        dest.writeParcelable(position, flags);
        dest.writeDouble(aglMeters);
        dest.writeDouble(maxEngagementDistMeters);
        dest.writeString(siteUid);
    }

    /** Copies base + point fields (deep-copying the position) into {@code dst}. */
    protected void copyPointInto(PointItem dst) {
        dst.uid = uid;
        dst.label = label;
        dst.color = color;
        dst.remarks = remarks;
        dst.createdMillis = createdMillis;
        dst.modifiedMillis = modifiedMillis;
        dst.position = position == null ? new GeoPos() : new GeoPos(position);
        dst.aglMeters = aglMeters;
        dst.maxEngagementDistMeters = maxEngagementDistMeters;
        dst.siteUid = siteUid;
    }

    @Override
    public GeoPos getAnchor() {
        return position;
    }

    @Override
    public boolean isValid() {
        return position != null && position.isValid() && aglMeters >= 0 && maxEngagementDistMeters > 0;
    }
}
