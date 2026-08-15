package com.andrewbristowx.emimobcontrol.spawner;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class SpawnerCaptureService {
    private SpawnerCaptureService() {}

    public static boolean beforeBlockBreak(Level level, Player player, BlockPos pos,
                                           BlockState state, BlockEntity blockEntity) {
        if (level.isClientSide || !state.is(Blocks.SPAWNER) || player.isCreative()) return true;
        if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) return true;
        if (!(blockEntity instanceof SpawnerBlockEntity spawner)) return true;

        ItemStack tool = player.getMainHandItem();
        if (!tool.is(ItemTags.PICKAXES) || !hasSilkTouch(serverLevel, tool)) return true;

        CompoundTag saved = spawner.getSpawner().save(new CompoundTag());
        String entityId = saved.getCompound("SpawnData").getCompound("entity").getString("id");
        if (!SpawnerFarmBlockEntity.isAllowedEntity(entityId)) return true;

        ItemStack captured = SpawnerFarmBlockEntity.createSpawnerItem(entityId);
        level.removeBlock(pos, false);
        level.levelEvent(2001, pos, Block.getId(state));
        Block.popResource(level, pos, captured);
        tool.hurtAndBreak(1, serverLevel, serverPlayer, ignored -> {});
        return false;
    }

    public static boolean hasSilkTouch(ServerLevel level, ItemStack stack) {
        var registry = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        return EnchantmentHelper.getItemEnchantmentLevel(registry.getOrThrow(Enchantments.SILK_TOUCH), stack) > 0;
    }
}
