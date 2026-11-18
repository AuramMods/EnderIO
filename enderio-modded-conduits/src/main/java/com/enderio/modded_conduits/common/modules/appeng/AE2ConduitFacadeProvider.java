package com.enderio.modded_conduits.common.modules.appeng;

import net.minecraft.core.Direction;
import appeng.api.implementations.items.IFacadeItem;
import com.enderio.enderio.api.conduits.facade.ConduitFacadeProvider;
import com.enderio.enderio.api.conduits.facade.FacadeType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import java.util.function.BiFunction;

/**
 * Exposes AE2 facades to the Ender IO facade system.
 */
public class AE2ConduitFacadeProvider implements ConduitFacadeProvider {

    public static final BiFunction<ItemStack, Direction, ConduitFacadeProvider> PROVIDER = (stack,
            v) -> new AE2ConduitFacadeProvider(stack);

    private final ItemStack itemStack;

    public AE2ConduitFacadeProvider(ItemStack itemStack) {
        this.itemStack = itemStack;
    }

    @Override
    public boolean isValid() {
        return itemStack.getItem() instanceof IFacadeItem facade
            && facade.getTextureItem(itemStack).getItem() instanceof BlockItem;
    }

    @Override
    public Block block() {
        if (itemStack.getItem() instanceof IFacadeItem facade
                && facade.getTextureItem(itemStack).getItem() instanceof BlockItem blockItem) {
            return blockItem.getBlock();
        }

        // TODO: Ideally an exception?
        return Blocks.AIR;
    }

    @Override
    public FacadeType type() {
        // TODO: Could make this configurable?
        return FacadeType.BASIC;
    }
}
