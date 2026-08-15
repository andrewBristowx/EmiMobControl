package com.andrewbristowx.emimobcontrol.system;

import com.andrewbristowx.emimobcontrol.config.EmiMobControlConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.npc.AbstractVillager;

import java.util.ArrayList;

public final class MobCleanupService {
    private static long ticksUntilCleanup = -1;

    private MobCleanupService() {}

    public static void tick(MinecraftServer server) {
        EmiMobControlConfig config = EmiMobControlConfig.get();
        if (!config.cleanupEnabled) {
            ticksUntilCleanup = -1;
            return;
        }

        if (ticksUntilCleanup < 0) ticksUntilCleanup = intervalTicks(config);
        ticksUntilCleanup--;

        if (ticksUntilCleanup == 1200) announce(server, "Limpieza segura de mobs en 60 segundos.", ChatFormatting.YELLOW);
        if (ticksUntilCleanup == 600) announce(server, "Limpieza segura de mobs en 30 segundos.", ChatFormatting.YELLOW);
        if (ticksUntilCleanup == 200) announce(server, "Limpieza segura de mobs en 10 segundos.", ChatFormatting.GOLD);
        if (ticksUntilCleanup == 100) announce(server, "Limpieza segura de mobs en 5 segundos.", ChatFormatting.RED);

        if (ticksUntilCleanup <= 0) {
            int removed = cleanupNow(server);
            announce(server, "Limpieza terminada: " + removed + " mobs retirados.", ChatFormatting.GREEN);
            ticksUntilCleanup = intervalTicks(config);
        }
    }

    public static int cleanupNow(MinecraftServer server) {
        int removed = 0;
        for (ServerLevel level : server.getAllLevels()) {
            ArrayList<Mob> candidates = new ArrayList<>();
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof Mob mob && shouldRemove(level, mob)) candidates.add(mob);
            }
            for (Mob mob : candidates) {
                if (!mob.isRemoved()) {
                    mob.discard();
                    removed++;
                }
            }
        }
        return removed;
    }

    public static long secondsRemaining() {
        return Math.max(0, ticksUntilCleanup / 20);
    }

    public static void resetSchedule() {
        ticksUntilCleanup = -1;
    }

    private static boolean shouldRemove(ServerLevel level, Mob mob) {
        EmiMobControlConfig config = EmiMobControlConfig.get();
        var typeId = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType());
        if (!"minecraft".equals(typeId.getNamespace())) return false;
        if (config.excludedEntityIds.contains(typeId.toString())) return false;
        if (config.protectNamedMobs && mob.hasCustomName()) return false;
        if (config.protectTamedMobs && mob instanceof TamableAnimal tamable && tamable.isTame()) return false;
        if (config.protectLeashedMobs && mob.isLeashed()) return false;
        if (config.protectPersistentMobs && mob.isPersistenceRequired()) return false;
        if (config.protectVillagers && mob instanceof AbstractVillager) return false;
        if (mob.isPassenger() || mob.isVehicle()) return false;
        if (config.cleanupMinimumPlayerDistance > 0
                && level.getNearestPlayer(mob, config.cleanupMinimumPlayerDistance) != null) return false;

        MobCategory category = mob.getType().getCategory();
        if (category == MobCategory.MONSTER) return config.removeHostileMobs;
        return config.removePassiveMobs;
    }

    private static long intervalTicks(EmiMobControlConfig config) {
        return config.cleanupIntervalMinutes * 60L * 20L;
    }

    private static void announce(MinecraftServer server, String message, ChatFormatting color) {
        server.getPlayerList().broadcastSystemMessage(Component.literal(message).withStyle(color), false);
    }
}
