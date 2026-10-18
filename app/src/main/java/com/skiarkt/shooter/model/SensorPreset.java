package com.skiarkt.shooter.model;

/**
 * Catalog of fielded C-sUAS radars. Values come from public manufacturer data sheets
 * (Leonardo DRS / RADA MHR family and nMHR, 2023) and are nominal: real detection range
 * depends on target RCS, clutter, siting and mode. The planner only uses range and horizontal
 * field of view; minimum range and elevation limits are shown for reference.
 */
public enum SensorPreset {
    RADA_RPS42("RADA RPS-42 (MHR)", "S-band AESA pulse-Doppler",
            90.0, 150.0, -10.0, 70.0,
            new String[] {"Group 1-2 micro / mini UAS", "Aircraft / large air targets"},
            new double[] {10000.0, 25000.0}),
    RADA_RPS92_NMHR("RADA RPS-92 / nMHR", "X-band AESA pulse-Doppler",
            90.0, 0.0, 0.0, 90.0,
            new String[] {"Nano UAV", "Medium-size UAV"},
            new double[] {8000.0, 36000.0});

    /** Panels for a hemispheric (360 deg) installation. */
    public static final int FULL_PANELS = 4;

    public final String displayName;
    public final String description;
    public final double panelAzimuthDeg;
    /** 0 = not published. */
    public final double minRangeMeters;
    public final double elevationMinDeg;
    public final double elevationMaxDeg;
    private final String[] targetClasses;
    private final double[] rangesMeters;

    SensorPreset(String displayName, String description, double panelAzimuthDeg,
            double minRangeMeters, double elevationMinDeg, double elevationMaxDeg,
            String[] targetClasses, double[] rangesMeters) {
        this.displayName = displayName;
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

    /** Short model tag used in auto-generated labels, e.g. "RPS-42". */
    public String shortName() {
        return this == RADA_RPS42 ? "RPS-42" : "nMHR";
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
