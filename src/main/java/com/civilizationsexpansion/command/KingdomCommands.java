package com.civilizationsexpansion.command;

import com.civilizationsexpansion.CivilizationsExpansion;
import com.civilizationsexpansion.civilization.Civilization;
import com.civilizationsexpansion.civilization.CivilizationCatalog;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
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
                .then(Commands.literal("recruit")
                    .then(Commands.literal("villager")
                        .executes(context -> recruitVillager(context.getSource().getPlayerOrException()))))
                .then(Commands.literal("build")
                    .then(Commands.argument("structure", StringArgumentType.word())
                        .executes(context -> buildStructure(context.getSource().getPlayerOrException(), StringArgumentType.getString(context, "structure")))))
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
        player.sendSystemMessage(Component.literal("Build: townhall, house, watchtower, horde_tent, jade_palace, jade_pavilion, dynasty_wall, dragon_tower, dwarven_keep, caladrian_keep."));
        player.sendSystemMessage(Component.literal("Buildings placed: " + kingdom.getInt("buildings") + (kingdom.getString("civilization").equals("orcs") ? "/20 Orc building limit" : "")));
        player.sendSystemMessage(Component.literal("Recruit with /kingdom recruit villager (4 treasury emeralds)."));
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



    private static int recruitVillager(ServerPlayer player) {
        CompoundTag kingdom = getKingdom(player);
        if (kingdom == null) {
            player.sendSystemMessage(Component.literal("Found a settlement first with /kingdom found."));
            return 0;
        }
        int treasury = kingdom.getInt("treasury");
        if (treasury < 4) {
            player.sendSystemMessage(Component.literal("Recruiting a villager costs 4 treasury emeralds; your treasury has " + treasury + "."));
            return 0;
        }
        if (!player.level().dimension().location().toString().equals(kingdom.getString("dimension"))) {
            player.sendSystemMessage(Component.literal("Travel to your settlement's dimension before recruiting."));
            return 0;
        }
        ServerLevel level = player.serverLevel();
        BlockPos pos = new BlockPos(kingdom.getInt("x"), kingdom.getInt("y"), kingdom.getInt("z")).offset(4, 1, 4);
        if (!level.hasChunkAt(pos)) {
            player.sendSystemMessage(Component.literal("Settlement area is not loaded. Travel closer and try again."));
            return 0;
        }
        Villager villager = net.minecraft.world.entity.EntityType.VILLAGER.create(level);
        if (villager == null) {
            player.sendSystemMessage(Component.literal("Could not create a villager."));
            return 0;
        }
        villager.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, player.getYRot(), 0.0F);
        villager.setCustomName(Component.literal(kingdom.getString("name") + " Resident"));
        villager.setCustomNameVisible(true);
        if (!level.addFreshEntity(villager)) {
            player.sendSystemMessage(Component.literal("Could not add the villager to this world."));
            return 0;
        }
        kingdom.putInt("treasury", treasury - 4);
        kingdom.putInt("population", kingdom.getInt("population") + 1);
        player.sendSystemMessage(Component.literal("A villager joined " + kingdom.getString("name") + "! Treasury: " + kingdom.getInt("treasury") + "; recorded population: " + kingdom.getInt("population") + "."));
        player.sendSystemMessage(Component.literal("This is a regular Minecraft villager for now; faction-specific workers are planned."));
        return 1;
    }

    private static int buildStructure(ServerPlayer player, String requested) {
        CompoundTag kingdom = getKingdom(player);
        if (kingdom == null) {
            player.sendSystemMessage(Component.literal("Found a settlement first with /kingdom found."));
            return 0;
        }
        String type = requested.toLowerCase(java.util.Locale.ROOT);
        int cost;
        switch (type) {
            case "townhall" -> cost = 5;
            case "house" -> cost = 3;
            case "watchtower" -> cost = 4;
            case "horde_tent" -> cost = 6;
            case "jade_palace" -> cost = 18;
            case "jade_pavilion" -> cost = 8;
            case "dynasty_wall" -> cost = 7;
            case "dragon_tower" -> cost = 12;
            case "dwarven_keep" -> cost = 14;
            case "caladrian_keep" -> cost = 16;
            default -> {
                player.sendSystemMessage(Component.literal("Unknown structure. Supported structures: townhall, house, watchtower, horde_tent, jade_palace, jade_pavilion, dynasty_wall, dragon_tower, dwarven_keep, caladrian_keep."));
                return 0;
            }
        }
        String civId = kingdom.getString("civilization");
        if ((type.equals("horde_tent") && !civId.equals("orcs"))
            || ((type.equals("jade_palace") || type.equals("jade_pavilion") || type.equals("dynasty_wall") || type.equals("dragon_tower")) && !civId.equals("jade_warriors"))
            || (type.equals("dwarven_keep") && !civId.equals("dwarves"))
            || (type.equals("caladrian_keep") && !civId.equals("caladrians"))) {
            player.sendSystemMessage(Component.literal("That structure is reserved for its matching civilization."));
            return 0;
        }
        if (civId.equals("orcs") && kingdom.getInt("buildings") >= 20) {
            player.sendSystemMessage(Component.literal("The Orc Horde cannot maintain more than 20 buildings."));
            return 0;
        }
        int treasury = kingdom.getInt("treasury");
        if (treasury < cost) {
            player.sendSystemMessage(Component.literal("This costs " + cost + " treasury emeralds; your treasury has " + treasury + "."));
            return 0;
        }
        if (!player.level().dimension().location().toString().equals(kingdom.getString("dimension"))) {
            player.sendSystemMessage(Component.literal("Travel to your settlement's dimension before building."));
            return 0;
        }
        ServerLevel level = player.serverLevel();
        BlockPos p = new BlockPos(kingdom.getInt("x"), kingdom.getInt("y"), kingdom.getInt("z"));
        // Place a little above the recorded block so the structure does not replace its foundation.
        p = p.above();
        String civ = kingdom.getString("civilization");
        if (type.equals("townhall")) {
            var wall = civ.equals("dwarves") ? Blocks.DEEPSLATE_BRICKS : civ.equals("orcs") ? Blocks.COBBLED_DEEPSLATE : civ.equals("jade_warriors") ? Blocks.POLISHED_ANDESITE : Blocks.STONE_BRICKS;
            var roof = civ.equals("orcs") ? Blocks.DARK_OAK_PLANKS : civ.equals("jade_warriors") ? Blocks.OAK_PLANKS : Blocks.SPRUCE_PLANKS;
            for (int x=-3;x<=3;x++) for (int z=-3;z<=3;z++) {
                level.setBlock(p.offset(x,0,z), wall.defaultBlockState(), 3);
                for (int y=1;y<=3;y++) {
                    boolean edge=Math.abs(x)==3 || Math.abs(z)==3;
                    boolean doorway=(x==0 && z==-3);
                    level.setBlock(p.offset(x,y,z), edge && !doorway ? wall.defaultBlockState() : Blocks.AIR.defaultBlockState(), 3);
                }
                level.setBlock(p.offset(x,4,z), roof.defaultBlockState(), 3);
            }
            level.setBlock(p.offset(0,1,0), Blocks.CRAFTING_TABLE.defaultBlockState(), 3);
            level.setBlock(p.offset(1,1,0), Blocks.BARREL.defaultBlockState(), 3);
        } else if (type.equals("house")) {
            var wall = civ.equals("dwarves") ? Blocks.COBBLED_DEEPSLATE : civ.equals("jade_warriors") ? Blocks.BAMBOO_PLANKS : Blocks.OAK_PLANKS;
            BlockPos q=p.offset(8,0,0);
            for (int x=-2;x<=2;x++) for (int z=-2;z<=2;z++) {
                level.setBlock(q.offset(x,0,z), Blocks.COBBLESTONE.defaultBlockState(), 3);
                for (int y=1;y<=3;y++) {
                    boolean edge=Math.abs(x)==2 || Math.abs(z)==2;
                    boolean doorway=(x==0 && z==-2);
                    level.setBlock(q.offset(x,y,z), edge && !doorway ? wall.defaultBlockState() : Blocks.AIR.defaultBlockState(), 3);
                }
                level.setBlock(q.offset(x,4,z), Blocks.SPRUCE_PLANKS.defaultBlockState(), 3);
            }
            level.setBlock(q.offset(0,1,0), Blocks.CRAFTING_TABLE.defaultBlockState(), 3);
        } else {
            BlockPos q=p.offset(-8,0,0);
            for (int y=0;y<=5;y++) for (int x=-1;x<=1;x++) for (int z=-1;z<=1;z++) {
                boolean edge=Math.abs(x)==1 || Math.abs(z)==1;
                level.setBlock(q.offset(x,y,z), (y==0 || edge) ? Blocks.DEEPSLATE_BRICKS.defaultBlockState() : Blocks.AIR.defaultBlockState(), 3);
            }
            for (int x=-2;x<=2;x++) for (int z=-2;z<=2;z++) level.setBlock(q.offset(x,6,z), Blocks.SPRUCE_PLANKS.defaultBlockState(), 3);
            for (int y=1;y<=4;y++) level.setBlock(q.offset(0,y,0), Blocks.LADDER.defaultBlockState(), 3);
        }
        kingdom.putInt("treasury", treasury-cost);
        kingdom.putInt("buildings", kingdom.getInt("buildings")+1);
        player.sendSystemMessage(Component.literal("Built " + type + "! Treasury now has " + kingdom.getInt("treasury") + " emeralds."));
        player.sendSystemMessage(Component.literal("A simple physical structure has been placed at your settlement. Detailed faction designs are still in development."));
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
