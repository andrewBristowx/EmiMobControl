package com.andrewbristowx.emimobcontrol;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

class SpawnerTierTest {
    @Test
    void everyUpgradeIsFasterAndProducesMore() {
        SpawnerTier[] tiers = SpawnerTier.values();
        assertEquals(6, tiers.length);
        for (int index = 1; index < tiers.length; index++) {
            assertTrue(tiers[index].cycleTicks() < tiers[index - 1].cycleTicks());
            assertTrue(tiers[index].simulatedKills() > tiers[index - 1].simulatedKills());
        }
    }

    @Test
    void baseAndNetheriteStayInsideTheDesignedLimits() {
        assertEquals(600, SpawnerTier.BASE.cycleTicks());
        assertEquals(1, SpawnerTier.BASE.simulatedKills());
        assertEquals(100, SpawnerTier.NETHERITE.cycleTicks());
        assertEquals(7, SpawnerTier.NETHERITE.simulatedKills());
    }

    @Test
    void upgradesFollowTheRequiredCraftingOrder() {
        assertEquals(SpawnerTier.IRON, SpawnerTier.BASE.next());
        assertEquals("minecraft:iron_ingot", SpawnerTier.IRON.upgradeIngredientId().toString());
        assertEquals("minecraft:gold_ingot", SpawnerTier.GOLD.upgradeIngredientId().toString());
        assertEquals("minecraft:emerald", SpawnerTier.EMERALD.upgradeIngredientId().toString());
        assertEquals("minecraft:diamond", SpawnerTier.DIAMOND.upgradeIngredientId().toString());
        assertEquals("minecraft:netherite_ingot", SpawnerTier.NETHERITE.upgradeIngredientId().toString());
        assertNull(SpawnerTier.NETHERITE.next());
    }

    @Test
    void everyTierHasItsOwnBorderColor() {
        long distinctColors = Arrays.stream(SpawnerTier.values())
                .map(SpawnerTier::color)
                .distinct()
                .count();
        assertEquals(SpawnerTier.values().length, distinctColors);
    }
}
