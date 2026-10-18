package com.skiarkt.shooter.model;

/**
 * Catalog of fielded C-sUAS radars. Values come from public manufacturer data (Leonardo DRS /
 * RADA MHR family and nMHR data sheets; Echodyne EchoShield published tracking ranges, which is
 * the primary radar of Trust Automation SUADS) and are nominal: real detection range
 * depends on target RCS, clutter, siting and mode. The planner only uses range and horizontal
 * field of view; minimum range and elevation limits are shown for reference.
 */
public enum SensorPreset {
    RADA_RPS42("RADA RPS-42 (MHR)", "RPS-42", "S-band AESA pulse-Doppler",
            90.0, 150.0, -10.0, 70.0,
            new String[] {"Group 1-2 micro / mini UAS", "Aircraft / large air targets"},
            new double[] {10000.0, 25000.0}),
    RADA_RPS92_NMHR("RADA RPS-92 / nMHR", "nMHR", "X-band AESA pulse-Doppler",
            90.0, 0.0, Double.NaN, 90.0,
            new String[] {"Nano UAV", "Medium-size UAV"},
            new double[] {8000.0, 36000.0}),
    /**
     * Trust Automation SUADS (RD-/FS-/EX-SUADS) modelled by its Echodyne EchoShield radar panels
     * (Ku-band MESA, 120 x 80 deg each, up to 4 per site). Ranges are the low end of Echodyne's
     * published average tracking ranges per UAS group (conservative).
     */
    TRUST_SUADS("Trust Automation SUADS (EchoShield)", "SUADS", "Ku-band MESA radar, multi-sensor C-sUAS",
            120.0, 0.0, Double.NaN, 80.0,
            new String[] {"Group 1 UAS", "Group 2 UAS", "Group 3 UAS"},
            new double[] {2700.0, 4800.0, 6400.0});

    /** Max panels offered in the picker. */
    public static final int FULL_PANELS = 4;

    public final String displayName;
    private final String shortName;
    public final String description;
    public final double panelAzimuthDeg;
    /** 0 = not published. */
    public final double minRangeMeters;
    /** NaN = only the total span ({@link #elevationMaxDeg}) is published. */
    public final double elevationMinDeg;
    public final double elevationMaxDeg;
    private final String[] targetClasses;
    private final double[] rangesMeters;

    SensorPreset(String displayName, String shortName, String description, double panelAzimuthDeg,
            double minRangeMeters, double elevationMinDeg, double elevationMaxDeg,
            String[] targetClasses, double[] rangesMeters) {
        this.displayName = displayName;
        this.shortName = shortName;
        this.description = description;
        this.panelAzimuthDeg = panelAzimuthDeg;
        this.minRangeMeters = minRangeMeters;
        this.elevationMinDeg = elevationMinDeg;
        this.elevationMaxDeg = elevationMaxDeg;
        this.targetClasses = targetClasses;
        this.rangesMeters = rangesMeters;
    }

    public int targetClassCount() {
        return rangesMeters.length;
    }

    public String targetClass(int i) {
        return targetClasses[i];
    }

    public double rangeMeters(int i) {
        return rangesMeters[i];
    }

    /** Horizontal FOV for 1..4 panels (4 = 360 omni). */
    public double beamWidthDeg(int panels) {
        int p = Math.max(1, Math.min(FULL_PANELS, panels));
        return Math.min(360.0, p * panelAzimuthDeg);
    }

    /** Panels needed for 360 deg (extra panels add no horizontal coverage). */
    public int panelsFor360() {
        return Math.min(FULL_PANELS, (int) Math.ceil(360.0 / panelAzimuthDeg - 1e-9));
    }

    /** Short model tag used in auto-generated labels, e.g. "RPS-42". */
    public String shortName() {
        return shortName;
    }

    /** "-10° to +70°" or "80° span" when only the span is published. */
    public String elevationText() {
        if (Double.isNaN(elevationMinDeg)) {
            return String.format(java.util.Locale.US, "%.0f\u00B0 span", elevationMaxDeg);
        }
        return String.format(java.util.Locale.US, "%+.0f\u00B0 to %+.0f\u00B0", elevationMinDeg, elevationMaxDeg);
    }

    /**
     * Applies this radar to a sensor: type RADAR, range for target class {@code targetIdx},
     * horizontal FOV for {@code panels}. Azimuth, position, label, color are left unchanged.
     */
    public void applyTo(SensorItem s, int targetIdx, int panels) {
        int i = Math.max(0, Math.min(rangesMeters.length - 1, targetIdx));
        s.type = SensorType.RADAR;
        s.rangeMeters = rangesMeters[i];
        s.beamWidthDeg = beamWidthDeg(panels);
    }
}
