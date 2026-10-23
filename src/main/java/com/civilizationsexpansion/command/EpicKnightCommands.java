package com.civilizationsexpansion.command;

import com.civilizationsexpansion.CivilizationsExpansion;
import com.mojang.brigadier.Command;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

/**
 * Epic Knights equipment preview. It is a stationary display, not a combat-ready soldier.
 * The spawn search avoids placing the display inside the settlement's walls or roof.
 */
@Mod.EventBusSubscriber(modid = CivilizationsExpansion.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class EpicKnightCommands {
    private static final String KINGDOM_KEY = "civilizations_expansion_kingdom";
    private static final int COST = 20;

    private EpicKnightCommands() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("kingdom")
            .then(Commands.literal("recruit")
                .then(Commands.literal("knight")
                    .executes(context -> recruit(context.getSource().getPlayerOrException())))));
    }

    private static int recruit(ServerPlayer player) {
        CompoundTag data = player.getPersistentData();
        if (!data.contains(KINGDOM_KEY, CompoundTag.TAG_COMPOUND)
            || !data.getCompound(KINGDOM_KEY).getBoolean("founded")) {
            player.sendSystemMessage(Component.literal("Found a settlement first with /kingdom found."));
            return 0;
        }
        if (!ModList.get().isLoaded("magistuarmory")) {
            player.sendSystemMessage(Component.literal("Epic Knights is not loaded (expected mod ID magistuarmory). Install Epic Knights for Minecraft 1.20.1 Forge."));
            return 0;
        }
        CompoundTag kingdom = data.getCompound(KINGDOM_KEY);
        if (!player.level().dimension().location().toString().equals(kingdom.getString("dimension"))) {
            player.sendSystemMessage(Component.literal("Travel to your settlement's dimension before recruiting."));
            return 0;
        }
        int treasury = kingdom.getInt("treasury");
        if (treasury < COST) {
            player.sendSystemMessage(Component.literal("An Epic Knight display costs " + COST
                + " treasury emeralds. Current treasury: " + treasury + ". Use /kingdom contribute 20 if needed."));
            return 0;
        }
        ServerLevel level = player.serverLevel();
        BlockPos settlement = new BlockPos(kingdom.getInt("x"), kingdom.getInt("y"), kingdom.getInt("z"));
        BlockPos spawn = findSpawn(level, settlement);
        if (spawn == null) {
            player.sendSystemMessage(Component.literal("No safe, open space was found near your settlement. Clear a 1-block-wide, 2-block-high space on solid ground and try again."));
            return 0;
        }
        Zombie knight = EntityType.ZOMBIE.create(level);
        if (knight == null) {
            player.sendSystemMessage(Component.literal("Could not create the knight display."));
            return 0;
        }
        knight.moveTo(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, player.getYRot(), 0.0F);
        knight.setNoAi(true);
        knight.setSilent(true);
        knight.setPersistenceRequired();
        knight.setCustomName(Component.literal("Epic Knight Guard"));
        knight.setCustomNameVisible(true);
        equip(knight, EquipmentSlot.HEAD, "magistuarmoryaddon:dark_maximilian_helmet", Items.IRON_HELMET);
        equip(knight, EquipmentSlot.CHEST, "magistuarmoryaddon:dark_maximilian_chestplate", Items.IRON_CHESTPLATE);
        equip(knight, EquipmentSlot.LEGS, "magistuarmoryaddon:dark_maximilian_leggings", Items.IRON_LEGGINGS);
        equip(knight, EquipmentSlot.FEET, "magistuarmoryaddon:dark_maximilian_boots", Items.IRON_BOOTS);
        equip(knight, EquipmentSlot.MAINHAND, "magistuarmoryaddon:steel_longsword", Items.IRON_SWORD);
        equip(knight, EquipmentSlot.OFFHAND, "magistuarmory:steel_heatershield", Items.SHIELD);
        if (!level.addFreshEntity(knight)) {
            player.sendSystemMessage(Component.literal("The knight could not enter the world; no emeralds were charged."));
            return 0;
        }
        kingdom.putInt("treasury", treasury - COST);
        player.sendSystemMessage(Component.literal("Spawned an Epic Knight equipment display in open space. Treasury: "
            + kingdom.getInt("treasury") + " emeralds."));
        player.sendSystemMessage(Component.literal("Prototype limitation: this knight is still stationary and does not fight or follow orders."));
        return Command.SINGLE_SUCCESS;
    }

    private static BlockPos findSpawn(ServerLevel level, BlockPos center) {
        // Search outward so a wall, roof, or enclosed town hall does not hide the display.
        for (int radius = 2; radius <= 10; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                    for (int dy = 0; dy <= 3; dy++) {
                        BlockPos feet = center.offset(dx, dy, dz);
                        BlockPos head = feet.above();
                        BlockPos floor = feet.below();
                        if (!level.hasChunkAt(feet) || !level.hasChunkAt(head) || !level.hasChunkAt(floor)) continue;
                        if (level.getBlockState(floor).isSolidRender(level, floor)
                            && level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                            && level.getBlockState(head).getCollisionShape(level, head).isEmpty()) {
                            return feet;
                        }
                    }
                }
            }
        }
        return null;
    }

    private static void equip(Zombie mob, EquipmentSlot slot, String itemId, Item fallback) {
        ResourceLocation id = ResourceLocation.tryParse(itemId);
        if (id != null && BuiltInRegistries.ITEM.containsKey(id)) {
            mob.setItemSlot(slot, new ItemStack(BuiltInRegistries.ITEM.get(id)));
        } else {
            mob.setItemSlot(slot, new ItemStack(fallback));
        }
    }
}