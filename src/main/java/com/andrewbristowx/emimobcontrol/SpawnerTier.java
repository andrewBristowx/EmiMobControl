package com.andrewbristowx.emimobcontrol;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public enum SpawnerTier {
    BASE("base", null, 600, 1, 0xA0A0A0),
    IRON("iron", "minecraft:iron_ingot", 480, 2, 0xD8D8D8),
    GOLD("gold", "minecraft:gold_ingot", 360, 3, 0xFFD84A),
    EMERALD("emerald", "minecraft:emerald", 260, 4, 0x42E07B),
    DIAMOND("diamond", "minecraft:diamond", 180, 5, 0x55E5E5),
    NETHERITE("netherite", "minecraft:netherite_ingot", 100, 7, 0xB38AAE);

    private final String key;
    private final ResourceLocation upgradeIngredientId;
    private final int cycleTicks;
    private final int simulatedKills;
    private final int color;

    SpawnerTier(String key, String upgradeIngredientId, int cycleTicks, int simulatedKills, int color) {
        this.key = key;
        this.upgradeIngredientId = upgradeIngredientId == null ? null : ResourceLocation.parse(upgradeIngredientId);
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

    public ResourceLocation upgradeIngredientId() {
        return upgradeIngredientId;
    }

    public SpawnerTier next() {
        int nextIndex = ordinal() + 1;
        return nextIndex < values().length ? values()[nextIndex] : null;
    }

    public static SpawnerTier fromOrdinal(int ordinal) {
        int safeIndex = Math.max(0, Math.min(values().length - 1, ordinal));
        return values()[safeIndex];
    }
}
