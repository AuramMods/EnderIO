package com.enderio.enderio.content.tools;

import com.enderio.core.common.capability.CapabilityUtil;
import com.enderio.enderio.api.io.SideConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

public class YetaWrenchItem extends Item {

    public YetaWrenchItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();

        // Check for side config capability
        SideConfig sideConfig = CapabilityUtil.get(level, pos, SideConfig.CAPABILITY, context.getClickedFace());
        if (sideConfig != null) {
            if (!level.isClientSide) sideConfig.cycleMode();
            return InteractionResult.sidedSuccess(level.isClientSide());
        }

        return super.onItemUseFirst(stack, context);
    }
}
