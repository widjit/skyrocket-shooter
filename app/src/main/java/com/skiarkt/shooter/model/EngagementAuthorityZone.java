package com.skiarkt.shooter.model;

import android.os.Parcel;

import java.util.ArrayList;
import java.util.List;

/**
 * Engagement-authority perimeter (ROADMAP R6): a buffer zone offset outward (positive
 * {@link #bufferMeters}) or inward (negative) from a base perimeter.
 *
 * <p>The base is either a linked plan polygon ({@link #baseUid}: an exclusion zone or defended
 * asset) or the zone's own drawn boundary. {@link #points} always holds the base boundary: for
 * linked zones it is a snapshot refreshed on save, so a zone shared over CoT stays meaningful on
 * a device that does not have the base item. {@link #resolveBase} prefers the live base.
 */
public class EngagementAuthorityZone extends PolygonItem {
    public static final float DEFAULT_FILL_OPACITY = 0.15f;
    /** Inward buffers deeper than this are rejected (avoids degenerate rings). */
    public static final double MAX_ABS_BUFFER_M = 100000.0;

    public AuthorityType authorityType = AuthorityType.KINETIC;
    /** Linked base polygon uid, or null when {@link #points} is a drawn boundary. */
    public String baseUid;
    /** Offset from the base perimeter: positive = outward, negative = inward (meters). */
    public double bufferMeters = 0.0;
    /** Fill RGB (alpha ignored; see {@link #fillOpacity}). */
    public int fillColor = AuthorityType.KINETIC.defaultColor;
    public float fillOpacity = DEFAULT_FILL_OPACITY;
    public boolean strokeDashed = false;

    public EngagementAuthorityZone() {
        super("Authority Zone", AuthorityType.KINETIC.defaultColor);
    }

    protected EngagementAuthorityZone(Parcel in) {
        super(in);
        authorityType = AuthorityType.fromName(in.readString(), AuthorityType.KINETIC);
        baseUid = in.readString();
        bufferMeters = in.readDouble();
        fillColor = in.readInt();
        fillOpacity = in.readFloat();
        strokeDashed = in.readInt() != 0;
    }

    /** Applies the type's default stroke / fill colors. */
    public void applyTypeColors() {
        color = authorityType.defaultColor;
        fillColor = authorityType.defaultColor;
    }

    public int getFillArgb() {
        return PlanColors.withAlpha(fillColor, fillOpacity);
    }

    public void normalize() {
        if (authorityType == null) authorityType = AuthorityType.KINETIC;
        if (Float.isNaN(fillOpacity)) fillOpacity = DEFAULT_FILL_OPACITY;
        fillOpacity = Math.max(0f, Math.min(1f, fillOpacity));
        if (Double.isNaN(bufferMeters) || Double.isInfinite(bufferMeters)) bufferMeters = 0;
        bufferMeters = Math.max(-MAX_ABS_BUFFER_M, Math.min(MAX_ABS_BUFFER_M, bufferMeters));
        if (baseUid != null && baseUid.trim().isEmpty()) baseUid = null;
    }

    /** Live base polygon from {@code plan} when linked and present, otherwise the stored boundary. */
    public List<GeoPos> resolveBase(PointDefensePlan plan) {
        if (baseUid != null && plan != null) {
            BasePlanItem b = plan.findByUid(baseUid);
            if (b instanceof PolygonItem && !(b instanceof EngagementAuthorityZone) && b.isValid()) {
                return ((PolygonItem) b).points;
            }
        }
        return points;
    }

    /** True when linked to a base item that is missing from {@code plan} (snapshot in use). */
    public boolean isBaseMissing(PointDefensePlan plan) {
        return baseUid != null && plan != null && !(plan.findByUid(baseUid) instanceof PolygonItem);
    }

    /** Refreshes the {@link #points} snapshot from the live base. */
    public void syncSnapshot(PointDefensePlan plan) {
        List<GeoPos> live = resolveBase(plan);
        if (live == points || live == null) return;
        List<GeoPos> copy = new ArrayList<>();
        for (GeoPos p : live) copy.add(new GeoPos(p));
        points = copy;
    }

    public EngagementAuthorityZone copy() {
        EngagementAuthorityZone z = new EngagementAuthorityZone();
        z.uid = uid;
        z.label = label;
        z.color = color;
        z.remarks = remarks;
        z.createdMillis = createdMillis;
        z.modifiedMillis = modifiedMillis;
        for (GeoPos p : points) z.points.add(new GeoPos(p));
        z.authorityType = authorityType;
        z.baseUid = baseUid;
        z.bufferMeters = bufferMeters;
        z.fillColor = fillColor;
        z.fillOpacity = fillOpacity;
        z.strokeDashed = strokeDashed;
        return z;
    }

    @Override
    public ItemKind getKind() {
        return ItemKind.AUTHORITY_ZONE;
    }

    @Override
    public boolean isValid() {
        return super.isValid() && authorityType != null && !Double.isNaN(bufferMeters)
                && !Double.isInfinite(bufferMeters);
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        writePolygon(dest);
        dest.writeString(authorityType == null ? null : authorityType.name());
        dest.writeString(baseUid);
        dest.writeDouble(bufferMeters);
        dest.writeInt(fillColor);
        dest.writeFloat(fillOpacity);
        dest.writeInt(strokeDashed ? 1 : 0);
    }

    public static final Creator<EngagementAuthorityZone> CREATOR = new Creator<EngagementAuthorityZone>() {
        @Override
        public EngagementAuthorityZone createFromParcel(Parcel in) {
            return new EngagementAuthorityZone(in);
        }

        @Override
        public EngagementAuthorityZone[] newArray(int size) {
            return new EngagementAuthorityZone[size];
        }
    };
}
