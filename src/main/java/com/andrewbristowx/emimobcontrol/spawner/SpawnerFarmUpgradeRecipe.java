package com.andrewbristowx.emimobcontrol.spawner;

import com.andrewbristowx.emimobcontrol.EmiMobControl;
import com.andrewbristowx.emimobcontrol.SpawnerTier;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

public final class SpawnerFarmUpgradeRecipe extends CustomRecipe {
    public SpawnerFarmUpgradeRecipe(CraftingBookCategory category) {
        super(category);
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        if (input.width() != 3 || input.height() != 3) return false;

        ItemStack farm = input.getItem(1, 1);
        if (!farm.is(EmiMobControl.SPAWNER_FARM_ITEM)) return false;

        SpawnerTier targetTier = SpawnerFarmBlockEntity.getItemTier(farm).next();
        if (targetTier == null) return false;
        Item ingredient = BuiltInRegistries.ITEM.get(targetTier.upgradeIngredientId());

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                if (row == 1 && column == 1) continue;
                if (!input.getItem(column, row).is(ingredient)) return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        ItemStack farm = input.getItem(1, 1);
        SpawnerTier targetTier = SpawnerFarmBlockEntity.getItemTier(farm).next();
        if (targetTier == null) return ItemStack.EMPTY;

        ItemStack upgraded = farm.copyWithCount(1);
        SpawnerFarmBlockEntity.setItemTier(upgraded, targetTier);
        return upgraded;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width >= 3 && height >= 3;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return EmiMobControl.SPAWNER_FARM_UPGRADE_RECIPE;
    }
}
