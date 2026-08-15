package com.andrewbristowx.emimobcontrol;

import com.andrewbristowx.emimobcontrol.command.EmiMobControlCommand;
import com.andrewbristowx.emimobcontrol.config.EmiMobControlConfig;
import com.andrewbristowx.emimobcontrol.spawner.SpawnerCaptureService;
import com.andrewbristowx.emimobcontrol.spawner.SpawnerFarmBlock;
import com.andrewbristowx.emimobcontrol.spawner.SpawnerFarmBlockEntity;
import com.andrewbristowx.emimobcontrol.spawner.SpawnerFarmItem;
import com.andrewbristowx.emimobcontrol.spawner.SpawnerFarmUpgradeRecipe;
import com.andrewbristowx.emimobcontrol.spawner.SpawnerFarmDisplayService;
import com.andrewbristowx.emimobcontrol.system.MobCleanupService;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class EmiMobControl implements ModInitializer {
    public static final String MOD_ID = "emimobcontrol";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static Block SPAWNER_FARM;
    public static Item SPAWNER_FARM_ITEM;
    public static Item XP_ESSENCE;
    public static BlockEntityType<SpawnerFarmBlockEntity> SPAWNER_FARM_BLOCK_ENTITY;
    public static RecipeSerializer<SpawnerFarmUpgradeRecipe> SPAWNER_FARM_UPGRADE_RECIPE;

    @Override
    public void onInitialize() {
        EmiMobControlConfig.load();
        registerContent();
        PlayerBlockBreakEvents.BEFORE.register(SpawnerCaptureService::beforeBlockBreak);
        ServerTickEvents.END_SERVER_TICK.register(MobCleanupService::tick);
        ServerTickEvents.END_SERVER_TICK.register(SpawnerFarmDisplayService::serverTick);
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                EmiMobControlCommand.register(dispatcher));
        LOGGER.info("EmiMobControl listo: limpieza segura y granjas compactas activadas.");
    }

    private static void registerContent() {
        ResourceLocation blockId = id("spawner_farm");
        SPAWNER_FARM = Registry.register(BuiltInRegistries.BLOCK, blockId,
                new SpawnerFarmBlock(BlockBehaviour.Properties.of()
                        .strength(5.0F, 1200.0F)
                        .sound(SoundType.METAL)
                        .requiresCorrectToolForDrops()
                        .noOcclusion()));
        SPAWNER_FARM_ITEM = Registry.register(BuiltInRegistries.ITEM, blockId,
                new SpawnerFarmItem(SPAWNER_FARM, new Item.Properties().stacksTo(1)));
        XP_ESSENCE = Registry.register(BuiltInRegistries.ITEM, id("xp_essence"),
                new XpEssenceItem(new Item.Properties().stacksTo(64)));
        SPAWNER_FARM_BLOCK_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
                id("spawner_farm"),
                FabricBlockEntityTypeBuilder.create(SpawnerFarmBlockEntity::new, SPAWNER_FARM).build());
        SPAWNER_FARM_UPGRADE_RECIPE = Registry.register(BuiltInRegistries.RECIPE_SERIALIZER,
                id("spawner_farm_upgrade"),
                new SimpleCraftingRecipeSerializer<>(SpawnerFarmUpgradeRecipe::new));
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
