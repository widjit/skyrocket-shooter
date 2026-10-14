package com.skiarkt.shooter.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/** Root aggregate: a complete base / point defense plan. */
public class PointDefensePlan {
    public static final String DEFAULT_CLASSIFICATION = "UNCLASSIFIED";

    public String uid = "skiarkt-plan-" + UUID.randomUUID();
    public String name = "Untitled Point Defense Plan";
    public String author = "";
    public String classification = DEFAULT_CLASSIFICATION;
    public String description = "";
    public long createdMillis = System.currentTimeMillis();
    public long modifiedMillis = createdMillis;
    public int schemaVersion = 1;

    public List<ExclusionZone> exclusionZones = new ArrayList<>();
    public List<SensorItem> sensors = new ArrayList<>();
    public List<NonKineticEffector> nonKineticEffectors = new ArrayList<>();
    public List<KineticEffector> kineticEffectors = new ArrayList<>();
    public List<DefendedAsset> defendedAssets = new ArrayList<>();
    public List<ThreatCorridor> threatCorridors = new ArrayList<>();

    /** Live (mutable) list for a given kind. */
    @SuppressWarnings("unchecked")
    public <T extends BasePlanItem> List<T> listFor(ItemKind kind) {
        switch (kind) {
            case EXCLUSION_ZONE: return (List<T>) exclusionZones;
            case SENSOR: return (List<T>) sensors;
            case NON_KINETIC_EFFECTOR: return (List<T>) nonKineticEffectors;
            case KINETIC_EFFECTOR: return (List<T>) kineticEffectors;
            case DEFENDED_ASSET: return (List<T>) defendedAssets;
            case THREAT_CORRIDOR: return (List<T>) threatCorridors;
            default: return Collections.emptyList();
        }
    }

    public List<BasePlanItem> getAllItems() {
        List<BasePlanItem> all = new ArrayList<>();
        for (ItemKind k : ItemKind.values()) {
            all.addAll(listFor(k));
        }
        return all;
    }

    public BasePlanItem findByUid(String itemUid) {
        if (itemUid == null) return null;
        for (BasePlanItem i : getAllItems()) {
            if (itemUid.equals(i.uid)) return i;
        }
        return null;
    }

    public int count(ItemKind kind) {
        return listFor(kind).size();
    }

    public int totalItems() {
        int n = 0;
        for (ItemKind k : ItemKind.values()) n += count(k);
        return n;
    }

    /** Gson leaves lists null when a field is absent in older files; normalise. */
    public void ensureLists() {
        if (exclusionZones == null) exclusionZones = new ArrayList<>();
        if (sensors == null) sensors = new ArrayList<>();
        if (nonKineticEffectors == null) nonKineticEffectors = new ArrayList<>();
        if (kineticEffectors == null) kineticEffectors = new ArrayList<>();
        if (defendedAssets == null) defendedAssets = new ArrayList<>();
        if (threatCorridors == null) threatCorridors = new ArrayList<>();
        if (classification == null) classification = DEFAULT_CLASSIFICATION;
    }

    public void touch() {
        modifiedMillis = System.currentTimeMillis();
    }
}
