package com.andrewbristowx.emimobcontrol.spawner;

import com.andrewbristowx.emimobcontrol.EmiMobControl;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

public final class SpawnerFarmDisplayService {
    private static final String DISPLAY_TAG = "emimobcontrol_spawner_display";
    private static final String POSITION_TAG_PREFIX = "emimobcontrol_spawner_pos:";
    private static final String STATE_TAG_PREFIX = "emimobcontrol_spawner_state:";
    private static final double DISPLAY_HEIGHT = 2.15D;
    private static long cleanupTicker;

    private SpawnerFarmDisplayService() {}

    public static void sync(ServerLevel level, BlockPos pos, SpawnerFarmBlockEntity farm) {
        String positionTag = positionTag(pos);
        String stateTag = stateTag(farm);
        List<Display.TextDisplay> displays = displaysNear(level, pos, positionTag);

        Display.TextDisplay current = null;
        for (Display.TextDisplay display : displays) {
            if (current == null && display.getTags().contains(stateTag)) {
                current = display;
            } else {
                display.discard();
            }
        }
        if (current != null) return;

        Display.TextDisplay created = EntityType.TEXT_DISPLAY.create(level);
        if (created == null) return;
        configure(created, level, pos, farm, positionTag, stateTag);
        if (!level.addFreshEntity(created)) {
            EmiMobControl.LOGGER.warn("No se pudo crear el texto de la granja en {}.", pos);
        }
    }

    public static void remove(ServerLevel level, BlockPos pos) {
        String positionTag = positionTag(pos);
        for (Display.TextDisplay display : displaysNear(level, pos, positionTag)) {
            display.discard();
        }
    }

    public static void serverTick(MinecraftServer server) {
        cleanupTicker++;
        if (cleanupTicker % 100L != 0L) return;

        for (ServerLevel level : server.getAllLevels()) {
            List<Display.TextDisplay> orphaned = new ArrayList<>();
            for (Display.TextDisplay display : level.getEntities(
                    EntityTypeTest.forClass(Display.TextDisplay.class),
                    candidate -> candidate.getTags().contains(DISPLAY_TAG))) {
                BlockPos pos = taggedPosition(display);
                if (pos == null || !(level.getBlockEntity(pos) instanceof SpawnerFarmBlockEntity)) {
                    orphaned.add(display);
                }
            }
            orphaned.forEach(Display.TextDisplay::discard);
        }
    }

    private static void configure(Display.TextDisplay display, ServerLevel level, BlockPos pos,
                                  SpawnerFarmBlockEntity farm, String positionTag, String stateTag) {
        double x = pos.getX() + 0.5D;
        double y = pos.getY() + DISPLAY_HEIGHT;
        double z = pos.getZ() + 0.5D;
        display.setPos(x, y, z);
        display.setNoGravity(true);
        display.setInvulnerable(true);
        display.setSilent(true);

        CompoundTag tag = display.saveWithoutId(new CompoundTag());
        tag.putString("text", Component.Serializer.toJson(buildText(farm), level.registryAccess()));
        tag.putString("billboard", "center");
        tag.putFloat("view_range", 4.0F);
        tag.putFloat("width", 2.0F);
        tag.putFloat("height", 1.0F);
        tag.putInt("line_width", 220);
        tag.putByte("text_opacity", (byte) 0xFF);
        tag.putBoolean("shadow", true);
        tag.putBoolean("see_through", false);
        tag.putBoolean("default_background", false);
        tag.putInt("background", 0x50000000);

        CompoundTag brightness = new CompoundTag();
        brightness.putInt("block", 15);
        brightness.putInt("sky", 15);
        tag.put("brightness", brightness);

        display.load(tag);
        display.setPos(x, y, z);
        display.addTag(DISPLAY_TAG);
        display.addTag(positionTag);
        display.addTag(stateTag);
        display.setCustomName(Component.literal("EmiMobControl spawner display"));
        display.setCustomNameVisible(false);
    }

    private static MutableComponent buildText(SpawnerFarmBlockEntity farm) {
        MutableComponent text = Component.literal("✦ SPAWNER ✦")
                .withStyle(Style.EMPTY.withBold(true).withColor(farm.getTier().color()));
        text.append(Component.literal("\n"));
        text.append(farm.getMobName().copy().withStyle(ChatFormatting.WHITE));
        text.append(Component.literal("\nTier: ").withStyle(ChatFormatting.GRAY));
        text.append(farm.getTier().displayName().copy()
                .withStyle(Style.EMPTY.withColor(farm.getTier().color())));
        text.append(Component.literal("\n"));

        if (farm.isOutputFull()) {
            text.append(Component.translatable("text.emimobcontrol.inventory.full")
                    .withStyle(ChatFormatting.GOLD));
        } else if (farm.isHopperConnected()) {
            text.append(Component.translatable("text.emimobcontrol.hopper.connected")
                    .withStyle(ChatFormatting.GREEN));
        } else {
            text.append(Component.translatable("text.emimobcontrol.hopper.missing")
                    .withStyle(ChatFormatting.RED));
        }
        return text;
    }

    private static List<Display.TextDisplay> displaysNear(ServerLevel level, BlockPos pos,
                                                           String positionTag) {
        AABB area = new AABB(pos).inflate(2.0D, 4.0D, 2.0D);
        return level.getEntities(EntityTypeTest.forClass(Display.TextDisplay.class), area,
                display -> display.getTags().contains(DISPLAY_TAG)
                        && display.getTags().contains(positionTag));
    }

    static String positionTag(BlockPos pos) {
        return POSITION_TAG_PREFIX + pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    static String stateTag(SpawnerFarmBlockEntity farm) {
        return STATE_TAG_PREFIX + farm.getEntityTypeId().replace(':', '_') + ":"
                + farm.getTier().ordinal() + ":"
                + (farm.isHopperConnected() ? "1" : "0") + ":"
                + (farm.isOutputFull() ? "1" : "0");
    }

    private static BlockPos taggedPosition(Display.TextDisplay display) {
        for (String tag : display.getTags()) {
            if (!tag.startsWith(POSITION_TAG_PREFIX)) continue;
            String[] parts = tag.substring(POSITION_TAG_PREFIX.length()).split(",", -1);
            if (parts.length != 3) return null;
            try {
                return new BlockPos(Integer.parseInt(parts[0]),
                        Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
