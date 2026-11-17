package com.enderio.enderio.content.conduits.facades;

import net.minecraft.core.Direction;
import com.enderio.enderio.api.conduits.facade.ConduitFacadeProvider;
import com.enderio.enderio.api.conduits.facade.FacadeType;
import com.enderio.enderio.init.EIODataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import java.util.function.BiFunction;

import java.util.Objects;

public class ComponentBackedConduitFacadeProvider implements ConduitFacadeProvider {

    public static final BiFunction<ItemStack, Direction, ConduitFacadeProvider> PROVIDER = (stack,
            v) -> new ComponentBackedConduitFacadeProvider(stack);

    private final ItemStack itemStack;

    public ComponentBackedConduitFacadeProvider(ItemStack itemStack) {
        this.itemStack = itemStack;
    }

    @Override
    public boolean isValid() {
        return itemStack.has(EIODataComponents.BLOCK_PAINT);
    }

    @Override
    public Block block() {
        if (!isValid()) {
            throw new IllegalStateException("Facade cannot be used if isValid() is false.");
        }

        return Objects.requireNonNull(itemStack.get(EIODataComponents.BLOCK_PAINT)).paint();
    }

    @Override
    public FacadeType type() {
        return itemStack.getOrDefault(EIODataComponents.FACADE_TYPE, FacadeType.BASIC);
    }
}
