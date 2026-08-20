package com.andrewbristowx.emimobcontrol.command;

import com.andrewbristowx.emimobcontrol.config.EmiMobControlConfig;
import com.andrewbristowx.emimobcontrol.system.MobCleanupService;
import com.andrewbristowx.emimobcontrol.system.PassiveMobControlService;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public final class EmiMobControlCommand {
    private EmiMobControlCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("chainamobcontrol")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("status").executes(context -> status(context.getSource())))
                .then(Commands.literal("reload").executes(context -> reload(context.getSource())))
                .then(Commands.literal("cleanup")
                        .then(Commands.literal("now").executes(context -> cleanup(context.getSource())))
                        .then(Commands.literal("passives").executes(context -> cleanupPassives(context.getSource()))))
                .then(Commands.literal("passive")
                        .then(Commands.literal("stats").executes(context -> passiveStats(context.getSource())))
                        .then(Commands.literal("reset").executes(context -> passiveReset(context.getSource())))));
    }

    private static int status(CommandSourceStack source) {
        EmiMobControlConfig config = EmiMobControlConfig.get();
        source.sendSuccess(() -> Component.literal("ChainaMobControl: limpieza "
                        + (config.cleanupEnabled ? "activa" : "desactivada")
                        + " • intervalo " + config.cleanupIntervalMinutes + " min"
                        + " • próxima en " + MobCleanupService.secondsRemaining() + " s"
                        + " • fauna natural " + (config.blockNaturalPassiveSpawns ? "bloqueada" : "permitida")
                        + " • chunks pendientes " + PassiveMobControlService.pendingChunks())
                .withStyle(ChatFormatting.AQUA), false);
        return 1;
    }

    private static int reload(CommandSourceStack source) {
        EmiMobControlConfig.load();
        MobCleanupService.resetSchedule();
        source.sendSuccess(() -> Component.literal("Configuración de ChainaMobControl recargada.")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int cleanup(CommandSourceStack source) {
        int removed = MobCleanupService.cleanupNow(source.getServer());
        source.sendSuccess(() -> Component.literal("Limpieza manual terminada: " + removed + " mobs retirados.")
                .withStyle(ChatFormatting.GREEN), true);
        return Math.max(1, removed);
    }

    private static int cleanupPassives(CommandSourceStack source) {
        int removed = PassiveMobControlService.cleanupLoadedPassives(source.getServer());
        source.sendSuccess(() -> Component.literal("Fauna vanilla cargada retirada: " + removed + " entidades.")
                .withStyle(ChatFormatting.GREEN), true);
        return Math.max(1, removed);
    }

    private static int passiveStats(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("Control de fauna: "
                        + PassiveMobControlService.cleanedChunkCount() + " chunks revisados • "
                        + PassiveMobControlService.pendingChunks() + " pendientes.")
                .withStyle(ChatFormatting.AQUA), false);
        return 1;
    }

    private static int passiveReset(CommandSourceStack source) {
        PassiveMobControlService.resetCleanedChunks(source.getServer());
        source.sendSuccess(() -> Component.literal("Registro de chunks revisados reiniciado. Se limpiarán de nuevo al cargarse.")
                .withStyle(ChatFormatting.YELLOW), true);
        return 1;
    }
}
