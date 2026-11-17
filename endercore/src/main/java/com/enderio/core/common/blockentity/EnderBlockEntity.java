package com.enderio.core.common.blockentity;

import com.enderio.core.annotations.UseOnly;
import com.enderio.core.common.network.ClientboundDataSlotChange;
import com.enderio.core.common.network.CoreNetwork;
import com.enderio.core.common.network.NetworkDataSlot;
import com.enderio.core.common.network.ServerboundCDataSlotUpdate;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fml.LogicalSide;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Base block entity class for EnderIO.
 * Handles data slot syncing and capability providers.
 */
public class EnderBlockEntity extends BlockEntity {

    public static final String DATA = "Data";
    public static final String INDEX = "Index";
    private final List<NetworkDataSlot<?>> dataSlots = new CopyOnWriteArrayList<>();
    private final List<Runnable> afterDataSync = new CopyOnWriteArrayList<>();

    private final List<NetworkDataSlot<?>> clientUpdatableDataSlots = new ArrayList<>();

    public EnderBlockEntity(BlockEntityType<?> type, BlockPos worldPosition, BlockState blockState) {
        super(type, worldPosition, blockState);
    }

    // region Ticking

    @SuppressWarnings("unused")
    public static void tick(Level level, BlockPos pos, BlockState state, EnderBlockEntity blockEntity) {
        if (level.isClientSide) {
            blockEntity.clientTick();
        } else {
            blockEntity.serverTick();
        }
        blockEntity.endTick();
    }

    private int nextTick = 0;

    /**
     * Perform server-side ticking
     */
    public void serverTick() {
        // Perform syncing.
        if (level != null) {
            if(nextTick-- == 0) { // do we really need to update it once a tick?
                sync();
                nextTick = 4;
            }
        }
    }

    /**
     * Perform client side ticking.
     */
    public void clientTick() {
    }

    /**
     * Perform client and server side ticking.
     */
    public void endTick() {
    }

    @UseOnly(LogicalSide.CLIENT)
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
    }

    // endregion

    // region Sync

    /**
     * This is the initial packet sent to a client loading the block (or when it is placed).
     */
    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag data = super.getUpdateTag();
        HolderLookup.Provider registries = registryLookup();

        ListTag dataList = new ListTag();
        for (int i = 0; i < dataSlots.size(); i++) {
            var slot = dataSlots.get(i);
            var nbt = slot.save(registries, true);

            CompoundTag slotTag = new CompoundTag();
            slotTag.putInt(INDEX, i);
            slotTag.put(DATA, nbt);

            dataList.add(slotTag);
        }

        data.put(DATA, dataList);

        // NEW: Add synced data properties.
        saveAdditionalSynced(data);

        return data;
    }

    /**
     * This is the client handling the tag above.
     * @param syncData The {@link CompoundTag} sent from {@link BlockEntity#getUpdateTag()}
     */
    @Override
    public void handleUpdateTag(CompoundTag syncData) {
        super.handleUpdateTag(syncData);
        HolderLookup.Provider lookupProvider = registryLookup();

        if (syncData.contains(DATA, Tag.TAG_LIST)) {
            ListTag dataList = syncData.getList(DATA, Tag.TAG_COMPOUND);

            for (Tag dataEntry : dataList) {
                if (dataEntry instanceof CompoundTag slotData) {
                    int slotIdx = slotData.getInt(INDEX);
                    if (slotIdx >= 0 && slotIdx < dataSlots.size() && slotData.contains(DATA)) {
                        dataSlots.get(slotIdx).parse(lookupProvider, Objects.requireNonNull(slotData.get(DATA)));
                    }
                }
            }

            for (Runnable task : afterDataSync) {
                task.run();
            }
        }
    }

    private byte @Nullable [] createBufferSlotUpdate() {
        List<Integer> needsUpdate = new ArrayList<>();
        for (int i = 0; i < dataSlots.size(); i++) {
            if (dataSlots.get(i).doesNeedUpdate()) {
                needsUpdate.add(i);
            }
        }

        if (needsUpdate.isEmpty()) {
            return null;
        }

        // Fine to use a normal byte buf here, we're not using codecs in here.
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try{
            buf.writeInt(needsUpdate.size());
            needsUpdate.forEach(i -> {
                buf.writeInt(i);
                dataSlots.get(i).write(buf);
            });
            byte[] data = new byte[buf.readableBytes()];
            buf.readBytes(data);
            return data;
        }finally {
            buf.release(); // release the buffer safely
        }
    }

    @Deprecated(forRemoval = true, since = "7.1")
    public <T extends NetworkDataSlot<?>> T addDataSlot(T slot) {
        dataSlots.add(slot);
        return slot;
    }

    /** Explicitly permits this legacy slot to be edited from its open block-entity menu. */
    @Deprecated(forRemoval = true, since = "7.1")
    public <T extends NetworkDataSlot<?>> T addUpdatableDataSlot(T slot) {
        addDataSlot(slot);
        clientUpdatableDataSlots.add(slot);
        return slot;
    }

    @Deprecated(forRemoval = true, since = "7.1")
    public void addAfterSyncRunnable(Runnable runnable) {
        afterDataSync.add(runnable);
    }

    /**
     * Fire this when you change the value of a {@link NetworkDataSlot} on the client side.
     */
    @Deprecated(forRemoval = true, since = "7.1")
    public <T> void clientUpdateSlot(@Nullable NetworkDataSlot<T> slot, T value) {
        if (slot == null) {
            return;
        }

        if (clientUpdatableDataSlots.contains(slot)) {
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            try{
                buf.writeInt(dataSlots.indexOf(slot));
                slot.write(buf, value);
                byte[] data = new byte[buf.readableBytes()];
                buf.readBytes(data);
                CoreNetwork.sendToServer(new ClientboundDataSlotChange(getBlockPos(), data));
            }finally {
                buf.release(); // release the buffer safely
            }
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), Block.UPDATE_NEIGHBORS);
        }
    }

    /**
     * Sync the BlockEntity to all tracking players. Don't call this if you don't know what you do
     */
    @Deprecated(forRemoval = true, since = "7.1")
    @UseOnly(LogicalSide.SERVER)
    public void sync() {
        var syncData = createBufferSlotUpdate();
        if (syncData != null && level instanceof ServerLevel serverLevel) {
            setChanged();
            CoreNetwork.sendToPlayersTrackingChunk(serverLevel, new ChunkPos(getBlockPos()),
                    new ServerboundCDataSlotUpdate(getBlockPos(), syncData));
        }
    }

    @Deprecated(forRemoval = true, since = "7.1")
    public void clientHandleBufferSync(FriendlyByteBuf buf) {
        int count = buf.readInt();
        if (count < 0 || count > dataSlots.size()) throw new DecoderException("Invalid data slot count: " + count);
        for (int i = 0; i < count; i++) {
            int index = buf.readInt();
            if (index < 0 || index >= dataSlots.size()) throw new DecoderException("Invalid data slot index: " + index);
            dataSlots.get(index).read(buf);
        }

        for (Runnable task : afterDataSync) {
            task.run();
        }
    }

    @Deprecated(forRemoval = true, since = "7.1")
    public void serverHandleBufferChange(FriendlyByteBuf buf) {
        int index;
        try {
            index = buf.readInt();
        } catch (Exception e) {
            throw new IllegalStateException("Invalid buffer was passed over the network to the server.");
        }

        if (index < 0 || index >= dataSlots.size()) throw new DecoderException("Invalid data slot index: " + index);
        var slot = dataSlots.get(index);
        if (!clientUpdatableDataSlots.contains(slot)) throw new DecoderException("Data slot is server-owned: " + index);
        slot.read(buf);
    }

    // New Sync

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        saveAdditionalSynced(tag);
    }

    /**
     * Override this to write data which should be synced over the network.
     * Must be opted-in by overriding {@link BlockEntity#getUpdatePacket}.
     */
    protected void saveAdditionalSynced(CompoundTag tag) {
    }

    // endregion

    private HolderLookup.Provider registryLookup() {
        return level == null ? RegistryAccess.EMPTY : level.registryAccess();
    }

    // region Neighboring Capabilities

    @Nullable
    protected <T> T getSelfCapability(Capability<T> capability, @Nullable Direction side) {
        if (!(level instanceof ServerLevel)) return null;
        return getCapability(capability, side).orElse(null);
    }

    /** Resolve the current native provider, so unload, replacement and invalidation cannot leave a stale cache. */
    @Nullable
    protected <T> T getNeighbouringCapability(Capability<T> capability, Direction side) {
        if (!(level instanceof ServerLevel serverLevel)) return null;
        BlockPos target = getBlockPos().relative(side);
        if (!serverLevel.hasChunkAt(target)) return null;
        BlockEntity neighbour = serverLevel.getBlockEntity(target);
        return neighbour == null ? null : neighbour.getCapability(capability, side.getOpposite()).orElse(null);
    }

    // endregion
}
