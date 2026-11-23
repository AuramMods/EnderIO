package com.enderio.enderio.content.conduits.menu;

import com.enderio.enderio.integration.gamestages.StageFeature;
import com.enderio.enderio.integration.gamestages.StageRules;

import com.enderio.core.common.menu.BaseEnderMenu;
import com.enderio.core.common.network.codec.RegistryPacketBuffer;
import com.enderio.enderio.api.EnderIOCapabilities;
import com.enderio.core.annotations.UseOnly;
import com.enderio.enderio.api.conduits.Conduit;
import com.enderio.enderio.api.conduits.connection.config.ConnectionConfig;
import com.enderio.enderio.api.conduits.connection.config.ConnectionConfigType;
import com.enderio.enderio.content.conduits.bundle.ConduitBundleBlockEntity;
import com.enderio.enderio.foundation.network.packets.ClientboundConduitExtraGuiDataPacket;
import com.enderio.enderio.foundation.network.packets.ClientboundConduitListPacket;
import com.enderio.enderio.foundation.network.packets.ServerboundOpenConduitFilterMenu;
import com.enderio.enderio.foundation.network.packets.SetConduitConnectionConfigPacket;
import com.enderio.enderio.init.EIOMenus;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import com.enderio.core.common.network.codec.PacketCodecs;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.SlotItemHandler;
import com.enderio.enderio.foundation.network.EIONetwork;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class ConduitMenu extends BaseEnderMenu {

    public static void openConduitMenu(ServerPlayer serverPlayer, ConduitBundleBlockEntity conduitBundle,
            Direction side, Holder<Conduit<?, ?>> conduit) {
        if (!StageRules.permitsAction(serverPlayer, StageFeature.CONDUIT_CONFIGURATION)) {
            return;
        }
        // Forge closes the old server menu without sending a close-screen packet.
        net.minecraftforge.network.NetworkHooks.openScreen(serverPlayer, new MenuProvider(conduitBundle, side, conduit), raw -> {
            var buf = new RegistryPacketBuffer(raw, serverPlayer.level().registryAccess());
            buf.writeBlockPos(conduitBundle.getBlockPos());
            buf.writeEnum(side);
            Conduit.STREAM_CODEC.encode(buf, conduit);
            ClientConnectionAccessor.writeStartingSyncData(conduitBundle, conduit, side, buf);
        });
    }

    public static final int BUTTON_CHANGE_CONDUIT_START_ID = 0;
    public static final int BUTTON_CHANGE_CONDUIT_ID_COUNT = ConduitBundleBlockEntity.MAX_CONDUITS;

    private final BlockPos pos;
    private final Holder<Conduit<?, ?>> conduit;
    private final Direction side;
    private final ConnectionAccessor connectionAccessor;

    @Nullable
    private final IItemHandlerModifiable conduitInventory;

    @UseOnly(LogicalSide.SERVER)
    private ConnectionConfig remoteConnectionConfig;

    @UseOnly(LogicalSide.SERVER)
    private CompoundTag remoteExtraGuiData;

    @UseOnly(LogicalSide.SERVER)
    private int conduitListHashCode;

    public ConduitMenu(int containerId, Inventory playerInventory, ConduitBundleBlockEntity conduitBundle,
            Holder<Conduit<?, ?>> conduit, Direction side) {
        super(EIOMenus.CONDUIT_MENU.get(), containerId, playerInventory);

        this.pos = conduitBundle.getBlockPos();
        this.side = side;
        this.conduit = conduit;
        this.connectionAccessor = conduitBundle;

        // Set to sensible defaults to allow a sync after the menu opens
        this.remoteConnectionConfig = conduit.value().type().connectionConfigType().getDefault();
        this.conduitInventory = conduitBundle.getConnectionInventory(conduit, side);

        addSlots();
    }

    public ConduitMenu(int containerId, Inventory playerInventory, FriendlyByteBuf buf) {
        super(EIOMenus.CONDUIT_MENU.get(), containerId, playerInventory);
        buf = new RegistryPacketBuffer(buf, playerInventory.player.level().registryAccess());

        pos = buf.readBlockPos();
        side = buf.readEnum(Direction.class);
        conduit = Conduit.STREAM_CODEC.decode(buf);

        // Uses initially transmitted state to immediately show an up-to-date UI.
        this.connectionAccessor = new ClientConnectionAccessor(buf);

        // If this conduit has an inventory, create its client representation.
        if (conduit.value().getInventorySize() > 0) {
            this.conduitInventory = new ClientConduitInventory();
        } else {
            this.conduitInventory = null;
        }

        addSlots();
    }

    private void addSlots() {
        if (conduitInventory != null) {
            for (int i = 0; i < conduit.value().getInventorySize(); i++) {
                var pos = conduit.value().getInventorySlotPosition(i);
                addSlot(new SlotItemHandler(conduitInventory, i, pos.x, pos.y) {
                    @Override
                    public int getMaxStackSize(ItemStack stack) {
                        // The Forge default clears and restores the real handler slot to probe capacity.
                        return mayPlace(stack) ? Math.min(getMaxStackSize(), stack.getMaxStackSize()) : 0;
                    }
                });
            }
        }

        addPlayerInventorySlots(23, 113);
    }

    public BlockPos getBlockPos() {
        return pos;
    }

    public Direction getSide() {
        return side;
    }

    public Holder<Conduit<?, ?>> getConduit() {
        return conduit;
    }

    public List<Holder<Conduit<?, ?>>> getConnectedConduits() {
        return connectionAccessor.getAllOpenableConduits(side);
    }

    @Nullable
    public IItemHandler getConduitInventory() {
        return conduitInventory;
    }

    @UseOnly(LogicalSide.CLIENT)
    public void setConnectedConduits(List<Holder<Conduit<?, ?>>> connectedConduits) {
        if (connectionAccessor instanceof ClientConnectionAccessor clientConnectionAccessor) {
            clientConnectionAccessor.connectedConduits = connectedConduits;
        }
    }

    public ConnectionConfigType<?> connectionConfigType() {
        return conduit.value().type().connectionConfigType();
    }

    public ConnectionConfig connectionConfig() {
        return connectionAccessor.getConnectionConfig(conduit, side);
    }

    public <T extends ConnectionConfig> T connectionConfig(ConnectionConfigType<T> type) {
        var config = connectionConfig();
        if (config.type() == type) {
            // noinspection unchecked
            return (T) config;
        }

        throw new IllegalStateException("Connection config type mismatch");
    }

    public void setConnectionConfig(ConnectionConfig config) {
        if (getPlayerInventory().player instanceof LocalPlayer localPlayer) {
            // Prevent editing while player is in spectator mode.
            if (!localPlayer.isSpectator()) {
                connectionAccessor.setConnectionConfig(conduit, side, config);
                EIONetwork.sendToServer(new SetConduitConnectionConfigPacket(containerId, config));
            }
        } else {
            connectionAccessor.setConnectionConfig(conduit, side, config);
        }
    }

    public void handleConnectionConfigUpdate(SetConduitConnectionConfigPacket packet) {
        if (packet.connectionConfig().type() == connectionConfigType()) {
            connectionAccessor.setConnectionConfig(conduit, side, packet.connectionConfig());
        }
    }

    @Nullable
    public CompoundTag extraGuiData() {
        return connectionAccessor.getConduitExtraGuiData(conduit, side);
    }

    @UseOnly(LogicalSide.CLIENT)
    public void setExtraGuiData(CompoundTag extraGuiData) {
        if (connectionAccessor instanceof ClientConnectionAccessor clientConnectionAccessor) {
            clientConnectionAccessor.extraGuiData = extraGuiData;
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return StageRules.permitsAction(player, StageFeature.CONDUIT_CONFIGURATION)
                && connectionAccessor.stillValid(player) && connectionAccessor.canOpenScreen(conduit, side);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (player instanceof ServerPlayer serverPlayer) {
            if (id >= BUTTON_CHANGE_CONDUIT_START_ID
                    && id <= BUTTON_CHANGE_CONDUIT_ID_COUNT + BUTTON_CHANGE_CONDUIT_ID_COUNT) {
                int conduitIndex = id - BUTTON_CHANGE_CONDUIT_START_ID;
                var connectedConduits = getConnectedConduits();
                if (conduitIndex < connectedConduits.size()) {
                    openConduitMenu(serverPlayer, (ConduitBundleBlockEntity) connectionAccessor, side,
                            connectedConduits.get(conduitIndex));
                }
            }
        }

        return super.clickMenuButton(player, id);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (conduitInventory == null || index < 0 || index >= slots.size()) return ItemStack.EMPTY;

        var source = slots.get(index);
        if (!source.hasItem() || !source.mayPickup(player)) return ItemStack.EMPTY;

        var original = source.getItem().copy();
        var remaining = original.copy();
        int conduitEnd = conduitInventory.getSlots();
        if (index < conduitEnd) {
            if (!moveItemStackTo(remaining, conduitEnd, slots.size(), true)) return ItemStack.EMPTY;
        } else {
            for (int slot = 0; slot < conduitEnd && !remaining.isEmpty(); slot++) {
                remaining = conduitInventory.insertItem(slot, remaining, false);
            }
            if (remaining.getCount() == original.getCount()) return ItemStack.EMPTY;
        }

        source.setByPlayer(remaining);
        source.onTake(player, remaining);
        return original;
    }

    public void tryOpenFilterMenu(int slot) {
        if (conduitInventory == null || slot < 0 || slot >= conduitInventory.getSlots()) return;
        if (getPlayerInventory().player instanceof ServerPlayer serverPlayer) {
            var stack = conduitInventory.getStackInSlot(slot);
            var menuProvider = stack.getCapability(EnderIOCapabilities.FILTER_MENU_PROVIDER).orElse(null);
            if (menuProvider != null) {
                var bundle = (ConduitBundleBlockEntity) connectionAccessor;
                menuProvider.openMenu(serverPlayer, conduitInventory, slot,
                    () -> openConduitMenu(serverPlayer, bundle, side, conduit),
                    () -> stillValid(serverPlayer) && bundle.getConnectionInventory(conduit, side) == conduitInventory);
            }
        } else {
            EIONetwork.sendToServer(new ServerboundOpenConduitFilterMenu(containerId, slot));
        }
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();

        // Belt & Braces check to ensure the menu is in a valid state before accessing connections
        if (getPlayerInventory().player instanceof ServerPlayer serverPlayer && stillValid(serverPlayer)) {
            if (!Objects.equals(connectionConfig(), remoteConnectionConfig)) {
                EIONetwork.sendToPlayer(serverPlayer,
                        new SetConduitConnectionConfigPacket(containerId, connectionConfig()));
                this.remoteConnectionConfig = connectionConfig();
            }

            var extraGuiData = extraGuiData();
            if (!Objects.equals(extraGuiData, remoteExtraGuiData)) {
                EIONetwork.sendToPlayer(serverPlayer,
                        new ClientboundConduitExtraGuiDataPacket(containerId, extraGuiData));
                this.remoteExtraGuiData = extraGuiData;
            }

            var conduitList = connectionAccessor.getAllOpenableConduits(side);
            if (conduitListHashCode != conduitList.hashCode()) {
                EIONetwork.sendToPlayer(serverPlayer, new ClientboundConduitListPacket(containerId, conduitList));
                conduitListHashCode = conduitList.hashCode();
            }
        }
    }

    public interface ConnectionAccessor {
        List<Holder<Conduit<?, ?>>> getAllOpenableConduits(Direction side);

        ConnectionConfig getConnectionConfig(Holder<Conduit<?, ?>> conduit, Direction side);

        void setConnectionConfig(Holder<Conduit<?, ?>> conduit, Direction side, ConnectionConfig config);

        boolean canOpenScreen(Holder<Conduit<?, ?>> conduit, Direction side);

        @Nullable
        CompoundTag getConduitExtraGuiData(Holder<Conduit<?, ?>> conduit, Direction side);

        boolean stillValid(Player player);
    }

    private static class ClientConnectionAccessor implements ConnectionAccessor {

        private List<Holder<Conduit<?, ?>>> connectedConduits = List.of();

        private ConnectionConfig connectionConfig;

        @Nullable
        private CompoundTag extraGuiData;

        public ClientConnectionAccessor(FriendlyByteBuf buf) {
            this.connectedConduits = Conduit.STREAM_CODEC
                    .list(ConduitBundleBlockEntity.MAX_CONDUITS)
                    .decode(buf);

            this.connectionConfig = ConnectionConfig.STREAM_CODEC.decode(buf);

            extraGuiData = PacketCodecs.COMPOUND_TAG.optional()
                    .map(opt -> opt.orElse(null), Optional::ofNullable)
                    .decode(buf);
        }

        private static void writeStartingSyncData(ConduitBundleBlockEntity conduitBundle, Holder<Conduit<?, ?>> conduit,
                Direction side, FriendlyByteBuf buf) {
            Conduit.STREAM_CODEC.list(ConduitBundleBlockEntity.MAX_CONDUITS)
                    .encode(buf, conduitBundle.getAllOpenableConduits(side));

            ConnectionConfig.STREAM_CODEC.encode(buf, conduitBundle.getConnectionConfig(conduit, side));

            // noinspection DataFlowIssue
            PacketCodecs.COMPOUND_TAG.optional()
                    .map(opt -> opt.orElse(null), Optional::ofNullable)
                    .encode(buf, conduitBundle.getConduitExtraGuiData(conduit, side));
        }

        @Override
        public List<Holder<Conduit<?, ?>>> getAllOpenableConduits(Direction side) {
            return connectedConduits;
        }

        @Override
        public ConnectionConfig getConnectionConfig(Holder<Conduit<?, ?>> conduit, Direction side) {
            return connectionConfig;
        }

        @Override
        public void setConnectionConfig(Holder<Conduit<?, ?>> conduit, Direction side, ConnectionConfig config) {
            connectionConfig = config;
        }

        @Override
        public boolean canOpenScreen(Holder<Conduit<?, ?>> conduit, Direction side) {
            return true;
        }

        @Override
        public CompoundTag getConduitExtraGuiData(Holder<Conduit<?, ?>> conduit, Direction side) {
            return extraGuiData;
        }

        @Override
        public boolean stillValid(Player player) {
            return true;
        }
    }

    private class ClientConduitInventory extends ItemStackHandler {
        private ClientConduitInventory() {
            super(conduit.value().getInventorySize());
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return conduit.value().isItemValid(slot, stack);
        }

        @Override
        public int getSlotLimit(int slot) {
            return conduit.value().getInventorySlotLimit(slot);
        }
    }

    private record MenuProvider(ConduitBundleBlockEntity conduitBundle, Direction side, Holder<Conduit<?, ?>> conduit)
            implements net.minecraft.world.MenuProvider {

        @Override
        public Component getDisplayName() {
            return conduit.value().description();
        }

        @Override
        public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
            return new ConduitMenu(containerId, inventory, conduitBundle, conduit, side);
        }

    }
}
