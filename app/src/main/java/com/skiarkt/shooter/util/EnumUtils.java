package com.skiarkt.shooter.util;

/** Shared helpers for enums that are cycled in the UI and persisted via {@code name()}. */
public final class EnumUtils {

    private EnumUtils() {}

    /** Next constant of {@code value}'s enum, wrapping to the first after the last. */
    public static <E extends Enum<E>> E next(E value) {
        E[] all = value.getDeclaringClass().getEnumConstants();
        return all[(value.ordinal() + 1) % all.length];
    }

    /**
     * Lenient lookup by constant name: trimmed and case-insensitive.
     * Returns {@code dflt} when {@code s} is null or matches no constant.
     */
    public static <E extends Enum<E>> E fromName(Class<E> type, String s, E dflt) {
        if (s == null) return dflt;
        String key = s.trim();
        for (E v : type.getEnumConstants()) {
            if (v.name().equalsIgnoreCase(key)) return v;
        }
        return dflt;
    }
}
