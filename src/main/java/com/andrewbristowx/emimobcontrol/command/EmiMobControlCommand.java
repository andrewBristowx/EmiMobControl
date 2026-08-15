package com.andrewbristowx.emimobcontrol.command;

import com.andrewbristowx.emimobcontrol.config.EmiMobControlConfig;
import com.andrewbristowx.emimobcontrol.system.MobCleanupService;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public final class EmiMobControlCommand {
    private EmiMobControlCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("emimobcontrol")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("status").executes(context -> status(context.getSource())))
                .then(Commands.literal("reload").executes(context -> reload(context.getSource())))
                .then(Commands.literal("cleanup")
                        .then(Commands.literal("now").executes(context -> cleanup(context.getSource())))));
    }

    private static int status(CommandSourceStack source) {
        EmiMobControlConfig config = EmiMobControlConfig.get();
        source.sendSuccess(() -> Component.literal("EmiMobControl: limpieza "
                        + (config.cleanupEnabled ? "activa" : "desactivada")
                        + " • intervalo " + config.cleanupIntervalMinutes + " min"
                        + " • próxima en " + MobCleanupService.secondsRemaining() + " s")
                .withStyle(ChatFormatting.AQUA), false);
        return 1;
    }

    private static int reload(CommandSourceStack source) {
        EmiMobControlConfig.load();
        MobCleanupService.resetSchedule();
        source.sendSuccess(() -> Component.literal("Configuración de EmiMobControl recargada.")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int cleanup(CommandSourceStack source) {
        int removed = MobCleanupService.cleanupNow(source.getServer());
        source.sendSuccess(() -> Component.literal("Limpieza manual terminada: " + removed + " mobs retirados.")
                .withStyle(ChatFormatting.GREEN), true);
        return removed;
    }
}
