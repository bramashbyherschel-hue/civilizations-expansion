package com.civilizationsexpansion.command;

import com.civilizationsexpansion.CivilizationsExpansion;
import com.civilizationsexpansion.civilization.Civilization;
import com.civilizationsexpansion.civilization.CivilizationCatalog;
import com.mojang.brigadier.Command;
import net.minecraft.commands.Commands;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

/**
 * First army prototype: recruit a vanilla iron golem as a settlement defender.
 * Uses vanilla entity AI rather than copying another mod's implementation.
 */
@Mod.EventBusSubscriber(modid = CivilizationsExpansion.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ArmyCommands {
    private static final String KINGDOM_KEY = "civilizations_expansion_kingdom";
    private static final String CIVILIZATION_KEY = "civilizations_expansion_civilization";
    private static final int GUARD_COST = 12;

    private ArmyCommands() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(
            Commands.literal("kingdom")
                .then(Commands.literal("recruit")
                    .then(Commands.literal("guard")
                        .executes(context -> recruitGuard(context.getSource().getPlayerOrException()))))
        );
        event.getDispatcher().register(
            Commands.literal("cex")
                .then(Commands.literal("compatibility")
                    .executes(context -> compatibility(context.getSource().getPlayerOrException())))
        );
    }

    private static int recruitGuard(ServerPlayer player) {
        CompoundTag data = player.getPersistentData();
        if (!data.contains(KINGDOM_KEY, CompoundTag.TAG_COMPOUND)
            || !data.getCompound(KINGDOM_KEY).getBoolean("founded")) {
            player.sendSystemMessage(Component.literal("Found a settlement first with /kingdom found."));
            return 0;
        }
        CompoundTag kingdom = data.getCompound(KINGDOM_KEY);
        if (!player.level().dimension().location().toString().equals(kingdom.getString("dimension"))) {
            player.sendSystemMessage(Component.literal("Travel to your settlement's dimension before recruiting."));
            return 0;
        }
        int treasury = kingdom.getInt("treasury");
        if (treasury < GUARD_COST) {
            player.sendSystemMessage(Component.literal("Recruiting a guard costs " + GUARD_COST
                + " treasury emeralds. Current treasury: " + treasury + "."));
            return 0;
        }

        ServerLevel level = player.serverLevel();
        BlockPos settlement = new BlockPos(kingdom.getInt("x"), kingdom.getInt("y"), kingdom.getInt("z"));
        BlockPos spawn = settlement.offset(2, 1, 2);
        if (!level.hasChunkAt(spawn)) {
            player.sendSystemMessage(Component.literal("Settlement area is not loaded. Travel closer and try again."));
            return 0;
        }
        IronGolem guard = EntityType.IRON_GOLEM.create(level);
        if (guard == null) {
            player.sendSystemMessage(Component.literal("Could not create the guard."));
            return 0;
        }
        guard.moveTo(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, player.getYRot(), 0.0F);
        String civId = kingdom.getString("civilization");
        Civilization civilization = CivilizationCatalog.byId(civId);
        String civName = civilization == null ? "Settlement" : civilization.adjective();
        int guardNumber = kingdom.getInt("guards") + 1;
        guard.setCustomName(Component.literal(civName + " Guard " + guardNumber));
        guard.setCustomNameVisible(true);
        guard.setPlayerCreated(true);
        if (!level.addFreshEntity(guard)) {
            player.sendSystemMessage(Component.literal("The guard could not enter the world; no emeralds were charged."));
            return 0;
        }
        kingdom.putInt("treasury", treasury - GUARD_COST);
        kingdom.putInt("guards", guardNumber);
        player.sendSystemMessage(Component.literal("Recruited " + civName + " Guard " + guardNumber
            + "! Treasury: " + kingdom.getInt("treasury") + " emeralds."));
        player.sendSystemMessage(Component.literal("This first guard uses vanilla iron-golem behavior. Custom soldier models, weapons, formations, and orders are not implemented yet."));
        return Command.SINGLE_SUCCESS;
    }

    private static int compatibility(ServerPlayer player) {
        player.sendSystemMessage(Component.literal("Civilizations Expansion compatibility check (mod detection only):"));
        player.sendSystemMessage(Component.literal("Minecraft/Forge: running inside Forge; target is Minecraft 1.20.1 / Forge 47.4.x."));
        reportMod(player, "reignofnether", "Reign of the Nether");
        reportMod(player, "valarianconquest", "Valarian Conquest");
        player.sendSystemMessage(Component.literal("Detection does not prove gameplay integration. This add-on currently uses its own commands and vanilla entities; it does not yet call either mod's internal builder or unit APIs."));
        return Command.SINGLE_SUCCESS;
    }

    private static void reportMod(ServerPlayer player, String modId, String displayName) {
        boolean loaded = ModList.get().isLoaded(modId);
        player.sendSystemMessage(Component.literal(displayName + " (" + modId + "): "
            + (loaded ? "detected as loaded" : "not detected under this mod ID")));
    }
}
