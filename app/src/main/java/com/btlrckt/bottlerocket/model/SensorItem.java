package com.btlrckt.bottlerocket.model;

import android.os.Parcel;

import java.util.ArrayList;
import java.util.List;

/** Radar / camera / EO-IR / IR-thermal / laser designator sensor emplacement. */
public class SensorItem extends PointItem {
    public static final float DEFAULT_OPACITY = 0.35f;
    /** Standard NATO PRF code default for laser designators. */
    public static final int DEFAULT_LASER_PRF_CODE = 1688;
    public static final double DEFAULT_BEAM_DIVERGENCE_MRAD = 0.5;

    public SensorType type = SensorType.RADAR;
    /** Nominal detection range (meters). */
    public double rangeMeters = 2000.0;
    /** Horizontal field of view (degrees); 360 = omnidirectional. */
    public double beamWidthDeg = 360.0;
    /** Boresight bearing, degrees true (ignored when beamWidthDeg >= 360). */
    public double azimuthDeg = 0.0;
    /** Coverage fill opacity 0 (invisible) .. 1 (opaque). Ring color = {@link #color}. */
    public float opacity = DEFAULT_OPACITY;
    /** R0b: other sensors this sensor cues / is fused with (uids). */
    public List<String> linkedSensorUids = new ArrayList<>();
    /** R2: radar blind inner radius (meters); 0 = none. Drawn as a dashed ring. */
    public double minRangeMeters = 0.0;
    /** R2: elevation coverage limits (deg); NaN = unknown / only the span is known. */
    public double elevationMinDeg = Double.NaN;
    public double elevationMaxDeg = Double.NaN;
    /** R2: day / night capability (camera, EO/IR, IR). */
    public DayNight dayNight = DayNight.DAY_NIGHT;
    /** R2 laser designator: max lasing range (meters); 0 = use the nominal range. */
    public double maxLasingRangeMeters = 0.0;
    /** R2 laser designator: PRF code (e.g. 1688). */
    public int laserPrfCode = DEFAULT_LASER_PRF_CODE;
    /** R2 laser designator: beam divergence (mrad). */
    public double beamDivergenceMrad = DEFAULT_BEAM_DIVERGENCE_MRAD;

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
        in.readStringList(linkedSensorUids);
        // R2 fields (appended at the end of the parcel).
        minRangeMeters = in.readDouble();
        elevationMinDeg = in.readDouble();
        elevationMaxDeg = in.readDouble();
        dayNight = DayNight.fromName(in.readString(), DayNight.DAY_NIGHT);
        maxLasingRangeMeters = in.readDouble();
        laserPrfCode = in.readInt();
        beamDivergenceMrad = in.readDouble();
    }

    public void ensureLists() {
        if (linkedSensorUids == null) linkedSensorUids = new ArrayList<>();
        if (dayNight == null) dayNight = DayNight.DAY_NIGHT;
    }

    /** True when a blind inner ring (min range) should be drawn. */
    public boolean hasMinRange() {
        return minRangeMeters > 0.5 && minRangeMeters < rangeMeters;
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
        s.linkedSensorUids = linkedSensorUids == null ? new ArrayList<String>()
                : new ArrayList<>(linkedSensorUids);
        s.minRangeMeters = minRangeMeters;
        s.elevationMinDeg = elevationMinDeg;
        s.elevationMaxDeg = elevationMaxDeg;
        s.dayNight = dayNight == null ? DayNight.DAY_NIGHT : dayNight;
        s.maxLasingRangeMeters = maxLasingRangeMeters;
        s.laserPrfCode = laserPrfCode;
        s.beamDivergenceMrad = beamDivergenceMrad;
        return s;
    }

    @Override
    public ItemKind getKind() {
        return ItemKind.SENSOR;
    }

    @Override
    public boolean isValid() {
        return super.isValid() && rangeMeters > 0 && beamWidthDeg > 0 && beamWidthDeg <= 360.0
                && opacity >= 0f && opacity <= 1f
                && minRangeMeters >= 0 && minRangeMeters < rangeMeters
                && (Double.isNaN(elevationMinDeg) || Double.isNaN(elevationMaxDeg) || elevationMinDeg <= elevationMaxDeg)
                && maxLasingRangeMeters >= 0 && laserPrfCode >= 0
                && beamDivergenceMrad >= 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        writePoint(dest, flags);
        dest.writeString(type.name());
        dest.writeDouble(rangeMeters);
        dest.writeDouble(beamWidthDeg);
        dest.writeDouble(azimuthDeg);
        dest.writeFloat(opacity);
        ensureLists();
        dest.writeStringList(linkedSensorUids);
        dest.writeDouble(minRangeMeters);
        dest.writeDouble(elevationMinDeg);
        dest.writeDouble(elevationMaxDeg);
        dest.writeString(dayNight.name());
        dest.writeDouble(maxLasingRangeMeters);
        dest.writeInt(laserPrfCode);
        dest.writeDouble(beamDivergenceMrad);
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
