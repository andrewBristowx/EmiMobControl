package com.andrewbristowx.emimobcontrol.spawner;

import com.andrewbristowx.emimobcontrol.EmiMobControl;
import com.andrewbristowx.emimobcontrol.SpawnerTier;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpawnerFarmUpgradeRecipeTest {
    private static SpawnerFarmUpgradeRecipe recipe;

    @BeforeAll
    static void prepareMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        EmiMobControl.SPAWNER_FARM_ITEM = Items.SPAWNER;
        recipe = new SpawnerFarmUpgradeRecipe(CraftingBookCategory.MISC);
    }

    @Test
    void ironCraftPreservesMobAndAddsTheTier() {
        ItemStack farm = SpawnerFarmBlockEntity.createSpawnerItem("minecraft:zombie");
        CraftingInput input = surroundedFarm(farm, Items.IRON_INGOT);

        assertTrue(recipe.matches(input, null));
        ItemStack result = recipe.assemble(input, null);
        assertEquals(SpawnerTier.IRON, SpawnerFarmBlockEntity.getItemTier(result));
        assertEquals("minecraft:zombie", result.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
                .copyTag().getString("EmiSpawnerEntity"));
    }

    @Test
    void tiersMustBeCraftedInOrder() {
        ItemStack ironFarm = SpawnerFarmBlockEntity.createSpawnerItem(
                "minecraft:skeleton", SpawnerTier.IRON);

        assertTrue(recipe.matches(surroundedFarm(ironFarm, Items.GOLD_INGOT), null));
        assertFalse(recipe.matches(surroundedFarm(ironFarm, Items.DIAMOND), null));
    }

    @Test
    void netheriteIsTheMaximumTier() {
        ItemStack farm = SpawnerFarmBlockEntity.createSpawnerItem(
                "minecraft:blaze", SpawnerTier.NETHERITE);
        assertFalse(recipe.matches(surroundedFarm(farm, Items.NETHERITE_INGOT), null));
    }

    private static CraftingInput surroundedFarm(ItemStack farm, Item material) {
        List<ItemStack> stacks = new ArrayList<>(9);
        for (int index = 0; index < 9; index++) {
            stacks.add(index == 4 ? farm : new ItemStack(material));
        }
        return CraftingInput.of(3, 3, stacks);
    }
}
