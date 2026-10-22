package com.civilizationsexpansion.civilization;

import java.util.Locale;

/**
 * Stable identifiers and display names for the seven planned civilizations.
 * These are framework definitions only; this enum does not create units,
 * structures, diplomacy, or autonomous kingdoms yet.
 */
public enum Civilization {
    VALARIANS("Valarians", "Valarian"),
    BOHEMIANS("Bohemians", "Bohemian"),
    CALADRIANS("Caladrians", "Caladrian"),
    VISIGOTHIANS("Visigothians", "Visigothian"),
    ORCS("Orcs", "Orc"),
    DWARVES("Dwarves", "Dwarven"),
    JADE_WARRIORS("Jade Warriors", "Jade Warrior");

    private final String displayName;
    private final String adjective;
    private final String id;

    Civilization(String displayName, String adjective) {
        this.displayName = displayName;
        this.adjective = adjective;
        this.id = name().toLowerCase(Locale.ROOT);
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public String adjective() {
        return adjective;
    }
}
