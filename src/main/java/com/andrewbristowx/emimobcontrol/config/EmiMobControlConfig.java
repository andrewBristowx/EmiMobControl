package com.andrewbristowx.emimobcontrol.config;

import com.andrewbristowx.emimobcontrol.EmiMobControl;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class EmiMobControlConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("chainamobcontrol.json");
    private static EmiMobControlConfig instance = new EmiMobControlConfig();

    // Limpieza periódica original: por defecto solo hostiles vanilla lejanos.
    public boolean cleanupEnabled = true;
    public int cleanupIntervalMinutes = 15;
    public int cleanupMinimumPlayerDistance = 64;
    public boolean removeHostileMobs = true;
    public boolean removePassiveMobs = false;
    public boolean protectNamedMobs = true;
    public boolean protectTamedMobs = true;
    public boolean protectLeashedMobs = true;
    public boolean protectPersistentMobs = true;
    public boolean protectVillagers = true;

    // Control específico de fauna vanilla para CHAINA.
    public boolean blockNaturalPassiveSpawns = true;
    public boolean cleanPregeneratedPassiveMobs = true;
    public int passiveCleanupChunksPerTick = 2;
    public int passiveChunkLoadDelayTicks = 20;
    public String passiveKeepTag = "chainamobcontrol_keep";
    public List<String> vanillaPassiveEntityIds = defaultPassiveEntityIds();

    public List<String> excludedEntityIds = new ArrayList<>(List.of(
            "minecraft:villager",
            "minecraft:wandering_trader",
            "minecraft:iron_golem",
            "minecraft:snow_golem"
    ));

    public static EmiMobControlConfig get() {
        return instance;
    }

    public static void load() {
        EmiMobControlConfig loaded = new EmiMobControlConfig();
        if (Files.isRegularFile(PATH)) {
            try (Reader reader = Files.newBufferedReader(PATH)) {
                EmiMobControlConfig parsed = GSON.fromJson(reader, EmiMobControlConfig.class);
                if (parsed != null) loaded = parsed;
            } catch (Exception exception) {
                EmiMobControl.LOGGER.error("No se pudo leer {}. Se usarán valores seguros.", PATH, exception);
            }
        }
        loaded.normalize();
        instance = loaded;
        save();
    }

    public static void save() {
        try {
            Files.createDirectories(PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(PATH)) {
                GSON.toJson(instance, writer);
            }
        } catch (IOException exception) {
            EmiMobControl.LOGGER.error("No se pudo guardar {}.", PATH, exception);
        }
    }

    private void normalize() {
        cleanupIntervalMinutes = Math.max(1, Math.min(1440, cleanupIntervalMinutes));
        cleanupMinimumPlayerDistance = Math.max(0, Math.min(512, cleanupMinimumPlayerDistance));
        passiveCleanupChunksPerTick = Math.max(1, Math.min(16, passiveCleanupChunksPerTick));
        passiveChunkLoadDelayTicks = Math.max(0, Math.min(200, passiveChunkLoadDelayTicks));

        if (passiveKeepTag == null || passiveKeepTag.isBlank()) passiveKeepTag = "chainamobcontrol_keep";
        if (vanillaPassiveEntityIds == null) vanillaPassiveEntityIds = defaultPassiveEntityIds();
        vanillaPassiveEntityIds.removeIf(value -> value == null || value.isBlank());
        if (excludedEntityIds == null) excludedEntityIds = new ArrayList<>();
        excludedEntityIds.removeIf(value -> value == null || value.isBlank());
    }

    private static ArrayList<String> defaultPassiveEntityIds() {
        return new ArrayList<>(List.of(
                "minecraft:armadillo",
                "minecraft:axolotl",
                "minecraft:bat",
                "minecraft:bee",
                "minecraft:camel",
                "minecraft:cat",
                "minecraft:chicken",
                "minecraft:cod",
                "minecraft:cow",
                "minecraft:dolphin",
                "minecraft:donkey",
                "minecraft:fox",
                "minecraft:frog",
                "minecraft:glow_squid",
                "minecraft:goat",
                "minecraft:horse",
                "minecraft:llama",
                "minecraft:mooshroom",
                "minecraft:mule",
                "minecraft:ocelot",
                "minecraft:panda",
                "minecraft:parrot",
                "minecraft:pig",
                "minecraft:polar_bear",
                "minecraft:pufferfish",
                "minecraft:rabbit",
                "minecraft:salmon",
                "minecraft:sheep",
                "minecraft:sniffer",
                "minecraft:squid",
                "minecraft:strider",
                "minecraft:tadpole",
                "minecraft:tropical_fish",
                "minecraft:turtle",
                "minecraft:wolf"
        ));
    }
}
