package com.civilizationsexpansion.command;

import com.civilizationsexpansion.CivilizationsExpansion;
import com.civilizationsexpansion.civilization.Civilization;
import com.civilizationsexpansion.civilization.CivilizationCatalog;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Player-facing civilization selection and diplomacy-ledger commands.
 * These player choices are saved in persistent player NBT data.
 */
@Mod.EventBusSubscriber(modid = CivilizationsExpansion.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CivilizationCommands {
    private static final String PLAYER_CIVILIZATION_KEY = "civilizations_expansion_civilization";
    private static final String DIPLOMACY_KEY = "civilizations_expansion_diplomacy";
    private static final String[] DIPLOMACY_STATUSES = {"allied", "neutral", "hostile", "war"};

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
                .then(Commands.literal("roster")
                    .executes(context -> showRoster(context.getSource().getPlayerOrException(), null))
                    .then(Commands.argument("id", StringArgumentType.word())
                        .executes(context -> showRoster(
                            context.getSource().getPlayerOrException(),
                            StringArgumentType.getString(context, "id")
                        ))))
                .then(Commands.literal("diplomacy")
                    .executes(context -> showDiplomacy(context.getSource().getPlayerOrException()))
                    .then(Commands.literal("list")
                        .executes(context -> showDiplomacy(context.getSource().getPlayerOrException())))
                    .then(Commands.literal("set")
                        .then(Commands.argument("civilization", StringArgumentType.word())
                            .then(Commands.argument("status", StringArgumentType.word())
                                .executes(context -> setDiplomacy(
                                    context.getSource().getPlayerOrException(),
                                    StringArgumentType.getString(context, "civilization"),
                                    StringArgumentType.getString(context, "status")
                                ))))))
        );
    }


    /**
     * Displays the roster described in the design document. This is an informational
     * catalog milestone: it does not claim these soldiers are recruitable entities yet.
     */
    private static int showRoster(ServerPlayer player, String requestedId) {
        if (requestedId == null) {
            String selectedId = player.getPersistentData().getString(PLAYER_CIVILIZATION_KEY);
            Civilization selected = CivilizationCatalog.byId(selectedId);
            if (selected == null) {
                player.sendSystemMessage(Component.literal("Choose a civilization first with /civilizations choose <id>, or view a specific roster with /civilizations roster <id>."));
                return 0;
            }
            return showRoster(player, selected.id());
        }

        Civilization civilization = CivilizationCatalog.byId(requestedId);
        if (civilization == null) {
            player.sendSystemMessage(Component.literal("Unknown civilization: " + requestedId + ". Use /civilizations list."));
            return 0;
        }

        player.sendSystemMessage(Component.literal("=== " + civilization.displayName() + " roster ==="));
        switch (civilization) {
            case VALARIANS -> {
                line(player, "Core: Spearmen, Axemen, Swordsmen, Knights, Archers");
                line(player, "Special: Breacher — weak suicide unit; commanded or death-triggered explosion");
                line(player, "Civilians: Villager, Farmer, Miner, Builder, Lumberjack");
            }
            case BOHEMIANS -> {
                line(player, "Core: Spearmen, Axemen, Swordsmen, Knights, Archers");
                line(player, "Special: Mercenaries — hire non-hostile bandits by paying ores");
                line(player, "Civilians: Villager, Farmer, Miner, Builder, Lumberjack");
            }
            case CALADRIANS -> {
                line(player, "Core: Spearmen, Axemen, Swordsmen, Knights, Archers");
                line(player, "Special: Men-at-Arms — musket troops requiring a blacksmith and blackpowder; gun errors may cause an explosion");
                line(player, "Civilians: Villager, Farmer, Miner, Builder, Lumberjack; Militia");
            }
            case VISIGOTHIANS -> {
                line(player, "Core: Spearmen, Axemen, Swordsmen, Knights, Archers");
                line(player, "Special: Javelinmen — throw tridents that can break or disable shields; carry 2 and restock after one in-game day");
                line(player, "Civilians: Villager, Farmer, Miner, Builder, Lumberjack");
            }
            case ORCS -> {
                line(player, "Traits: slow, strong; wild NPC tribes planned; maximum 20 buildings");
                line(player, "Core: Spearmen (can throw spears), Berserkers (battle rage may hit allies), Swordsmen, Pig Riders, Archers");
                line(player, "Special: Wargs (fast fear howl); Orc Shaman (fireballs and healing)");
                line(player, "Civilians: Tribal, Underearther, Grunt, Lumberbök, Nomad");
            }
            case DWARVES -> {
                line(player, "Traits: slow and strong; faster during night");
                line(player, "Core: Spearmen, Axemen, Swordsmen, Ram Riders, Crossbowmen");
                line(player, "Special: Repeating Crossbowmen; Repeating Chariots; Pikemen with phalanx and brace mechanics");
                line(player, "Civilians: Villager, Farmer (4x mining reward belongs to miners), Miner (4x reward and 2x mining speed), Builder, Lumberjack");
            }
            case JADE_WARRIORS -> {
                line(player, "Traits: holy soldiers that frighten undead");
                line(player, "Core: Spearmen, Axemen, Swordsmen, Jade Lancers, Archers");
                line(player, "Special: Flying Lantern (fireballs or transport 5 soldiers); Mounted Jade Gunmen; Priest (heals allies within 10 blocks)");
                line(player, "Civilians: Villager, Farmer, Miner, Builder, Lumberjack");
            }
        }
        player.sendSystemMessage(Component.literal("Design roster only: custom recruitment, models, abilities, and faction AI are not implemented by this command yet."));
        return Command.SINGLE_SUCCESS;
    }

    private static void line(ServerPlayer player, String text) {
        player.sendSystemMessage(Component.literal("- " + text));
    }

    private static int showCatalog(ServerPlayer player) {
        player.sendSystemMessage(Component.literal("Civilizations Expansion — choose with /civilizations choose <id>"));
        for (Civilization civilization : Civilization.values()) {
            player.sendSystemMessage(Component.literal("- " + civilization.displayName() + " [" + civilization.id() + "]"));
        }
        player.sendSystemMessage(Component.literal("Choose a civilization, then manage your diplomacy ledger with /civilizations diplomacy."));
        return Command.SINGLE_SUCCESS;
    }

    private static int chooseCivilization(ServerPlayer player, String id) {
        Civilization civilization = CivilizationCatalog.byId(id);
        if (civilization == null) {
            player.sendSystemMessage(Component.literal("Unknown civilization: " + id + ". Use /civilizations list to see valid IDs."));
            return 0;
        }

        player.getPersistentData().putString(PLAYER_CIVILIZATION_KEY, civilization.id());
        player.sendSystemMessage(Component.literal("Your civilization is now: " + civilization.displayName() + "."));
        player.sendSystemMessage(Component.literal("Your choice is saved to your player data."));
        return Command.SINGLE_SUCCESS;
    }

    private static int showSelection(ServerPlayer player) {
        String id = player.getPersistentData().getString(PLAYER_CIVILIZATION_KEY);
        Civilization civilization = CivilizationCatalog.byId(id);
        if (civilization == null) {
            player.sendSystemMessage(Component.literal("You have not chosen a civilization yet. Use /civilizations choose <id>."));
            return Command.SINGLE_SUCCESS;
        }
        player.sendSystemMessage(Component.literal("Your civilization: " + civilization.displayName() + " [" + civilization.id() + "]"));
        return Command.SINGLE_SUCCESS;
    }

    private static int showDiplomacy(ServerPlayer player) {
        String ownId = player.getPersistentData().getString(PLAYER_CIVILIZATION_KEY);
        Civilization ownCivilization = CivilizationCatalog.byId(ownId);
        if (ownCivilization == null) {
            player.sendSystemMessage(Component.literal("Choose your civilization first with /civilizations choose <id>."));
            return Command.SINGLE_SUCCESS;
        }

        CompoundTag diplomacy = getDiplomacyTag(player);
        player.sendSystemMessage(Component.literal("Diplomacy ledger for " + ownCivilization.displayName() + ":"));
        for (Civilization civilization : Civilization.values()) {
            if (civilization == ownCivilization) {
                continue;
            }
            String status = diplomacy.contains(civilization.id())
                ? diplomacy.getString(civilization.id()) : "neutral";
            player.sendSystemMessage(Component.literal("- " + civilization.displayName() + ": " + status));
        }
        player.sendSystemMessage(Component.literal("Set a ledger entry: /civilizations diplomacy set <id> <allied|neutral|hostile|war>"));
        player.sendSystemMessage(Component.literal("This is a saved player diplomacy ledger; it does not yet command NPC kingdoms or change combat AI."));
        return Command.SINGLE_SUCCESS;
    }

    private static int setDiplomacy(ServerPlayer player, String civilizationId, String statusInput) {
        String ownId = player.getPersistentData().getString(PLAYER_CIVILIZATION_KEY);
        Civilization ownCivilization = CivilizationCatalog.byId(ownId);
        if (ownCivilization == null) {
            player.sendSystemMessage(Component.literal("Choose your civilization first with /civilizations choose <id>."));
            return 0;
        }

        Civilization target = CivilizationCatalog.byId(civilizationId);
        if (target == null) {
            player.sendSystemMessage(Component.literal("Unknown civilization: " + civilizationId + ". Use /civilizations list."));
            return 0;
        }
        if (target == ownCivilization) {
            player.sendSystemMessage(Component.literal("Your own civilization cannot have a diplomatic stance toward itself."));
            return 0;
        }

        String status = statusInput.toLowerCase(java.util.Locale.ROOT);
        boolean valid = false;
        for (String candidate : DIPLOMACY_STATUSES) {
            if (candidate.equals(status)) {
                valid = true;
                break;
            }
        }
        if (!valid) {
            player.sendSystemMessage(Component.literal("Unknown status: " + statusInput + ". Use allied, neutral, hostile, or war."));
            return 0;
        }

        getDiplomacyTag(player).putString(target.id(), status);
        player.sendSystemMessage(Component.literal("Diplomacy ledger updated: " + target.displayName() + " is marked " + status + "."));
        player.sendSystemMessage(Component.literal("Saved to your player data. This does not yet change NPC behavior or combat."));
        return Command.SINGLE_SUCCESS;
    }

    private static CompoundTag getDiplomacyTag(ServerPlayer player) {
        CompoundTag persistentData = player.getPersistentData();
        if (!persistentData.contains(DIPLOMACY_KEY, CompoundTag.TAG_COMPOUND)) {
            persistentData.put(DIPLOMACY_KEY, new CompoundTag());
        }
        return persistentData.getCompound(DIPLOMACY_KEY);
    }
}
