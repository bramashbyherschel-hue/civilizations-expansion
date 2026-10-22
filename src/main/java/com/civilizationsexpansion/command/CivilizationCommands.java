package com.civilizationsexpansion.command;

import com.civilizationsexpansion.CivilizationsExpansion;
import com.civilizationsexpansion.civilization.Civilization;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Player-facing first faction feature: select and inspect a civilization.
 * The selection is saved in the player's persistent NBT data.
 */
@Mod.EventBusSubscriber(modid = CivilizationsExpansion.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CivilizationCommands {
    private static final String PLAYER_CIVILIZATION_KEY = "civilizations_expansion_civilization";

    private CivilizationCommands() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
            Commands.literal("civilizations")
                .executes(context -> showCatalog(context.getSource().getPlayerOrException()))
                .then(Commands.literal("list")
                    .executes(context -> showCatalog(context.getSource().getPlayerOrException())))
                .then(Commands.literal("choose")
                    .then(Commands.argument("id", StringArgumentType.word())
                        .executes(context -> chooseCivilization(
                            context.getSource().getPlayerOrException(),
                            StringArgumentType.getString(context, "id")
                        ))))
                .then(Commands.literal("info")
                    .executes(context -> showSelection(context.getSource().getPlayerOrException())))
        );
    }

    private static int showCatalog(ServerPlayer player) {
        player.sendSystemMessage(Component.literal("Civilizations Expansion — choose with /civilizations choose <id>"));
        for (Civilization civilization : Civilization.values()) {
            player.sendSystemMessage(Component.literal("- " + civilization.displayName() + " ["
                + civilization.id() + "]"));
        }
        player.sendSystemMessage(Component.literal("Note: faction-specific units, buildings, and diplomacy are still in development."));
        return Command.SINGLE_SUCCESS;
    }

    private static int chooseCivilization(ServerPlayer player, String id) {
        Civilization civilization = CivilizationCatalogLookup.byId(id);
        if (civilization == null) {
            player.sendSystemMessage(Component.literal("Unknown civilization: " + id
                + ". Use /civilizations list to see valid IDs."));
            return 0;
        }

        player.getPersistentData().putString(PLAYER_CIVILIZATION_KEY, civilization.id());
        player.sendSystemMessage(Component.literal("Your civilization is now: " + civilization.displayName() + "."));
        player.sendSystemMessage(Component.literal("Your choice is saved to your player data."));
        return Command.SINGLE_SUCCESS;
    }

    private static int showSelection(ServerPlayer player) {
        String id = player.getPersistentData().getString(PLAYER_CIVILIZATION_KEY);
        Civilization civilization = CivilizationCatalogLookup.byId(id);
        if (civilization == null) {
            player.sendSystemMessage(Component.literal("You have not chosen a civilization yet. Use /civilizations choose <id>."));
            return Command.SINGLE_SUCCESS;
        }
        player.sendSystemMessage(Component.literal("Your civilization: " + civilization.displayName()
            + " [" + civilization.id() + "]"));
        return Command.SINGLE_SUCCESS;
    }

    /** Small indirection keeps all ID matching in the catalog's stable rules. */
    private static final class CivilizationCatalogLookup {
        private static Civilization byId(String id) {
            return com.civilizationsexpansion.civilization.CivilizationCatalog.byId(id);
        }
    }
}
