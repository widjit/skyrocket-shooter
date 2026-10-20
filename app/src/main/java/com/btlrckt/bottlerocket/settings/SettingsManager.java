package com.btlrckt.bottlerocket.settings;

import android.content.Context;
import android.content.SharedPreferences;

import com.btlrckt.bottlerocket.model.PlanColors;
import com.btlrckt.bottlerocket.util.UnitConverter;

/**
 * Typed access to BOTTLE ROCKET (BTL RCKT) preferences. Uses a dedicated SharedPreferences
 * file on the host (ATAK) context so values survive plugin reinstalls.
 */
public class SettingsManager {
    public static final String PREFS_NAME = "btlrckt_prefs";

    public static final String KEY_UNIT_SYSTEM = "unit_system";
    public static final String KEY_PRECISION = "precision_decimals";
    public static final String KEY_HEATMAP_TRANSPARENCY = "heatmap_transparency";
    public static final String KEY_ZONE_FILL_TRANSPARENCY = "zone_fill_transparency";
    public static final String KEY_COT_STALE_DAYS = "cot_stale_days";
    public static final String KEY_DEFAULT_AUTHOR = "default_author";
    public static final String KEY_DEFAULT_CLASSIFICATION = "default_classification";
    public static final String KEY_INTERCEPT_SAMPLE_SPACING = "intercept_sample_spacing_m";
    public static final String KEY_INTERCEPT_SAFETY_BUFFER = "intercept_safety_buffer_m";
    public static final String KEY_INTERCEPT_SHOW_ON_MAP = "intercept_show_on_map";
    public static final String KEY_RF_FREQUENCY_MHZ = "rf_frequency_mhz";
    public static final String KEY_RF_TERRAIN_LOSS_DB = "rf_terrain_loss_db";
    public static final String KEY_RF_THRESHOLD_DBM = "rf_threshold_dbm";
    public static final String KEY_LOS_RANGE_STEP_M = "los_range_step_m";
    public static final String KEY_LOS_ANGULAR_STEP_DEG = "los_angular_step_deg";
    public static final String KEY_LOS_TARGET_AGL_M = "los_target_agl_m";
    private static final String KEY_COLOR_PREFIX = "color_";

    public static final UnitSystem DEFAULT_UNIT_SYSTEM = UnitSystem.METERS;
    public static final int DEFAULT_PRECISION = UnitConverter.TENTHS;
    public static final float DEFAULT_HEATMAP_TRANSPARENCY = 0.5f;
    public static final float DEFAULT_ZONE_FILL_TRANSPARENCY = 0.7f;
    public static final int DEFAULT_COT_STALE_DAYS = 30;
    public static final int MAX_PRECISION = 3;
    /** Intercept altitude calculator: sample every N meters along the ingress line. */
    public static final double DEFAULT_INTERCEPT_SAMPLE_SPACING_M = 10.0;
    public static final double MIN_INTERCEPT_SAMPLE_SPACING_M = 1.0;
    public static final double MAX_INTERCEPT_SAMPLE_SPACING_M = 500.0;
    /** Intercept altitude calculator: clearance added above the highest roof. */
    public static final double DEFAULT_INTERCEPT_SAFETY_BUFFER_M = 10.0;

    // Phase 3: RF propagation + terrain LOS sweep.
    public static final double DEFAULT_RF_FREQUENCY_MHZ = 2400.0;
    public static final double MIN_RF_FREQUENCY_MHZ = 1.0;
    public static final double MAX_RF_FREQUENCY_MHZ = 100000.0;
    public static final double DEFAULT_RF_TERRAIN_LOSS_DB = 40.0;
    public static final double MAX_RF_TERRAIN_LOSS_DB = 200.0;
    public static final double DEFAULT_RF_THRESHOLD_DBM = -70.0;
    public static final double MIN_RF_THRESHOLD_DBM = -200.0;
    public static final double MAX_RF_THRESHOLD_DBM = 100.0;
    public static final double DEFAULT_LOS_RANGE_STEP_M = 50.0;
    public static final double MIN_LOS_RANGE_STEP_M = 5.0;
    public static final double MAX_LOS_RANGE_STEP_M = 1000.0;
    public static final double DEFAULT_LOS_ANGULAR_STEP_DEG = 1.0;
    public static final double MIN_LOS_ANGULAR_STEP_DEG = 0.1;
    public static final double MAX_LOS_ANGULAR_STEP_DEG = 10.0;
    /** Proxy drone altitude used as the LOS target height (sensor coverage + RF heat map). */
    public static final double DEFAULT_LOS_TARGET_AGL_M = 50.0;
    public static final double MAX_LOS_TARGET_AGL_M = 5000.0;

    /** Configurable default colors, one per renderable layer. */
    public enum ColorKey {
        EXCLUSION_ZONE(PlanColors.EXCLUSION_ZONE),
        SENSOR(PlanColors.SENSOR),
        NKE_HEATMAP(PlanColors.NKE_HEATMAP),
        KE_NO_SHOOT(PlanColors.KE_NO_SHOOT),
        KE_FRAG_ZONE(PlanColors.KE_FRAG_ZONE),
        KE_CLEAR_ZONE(PlanColors.KE_CLEAR_ZONE),
        DEFENDED_FILL(PlanColors.DEFENDED_FILL),
        DEFENDED_BORDER(PlanColors.DEFENDED_BORDER),
        THREAT_CORRIDOR(PlanColors.THREAT_CORRIDOR);

        public final int defaultColor;

        ColorKey(int defaultColor) {
            this.defaultColor = defaultColor;
        }
    }

    private final SharedPreferences prefs;

    public SettingsManager(Context hostContext) {
        this(hostContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE));
    }

    public SettingsManager(SharedPreferences prefs) {
        this.prefs = prefs;
    }

    public SharedPreferences getPreferences() {
        return prefs;
    }

    public UnitSystem getUnitSystem() {
        return UnitSystem.fromName(prefs.getString(KEY_UNIT_SYSTEM, null), DEFAULT_UNIT_SYSTEM);
    }

    public void setUnitSystem(UnitSystem unit) {
        prefs.edit().putString(KEY_UNIT_SYSTEM, unit.name()).apply();
    }

    /** Decimal places shown in the UI (1 = tenths). */
    public int getPrecision() {
        return clamp(prefs.getInt(KEY_PRECISION, DEFAULT_PRECISION), 0, MAX_PRECISION);
    }

    public void setPrecision(int decimals) {
        prefs.edit().putInt(KEY_PRECISION, clamp(decimals, 0, MAX_PRECISION)).apply();
    }

    public float getHeatmapTransparency() {
        return clamp01(prefs.getFloat(KEY_HEATMAP_TRANSPARENCY, DEFAULT_HEATMAP_TRANSPARENCY));
    }

    public void setHeatmapTransparency(float t) {
        prefs.edit().putFloat(KEY_HEATMAP_TRANSPARENCY, clamp01(t)).apply();
    }

    public float getZoneFillTransparency() {
        return clamp01(prefs.getFloat(KEY_ZONE_FILL_TRANSPARENCY, DEFAULT_ZONE_FILL_TRANSPARENCY));
    }

    public void setZoneFillTransparency(float t) {
        prefs.edit().putFloat(KEY_ZONE_FILL_TRANSPARENCY, clamp01(t)).apply();
    }

    public int getColor(ColorKey key) {
        return prefs.getInt(KEY_COLOR_PREFIX + key.name(), key.defaultColor);
    }

    public void setColor(ColorKey key, int argb) {
        prefs.edit().putInt(KEY_COLOR_PREFIX + key.name(), argb).apply();
    }

    public int getCotStaleDays() {
        return clamp(prefs.getInt(KEY_COT_STALE_DAYS, DEFAULT_COT_STALE_DAYS), 1, 3650);
    }

    public void setCotStaleDays(int days) {
        prefs.edit().putInt(KEY_COT_STALE_DAYS, clamp(days, 1, 3650)).apply();
    }

    public double getInterceptSampleSpacingMeters() {
        double v = prefs.getFloat(KEY_INTERCEPT_SAMPLE_SPACING, (float) DEFAULT_INTERCEPT_SAMPLE_SPACING_M);
        if (Double.isNaN(v)) v = DEFAULT_INTERCEPT_SAMPLE_SPACING_M;
        return Math.max(MIN_INTERCEPT_SAMPLE_SPACING_M, Math.min(MAX_INTERCEPT_SAMPLE_SPACING_M, v));
    }

    public void setInterceptSampleSpacingMeters(double meters) {
        if (Double.isNaN(meters) || Double.isInfinite(meters)) return;
        double v = Math.max(MIN_INTERCEPT_SAMPLE_SPACING_M, Math.min(MAX_INTERCEPT_SAMPLE_SPACING_M, meters));
        prefs.edit().putFloat(KEY_INTERCEPT_SAMPLE_SPACING, (float) v).apply();
    }

    public double getInterceptSafetyBufferMeters() {
        double v = prefs.getFloat(KEY_INTERCEPT_SAFETY_BUFFER, (float) DEFAULT_INTERCEPT_SAFETY_BUFFER_M);
        return Double.isNaN(v) || v < 0 ? DEFAULT_INTERCEPT_SAFETY_BUFFER_M : v;
    }

    public void setInterceptSafetyBufferMeters(double meters) {
        if (Double.isNaN(meters) || Double.isInfinite(meters) || meters < 0) return;
        prefs.edit().putFloat(KEY_INTERCEPT_SAFETY_BUFFER, (float) meters).apply();
    }

    public boolean isInterceptShownOnMap() {
        return prefs.getBoolean(KEY_INTERCEPT_SHOW_ON_MAP, true);
    }

    public void setInterceptShownOnMap(boolean show) {
        prefs.edit().putBoolean(KEY_INTERCEPT_SHOW_ON_MAP, show).apply();
    }

    // ------------------------------------------------------------ RF / LOS (Phase 3)

    public double getRfFrequencyMhz() {
        return getClamped(KEY_RF_FREQUENCY_MHZ, DEFAULT_RF_FREQUENCY_MHZ, MIN_RF_FREQUENCY_MHZ, MAX_RF_FREQUENCY_MHZ);
    }

    public void setRfFrequencyMhz(double v) {
        putClamped(KEY_RF_FREQUENCY_MHZ, v, MIN_RF_FREQUENCY_MHZ, MAX_RF_FREQUENCY_MHZ);
    }

    public double getRfTerrainLossDb() {
        return getClamped(KEY_RF_TERRAIN_LOSS_DB, DEFAULT_RF_TERRAIN_LOSS_DB, 0, MAX_RF_TERRAIN_LOSS_DB);
    }

    public void setRfTerrainLossDb(double v) {
        putClamped(KEY_RF_TERRAIN_LOSS_DB, v, 0, MAX_RF_TERRAIN_LOSS_DB);
    }

    /** Global "jam threshold" (dBm received at the drone), overridable per effector. */
    public double getRfThresholdDbm() {
        return getClamped(KEY_RF_THRESHOLD_DBM, DEFAULT_RF_THRESHOLD_DBM, MIN_RF_THRESHOLD_DBM, MAX_RF_THRESHOLD_DBM);
    }

    public void setRfThresholdDbm(double v) {
        putClamped(KEY_RF_THRESHOLD_DBM, v, MIN_RF_THRESHOLD_DBM, MAX_RF_THRESHOLD_DBM);
    }

    public double getLosRangeStepMeters() {
        return getClamped(KEY_LOS_RANGE_STEP_M, DEFAULT_LOS_RANGE_STEP_M, MIN_LOS_RANGE_STEP_M, MAX_LOS_RANGE_STEP_M);
    }

    public void setLosRangeStepMeters(double v) {
        putClamped(KEY_LOS_RANGE_STEP_M, v, MIN_LOS_RANGE_STEP_M, MAX_LOS_RANGE_STEP_M);
    }

    public double getLosAngularStepDeg() {
        return getClamped(KEY_LOS_ANGULAR_STEP_DEG, DEFAULT_LOS_ANGULAR_STEP_DEG,
                MIN_LOS_ANGULAR_STEP_DEG, MAX_LOS_ANGULAR_STEP_DEG);
    }

    public void setLosAngularStepDeg(double v) {
        putClamped(KEY_LOS_ANGULAR_STEP_DEG, v, MIN_LOS_ANGULAR_STEP_DEG, MAX_LOS_ANGULAR_STEP_DEG);
    }

    public double getLosTargetAglMeters() {
        return getClamped(KEY_LOS_TARGET_AGL_M, DEFAULT_LOS_TARGET_AGL_M, 0, MAX_LOS_TARGET_AGL_M);
    }

    public void setLosTargetAglMeters(double v) {
        putClamped(KEY_LOS_TARGET_AGL_M, v, 0, MAX_LOS_TARGET_AGL_M);
    }

    /** Global RF heat map opacity 0..1 (stored as transparency for Phase 1 compatibility). */
    public float getHeatmapOpacity() {
        return 1f - getHeatmapTransparency();
    }

    public void setHeatmapOpacity(float opacity) {
        setHeatmapTransparency(1f - clamp01(opacity));
    }

    private double getClamped(String key, double dflt, double lo, double hi) {
        double v = prefs.getFloat(key, (float) dflt);
        if (Double.isNaN(v) || Double.isInfinite(v)) return dflt;
        return Math.max(lo, Math.min(hi, v));
    }

    private void putClamped(String key, double v, double lo, double hi) {
        if (Double.isNaN(v) || Double.isInfinite(v)) return;
        prefs.edit().putFloat(key, (float) Math.max(lo, Math.min(hi, v))).apply();
    }

    /** Converts a meter value to the current display unit, rounded to the current precision. */
    public double toDisplayValue(double meters) {
        return UnitConverter.displayValue(meters, getUnitSystem(), getPrecision());
    }

    /** Plain number (no unit suffix) for pre-filling numeric input fields. */
    public String formatNumber(double meters) {
        if (Double.isNaN(meters)) return "";
        int d = getPrecision();
        return String.format(java.util.Locale.US, "%." + d + "f", toDisplayValue(meters));
    }

    public String getDefaultAuthor() {
        return prefs.getString(KEY_DEFAULT_AUTHOR, "");
    }

    public void setDefaultAuthor(String author) {
        prefs.edit().putString(KEY_DEFAULT_AUTHOR, author == null ? "" : author).apply();
    }

    public String getDefaultClassification() {
        return prefs.getString(KEY_DEFAULT_CLASSIFICATION, "UNCLASSIFIED");
    }

    public void setDefaultClassification(String c) {
        prefs.edit().putString(KEY_DEFAULT_CLASSIFICATION, c == null ? "UNCLASSIFIED" : c).apply();
    }

    /** Formats a meter value using the current unit system and precision. */
    public String formatDistance(double meters) {
        return UnitConverter.format(meters, getUnitSystem(), getPrecision());
    }

    /** Parses user input in the current unit system to meters (NaN on failure). */
    public double parseDistance(String text) {
        return UnitConverter.parseToMeters(text, getUnitSystem());
    }

    public void resetToDefaults() {
        prefs.edit().clear().apply();
    }

    public void registerListener(SharedPreferences.OnSharedPreferenceChangeListener l) {
        prefs.registerOnSharedPreferenceChangeListener(l);
    }

    public void unregisterListener(SharedPreferences.OnSharedPreferenceChangeListener l) {
        prefs.unregisterOnSharedPreferenceChangeListener(l);
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static float clamp01(float v) {
        return Float.isNaN(v) ? 0f : Math.max(0f, Math.min(1f, v));
    }
}
