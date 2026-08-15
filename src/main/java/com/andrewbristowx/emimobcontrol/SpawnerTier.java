package com.andrewbristowx.emimobcontrol;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

public enum SpawnerTier {
    BASE("base", null, 600, 1, 0xA0A0A0),
    IRON("iron", "minecraft:iron_block", 480, 2, 0xD8D8D8),
    GOLD("gold", "minecraft:gold_block", 360, 3, 0xFFD84A),
    EMERALD("emerald", "minecraft:emerald_block", 260, 4, 0x42E07B),
    DIAMOND("diamond", "minecraft:diamond_block", 180, 5, 0x55E5E5),
    NETHERITE("netherite", "minecraft:netherite_block", 100, 7, 0xB38AAE);

    private final String key;
    private final ResourceLocation ringBlockId;
    private final int cycleTicks;
    private final int simulatedKills;
    private final int color;

    SpawnerTier(String key, String ringBlockId, int cycleTicks, int simulatedKills, int color) {
        this.key = key;
        this.ringBlockId = ringBlockId == null ? null : ResourceLocation.parse(ringBlockId);
        this.cycleTicks = cycleTicks;
        this.simulatedKills = simulatedKills;
        this.color = color;
    }

    public int cycleTicks() {
        return cycleTicks;
    }

    public int simulatedKills() {
        return simulatedKills;
    }

    public int color() {
        return color;
    }

    public Component displayName() {
        return Component.translatable("text.emimobcontrol.tier." + key);
    }

    public static SpawnerTier detect(Level level, BlockPos center) {
        for (int index = values().length - 1; index >= 1; index--) {
            SpawnerTier tier = values()[index];
            if (hasCompleteRing(level, center, tier.ringBlockId)) {
                return tier;
            }
        }
        return BASE;
    }

    private static boolean hasCompleteRing(Level level, BlockPos center, ResourceLocation materialId) {
        var material = BuiltInRegistries.BLOCK.get(materialId);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                if (!level.getBlockState(center.offset(dx, 0, dz)).is(material)) return false;
            }
        }
        return true;
    }
}
