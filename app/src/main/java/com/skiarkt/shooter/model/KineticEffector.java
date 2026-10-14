package com.skiarkt.shooter.model;

import android.os.Parcel;

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
        return super.isValid() && fragmentationRadiusMeters >= 0 && munitionSpeedMps > 0;
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
