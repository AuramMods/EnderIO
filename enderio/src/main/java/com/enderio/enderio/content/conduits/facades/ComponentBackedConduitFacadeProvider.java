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
        return EIODataComponents.BLOCK_PAINT.has(itemStack);
    }

    @Override
    public Block block() {
        if (!isValid()) {
            throw new IllegalStateException("Facade cannot be used if isValid() is false.");
        }

        return Objects.requireNonNull(EIODataComponents.BLOCK_PAINT.get(itemStack)).paint();
    }

    @Override
    public FacadeType type() {
        return EIODataComponents.FACADE_TYPE.getOrDefault(itemStack, FacadeType.BASIC);
    }
}
