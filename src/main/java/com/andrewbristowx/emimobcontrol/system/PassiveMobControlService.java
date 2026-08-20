package com.andrewbristowx.emimobcontrol.system;

import com.andrewbristowx.emimobcontrol.EmiMobControl;
import com.andrewbristowx.emimobcontrol.config.EmiMobControlConfig;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Control de fauna vanilla de CHAINA.
 *
 * <p>Dos capas independientes:</p>
 * <ul>
 *     <li>bloquea únicamente spawns NATURAL/CHUNK_GENERATION de la lista configurada;</li>
 *     <li>limpia una sola vez cada chunk pregenerado al cargarse, guardando el progreso.</li>
 * </ul>
 *
 * <p>No usa categorías amplias para evitar tocar Pokémon, NPC, aldeanos, gólems o mobs de otros mods.</p>
 */
public final class PassiveMobControlService {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String STATE_FILE = "chainamobcontrol-passive-cleaned.json";

    private static final ArrayDeque<PendingChunk> QUEUE = new ArrayDeque<>();
    private static final Set<String> QUEUED = new HashSet<>();
    private static SavedState state = new SavedState();
    private static long serviceTick = 0L;
    private static long ticksSinceSave = 0L;
    private static boolean dirty = false;

    private PassiveMobControlService() {}

    public static void onServerStarted(MinecraftServer server) {
        serviceTick = 0L;
        ticksSinceSave = 0L;
        QUEUE.clear();
        QUEUED.clear();
        loadState(server);
    }

    public static void onServerStopping(MinecraftServer server) {
        saveState(server, true);
        QUEUE.clear();
        QUEUED.clear();
    }

    public static void onChunkLoad(ServerLevel level, LevelChunk chunk) {
        EmiMobControlConfig config = EmiMobControlConfig.get();
        if (!config.cleanPregeneratedPassiveMobs) return;

        ChunkPos pos = chunk.getPos();
        if (isCleaned(level, pos.x, pos.z)) return;

        String queueKey = queueKey(level, pos.x, pos.z);
        if (!QUEUED.add(queueKey)) return;

        long readyAt = serviceTick + config.passiveChunkLoadDelayTicks;
        QUEUE.addLast(new PendingChunk(level, pos.x, pos.z, readyAt, queueKey));
    }

    public static void tick(MinecraftServer server) {
        serviceTick++;
        ticksSinceSave++;

        EmiMobControlConfig config = EmiMobControlConfig.get();
        if (config.cleanPregeneratedPassiveMobs) {
            int budget = config.passiveCleanupChunksPerTick;
            while (budget-- > 0 && !QUEUE.isEmpty()) {
                PendingChunk pending = QUEUE.peekFirst();
                if (pending.readyAtTick > serviceTick) break;
                QUEUE.removeFirst();
                QUEUED.remove(pending.queueKey);

                if (isCleaned(pending.level, pending.chunkX, pending.chunkZ)) continue;
                if (pending.level.getChunkSource().getChunkNow(pending.chunkX, pending.chunkZ) == null) {
                    // No marcamos como limpio un chunk que se descargó antes de poder revisarlo.
                    continue;
                }

                int removed = cleanupChunk(pending.level, pending.chunkX, pending.chunkZ);
                markCleaned(pending.level, pending.chunkX, pending.chunkZ);
                if (removed > 0) {
                    EmiMobControl.LOGGER.debug("Fauna vanilla pregenerada retirada: {} entidades en {} [{}, {}].",
                            removed, dimensionId(pending.level), pending.chunkX, pending.chunkZ);
                }
            }
        } else {
            QUEUE.clear();
            QUEUED.clear();
        }

        if (dirty && ticksSinceSave >= 1200L) {
            saveState(server, false);
            ticksSinceSave = 0L;
        }
    }

    /** Usado por el mixin de Mob.finalizeSpawn. */
    public static boolean shouldBlockNaturalSpawn(Mob mob, MobSpawnType spawnType) {
        if (!EmiMobControlConfig.get().blockNaturalPassiveSpawns) return false;
        if (spawnType != MobSpawnType.NATURAL && spawnType != MobSpawnType.CHUNK_GENERATION) return false;
        return isTargetVanillaPassive(mob);
    }

    /** Limpieza manual de toda la fauna objetivo que esté cargada ahora mismo. */
    public static int cleanupLoadedPassives(MinecraftServer server) {
        int removed = 0;
        for (ServerLevel level : server.getAllLevels()) {
            ArrayList<Mob> candidates = new ArrayList<>();
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof Mob mob && isTargetVanillaPassive(mob) && !isProtected(mob)) {
                    candidates.add(mob);
                }
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

    public static int pendingChunks() {
        return QUEUE.size();
    }

    public static long cleanedChunkCount() {
        long total = 0L;
        for (Set<Long> chunks : state.cleanedChunks.values()) total += chunks.size();
        return total;
    }

    /**
     * Borra únicamente el registro de chunks revisados. No elimina entidades en ese momento;
     * los chunks se volverán a revisar la próxima vez que se carguen.
     */
    public static void resetCleanedChunks(MinecraftServer server) {
        state = new SavedState();
        state.seed = server.overworld().getSeed();
        QUEUE.clear();
        QUEUED.clear();
        dirty = true;
        saveState(server, true);
    }

    private static int cleanupChunk(ServerLevel level, int chunkX, int chunkZ) {
        int minX = chunkX << 4;
        int minZ = chunkZ << 4;
        AABB bounds = new AABB(
                minX, level.getMinBuildHeight(), minZ,
                minX + 16, level.getMaxBuildHeight(), minZ + 16
        );

        ArrayList<Mob> candidates = new ArrayList<>();
        for (Entity entity : level.getEntities((Entity) null, bounds, PassiveMobControlService::isTargetVanillaPassive)) {
            if (entity instanceof Mob mob && !isProtected(mob)) candidates.add(mob);
        }

        int removed = 0;
        for (Mob mob : candidates) {
            if (!mob.isRemoved()) {
                mob.discard();
                removed++;
            }
        }
        return removed;
    }

    private static boolean isTargetVanillaPassive(Entity entity) {
        var id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        if (!"minecraft".equals(id.getNamespace())) return false;
        return EmiMobControlConfig.get().vanillaPassiveEntityIds.contains(id.toString());
    }

    private static boolean isProtected(Mob mob) {
        EmiMobControlConfig config = EmiMobControlConfig.get();
        if (mob.getTags().contains(config.passiveKeepTag)) return true;
        if (config.protectNamedMobs && mob.hasCustomName()) return true;
        if (config.protectTamedMobs && mob instanceof TamableAnimal tamable && tamable.isTame()) return true;
        if (config.protectLeashedMobs && mob.isLeashed()) return true;
        if (config.protectPersistentMobs && mob.isPersistenceRequired()) return true;
        return mob.isPassenger() || mob.isVehicle();
    }

    private static boolean isCleaned(ServerLevel level, int chunkX, int chunkZ) {
        Set<Long> chunks = state.cleanedChunks.get(dimensionId(level));
        return chunks != null && chunks.contains(ChunkPos.asLong(chunkX, chunkZ));
    }

    private static void markCleaned(ServerLevel level, int chunkX, int chunkZ) {
        state.cleanedChunks
                .computeIfAbsent(dimensionId(level), ignored -> new HashSet<>())
                .add(ChunkPos.asLong(chunkX, chunkZ));
        dirty = true;
    }

    private static String dimensionId(ServerLevel level) {
        return level.dimension().location().toString();
    }

    private static String queueKey(ServerLevel level, int chunkX, int chunkZ) {
        return dimensionId(level) + ":" + chunkX + ":" + chunkZ;
    }

    private static Path statePath(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve("data").resolve(STATE_FILE);
    }

    private static void loadState(MinecraftServer server) {
        Path path = statePath(server);
        SavedState loaded = null;
        if (Files.isRegularFile(path)) {
            try (Reader reader = Files.newBufferedReader(path)) {
                loaded = GSON.fromJson(reader, SavedState.class);
            } catch (Exception exception) {
                EmiMobControl.LOGGER.warn("No se pudo leer el progreso de limpieza de fauna; se reconstruirá.", exception);
            }
        }

        long currentSeed = server.overworld().getSeed();
        if (loaded == null || loaded.seed != currentSeed) {
            loaded = new SavedState();
            loaded.seed = currentSeed;
            dirty = true;
        }
        if (loaded.cleanedChunks == null) loaded.cleanedChunks = new HashMap<>();
        loaded.cleanedChunks.replaceAll((dimension, chunks) -> chunks == null ? new HashSet<>() : new HashSet<>(chunks));
        state = loaded;

        EmiMobControl.LOGGER.info("Control de fauna CHAINA: {} chunks ya revisados; bloqueo natural={}; limpieza de pregenerados={}.",
                cleanedChunkCount(),
                EmiMobControlConfig.get().blockNaturalPassiveSpawns,
                EmiMobControlConfig.get().cleanPregeneratedPassiveMobs);
    }

    private static void saveState(MinecraftServer server, boolean force) {
        if (!dirty && !force) return;
        Path path = statePath(server);
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path)) {
                GSON.toJson(state, writer);
            }
            dirty = false;
        } catch (Exception exception) {
            EmiMobControl.LOGGER.error("No se pudo guardar el progreso de limpieza de fauna en {}.", path, exception);
        }
    }

    private record PendingChunk(ServerLevel level, int chunkX, int chunkZ, long readyAtTick, String queueKey) {}

    private static final class SavedState {
        long seed = Long.MIN_VALUE;
        Map<String, Set<Long>> cleanedChunks = new HashMap<>();
    }
}
