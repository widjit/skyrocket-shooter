package com.skiarkt.shooter.model;

import android.os.Parcel;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Gun / missile / interceptor. Fragmentation is modelled along the entire munition
 * flight path as a 3D cylinder of radius {@link #fragmentationRadiusMeters}.
 */
public class KineticEffector extends PointItem {
    public double fragmentationRadiusMeters = 15.0;
    public double munitionSpeedMps = 800.0;
    /** Detect-to-fire reaction time (seconds) - used by the engagement timeline. */
    public double reactionTimeSec = 3.0;
    public int noShootColor = PlanColors.KE_NO_SHOOT;
    public int fragZoneColor = PlanColors.KE_FRAG_ZONE;
    public int clearZoneColor = PlanColors.KE_CLEAR_ZONE;

    // ---- Phase 4 engagement envelope (name = label, lat/lon = position, agl = aglMeters,
    // ---- maxEngagementRange = maxEngagementDistMeters, munitionVelocity = munitionSpeedMps)
    public KineticEffectorType type = KineticEffectorType.RIFLE;
    /** Dead-zone radius (meters) inside which the effector cannot engage. */
    public double minEngagementRange = 0.0;
    /** Sector limits, degrees true, clockwise min..max. Null (either) = full 360. */
    public Double azimuthMin;
    public Double azimuthMax;
    /** Elevation limits (degrees above horizon). */
    public double elevationMin = -5.0;
    public double elevationMax = 85.0;
    /** Width (meters) of the fragmentation corridor along the munition route. */
    public double fragmentationRouteWidth = 10.0;
    public MunitionType munitionType = MunitionType.KINETIC_IMPACTOR;
    public DetonationMode detonationMode = DetonationMode.IMPACT;
    /** No-fire bearing arcs. */
    public List<AzimuthArc> restrictedAzimuths = new ArrayList<>();
    /** Minimum intercept altitude above ground (meters). */
    public double minInterceptAltAGL = 0.0;
    /** Exclusion-zone / perimeter uid bounding engagement authority (nullable). */
    public String engagementAuthorityPerimeterUid;
    public boolean friendlyFireCheckEnabled = true;
    public List<String> linkedSensorUids = new ArrayList<>();
    public List<String> linkedLaserDesignatorUids = new ArrayList<>();
    /** 4-digit NATO laser PRF code (1111..1788), laser-guided rockets only (nullable). */
    public Integer laserCode;
    public EffectorStatus status = EffectorStatus.READY;
    public int ammoCount = 30;
    public double reloadTimeSec = 5.0;
    public String notes = "";

    // ---- R7 directed-energy (laser) fields - only meaningful when type == DIRECTED_ENERGY.
    /** Time on target needed per engagement (seconds). */
    public double beamDwellSec = 3.0;
    /** Beam director aperture diameter (cm). */
    public double apertureDiameterCm = 30.0;
    /** Output power (kW). Optional: null when the operator does not know it. */
    public Double laserPowerKw;
    /** Operator-entered effective range (m) used when power is unknown (nullable). */
    public Double qualitativeEffectiveRangeM;
    /** Radius (m) of the effective suppression zone around the aim point (replaces frag corridor). */
    public double suppressionZoneRadiusM = 5.0;
    /** Fluence (J/cm²) needed to defeat the target within the dwell time. */
    public double damageFluenceJPerCm2 = 1000.0;

    public KineticEffector() {
        super("Kinetic Effector", PlanColors.KE_CLEAR_ZONE);
    }

    protected KineticEffector(Parcel in) {
        super(in);
        fragmentationRadiusMeters = in.readDouble();
        munitionSpeedMps = in.readDouble();
        reactionTimeSec = in.readDouble();
        noShootColor = in.readInt();
        fragZoneColor = in.readInt();
        clearZoneColor = in.readInt();
        type = KineticEffectorType.fromName(in.readString(), KineticEffectorType.RIFLE);
        minEngagementRange = in.readDouble();
        azimuthMin = in.readInt() == 1 ? in.readDouble() : null;
        azimuthMax = in.readInt() == 1 ? in.readDouble() : null;
        elevationMin = in.readDouble();
        elevationMax = in.readDouble();
        fragmentationRouteWidth = in.readDouble();
        munitionType = MunitionType.fromName(in.readString(), MunitionType.KINETIC_IMPACTOR);
        detonationMode = DetonationMode.fromName(in.readString(), DetonationMode.IMPACT);
        int arcs = in.readInt();
        restrictedAzimuths = new ArrayList<>();
        for (int i = 0; i < arcs; i++) restrictedAzimuths.add(new AzimuthArc(in.readDouble(), in.readDouble()));
        minInterceptAltAGL = in.readDouble();
        engagementAuthorityPerimeterUid = in.readString();
        friendlyFireCheckEnabled = in.readInt() == 1;
        linkedSensorUids = in.createStringArrayList();
        linkedLaserDesignatorUids = in.createStringArrayList();
        laserCode = in.readInt() == 1 ? in.readInt() : null;
        status = EffectorStatus.fromName(in.readString(), EffectorStatus.READY);
        ammoCount = in.readInt();
        reloadTimeSec = in.readDouble();
        notes = in.readString();
        beamDwellSec = in.readDouble();
        apertureDiameterCm = in.readDouble();
        laserPowerKw = in.readInt() == 1 ? in.readDouble() : null;
        qualitativeEffectiveRangeM = in.readInt() == 1 ? in.readDouble() : null;
        suppressionZoneRadiusM = in.readDouble();
        damageFluenceJPerCm2 = in.readDouble();
        ensureLists();
    }

    // ------------------------------------------------------------ spec accessors

    public String getName() {
        return label;
    }

    public void setName(String name) {
        label = name;
    }

    public double getLat() {
        return position == null ? Double.NaN : position.lat;
    }

    public double getLon() {
        return position == null ? Double.NaN : position.lon;
    }

    public double getAgl() {
        return aglMeters;
    }

    public double getMaxEngagementRange() {
        return maxEngagementDistMeters;
    }

    public void setMaxEngagementRange(double meters) {
        maxEngagementDistMeters = meters;
    }

    public double getFragmentationRadius() {
        return fragmentationRadiusMeters;
    }

    public double getMunitionVelocity() {
        return munitionSpeedMps;
    }

    public void setMunitionVelocity(double mps) {
        munitionSpeedMps = mps;
    }

    /**
     * Changes the weapon type. If the velocity is still the old type's default (i.e. the
     * user never customised it), it follows the new type's default - so switching to
     * Directed Energy sets the speed of light, and switching back restores 800 m/s.
     */
    public void applyType(KineticEffectorType newType) {
        if (newType == null) return;
        munitionSpeedMps = KineticEffector.velocityForTypeChange(type, newType, munitionSpeedMps);
        type = newType;
    }

    /** Velocity to use after a type change; custom (non-default) values are preserved. */
    public static double velocityForTypeChange(KineticEffectorType oldType, KineticEffectorType newType,
            double currentMps) {
        if (newType == null) return currentMps;
        boolean isDefault = oldType != null && Double.compare(currentMps, oldType.defaultMunitionSpeedMps()) == 0;
        return isDefault || !(currentMps > 0) ? newType.defaultMunitionSpeedMps() : currentMps;
    }

    // ------------------------------------------------------------- envelope math

    /** True when no sector limits are set (either azimuth bound null). */
    public boolean isFullCircle() {
        return azimuthMin == null || azimuthMax == null;
    }

    /** Clockwise sector sweep (degrees); 360 when full circle or bounds equal. */
    public double azimuthSweepDeg() {
        if (isFullCircle()) return 360.0;
        return new AzimuthArc(azimuthMin, azimuthMax).sweepDeg();
    }

    /** True when {@code bearingDeg} is inside the sector and outside every restricted arc. */
    public boolean isBearingAllowed(double bearingDeg) {
        if (!isFullCircle() && !new AzimuthArc(azimuthMin, azimuthMax).contains(bearingDeg)) return false;
        if (restrictedAzimuths != null) {
            for (AzimuthArc a : restrictedAzimuths) {
                if (a != null && a.contains(bearingDeg)) return false;
            }
        }
        return true;
    }

    /** True when a target at {@code distanceM} / {@code bearingDeg} is inside the engagement envelope. */
    public boolean canEngage(double distanceM, double bearingDeg) {
        return distanceM >= minEngagementRange && distanceM <= maxEngagementDistMeters
                && isBearingAllowed(bearingDeg);
    }

    /** Valid NATO PRF code: 4 digits, 1st = 1, 2nd 1..7, 3rd/4th 1..8 (1111..1788). */
    public static boolean isValidLaserCode(Integer code) {
        if (code == null || code < 1111 || code > 1788) return false;
        String s = String.valueOf(code);
        if (s.charAt(1) < '1' || s.charAt(1) > '7') return false;
        for (int i = 2; i < 4; i++) {
            if (s.charAt(i) < '1' || s.charAt(i) > '8') return false;
        }
        return true;
    }

    /** Gson leaves absent fields null in older plan files; normalise. */
    public void ensureLists() {
        if (type == null) type = KineticEffectorType.RIFLE;
        if (munitionType == null) munitionType = MunitionType.KINETIC_IMPACTOR;
        if (detonationMode == null) detonationMode = DetonationMode.IMPACT;
        if (status == null) status = EffectorStatus.READY;
        if (restrictedAzimuths == null) restrictedAzimuths = new ArrayList<>();
        if (linkedSensorUids == null) linkedSensorUids = new ArrayList<>();
        if (linkedLaserDesignatorUids == null) linkedLaserDesignatorUids = new ArrayList<>();
        if (notes == null) notes = "";
    }

    /** First validation problem (field name), or null when the envelope is consistent. */
    public String validationError() {
        if (type == null || munitionType == null || detonationMode == null || status == null) return "enums";
        if (Double.isNaN(minEngagementRange) || minEngagementRange < 0
                || minEngagementRange >= maxEngagementDistMeters) return "minEngagementRange";
        if ((azimuthMin == null) != (azimuthMax == null)) return "azimuth";
        if (azimuthMin != null && (!AzimuthArc.isBearing(azimuthMin) || !AzimuthArc.isBearing(azimuthMax))) {
            return "azimuth";
        }
        if (Double.isNaN(elevationMin) || Double.isNaN(elevationMax) || elevationMin < -90 || elevationMax > 90
                || elevationMin >= elevationMax) return "elevation";
        if (!(fragmentationRadiusMeters >= 0)) return "fragmentationRadius";
        if (!(fragmentationRouteWidth >= 0)) return "fragmentationRouteWidth";
        if (!(munitionSpeedMps > 0)) return "munitionVelocity";
        if (restrictedAzimuths != null) {
            for (AzimuthArc a : restrictedAzimuths) {
                if (a == null || !a.isValid()) return "restrictedAzimuths";
            }
        }
        if (!(minInterceptAltAGL >= 0)) return "minInterceptAltAGL";
        if (laserCode != null && !isValidLaserCode(laserCode)) return "laserCode";
        if (type == KineticEffectorType.LASER_GUIDED_ROCKET && laserCode == null) return "laserCode";
        if (ammoCount < 0) return "ammoCount";
        if (!(reloadTimeSec >= 0)) return "reloadTimeSec";
        if (type == KineticEffectorType.DIRECTED_ENERGY) {
            if (!(beamDwellSec > 0)) return "beamDwellSec";
            if (!(apertureDiameterCm > 0)) return "apertureDiameterCm";
            if (laserPowerKw != null && !(laserPowerKw > 0)) return "laserPowerKw";
            if (qualitativeEffectiveRangeM != null && !(qualitativeEffectiveRangeM > 0)) {
                return "qualitativeEffectiveRange";
            }
            if (!(suppressionZoneRadiusM >= 0)) return "suppressionZoneRadius";
            if (!(damageFluenceJPerCm2 > 0)) return "damageFluence";
        }
        return null;
    }

    /** Deep copy (same uid) used as an edit working copy. */
    public KineticEffector copy() {
        KineticEffector k = new KineticEffector();
        copyPointInto(k);
        k.fragmentationRadiusMeters = fragmentationRadiusMeters;
        k.munitionSpeedMps = munitionSpeedMps;
        k.reactionTimeSec = reactionTimeSec;
        k.noShootColor = noShootColor;
        k.fragZoneColor = fragZoneColor;
        k.clearZoneColor = clearZoneColor;
        k.type = type;
        k.minEngagementRange = minEngagementRange;
        k.azimuthMin = azimuthMin;
        k.azimuthMax = azimuthMax;
        k.elevationMin = elevationMin;
        k.elevationMax = elevationMax;
        k.fragmentationRouteWidth = fragmentationRouteWidth;
        k.munitionType = munitionType;
        k.detonationMode = detonationMode;
        k.restrictedAzimuths = new ArrayList<>();
        if (restrictedAzimuths != null) {
            for (AzimuthArc a : restrictedAzimuths) k.restrictedAzimuths.add(a == null ? null : a.copy());
        }
        k.minInterceptAltAGL = minInterceptAltAGL;
        k.engagementAuthorityPerimeterUid = engagementAuthorityPerimeterUid;
        k.friendlyFireCheckEnabled = friendlyFireCheckEnabled;
        k.linkedSensorUids = linkedSensorUids == null ? new ArrayList<String>() : new ArrayList<>(linkedSensorUids);
        k.linkedLaserDesignatorUids = linkedLaserDesignatorUids == null ? new ArrayList<String>()
                : new ArrayList<>(linkedLaserDesignatorUids);
        k.laserCode = laserCode;
        k.status = status;
        k.ammoCount = ammoCount;
        k.reloadTimeSec = reloadTimeSec;
        k.notes = notes;
        k.beamDwellSec = beamDwellSec;
        k.apertureDiameterCm = apertureDiameterCm;
        k.laserPowerKw = laserPowerKw;
        k.qualitativeEffectiveRangeM = qualitativeEffectiveRangeM;
        k.suppressionZoneRadiusM = suppressionZoneRadiusM;
        k.damageFluenceJPerCm2 = damageFluenceJPerCm2;
        return k;
    }

    /** Field-by-field content comparison (uid, label, position and all effector fields). */
    public boolean sameContentAs(KineticEffector o) {
        if (o == null) return false;
        return Objects.equals(uid, o.uid) && Objects.equals(label, o.label) && color == o.color
                && Objects.equals(nz(remarks), nz(o.remarks))
                && position != null && o.position != null
                && Double.compare(position.lat, o.position.lat) == 0
                && Double.compare(position.lon, o.position.lon) == 0
                && Double.compare(aglMeters, o.aglMeters) == 0
                && Double.compare(maxEngagementDistMeters, o.maxEngagementDistMeters) == 0
                && Objects.equals(normalizeSiteUid(siteUid), normalizeSiteUid(o.siteUid))
                && Double.compare(fragmentationRadiusMeters, o.fragmentationRadiusMeters) == 0
                && Double.compare(munitionSpeedMps, o.munitionSpeedMps) == 0
                && Double.compare(reactionTimeSec, o.reactionTimeSec) == 0
                && noShootColor == o.noShootColor && fragZoneColor == o.fragZoneColor
                && clearZoneColor == o.clearZoneColor
                && type == o.type
                && Double.compare(minEngagementRange, o.minEngagementRange) == 0
                && Objects.equals(azimuthMin, o.azimuthMin) && Objects.equals(azimuthMax, o.azimuthMax)
                && Double.compare(elevationMin, o.elevationMin) == 0
                && Double.compare(elevationMax, o.elevationMax) == 0
                && Double.compare(fragmentationRouteWidth, o.fragmentationRouteWidth) == 0
                && munitionType == o.munitionType && detonationMode == o.detonationMode
                && Objects.equals(restrictedAzimuths, o.restrictedAzimuths)
                && Double.compare(minInterceptAltAGL, o.minInterceptAltAGL) == 0
                && Objects.equals(engagementAuthorityPerimeterUid, o.engagementAuthorityPerimeterUid)
                && friendlyFireCheckEnabled == o.friendlyFireCheckEnabled
                && Objects.equals(linkedSensorUids, o.linkedSensorUids)
                && Objects.equals(linkedLaserDesignatorUids, o.linkedLaserDesignatorUids)
                && Objects.equals(laserCode, o.laserCode)
                && status == o.status && ammoCount == o.ammoCount
                && Double.compare(reloadTimeSec, o.reloadTimeSec) == 0
                && Objects.equals(nz(notes), nz(o.notes))
                && Double.compare(beamDwellSec, o.beamDwellSec) == 0
                && Double.compare(apertureDiameterCm, o.apertureDiameterCm) == 0
                && Objects.equals(laserPowerKw, o.laserPowerKw)
                && Objects.equals(qualitativeEffectiveRangeM, o.qualitativeEffectiveRangeM)
                && Double.compare(suppressionZoneRadiusM, o.suppressionZoneRadiusM) == 0
                && Double.compare(damageFluenceJPerCm2, o.damageFluenceJPerCm2) == 0;
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    /** True for beam weapons, whose munition / detonation / frag / ammo fields do not apply. */
    public boolean isDirectedEnergy() {
        return type != null && type.isDirectedEnergy();
    }

    /** Munition time of flight to {@code distanceMeters} (constant-speed approximation). */
    public double timeOfFlightSec(double distanceMeters) {
        return munitionSpeedMps > 0 ? distanceMeters / munitionSpeedMps : Double.POSITIVE_INFINITY;
    }

    @Override
    public ItemKind getKind() {
        return ItemKind.KINETIC_EFFECTOR;
    }

    @Override
    public boolean isValid() {
        return super.isValid() && fragmentationRadiusMeters >= 0 && munitionSpeedMps > 0
                && validationError() == null;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        writePoint(dest, flags);
        dest.writeDouble(fragmentationRadiusMeters);
        dest.writeDouble(munitionSpeedMps);
        dest.writeDouble(reactionTimeSec);
        dest.writeInt(noShootColor);
        dest.writeInt(fragZoneColor);
        dest.writeInt(clearZoneColor);
        dest.writeString(type == null ? null : type.name());
        dest.writeDouble(minEngagementRange);
        writeBoxed(dest, azimuthMin);
        writeBoxed(dest, azimuthMax);
        dest.writeDouble(elevationMin);
        dest.writeDouble(elevationMax);
        dest.writeDouble(fragmentationRouteWidth);
        dest.writeString(munitionType == null ? null : munitionType.name());
        dest.writeString(detonationMode == null ? null : detonationMode.name());
        List<AzimuthArc> arcs = new ArrayList<>();
        if (restrictedAzimuths != null) {
            for (AzimuthArc a : restrictedAzimuths) if (a != null) arcs.add(a);
        }
        dest.writeInt(arcs.size());
        for (AzimuthArc a : arcs) {
            dest.writeDouble(a.bearingMin);
            dest.writeDouble(a.bearingMax);
        }
        dest.writeDouble(minInterceptAltAGL);
        dest.writeString(engagementAuthorityPerimeterUid);
        dest.writeInt(friendlyFireCheckEnabled ? 1 : 0);
        dest.writeStringList(linkedSensorUids);
        dest.writeStringList(linkedLaserDesignatorUids);
        dest.writeInt(laserCode != null ? 1 : 0);
        if (laserCode != null) dest.writeInt(laserCode);
        dest.writeString(status == null ? null : status.name());
        dest.writeInt(ammoCount);
        dest.writeDouble(reloadTimeSec);
        dest.writeString(notes);
        dest.writeDouble(beamDwellSec);
        dest.writeDouble(apertureDiameterCm);
        writeBoxed(dest, laserPowerKw);
        writeBoxed(dest, qualitativeEffectiveRangeM);
        dest.writeDouble(suppressionZoneRadiusM);
        dest.writeDouble(damageFluenceJPerCm2);
    }

    private static void writeBoxed(Parcel dest, Double v) {
        dest.writeInt(v != null ? 1 : 0);
        if (v != null) dest.writeDouble(v);
    }

    public static final Creator<KineticEffector> CREATOR = new Creator<KineticEffector>() {
        @Override
        public KineticEffector createFromParcel(Parcel in) {
            return new KineticEffector(in);
        }

        @Override
        public KineticEffector[] newArray(int size) {
            return new KineticEffector[size];
        }
    };
}
