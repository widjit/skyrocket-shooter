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
    public List<EngagementAuthorityZone> authorityZones = new ArrayList<>();

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
            case AUTHORITY_ZONE: return (List<T>) authorityZones;
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
        if (authorityZones == null) authorityZones = new ArrayList<>();
        for (EngagementAuthorityZone z : authorityZones) {
            if (z != null) z.normalize();
        }
        if (classification == null) classification = DEFAULT_CLASSIFICATION;
        for (KineticEffector k : kineticEffectors) {
            if (k != null) k.ensureLists();
        }
        for (SensorItem s : sensors) {
            if (s != null) s.ensureLists();
        }
        for (NonKineticEffector n : nonKineticEffectors) {
            if (n != null) n.ensureLists();
        }
    }

    // ------------------------------------------------------- kinetic effectors

    public KineticEffector findKineticEffector(String effectorUid) {
        if (effectorUid == null || kineticEffectors == null) return null;
        for (KineticEffector k : kineticEffectors) {
            if (k != null && effectorUid.equals(k.uid)) return k;
        }
        return null;
    }

    /** Adds {@code e} (replacing any existing effector with the same uid). */
    public void addKineticEffector(KineticEffector e) {
        if (e == null) return;
        if (kineticEffectors == null) kineticEffectors = new ArrayList<>();
        if (!updateKineticEffector(e)) kineticEffectors.add(e);
        touch();
    }

    /** Replaces the effector with the same uid; returns false when not present. */
    public boolean updateKineticEffector(KineticEffector e) {
        if (e == null || kineticEffectors == null) return false;
        for (int i = 0; i < kineticEffectors.size(); i++) {
            KineticEffector k = kineticEffectors.get(i);
            if (k != null && k.uid != null && k.uid.equals(e.uid)) {
                kineticEffectors.set(i, e);
                touch();
                return true;
            }
        }
        return false;
    }

    public boolean removeKineticEffector(String effectorUid) {
        KineticEffector k = findKineticEffector(effectorUid);
        if (k == null) return false;
        kineticEffectors.remove(k);
        touch();
        return true;
    }

    public void touch() {
        modifiedMillis = System.currentTimeMillis();
    }
}
