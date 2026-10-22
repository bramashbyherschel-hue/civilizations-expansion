package com.civilizationsexpansion;

import com.civilizationsexpansion.civilization.CivilizationCatalog;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

@Mod(CivilizationsExpansion.MOD_ID)
public final class CivilizationsExpansion {
    public static final String MOD_ID = "civilizations_expansion";
    public static final Logger LOGGER = LogUtils.getLogger();

    public CivilizationsExpansion() {
        CivilizationCatalog.logLoadedCivilizations();
        LOGGER.info("Civilizations Expansion core framework initialized.");
    }
}
