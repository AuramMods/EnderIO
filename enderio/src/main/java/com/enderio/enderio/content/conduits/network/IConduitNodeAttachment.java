package com.enderio.enderio.content.conduits.network;

import com.enderio.enderio.api.conduits.Conduit;
import com.enderio.enderio.api.conduits.connection.ConnectionStatus;
import com.enderio.enderio.api.conduits.connection.config.ConnectionConfig;
import com.enderio.enderio.api.conduits.connection.config.ConnectionConfigType;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.items.IItemHandlerModifiable;
import org.jetbrains.annotations.Nullable;

// Interface for attaching a node to the level.
// Added so that we can perform tests on ConduitNode and ConduitNetwork.
public interface IConduitNodeAttachment {
    boolean hasLevel();

    @Nullable
    Level getLevel();

    void markNodesDirty();

    @Nullable
    <T> T getCapability(Holder<Conduit<?, ?>> conduit, Capability<T> capability, Direction neighborSide, @Nullable Direction context);

    boolean hasRedstoneSignal(@Nullable DyeColor signalColor);

    ConnectionStatus getConnectionStatus(Holder<Conduit<?, ?>> conduit, Direction side);

    ConnectionConfig getConnectionConfig(Holder<Conduit<?, ?>> conduit, Direction side);

    void setConnectionConfig(Holder<Conduit<?, ?>> conduit, Direction side, ConnectionConfig config);

    @Nullable
    IItemHandlerModifiable getConnectionInventory(Holder<Conduit<?, ?>> conduit, Direction side);
}
