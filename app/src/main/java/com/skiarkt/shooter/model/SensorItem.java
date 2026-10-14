package com.skiarkt.shooter.model;

import android.os.Parcel;

/** Radar / camera / EO-IR sensor emplacement. */
public class SensorItem extends PointItem {
    public static final float DEFAULT_OPACITY = 0.35f;

    public SensorType type = SensorType.RADAR;
    /** Nominal detection range (meters). */
    public double rangeMeters = 2000.0;
    /** Horizontal field of view (degrees); 360 = omnidirectional. */
    public double beamWidthDeg = 360.0;
    /** Boresight bearing, degrees true (ignored when beamWidthDeg >= 360). */
    public double azimuthDeg = 0.0;
    /** Coverage fill opacity 0 (invisible) .. 1 (opaque). Ring color = {@link #color}. */
    public float opacity = DEFAULT_OPACITY;

    public SensorItem() {
        super("Sensor", PlanColors.SENSOR);
    }

    protected SensorItem(Parcel in) {
        super(in);
        type = SensorType.fromName(in.readString(), SensorType.RADAR);
        rangeMeters = in.readDouble();
        beamWidthDeg = in.readDouble();
        azimuthDeg = in.readDouble();
        opacity = in.readFloat();
    }

    public boolean isOmnidirectional() {
        return beamWidthDeg >= 360.0;
    }

    /** Deep copy (same uid) used as an edit working copy. */
    public SensorItem copy() {
        SensorItem s = new SensorItem();
        copyPointInto(s);
        s.type = type;
        s.rangeMeters = rangeMeters;
        s.beamWidthDeg = beamWidthDeg;
        s.azimuthDeg = azimuthDeg;
        s.opacity = opacity;
        return s;
    }

    @Override
    public ItemKind getKind() {
        return ItemKind.SENSOR;
    }

    @Override
    public boolean isValid() {
        return super.isValid() && rangeMeters > 0 && beamWidthDeg > 0 && beamWidthDeg <= 360.0
                && opacity >= 0f && opacity <= 1f;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        writePoint(dest, flags);
        dest.writeString(type.name());
        dest.writeDouble(rangeMeters);
        dest.writeDouble(beamWidthDeg);
        dest.writeDouble(azimuthDeg);
        dest.writeFloat(opacity);
    }

    public static final Creator<SensorItem> CREATOR = new Creator<SensorItem>() {
        @Override
        public SensorItem createFromParcel(Parcel in) {
            return new SensorItem(in);
        }

        @Override
        public SensorItem[] newArray(int size) {
            return new SensorItem[size];
        }
    };
}
