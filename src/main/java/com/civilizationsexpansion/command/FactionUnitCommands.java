package com.civilizationsexpansion.command;

import com.civilizationsexpansion.CivilizationsExpansion;
import com.civilizationsexpansion.civilization.CivilizationCatalog;
import com.mojang.brigadier.Command;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.DyeableLeatherItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Locale;

/**
 * Recruitable combat-unit layer for all seven player civilizations.
 * Vanilla mobs/equipment are used as a compatible first-pass stand-in for custom models.
 */
@Mod.EventBusSubscriber(modid = CivilizationsExpansion.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class FactionUnitCommands {
    private static final String KINGDOM_KEY = "civilizations_expansion_kingdom";
    private FactionUnitCommands() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        var recruit = Commands.literal("recruit")
            .then(Commands.literal("spearman").executes(c -> recruit(c.getSource().getPlayerOrException(), "spearman")))
            .then(Commands.literal("axeman").executes(c -> recruit(c.getSource().getPlayerOrException(), "axeman")))
            .then(Commands.literal("swordsman").executes(c -> recruit(c.getSource().getPlayerOrException(), "swordsman")))
            .then(Commands.literal("knight").executes(c -> recruit(c.getSource().getPlayerOrException(), "knight")))
            .then(Commands.literal("archer").executes(c -> recruit(c.getSource().getPlayerOrException(), "archer")))
            .then(Commands.literal("orc").executes(c -> recruit(c.getSource().getPlayerOrException(), "orc")))
            .then(Commands.literal("dwarf").executes(c -> recruit(c.getSource().getPlayerOrException(), "dwarf")))
            .then(Commands.literal("javelinman").executes(c -> recruit(c.getSource().getPlayerOrException(), "javelinman")))
            .then(Commands.literal("men_at_arms").executes(c -> recruit(c.getSource().getPlayerOrException(), "men_at_arms")))
            .then(Commands.literal("jade_lancer").executes(c -> recruit(c.getSource().getPlayerOrException(), "jade_lancer")))
            .then(Commands.literal("priest").executes(c -> recruit(c.getSource().getPlayerOrException(), "priest")));
        event.getDispatcher().register(Commands.literal("kingdom").then(recruit));
    }

    private static int recruit(ServerPlayer player, String unitType) {
        CompoundTag playerData = player.getPersistentData();
        if (!playerData.contains(KINGDOM_KEY, CompoundTag.TAG_COMPOUND)
            || !playerData.getCompound(KINGDOM_KEY).getBoolean("founded")) {
            player.sendSystemMessage(Component.literal("Found a settlement first with /kingdom found."));
            return 0;
        }
        CompoundTag kingdom = playerData.getCompound(KINGDOM_KEY);
        String civ = kingdom.getString("civilization");
        if (!unitAllowed(civ, unitType)) {
            player.sendSystemMessage(Component.literal("The " + unitType.replace('_', ' ')
                + " is not available to " + civ + ". Use /civilizations roster " + civ + " to view its roster."));
            return 0;
        }
        if (!player.level().dimension().location().toString().equals(kingdom.getString("dimension"))) {
            player.sendSystemMessage(Component.literal("Travel to your settlement's dimension before recruiting."));
            return 0;
        }
        int cost = cost(unitType);
        int treasury = kingdom.getInt("treasury");
        if (treasury < cost) {
            player.sendSystemMessage(Component.literal("Recruiting this unit costs " + cost
                + " treasury emeralds; your balance is " + treasury + "."));
            return 0;
        }
        ServerLevel level = player.serverLevel();
        BlockPos center = new BlockPos(kingdom.getInt("x"), kingdom.getInt("y"), kingdom.getInt("z"));
        if (!level.hasChunkAt(center)) {
            player.sendSystemMessage(Component.literal("Settlement area is not loaded. Travel closer and try again."));
            return 0;
        }
        BlockPos spawn = findSpawn(level, center, unitType);
        if (spawn == null) {
            player.sendSystemMessage(Component.literal("No safe space near the settlement to spawn this unit. Clear an area and try again."));
            return 0;
        }
        Entity unit = createEntity(level, unitType);
        if (unit == null) {
            player.sendSystemMessage(Component.literal("Could not create this unit."));
            return 0;
        }
        unit.moveTo(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, player.getYRot(), 0.0F);
        unit.setCustomName(Component.literal(displayName(civ, unitType)));
        unit.setCustomNameVisible(true);
        unit.setInvulnerable(false);
        if (unit instanceof Zombie zombie) {
            zombie.setCanPickUpLoot(false);
            zombie.setPersistenceRequired();
            equipZombie(zombie, civ, unitType);
        } else if (unit instanceof AbstractSkeleton skeleton) {
            skeleton.setPersistenceRequired();
            skeleton.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(unitType.equals("javelinman") ? Items.TRIDENT : Items.BOW));
            equipArmor(skeleton, civ);
        } else if (unit instanceof Pillager pillager) {
            pillager.setPersistenceRequired();
            pillager.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.CROSSBOW));
            equipArmor(pillager, civ);
        }
        if (!level.addFreshEntity(unit)) {
            player.sendSystemMessage(Component.literal("The unit could not enter the world; no emeralds were charged."));
            return 0;
        }
        kingdom.putInt("treasury", treasury - cost);
        String key = "recruited_" + unitType;
        kingdom.putInt(key, kingdom.getInt(key) + 1);
        kingdom.putInt("armySize", kingdom.getInt("armySize") + 1);
        player.sendSystemMessage(Component.literal("Recruited " + displayName(civ, unitType) + ". Army size: "
            + kingdom.getInt("armySize") + "; treasury: " + kingdom.getInt("treasury") + " emeralds."));
        player.sendSystemMessage(Component.literal("These units use vanilla mob AI and temporary visuals. Unique models, faction abilities, mounts, and siege logic are not implemented yet."));
        return Command.SINGLE_SUCCESS;
    }

    private static boolean unitAllowed(String civ, String type) {
        return switch (type) {
            case "orc" -> civ.equals("orcs");
            case "dwarf" -> civ.equals("dwarves");
            case "javelinman" -> civ.equals("visigothians");
            case "men_at_arms" -> civ.equals("caladrians");
            case "jade_lancer", "priest" -> civ.equals("jade_warriors");
            case "spearman", "axeman", "swordsman", "knight", "archer" ->
                civ.equals("valarians") || civ.equals("bohemians") || civ.equals("caladrians") || civ.equals("visigothians");
            default -> false;
        };
    }

    private static int cost(String type) {
        return switch (type) {
            case "spearman", "archer", "orc", "dwarf", "javelinman" -> 8;
            case "axeman", "swordsman", "men_at_arms", "jade_lancer" -> 12;
            case "priest" -> 16;
            case "knight" -> 20;
            default -> 8;
        };
    }

    private static Entity createEntity(ServerLevel level, String type) {
        if (type.equals("archer") || type.equals("javelinman")) return EntityType.SKELETON.create(level);
        if (type.equals("men_at_arms")) return EntityType.PILLAGER.create(level);
        return EntityType.ZOMBIE.create(level);
    }

    private static BlockPos findSpawn(ServerLevel level, BlockPos center, String type) {
        for (int radius = 3; radius <= 8; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.abs(dx) != radius && Math.abs(dz) != radius) continue;
                    BlockPos ground = center.offset(dx, 0, dz);
                    BlockPos feet = ground.above();
                    if (!level.hasChunkAt(feet) || !level.getBlockState(ground).isSolid()
                        || !level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                        || !level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()) continue;
                    return feet;
                }
            }
        }
        return null;
    }

    private static void equipZombie(Zombie z, String civ, String type) {
        equipArmor(z, civ);
        Item weapon = switch (type) {
            case "spearman", "javelinman" -> Items.TRIDENT;
            case "axeman", "orc" -> Items.IRON_AXE;
            case "knight", "jade_lancer" -> Items.IRON_SWORD;
            case "dwarf" -> Items.IRON_PICKAXE;
            case "priest" -> Items.GOLDEN_AXE;
            default -> Items.IRON_SWORD;
        };
        z.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(weapon));
        if (type.equals("dwarf")) z.setBaby(true);
        if (type.equals("knight")) {
            z.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
            z.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
            z.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
            z.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
        }
    }

    private static void equipArmor(net.minecraft.world.entity.LivingEntity entity, String civ) {
        int color = switch (civ) {
            case "orcs" -> 0x47752A;
            case "dwarves" -> 0x62503A;
            case "jade_warriors" -> 0x178A65;
            case "caladrians" -> 0x8A2929;
            case "visigothians" -> 0xB49B54;
            case "bohemians" -> 0x465E9C;
            default -> 0x777777;
        };
        entity.setItemSlot(EquipmentSlot.HEAD, dyedLeather(Items.LEATHER_HELMET, color));
        entity.setItemSlot(EquipmentSlot.CHEST, dyedLeather(Items.LEATHER_CHESTPLATE, color));
        entity.setItemSlot(EquipmentSlot.LEGS, dyedLeather(Items.LEATHER_LEGGINGS, color));
        entity.setItemSlot(EquipmentSlot.FEET, dyedLeather(Items.LEATHER_BOOTS, color));
    }

    private static ItemStack dyedLeather(Item item, int color) {
        ItemStack stack = new ItemStack(item);
        if (stack.getItem() instanceof DyeableLeatherItem leather) leather.setColor(stack, color);
        return stack;
    }

    private static String displayName(String civ, String type) {
        String c = CivilizationCatalog.byId(civ) == null ? civ : CivilizationCatalog.byId(civ).displayName();
        String t = type.replace('_', ' ');
        return c + " " + Character.toUpperCase(t.charAt(0)) + t.substring(1);
    }
}
