package com.btlrckt.bottlerocket.model;

import java.util.Arrays;
import java.util.Locale;

/**
 * R10 editable sensor preset: plain value object with the same data as {@link SensorPreset}, plus
 * an {@link #id} (built-in enum name, or "custom-..." for user-added presets). Gson-serialisable.
 */
public class SensorPresetDef {
    public static final String CUSTOM_PREFIX = "custom-";

    public String id;
    public String displayName;
    public String shortName;
    public String description;
    public double panelAzimuthDeg = 90.0;
    /** 0 = not published. */
    public double minRangeMeters;
    /** NaN = only the span ({@link #elevationMaxDeg}) is known. */
    public double elevationMinDeg = Double.NaN;
    public double elevationMaxDeg = Double.NaN;
    public String[] targetClasses = new String[0];
    public double[] rangesMeters = new double[0];

    public SensorPresetDef() {
    }

    /** Copies the built-in enum values (id = enum name). */
    public static SensorPresetDef fromEnum(SensorPreset p) {
        SensorPresetDef d = new SensorPresetDef();
        d.id = p.name();
        d.displayName = p.displayName;
        d.shortName = p.shortName();
        d.description = p.description;
        d.panelAzimuthDeg = p.panelAzimuthDeg;
        d.minRangeMeters = p.minRangeMeters;
        d.elevationMinDeg = p.elevationMinDeg;
        d.elevationMaxDeg = p.elevationMaxDeg;
        int n = p.targetClassCount();
        d.targetClasses = new String[n];
        d.rangesMeters = new double[n];
        for (int i = 0; i < n; i++) {
            d.targetClasses[i] = p.targetClass(i);
            d.rangesMeters[i] = p.rangeMeters(i);
        }
        return d;
    }

    public SensorPresetDef copy() {
        SensorPresetDef d = new SensorPresetDef();
        d.id = id;
        d.displayName = displayName;
        d.shortName = shortName;
        d.description = description;
        d.panelAzimuthDeg = panelAzimuthDeg;
        d.minRangeMeters = minRangeMeters;
        d.elevationMinDeg = elevationMinDeg;
        d.elevationMaxDeg = elevationMaxDeg;
        d.targetClasses = targetClasses == null ? new String[0] : targetClasses.clone();
        d.rangesMeters = rangesMeters == null ? new double[0] : rangesMeters.clone();
        return d;
    }

    public boolean isBuiltIn() {
        if (id == null) return false;
        for (SensorPreset p : SensorPreset.values()) if (p.name().equals(id)) return true;
        return false;
    }

    /** Usable in the picker: name, panel azimuth and at least one positive range. */
    public boolean isValid() {
        if (id == null || displayName == null || displayName.trim().isEmpty()) return false;
        if (!(panelAzimuthDeg > 0 && panelAzimuthDeg <= 360) || minRangeMeters < 0) return false;
        if (targetClasses == null || rangesMeters == null || targetClasses.length != rangesMeters.length
                || rangesMeters.length == 0) return false;
        for (double r : rangesMeters) if (!(r > 0) || r <= minRangeMeters) return false;
        return Double.isNaN(elevationMinDeg) || Double.isNaN(elevationMaxDeg) || elevationMinDeg <= elevationMaxDeg;
    }

    public int targetClassCount() {
        return rangesMeters == null ? 0 : rangesMeters.length;
    }

    public String targetClass(int i) {
        return targetClasses[i];
    }

    public double rangeMeters(int i) {
        return rangesMeters[i];
    }

    public String shortName() {
        return shortName == null || shortName.trim().isEmpty() ? "Sensor" : shortName.trim();
    }

    /** Horizontal FOV for 1..4 panels. */
    public double beamWidthDeg(int panels) {
        int p = Math.max(1, Math.min(SensorPreset.FULL_PANELS, panels));
        return Math.min(360.0, p * panelAzimuthDeg);
    }

    public int panelsFor360() {
        return Math.max(1, Math.min(SensorPreset.FULL_PANELS, (int) Math.ceil(360.0 / panelAzimuthDeg - 1e-9)));
    }

    public String elevationText() {
        if (Double.isNaN(elevationMaxDeg)) return "n/a";
        if (Double.isNaN(elevationMinDeg)) return String.format(Locale.US, "%.0f\u00B0 span", elevationMaxDeg);
        return String.format(Locale.US, "%+.0f\u00B0 to %+.0f\u00B0", elevationMinDeg, elevationMaxDeg);
    }

    /** Radar type, range for the target class, FOV for {@code panels}, min range and elevation. */
    public void applyTo(SensorItem s, int targetIdx, int panels) {
        int i = Math.max(0, Math.min(rangesMeters.length - 1, targetIdx));
        s.type = SensorType.RADAR;
        s.rangeMeters = rangesMeters[i];
        s.beamWidthDeg = beamWidthDeg(panels);
        s.minRangeMeters = Math.max(0, minRangeMeters);
        s.elevationMinDeg = elevationMinDeg;
        s.elevationMaxDeg = elevationMaxDeg;
    }

    /** Field-by-field equality (used by tests and "is overridden" checks). */
    public boolean sameContentAs(SensorPresetDef o) {
        return o != null && eq(id, o.id) && eq(displayName, o.displayName) && eq(shortName, o.shortName)
                && eq(description, o.description) && Double.compare(panelAzimuthDeg, o.panelAzimuthDeg) == 0
                && Double.compare(minRangeMeters, o.minRangeMeters) == 0
                && Double.compare(elevationMinDeg, o.elevationMinDeg) == 0
                && Double.compare(elevationMaxDeg, o.elevationMaxDeg) == 0
                && Arrays.equals(targetClasses, o.targetClasses) && Arrays.equals(rangesMeters, o.rangesMeters);
    }

    private static boolean eq(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }
}
