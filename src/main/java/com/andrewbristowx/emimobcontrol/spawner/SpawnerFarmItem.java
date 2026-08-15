package com.andrewbristowx.emimobcontrol.spawner;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

import java.util.List;

public final class SpawnerFarmItem extends BlockItem {
    public SpawnerFarmItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("text.emimobcontrol.tier",
                SpawnerFarmBlockEntity.getItemTier(stack).displayName()).withStyle(ChatFormatting.GRAY));
    }
}
