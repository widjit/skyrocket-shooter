package com.skiarkt.shooter.model;

import android.os.Parcel;

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
