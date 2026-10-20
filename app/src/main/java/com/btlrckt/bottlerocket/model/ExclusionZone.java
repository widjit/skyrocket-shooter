package com.btlrckt.bottlerocket.model;

import android.os.Parcel;

import com.btlrckt.bottlerocket.util.GeoUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Building / structure exclusion zone: a footprint polygon extruded upward to
 * {@link #heightAglMeters}. No effector may engage through this volume.
 *
 * Styling: {@link #color} (inherited) is the perimeter stroke color; the fill uses
 * {@link #fillColor}'s RGB with {@link #fillOpacity} as alpha.
 */
public class ExclusionZone extends PolygonItem {
    public static final double DEFAULT_HEIGHT_AGL_M = 10.0;
    public static final float DEFAULT_FILL_OPACITY = 0.3f;
    public static final double DEFAULT_STROKE_WIDTH = 3.0;
    public static final double MIN_STROKE_WIDTH = 1.0;
    public static final double MAX_STROKE_WIDTH = 12.0;

    /** Upward extent above ground level (meters). */
    public double heightAglMeters = DEFAULT_HEIGHT_AGL_M;
    /** Fill RGB (alpha channel ignored; see {@link #fillOpacity}). */
    public int fillColor = PlanColors.EXCLUSION_ZONE;
    /** Fill opacity 0 (invisible) .. 1 (opaque). */
    public float fillOpacity = DEFAULT_FILL_OPACITY;
    /** Perimeter stroke width in display pixels. */
    public double strokeWidth = DEFAULT_STROKE_WIDTH;
    /** Dashed (true) or solid (false) perimeter. */
    public boolean strokeDashed = true;

    // ---- R3 parametric shapes (points are regenerated from these for CIRCLE / RECTANGLE) ----
    public static final int CIRCLE_SEGMENTS = 64;
    /** Footprint shape; null (legacy Gson) means {@link ExclusionZoneShape#POLYGON}. */
    public ExclusionZoneShape shape = ExclusionZoneShape.POLYGON;
    /** Circle / rectangle center. */
    public GeoPos center;
    /** Circle radius (meters). */
    public double radiusMeters;
    /** Rectangle east-west size before rotation (meters). */
    public double rectWidthMeters;
    /** Rectangle north-south size before rotation (meters). Not the AGL height. */
    public double rectLengthMeters;
    /** Rectangle rotation, degrees clockwise from true north. */
    public double rotationDeg;

    public ExclusionZone() {
        super("Exclusion Zone", PlanColors.EXCLUSION_ZONE);
    }

    protected ExclusionZone(Parcel in) {
        super(in);
        heightAglMeters = in.readDouble();
        fillColor = in.readInt();
        fillOpacity = in.readFloat();
        strokeWidth = in.readDouble();
        strokeDashed = in.readInt() != 0;
        shape = ExclusionZoneShape.parse(in.readString());
        center = in.readInt() != 0 ? GeoPos.CREATOR.createFromParcel(in) : null;
        radiusMeters = in.readDouble();
        rectWidthMeters = in.readDouble();
        rectLengthMeters = in.readDouble();
        rotationDeg = in.readDouble();
    }

    public ExclusionZoneShape getShape() {
        return shape == null ? ExclusionZoneShape.POLYGON : shape;
    }

    /** True when the shape parameters are complete enough to generate a footprint. */
    public boolean hasShapeParams() {
        switch (getShape()) {
            case CIRCLE:
                return center != null && center.isValid() && radiusMeters > 0;
            case RECTANGLE:
                return center != null && center.isValid() && rectWidthMeters > 0
                        && rectLengthMeters > 0;
            default:
                return false;
        }
    }

    /** Makes this zone a circle and regenerates its points. */
    public void setCircle(GeoPos c, double radiusM) {
        shape = ExclusionZoneShape.CIRCLE;
        center = new GeoPos(c);
        radiusMeters = radiusM;
        regeneratePoints();
    }

    /** Makes this zone a rotated rectangle (center + size + rotation) and regenerates points. */
    public void setRectangle(GeoPos c, double widthM, double lengthM, double rotDeg) {
        shape = ExclusionZoneShape.RECTANGLE;
        center = new GeoPos(c);
        rectWidthMeters = widthM;
        rectLengthMeters = lengthM;
        rotationDeg = rotDeg;
        regeneratePoints();
    }

    /** Axis-aligned (north-up) rectangle from two opposite corners. */
    public void setRectangleFromCorners(GeoPos a, GeoPos b) {
        GeoPos c = new GeoPos((a.lat + b.lat) / 2.0, (a.lon + b.lon) / 2.0);
        double ns = GeoUtils.distanceMeters(new GeoPos(a.lat, c.lon), new GeoPos(b.lat, c.lon));
        double ew = GeoUtils.distanceMeters(new GeoPos(c.lat, a.lon), new GeoPos(c.lat, b.lon));
        setRectangle(c, Math.max(ew, 0.5), Math.max(ns, 0.5), 0.0);
    }

    /**
     * Rebuilds {@link #points} from the shape parameters (circle = {@link #CIRCLE_SEGMENTS}-gon,
     * rectangle = 4 rotated corners). No-op for polygons or incomplete parameters.
     */
    public void regeneratePoints() {
        if (!hasShapeParams()) return;
        List<GeoPos> out = new ArrayList<>();
        if (getShape() == ExclusionZoneShape.CIRCLE) {
            for (int i = 0; i < CIRCLE_SEGMENTS; i++) {
                out.add(GeoUtils.destination(center, 360.0 * i / CIRCLE_SEGMENTS, radiusMeters));
            }
        } else {
            double hw = rectWidthMeters / 2.0, hl = rectLengthMeters / 2.0;
            double[][] corners = {{-hw, hl}, {hw, hl}, {hw, -hl}, {-hw, -hl}};
            for (double[] c : corners) {
                double dist = Math.hypot(c[0], c[1]);
                double brg = Math.toDegrees(Math.atan2(c[0], c[1])) + rotationDeg;
                out.add(GeoUtils.destination(center, GeoUtils.normalizeDeg(brg), dist));
            }
        }
        points = out;
    }

    /** Reverts to a free polygon, keeping the current vertices. */
    public void convertToPolygon() {
        shape = ExclusionZoneShape.POLYGON;
    }

    /** Perimeter stroke color (alias for {@link #color}). */
    public int getStrokeColor() {
        return color;
    }

    public void setStrokeColor(int argb) {
        color = argb;
    }

    /** Fill color with {@link #fillOpacity} applied as the alpha channel. */
    public int getFillArgb() {
        return PlanColors.withAlpha(fillColor, fillOpacity);
    }

    /** Clamps style values into their valid ranges (used after UI / CoT input). */
    public void normalizeStyle() {
        if (Float.isNaN(fillOpacity)) fillOpacity = DEFAULT_FILL_OPACITY;
        fillOpacity = Math.max(0f, Math.min(1f, fillOpacity));
        if (Double.isNaN(strokeWidth)) strokeWidth = DEFAULT_STROKE_WIDTH;
        strokeWidth = Math.max(MIN_STROKE_WIDTH, Math.min(MAX_STROKE_WIDTH, strokeWidth));
    }

    /** Deep copy (new list of new vertices) for edit-then-save workflows. */
    public ExclusionZone copy() {
        ExclusionZone z = new ExclusionZone();
        z.uid = uid;
        z.label = label;
        z.color = color;
        z.remarks = remarks;
        z.createdMillis = createdMillis;
        z.modifiedMillis = modifiedMillis;
        for (GeoPos p : points) z.points.add(new GeoPos(p));
        z.heightAglMeters = heightAglMeters;
        z.fillColor = fillColor;
        z.fillOpacity = fillOpacity;
        z.strokeWidth = strokeWidth;
        z.strokeDashed = strokeDashed;
        z.shape = getShape();
        z.center = center == null ? null : new GeoPos(center);
        z.radiusMeters = radiusMeters;
        z.rectWidthMeters = rectWidthMeters;
        z.rectLengthMeters = rectLengthMeters;
        z.rotationDeg = rotationDeg;
        return z;
    }

    @Override
    public ItemKind getKind() {
        return ItemKind.EXCLUSION_ZONE;
    }

    @Override
    public boolean isValid() {
        return super.isValid() && heightAglMeters >= 0 && !Double.isNaN(heightAglMeters)
                && !Double.isInfinite(heightAglMeters);
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        writePolygon(dest);
        dest.writeDouble(heightAglMeters);
        dest.writeInt(fillColor);
        dest.writeFloat(fillOpacity);
        dest.writeDouble(strokeWidth);
        dest.writeInt(strokeDashed ? 1 : 0);
        dest.writeString(getShape().name());
        dest.writeInt(center != null ? 1 : 0);
        if (center != null) center.writeToParcel(dest, flags);
        dest.writeDouble(radiusMeters);
        dest.writeDouble(rectWidthMeters);
        dest.writeDouble(rectLengthMeters);
        dest.writeDouble(rotationDeg);
    }

    public static final Creator<ExclusionZone> CREATOR = new Creator<ExclusionZone>() {
        @Override
        public ExclusionZone createFromParcel(Parcel in) {
            return new ExclusionZone(in);
        }

        @Override
        public ExclusionZone[] newArray(int size) {
            return new ExclusionZone[size];
        }
    };
}
