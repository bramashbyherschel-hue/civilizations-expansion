package com.civilizationsexpansion.command;

import com.civilizationsexpansion.CivilizationsExpansion;
import com.civilizationsexpansion.civilization.Civilization;
import com.civilizationsexpansion.civilization.CivilizationCatalog;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * First playable kingdom-management layer.
 * The player's first settlement and treasury are stored in persistent player NBT.
 * This is intentionally a personal prototype; shared multiplayer kingdoms and NPC
 * simulation will be added in later milestones.
 */
@Mod.EventBusSubscriber(modid = CivilizationsExpansion.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class KingdomCommands {
    private static final String CIVILIZATION_KEY = "civilizations_expansion_civilization";
    private static final String KINGDOM_KEY = "civilizations_expansion_kingdom";
    private static final int MAX_CONTRIBUTION = 64;

    private KingdomCommands() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
            Commands.literal("kingdom")
                .then(Commands.literal("found")
                    .executes(context -> found(context.getSource().getPlayerOrException())))
                .then(Commands.literal("status")
                    .executes(context -> status(context.getSource().getPlayerOrException())))
                .then(Commands.literal("rename")
                    .then(Commands.argument("name", StringArgumentType.greedyString())
                        .executes(context -> rename(
                            context.getSource().getPlayerOrException(),
                            StringArgumentType.getString(context, "name")
                        ))))
                .then(Commands.literal("contribute")
                    .then(Commands.argument("emeralds", IntegerArgumentType.integer(1, MAX_CONTRIBUTION))
                        .executes(context -> contribute(
                            context.getSource().getPlayerOrException(),
                            IntegerArgumentType.getInteger(context, "emeralds")
                        ))))
        );
    }

    private static int found(ServerPlayer player) {
        Civilization civilization = CivilizationCatalog.byId(player.getPersistentData().getString(CIVILIZATION_KEY));
        if (civilization == null) {
            player.sendSystemMessage(Component.literal("Choose a civilization first: /civilizations choose <id>."));
            return 0;
        }

        CompoundTag data = player.getPersistentData();
        if (data.contains(KINGDOM_KEY, CompoundTag.TAG_COMPOUND)
            && data.getCompound(KINGDOM_KEY).getBoolean("founded")) {
            player.sendSystemMessage(Component.literal("You already founded a settlement. Use /kingdom status to inspect it."));
            return 0;
        }

        CompoundTag kingdom = new CompoundTag();
        kingdom.putBoolean("founded", true);
        kingdom.putString("name", civilization.adjective() + " Settlement");
        kingdom.putString("civilization", civilization.id());
        kingdom.putString("dimension", player.level().dimension().location().toString());
        kingdom.putInt("x", player.blockPosition().getX());
        kingdom.putInt("y", player.blockPosition().getY());
        kingdom.putInt("z", player.blockPosition().getZ());
        kingdom.putInt("level", 1);
        kingdom.putInt("population", 1);
        kingdom.putInt("treasury", 0);
        data.put(KINGDOM_KEY, kingdom);

        player.sendSystemMessage(Component.literal("Settlement founded: " + kingdom.getString("name") + "!"));
        player.sendSystemMessage(Component.literal("Location: " + kingdom.getInt("x") + ", "
            + kingdom.getInt("y") + ", " + kingdom.getInt("z") + " in "
            + kingdom.getString("dimension") + "."));
        player.sendSystemMessage(Component.literal("Use /kingdom status or /kingdom contribute <emeralds>."));
        player.sendSystemMessage(Component.literal("This records a settlement claim; it does not place buildings or spawn villagers yet."));
        return 1;
    }

    private static int status(ServerPlayer player) {
        CompoundTag kingdom = getKingdom(player);
        if (kingdom == null) {
            player.sendSystemMessage(Component.literal("You have no settlement yet. Found one with /kingdom found."));
            return 0;
        }

        player.sendSystemMessage(Component.literal("=== " + kingdom.getString("name") + " ==="));
        player.sendSystemMessage(Component.literal("Civilization: " + displayCivilization(kingdom.getString("civilization"))));
        player.sendSystemMessage(Component.literal("Settlement: " + kingdom.getString("dimension") + " at "
            + kingdom.getInt("x") + ", " + kingdom.getInt("y") + ", " + kingdom.getInt("z")));
        player.sendSystemMessage(Component.literal("Level: " + kingdom.getInt("level")
            + " | Population: " + kingdom.getInt("population")
            + " | Treasury: " + kingdom.getInt("treasury") + " emeralds"));
        player.sendSystemMessage(Component.literal("Contribute emeralds with /kingdom contribute <amount>."));
        return 1;
    }

    private static int rename(ServerPlayer player, String rawName) {
        CompoundTag kingdom = getKingdom(player);
        if (kingdom == null) {
            player.sendSystemMessage(Component.literal("Found a settlement first with /kingdom found."));
            return 0;
        }
        String name = rawName.trim();
        if (name.isEmpty() || name.length() > 32) {
            player.sendSystemMessage(Component.literal("Choose a name between 1 and 32 characters."));
            return 0;
        }
        kingdom.putString("name", name);
        player.sendSystemMessage(Component.literal("Your settlement is now called " + name + "."));
        return 1;
    }

    private static int contribute(ServerPlayer player, int amount) {
        CompoundTag kingdom = getKingdom(player);
        if (kingdom == null) {
            player.sendSystemMessage(Component.literal("Found a settlement first with /kingdom found."));
            return 0;
        }

        int available = countEmeralds(player);
        if (available < amount) {
            player.sendSystemMessage(Component.literal("You need " + amount + " emeralds, but only have " + available + "."));
            return 0;
        }

        removeEmeralds(player, amount);
        kingdom.putInt("treasury", kingdom.getInt("treasury") + amount);
        player.sendSystemMessage(Component.literal("Contributed " + amount + " emeralds to " + kingdom.getString("name") + "."));
        player.sendSystemMessage(Component.literal("Treasury balance: " + kingdom.getInt("treasury") + " emeralds."));
        return 1;
    }

    private static CompoundTag getKingdom(ServerPlayer player) {
        CompoundTag data = player.getPersistentData();
        if (!data.contains(KINGDOM_KEY, CompoundTag.TAG_COMPOUND)) {
            return null;
        }
        CompoundTag kingdom = data.getCompound(KINGDOM_KEY);
        return kingdom.getBoolean("founded") ? kingdom : null;
    }

    private static String displayCivilization(String id) {
        Civilization civilization = CivilizationCatalog.byId(id);
        return civilization == null ? id : civilization.displayName();
    }

    private static int countEmeralds(ServerPlayer player) {
        int count = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(Items.EMERALD)) {
                count += stack.getCount();
            }
        }
        for (ItemStack stack : player.getInventory().offhand) {
            if (stack.is(Items.EMERALD)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static void removeEmeralds(ServerPlayer player, int amount) {
        int remaining = amount;
        for (ItemStack stack : player.getInventory().items) {
            if (remaining <= 0) break;
            if (stack.is(Items.EMERALD)) {
                int taken = Math.min(remaining, stack.getCount());
                stack.shrink(taken);
                remaining -= taken;
            }
        }
        for (ItemStack stack : player.getInventory().offhand) {
            if (remaining <= 0) break;
            if (stack.is(Items.EMERALD)) {
                int taken = Math.min(remaining, stack.getCount());
                stack.shrink(taken);
                remaining -= taken;
            }
        }
    }
}
