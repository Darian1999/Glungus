package org.xiaojian999.superpowers;

/** The eight equippable superpowers. */
public enum Power {
    ICE,
    AIR,
    FIRE,
    WATER,
    GHOST,
    LIGHTNING,
    NATURE,
    GOD;

    private static final java.util.Map<String, Power> BY_NAME = new java.util.HashMap<>();

    static {
        for (Power p : values()) BY_NAME.put(p.name().toLowerCase(java.util.Locale.ROOT), p);
    }

    public static Power fromString(String name) {
        if (name == null) return null;
        return BY_NAME.get(name.toLowerCase(java.util.Locale.ROOT));
    }

    public String displayName() {
        return switch (this) {
            case AIR -> "Air";
            case FIRE -> "Fire";
            case GHOST -> "Ghost";
            case ICE -> "Ice";
            case WATER -> "Water";
            case LIGHTNING -> "Lightning";
            case NATURE -> "Nature";
            case GOD -> "God";
        };
    }
}
