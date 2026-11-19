package com.enderio.enderio.api.filter;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.items.IItemHandlerModifiable;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.function.BooleanSupplier;

// TODO: Needs better name
@ApiStatus.Experimental
@ApiStatus.AvailableSince(value = "8.0.0")
public interface FilterMenuProvider {
    void openMenu(ServerPlayer player, IItemHandlerModifiable itemHandler, int slot, @Nullable Runnable goBackRunnable);

    /** Opens an editor whose backing inventory belongs to another menu or block entity. */
    default void openMenu(ServerPlayer player, IItemHandlerModifiable itemHandler, int slot,
            @Nullable Runnable goBackRunnable, BooleanSupplier inventoryStillValid) {
        openMenu(player, itemHandler, slot, goBackRunnable);
    }
}
