package com.civilizationsexpansion.civilization;

import com.civilizationsexpansion.CivilizationsExpansion;

/**
 * Central read-only catalog for the first framework milestone.
 * Future faction systems should refer to these stable IDs rather than
 * duplicating faction names across systems.
 */
public final class CivilizationCatalog {
    private CivilizationCatalog() {}

    public static Civilization[] all() {
        return Civilization.values().clone();
    }

    public static Civilization byId(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        for (Civilization civilization : Civilization.values()) {
            if (civilization.id().equalsIgnoreCase(id.trim())) {
                return civilization;
            }
        }
        return null;
    }

    public static void logLoadedCivilizations() {
        for (Civilization civilization : Civilization.values()) {
            CivilizationsExpansion.LOGGER.info(
                "Civilization framework entry ready: {} ({})",
                civilization.displayName(),
                civilization.id()
            );
        }
    }
}
