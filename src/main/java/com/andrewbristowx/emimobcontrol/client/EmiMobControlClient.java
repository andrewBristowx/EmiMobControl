package com.andrewbristowx.emimobcontrol.client;

import com.andrewbristowx.emimobcontrol.EmiMobControl;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;

public final class EmiMobControlClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        BlockEntityRenderers.register(EmiMobControl.SPAWNER_FARM_BLOCK_ENTITY, SpawnerFarmRenderer::new);
    }
}
