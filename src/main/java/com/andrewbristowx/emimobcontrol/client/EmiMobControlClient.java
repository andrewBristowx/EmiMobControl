package com.andrewbristowx.emimobcontrol.client;

import com.andrewbristowx.emimobcontrol.EmiMobControl;
import com.andrewbristowx.emimobcontrol.spawner.SpawnerFarmBlockEntity;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.function.Function;

public final class EmiMobControlClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        BlockEntityRenderers.register(EmiMobControl.SPAWNER_FARM_BLOCK_ENTITY, SpawnerFarmRenderer::new);
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> allowSpawnerFarmRenderingWithEntityCulling());
    }

    private static void allowSpawnerFarmRenderingWithEntityCulling() {
        if (!FabricLoader.getInstance().isModLoaded("entityculling")) return;

        try {
            Class<?> modClass = Class.forName("dev.tr7zw.entityculling.EntityCullingModBase");
            Field instanceField = modClass.getField("instance");
            Object instance = instanceField.get(null);
            if (instance == null) {
                EmiMobControl.LOGGER.warn("EntityCulling todavía no está listo; no se pudo registrar la granja.");
                return;
            }

            Method whitelistMethod = modClass.getMethod("addDynamicBlockEntityWhitelist", Function.class);
            Function<Object, Boolean> whitelist = blockEntity -> blockEntity instanceof SpawnerFarmBlockEntity;
            whitelistMethod.invoke(instance, whitelist);
            EmiMobControl.LOGGER.info("Compatibilidad con EntityCulling activada para textos y bordes de granjas.");
        } catch (ReflectiveOperationException exception) {
            EmiMobControl.LOGGER.warn("No se pudo activar la compatibilidad opcional con EntityCulling.", exception);
        }
    }
}
