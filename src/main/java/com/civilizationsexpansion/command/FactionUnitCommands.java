package com.civilizationsexpansion.command;

import com.civilizationsexpansion.CivilizationsExpansion;
import com.civilizationsexpansion.civilization.Civilization;
import com.civilizationsexpansion.civilization.CivilizationCatalog;
import com.mojang.brigadier.Command;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.DyeableLeatherItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * First Orc/Dwarf unit gameplay prototype.
 * Uses vanilla zombie variants and equipment as a temporary visual stand-in;
 * bespoke models, mohawks, and exact one-block dwarf proportions are future work.
 */
@Mod.EventBusSubscriber(modid = CivilizationsExpansion.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class FactionUnitCommands {
    private static final String KINGDOM_KEY = "civilizations_expansion_kingdom";
    private static final int ORC_COST = 8;
    private static final int DWARF_COST = 8;

    private FactionUnitCommands() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(
            Commands.literal("kingdom")
                .then(Commands.literal("recruit")
                    .then(Commands.literal("orc").executes(c -> recruit(c.getSource().getPlayerOrException(), true)))
                    .then(Commands.literal("dwarf").executes(c -> recruit(c.getSource().getPlayerOrException(), false))))
        );
    }

    private static int recruit(ServerPlayer player, boolean orc) {
        CompoundTag data = player.getPersistentData();
        if (!data.contains(KINGDOM_KEY, CompoundTag.TAG_COMPOUND)
            || !data.getCompound(KINGDOM_KEY).getBoolean("founded")) {
            player.sendSystemMessage(Component.literal("Found a settlement first with /kingdom found."));
            return 0;
        }
        CompoundTag kingdom = data.getCompound(KINGDOM_KEY);
        String expected = orc ? "orcs" : "dwarves";
        Civilization civ = CivilizationCatalog.byId(kingdom.getString("civilization"));
        if (civ == null || !civ.id().equals(expected)) {
            player.sendSystemMessage(Component.literal("Your settlement must be " + (orc ? "Orcish" : "Dwarven")
                + " to recruit this unit. Found the matching civilization first."));
            return 0;
        }
        if (!player.level().dimension().location().toString().equals(kingdom.getString("dimension"))) {
            player.sendSystemMessage(Component.literal("Travel to your settlement's dimension before recruiting."));
            return 0;
        }
        int cost = orc ? ORC_COST : DWARF_COST;
        int treasury = kingdom.getInt("treasury");
        if (treasury < cost) {
            player.sendSystemMessage(Component.literal("This unit costs " + cost + " treasury emeralds; balance: " + treasury + "."));
            return 0;
        }

        ServerLevel level = player.serverLevel();
        BlockPos center = new BlockPos(kingdom.getInt("x"), kingdom.getInt("y"), kingdom.getInt("z"));
        BlockPos spawn = center.offset(orc ? 3 : -3, 1, 2);
        if (!level.hasChunkAt(spawn)) {
            player.sendSystemMessage(Component.literal("Settlement area is not loaded. Travel closer and try again."));
            return 0;
        }
        Zombie unit = EntityType.ZOMBIE.create(level);
        if (unit == null) {
            player.sendSystemMessage(Component.literal("Could not create the unit."));
            return 0;
        }
        if (!orc) unit.setBaby(true);
        unit.moveTo(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, player.getYRot(), 0.0F);
        int count = kingdom.getInt(orc ? "orcsRecruited" : "dwarvesRecruited") + 1;
        String name = (orc ? "Orc Warrior " : "Dwarf Warrior ") + count;
        unit.setCustomName(Component.literal(name));
        unit.setCustomNameVisible(true);
        unit.setCanPickUpLoot(false);
        unit.setPersistenceRequired();
        unit.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, dyedLeather(Items.LEATHER_HELMET, orc ? 0x8B2020 : 0x514331));
        unit.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, dyedLeather(Items.LEATHER_CHESTPLATE, orc ? 0x42382C : 0x514331));
        unit.setItemSlot(net.minecraft.world.entity.EquipmentSlot.LEGS, dyedLeather(Items.LEATHER_LEGGINGS, orc ? 0x302923 : 0x514331));
        unit.setItemSlot(net.minecraft.world.entity.EquipmentSlot.FEET, dyedLeather(Items.LEATHER_BOOTS, 0x292522));
        unit.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new ItemStack(orc ? Items.IRON_AXE : Items.IRON_PICKAXE));
        if (!level.addFreshEntity(unit)) {
            player.sendSystemMessage(Component.literal("The unit could not enter the world; no emeralds were charged."));
            return 0;
        }
        kingdom.putInt("treasury", treasury - cost);
        kingdom.putInt(orc ? "orcsRecruited" : "dwarvesRecruited", count);
        player.sendSystemMessage(Component.literal("Recruited " + name + "! Treasury: " + kingdom.getInt("treasury") + " emeralds."));
        player.sendSystemMessage(Component.literal(orc
            ? "Prototype: a hostile green zombie with red headgear and an axe. Custom green Orc skin and Mohawk model are not implemented yet."
            : "Prototype: a baby zombie in dwarf-colored armor with a pickaxe. It is short, but the exact one-block Dwarf model is not implemented yet."));
        return Command.SINGLE_SUCCESS;
    }

    private static ItemStack dyedLeather(net.minecraft.world.item.Item item, int color) {
        ItemStack stack = new ItemStack(item);
        if (stack.getItem() instanceof DyeableLeatherItem leather) leather.setColor(stack, color);
        return stack;
    }
}
