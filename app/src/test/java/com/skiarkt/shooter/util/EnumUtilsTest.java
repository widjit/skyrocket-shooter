package com.skiarkt.shooter.util;

import static org.junit.Assert.assertEquals;

import com.skiarkt.shooter.model.DetonationMode;
import com.skiarkt.shooter.model.EffectorStatus;
import com.skiarkt.shooter.model.KineticEffectorType;
import com.skiarkt.shooter.model.MunitionType;

import org.junit.Test;

public class EnumUtilsTest {

    @Test
    public void nextWrapsAround() {
        assertEquals(DetonationMode.IMPACT, EnumUtils.next(DetonationMode.PROXIMITY));
        assertEquals(DetonationMode.PROXIMITY, EnumUtils.next(DetonationMode.COMMAND));
        assertEquals(KineticEffectorType.RIFLE, KineticEffectorType.DIRECTED_ENERGY.next());
        assertEquals(EffectorStatus.OFFLINE, EffectorStatus.READY.next());
    }

    @Test
    public void fromNameIsLenientAndNullSafe() {
        assertEquals(MunitionType.NET,
                EnumUtils.fromName(MunitionType.class, "  net ", MunitionType.HE));
        assertEquals(MunitionType.HE,
                EnumUtils.fromName(MunitionType.class, null, MunitionType.HE));
        assertEquals(MunitionType.HE,
                EnumUtils.fromName(MunitionType.class, "bogus", MunitionType.HE));
        assertEquals(KineticEffectorType.LASER_GUIDED_ROCKET,
                KineticEffectorType.fromName("LASER_GUIDED_ROCKET", KineticEffectorType.RIFLE));
        assertEquals(EffectorStatus.MAINTENANCE,
                EffectorStatus.fromName("maintenance", EffectorStatus.READY));
    }

    @Test
    public void everyConstantRoundTripsThroughName() {
        for (KineticEffectorType v : KineticEffectorType.values())
            assertEquals(v, KineticEffectorType.fromName(v.name(), null));
        for (MunitionType v : MunitionType.values())
            assertEquals(v, MunitionType.fromName(v.name(), null));
        for (DetonationMode v : DetonationMode.values())
            assertEquals(v, DetonationMode.fromName(v.name(), null));
        for (EffectorStatus v : EffectorStatus.values())
            assertEquals(v, EffectorStatus.fromName(v.name(), null));
    }
}
