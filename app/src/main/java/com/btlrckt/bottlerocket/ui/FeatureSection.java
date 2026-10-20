package com.btlrckt.bottlerocket.ui;

import com.btlrckt.bottlerocket.R;
import com.btlrckt.bottlerocket.model.ItemKind;

/** Navigation entries in the main drop-down, with the roadmap phase that implements them. */
public enum FeatureSection {
    EXCLUSION_ZONES(R.string.section_exclusion_zones, R.string.desc_exclusion_zones, 2, ItemKind.EXCLUSION_ZONE),
    SENSORS(R.string.section_sensors, R.string.desc_sensors, 3, ItemKind.SENSOR),
    NON_KINETIC(R.string.section_non_kinetic, R.string.desc_non_kinetic, 3, ItemKind.NON_KINETIC_EFFECTOR),
    KINETIC(R.string.section_kinetic, R.string.desc_kinetic, 4, ItemKind.KINETIC_EFFECTOR),
    DEFENDED_ASSETS(R.string.section_defended_assets, R.string.desc_defended_assets, 2, ItemKind.DEFENDED_ASSET),
    AUTHORITY(R.string.section_authority, R.string.desc_authority, 5, ItemKind.AUTHORITY_ZONE),
    THREAT_CORRIDORS(R.string.section_threat_corridors, R.string.desc_threat_corridors, 4, ItemKind.THREAT_CORRIDOR),
    GAP_ANALYSIS(R.string.section_gap_analysis, R.string.desc_gap_analysis, 4, null),
    TIMELINE(R.string.section_timeline, R.string.desc_timeline, 5, null),
    MSA(R.string.section_msa, R.string.desc_msa, 5, null),
    PACE_PLAN(R.string.section_pace, R.string.desc_pace, 6, null),
    SETTINGS(R.string.section_settings, R.string.desc_settings, 1, null);

    public final int titleRes;
    public final int descriptionRes;
    public final int phase;
    /** Item kind managed by this section, or null for analysis / tool sections. */
    public final ItemKind kind;

    FeatureSection(int titleRes, int descriptionRes, int phase, ItemKind kind) {
        this.titleRes = titleRes;
        this.descriptionRes = descriptionRes;
        this.phase = phase;
        this.kind = kind;
    }
}
