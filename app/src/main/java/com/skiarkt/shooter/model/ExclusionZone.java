package com.skiarkt.shooter.model;

import android.os.Parcel;

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
