package com.andrewbristowx.emimobcontrol.client;

import com.andrewbristowx.emimobcontrol.spawner.SpawnerFarmBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.AABB;

public final class SpawnerFarmRenderer implements BlockEntityRenderer<SpawnerFarmBlockEntity> {
    private final Font font;

    public SpawnerFarmRenderer(BlockEntityRendererProvider.Context context) {
        this.font = context.getFont();
    }

    @Override
    public void render(SpawnerFarmBlockEntity farm, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffers, int packedLight, int packedOverlay) {
        drawTierBorder(farm, poseStack, buffers);

        Minecraft minecraft = Minecraft.getInstance();
        poseStack.pushPose();
        poseStack.translate(0.5D, 1.75D, 0.5D);
        poseStack.mulPose(minecraft.getEntityRenderDispatcher().cameraOrientation());
        poseStack.scale(-0.025F, -0.025F, 0.025F);

        drawCentered(farm.getMobName(), -20.0F, 0xFFFFFF, poseStack, buffers);
        drawCentered(Component.translatable("text.emimobcontrol.tier", farm.getTier().displayName()),
                -9.0F, farm.getTier().color(), poseStack, buffers);

        Component status = farm.isOutputFull()
                ? Component.translatable("text.emimobcontrol.inventory.full")
                : Component.translatable(farm.isHopperConnected()
                        ? "text.emimobcontrol.hopper.connected"
                        : "text.emimobcontrol.hopper.missing");
        int statusColor = farm.isOutputFull() ? 0xFFB52E : (farm.isHopperConnected() ? 0x55FF78 : 0xFF6262);
        drawCentered(status, 2.0F, statusColor, poseStack, buffers);
        poseStack.popPose();
    }

    private void drawCentered(Component text, float y, int color, PoseStack poseStack, MultiBufferSource buffers) {
        float x = -font.width(text) / 2.0F;
        font.drawInBatch(text, x, y, color, true, poseStack.last().pose(), buffers,
                Font.DisplayMode.NORMAL, 0xA0000000, LightTexture.FULL_BRIGHT);
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
