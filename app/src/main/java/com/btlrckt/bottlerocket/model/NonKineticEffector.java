package com.btlrckt.bottlerocket.model;

import android.os.Parcel;

import java.util.ArrayList;
import java.util.List;

/**
 * RF jammer / non-kinetic defeat system. The heat map uses a three-stop gradient:
 * {@link #heatmapColor} (effective, rx >= threshold), {@link #marginalColor} (within the
 * marginal band below threshold) and {@link #insufficientColor}.
 */
public class NonKineticEffector extends PointItem {
    /** Minimum received jamming power at the threat considered effective (dBm). */
    public double rfThresholdDbm = -70.0;
    /** When true the global Settings threshold is used instead of {@link #rfThresholdDbm}. */
    public boolean useGlobalThreshold = true;
    /** "Effective" heat map color. */
    public int heatmapColor = PlanColors.RF_EFFECTIVE;
    public int marginalColor = PlanColors.RF_MARGINAL;
    public int insufficientColor = PlanColors.RF_INSUFFICIENT;
    /** Heat map transparency 0 (opaque) .. 1 (invisible). */
    public float transparency = 0.5f;
    /** Jammer centre frequency (MHz); ignored when {@link #useGlobalFrequency}. */
    public double frequencyMhz = 2400.0;
    /** When true the global Settings drone frequency is used. */
    public boolean useGlobalFrequency = true;
    /** RF output power / EIRP (dBm). */
    public double eirpDbm = 47.0;
    /** R0b: sensors that cue this effector (uids). */
    public List<String> linkedSensorUids = new ArrayList<>();
    /**
     * R1 multi-band jamming. Empty = legacy single band ({@link #frequencyMhz}, {@link #eirpDbm},
     * threshold); otherwise these bands replace the single-band values for analysis.
     */
    public List<RfBand> bands = new ArrayList<>();
    /** How enabled bands are combined into one heat map (null = UNION). */
    public RfHeatmapMode heatmapMode = RfHeatmapMode.UNION;

    public NonKineticEffector() {
        super("RF Effector", PlanColors.NKE_HEATMAP);
    }

    protected NonKineticEffector(Parcel in) {
        super(in);
        rfThresholdDbm = in.readDouble();
        heatmapColor = in.readInt();
        transparency = in.readFloat();
        frequencyMhz = in.readDouble();
        eirpDbm = in.readDouble();
        useGlobalThreshold = in.readInt() != 0;
        useGlobalFrequency = in.readInt() != 0;
        marginalColor = in.readInt();
        insufficientColor = in.readInt();
        in.readStringList(linkedSensorUids);
        bands = RfBand.decodeList(in.readString());
        heatmapMode = RfHeatmapMode.parse(in.readString());
    }

    public void ensureLists() {
        if (linkedSensorUids == null) linkedSensorUids = new ArrayList<>();
        if (bands == null) bands = new ArrayList<>();
        if (heatmapMode == null) heatmapMode = RfHeatmapMode.UNION;
    }

    public boolean isMultiBand() {
        return bands != null && !bands.isEmpty();
    }

    public RfHeatmapMode getHeatmapMode() {
        return heatmapMode == null ? RfHeatmapMode.UNION : heatmapMode;
    }

    /** Enabled, valid bands (empty for legacy single-band effectors). */
    public List<RfBand> enabledBands() {
        List<RfBand> out = new ArrayList<>();
        if (bands == null) return out;
        for (RfBand b : bands) if (b != null && b.enabled && b.isValid()) out.add(b);
        return out;
    }

    /** Replaces the band list with a drone-class preset using current single-band defaults. */
    public void applyPreset(DroneClassPreset preset, double globalThresholdDbm) {
        bands = preset.bands(eirpDbm, effectiveThresholdDbm(globalThresholdDbm), 0.0);
    }

    public double effectiveThresholdDbm(double globalThresholdDbm) {
        return useGlobalThreshold ? globalThresholdDbm : rfThresholdDbm;
    }

    public double effectiveFrequencyMhz(double globalFrequencyMhz) {
        return useGlobalFrequency || frequencyMhz <= 0 ? globalFrequencyMhz : frequencyMhz;
    }

    /** Per-effector heat map opacity (1 - transparency). */
    public float getOpacity() {
        return 1f - transparency;
    }

    public void setOpacity(float opacity) {
        transparency = 1f - Math.max(0f, Math.min(1f, opacity));
    }

    /** Deep copy (same uid) used as an edit working copy. */
    public NonKineticEffector copy() {
        NonKineticEffector n = new NonKineticEffector();
        copyPointInto(n);
        n.rfThresholdDbm = rfThresholdDbm;
        n.useGlobalThreshold = useGlobalThreshold;
        n.heatmapColor = heatmapColor;
        n.marginalColor = marginalColor;
        n.insufficientColor = insufficientColor;
        n.transparency = transparency;
        n.frequencyMhz = frequencyMhz;
        n.useGlobalFrequency = useGlobalFrequency;
        n.eirpDbm = eirpDbm;
        n.linkedSensorUids = linkedSensorUids == null ? new ArrayList<String>()
                : new ArrayList<>(linkedSensorUids);
        n.bands = new ArrayList<>();
        if (bands != null) for (RfBand b : bands) if (b != null) n.bands.add(b.copy());
        n.heatmapMode = getHeatmapMode();
        return n;
    }

    @Override
    public ItemKind getKind() {
        return ItemKind.NON_KINETIC_EFFECTOR;
    }

    @Override
    public boolean isValid() {
        return super.isValid() && transparency >= 0f && transparency <= 1f && frequencyMhz > 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        writePoint(dest, flags);
        dest.writeDouble(rfThresholdDbm);
        dest.writeInt(heatmapColor);
        dest.writeFloat(transparency);
        dest.writeDouble(frequencyMhz);
        dest.writeDouble(eirpDbm);
        dest.writeInt(useGlobalThreshold ? 1 : 0);
        dest.writeInt(useGlobalFrequency ? 1 : 0);
        dest.writeInt(marginalColor);
        dest.writeInt(insufficientColor);
        ensureLists();
        dest.writeStringList(linkedSensorUids);
        dest.writeString(RfBand.encodeList(bands));
        dest.writeString(getHeatmapMode().name());
    }

    public static final Creator<NonKineticEffector> CREATOR = new Creator<NonKineticEffector>() {
        @Override
        public NonKineticEffector createFromParcel(Parcel in) {
            return new NonKineticEffector(in);
        }

        @Override
        public NonKineticEffector[] newArray(int size) {
            return new NonKineticEffector[size];
        }
    };
}
