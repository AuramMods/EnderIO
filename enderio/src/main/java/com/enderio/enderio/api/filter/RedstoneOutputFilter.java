package com.enderio.enderio.api.filter;

import net.minecraft.world.item.DyeColor;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Experimental
public interface RedstoneOutputFilter extends RedstoneFilter {

    /** Advances a stateful filter after the network has sampled its inputs. */
    default void updateOutput(RedstoneOutputFilterContext context, DyeColor control) {
    }

    int getOutputSignal(RedstoneOutputFilterContext context, DyeColor control);
}
