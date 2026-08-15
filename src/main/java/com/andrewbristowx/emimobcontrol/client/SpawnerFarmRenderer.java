package com.andrewbristowx.emimobcontrol.client;

import com.andrewbristowx.emimobcontrol.spawner.SpawnerFarmBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.phys.AABB;

public final class SpawnerFarmRenderer implements BlockEntityRenderer<SpawnerFarmBlockEntity> {
    public SpawnerFarmRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(SpawnerFarmBlockEntity farm, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffers, int packedLight, int packedOverlay) {
        drawTierBorder(farm, poseStack, buffers);
    }

    private void drawTierBorder(SpawnerFarmBlockEntity farm, PoseStack poseStack,
                                MultiBufferSource buffers) {
        int color = farm.getTier().color();
        float red = ((color >> 16) & 0xFF) / 255.0F;
        float green = ((color >> 8) & 0xFF) / 255.0F;
        float blue = (color & 0xFF) / 255.0F;
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());

        LevelRenderer.renderLineBox(poseStack, lines,
                new AABB(-0.006D, -0.006D, -0.006D, 1.006D, 1.006D, 1.006D),
                red, green, blue, 1.0F);
        LevelRenderer.renderLineBox(poseStack, lines,
                new AABB(-0.012D, -0.012D, -0.012D, 1.012D, 1.012D, 1.012D),
                red, green, blue, 0.65F);
    }

    @Override
    public boolean shouldRenderOffScreen(SpawnerFarmBlockEntity blockEntity) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 32;
    }
}
