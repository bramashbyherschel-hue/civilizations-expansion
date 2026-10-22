package com.civilizationsexpansion.command;

import com.civilizationsexpansion.CivilizationsExpansion;
import com.civilizationsexpansion.civilization.Civilization;
import com.mojang.brigadier.Command;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Initial in-game diagnostic command. This verifies the civilization catalog
 * is accessible from a running Forge world; it does not spawn faction content.
 */
@Mod.EventBusSubscriber(modid = CivilizationsExpansion.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CivilizationCommands {
    private CivilizationCommands() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
            Commands.literal("civilizations")
                .requires(source -> source.hasPermission(0))
                .executes(context -> {
                    context.getSource().sendSuccess(
                        () -> Component.literal("Civilizations Expansion — framework catalog"),
                        false
                    );
                    for (Civilization civilization : Civilization.values()) {
                        context.getSource().sendSuccess(
                            () -> Component.literal("• " + civilization.displayName() + " [" + civilization.id() + "]"),
                            false
                        );
                    }
                    context.getSource().sendSuccess(
                        () -> Component.literal("These are framework entries only; faction gameplay is still in development."),
                        false
                    );
                    return Command.SINGLE_SUCCESS;
                })
        );
    }
}
