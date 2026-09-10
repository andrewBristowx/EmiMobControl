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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;

public final class MobCleanupService {
    /** How many mobs get the (relatively expensive - it checks every online player's distance)
     * {@link #shouldRemove} test and, if it matches, get discarded, per tick. Scanning every entity in
     * every loaded level in one go was a multi-second freeze on a map with the mob counts this server
     * sees (thousands of entities) - the same reason {@code ItemCleanupService} already spreads its
     * own removals across ticks instead of draining everything at once. */
    private static final int SCAN_PER_TICK = 200;

    /** Matches TrialSpawnerMobTagMixin's tag on the Chainacobblemon side - kept as a plain string since
     * this module compiles against official Mojang mappings and Chainacobblemon against Yarn, so there's
     * no shared class to reference directly. */
    private static final String TRIAL_SPAWNED_TAG = "chaina_trial_spawned";

    private static long ticksUntilCleanup = -1;
    private static final Deque<ServerLevel> PENDING_LEVEL_SCAN = new ArrayDeque<>();
    private static final Deque<Mob> PENDING_SCAN = new ArrayDeque<>();
    private static int removedThisPass;
    private static boolean levelScanInProgress;
    private static boolean passInProgress;

    private MobCleanupService() {}

    public static void tick(MinecraftServer server) {
        if (levelScanInProgress) {
            continueLevelScan();
            return;
        }
        if (passInProgress) {
            continuePass(server);
            return;
        }

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
            startPass(server);
        }
    }

    /** Queues every level to be scanned one at a time instead of walking every level's full entity
     * list back to back in the same tick - on a map with this many dimensions and mob counts, that
     * single-tick scan was itself a multi-second freeze, the same problem {@code ItemCleanupService}
     * and {@code EntityCapService} already spread across ticks on the Chainacobblemon side. */
    private static void startPass(MinecraftServer server) {
        PENDING_LEVEL_SCAN.clear();
        for (ServerLevel level : server.getAllLevels()) PENDING_LEVEL_SCAN.add(level);
        PENDING_SCAN.clear();
        removedThisPass = 0;
        levelScanInProgress = true;
    }

    /** One level's worth of mobs snapshotted per tick instead of every level in the same tick. Once
     * every level has been scanned, {@link #continuePass} takes over to check and discard them. */
    private static void continueLevelScan() {
        ServerLevel level = PENDING_LEVEL_SCAN.poll();
        if (level != null) {
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof Mob mob) PENDING_SCAN.add(mob);
            }
        }
        if (PENDING_LEVEL_SCAN.isEmpty()) {
            levelScanInProgress = false;
            passInProgress = true;
        }
    }

    private static void continuePass(MinecraftServer server) {
        for (int i = 0; i < SCAN_PER_TICK && !PENDING_SCAN.isEmpty(); i++) {
            Mob mob = PENDING_SCAN.poll();
            if (mob.isRemoved() || !(mob.level() instanceof ServerLevel level)) continue;
            if (shouldRemove(level, mob)) {
                mob.discard();
                removedThisPass++;
            }
        }
        if (PENDING_SCAN.isEmpty()) {
            passInProgress = false;
            announce(server, "Limpieza terminada: " + removedThisPass + " mobs retirados.", ChatFormatting.GREEN);
            ticksUntilCleanup = intervalTicks(EmiMobControlConfig.get());
        }
    }

    /** Synchronous, whole-server sweep for admin use (the {@code /emimobcontrol} force-cleanup
     * command) - unlike the scheduled pass above, an admin explicitly asking for this right now is
     * expected to accept a one-off pause, the same tradeoff {@code ItemCleanupService.forceSweep}
     * already makes for items. */
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
        levelScanInProgress = false;
        passInProgress = false;
        PENDING_LEVEL_SCAN.clear();
        PENDING_SCAN.clear();
    }

    private static boolean shouldRemove(ServerLevel level, Mob mob) {
        EmiMobControlConfig config = EmiMobControlConfig.get();
        var typeId = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType());
        if (!"minecraft".equals(typeId.getNamespace())) return false;
        if (config.excludedEntityIds.contains(typeId.toString())) return false;
        if (config.protectNamedMobs && mob.hasCustomName()) return false;
        if (config.protectTamedMobs && mob instanceof TamableAnimal tamable && tamable.isTame()) return false;
        if (config.protectLeashedMobs && mob.isLeashed()) return false;
        if (config.protectPersistentMobs && mob.isPersistenceRequired()) {
            boolean trialSpawned = config.cleanupTrialSpawnedMobs && mob.getTags().contains(TRIAL_SPAWNED_TAG);
            if (!trialSpawned) return false;
        }
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
