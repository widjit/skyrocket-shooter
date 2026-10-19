package com.skiarkt.shooter.model;

import com.skiarkt.shooter.util.EnumUtils;

/** Kinetic effector weapon class. */
public enum KineticEffectorType {
    RIFLE("Rifle"),
    SHOTGUN("Shotgun"),
    NET_GUN("Net Gun"),
    ROCKET("Rocket"),
    LASER_GUIDED_ROCKET("Laser-Guided Rocket"),
    DRONE_INTERCEPTOR("Drone Interceptor"),
    DIRECTED_ENERGY("Directed Energy");

    /** Speed of light in vacuum (m/s) - effective "munition" velocity of a directed-energy beam. */
    public static final double SPEED_OF_LIGHT_MPS = 299_792_458.0;
    /** Default muzzle / flight velocity (m/s) for projectile weapons. */
    public static final double DEFAULT_PROJECTILE_MPS = 800.0;

    public final String displayName;

    KineticEffectorType(String displayName) {
        this.displayName = displayName;
    }

    /** True for beam weapons (lasers) whose effect travels at the speed of light. */
    public boolean isDirectedEnergy() {
        return this == DIRECTED_ENERGY;
    }

    /** Default munition velocity (m/s) for this type: speed of light for lasers. */
    public double defaultMunitionSpeedMps() {
        return isDirectedEnergy() ? SPEED_OF_LIGHT_MPS : DEFAULT_PROJECTILE_MPS;
    }

    /** Next constant (wraps) - used by cycling UI buttons. */
    public KineticEffectorType next() {
        return EnumUtils.next(this);
    }

    /** Lenient lookup by constant name; {@code dflt} when null / unknown. */
    public static KineticEffectorType fromName(String s, KineticEffectorType dflt) {
        return EnumUtils.fromName(KineticEffectorType.class, s, dflt);
    }
}
