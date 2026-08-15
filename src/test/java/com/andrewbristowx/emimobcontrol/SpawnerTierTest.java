package com.andrewbristowx.emimobcontrol;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
}
