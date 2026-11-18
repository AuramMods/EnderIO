package com.enderio.enderio.foundation.block.entity;

import com.enderio.core.common.capability.CapabilityUtil;
import com.enderio.core.annotations.UseOnly;
import com.enderio.enderio.api.io.IOConfigurable;
import com.enderio.enderio.api.io.IOMode;
import com.enderio.enderio.api.io.RedstoneControl;
import com.enderio.enderio.api.io.SideConfig;
import com.enderio.enderio.api.soul.binding.SoulBindable;
import com.enderio.enderio.foundation.MachineNBTKeys;
import com.enderio.enderio.foundation.block.EIOBlockEntity;
import com.enderio.enderio.foundation.inventory.MachineInventory;
import com.enderio.enderio.foundation.inventory.MachineInventoryLayout;
import com.enderio.enderio.foundation.io.IOConfig;
import com.enderio.enderio.foundation.io.SidedIOConfigurable;
import com.enderio.enderio.foundation.io.TransferUtil;
import com.enderio.enderio.foundation.state.MachineState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import java.util.function.BiFunction;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.client.model.data.ModelProperty;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Base block entity implementation for machines.
 * Implements Redstone Control and the Machine State system.
 */
public abstract class MachineBlockEntity extends EIOBlockEntity
        implements MenuProvider, Wrenchable, IOConfigurable, MachineInventoryHolder {

    public static final BiFunction<MachineBlockEntity, Direction, SideConfig> SIDE_CONFIG_PROVIDER = (be,
                                                                                                                side) -> side != null && be.isIOConfigMutable() ? new SidedIOConfigurable(be, side) : null;

    public static final BiFunction<MachineBlockEntity, Direction, IItemHandler> ITEM_HANDLER_PROVIDER = (be,
            side) -> be.inventory != null ? be.inventory.getForSide(side) : null;

    public static final BiFunction<BlockEntity, Direction, SoulBindable> SOUL_BINDABLE = (be, ctx)
        -> be instanceof SoulBindable bindable ? bindable : null;

    public static final ModelProperty<IOConfigurable> IO_CONFIG_PROPERTY = new ModelProperty<>();

    @Nullable
    private final MachineInventory inventory;

    private IOConfig ioConfig;
    private final boolean isIoConfigMutable;

    private Set<MachineState> states = new HashSet<>();

    private RedstoneControl redstoneControl = RedstoneControl.ALWAYS_ACTIVE;
    private boolean isRedstoneBlocked;

    private final boolean supportsActiveState;

    @Nullable
    private UUID owner;

    public MachineBlockEntity(BlockEntityType<?> type, BlockPos worldPosition, BlockState blockState,
            boolean isIoConfigMutable) {
        super(type, worldPosition, blockState);

        this.isIoConfigMutable = isIoConfigMutable;
        this.ioConfig = getDefaultIOConfig();

        // Create inventory if the machine has a layout
        var layout = createInventoryLayout();
        if (layout != null) {
            inventory = createMachineInventory(layout);
        } else {
            inventory = null;
        }

        this.supportsActiveState = blockState.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.POWERED);
    }

    /**
     * @return True if the machine is active, this applies the POWERED block state if available.
     */
    public abstract boolean isActive();

    @Override
    public void serverTick() {
        super.serverTick();

        if (canAct(distributeResourcesInterval())) {
            distributeResources();
        }

        // Every 5 ticks, ensure active state is up-to-date.
        // Doing this every 5 ticks instead of every tick should reduce visual flicker.
        if (level != null && level.getGameTime() % 5 == 0) {

            boolean isActive = isActive();
            boolean needBlockStateUpdate = supportsActiveState
                    && getBlockState().getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.POWERED) != isActive;
            boolean needStateUpdate = states.contains(MachineState.ACTIVE) != isActive;

            if (needBlockStateUpdate) {
                level.setBlockAndUpdate(worldPosition,
                        getBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.POWERED, isActive));
            }

            if (needStateUpdate) {
                updateMachineState(MachineState.ACTIVE, isActive);
            }
        }
    }

    /**
     * Get the facing direction of the machine.
     */
    protected final Direction getBlockFacing() {
        BlockState state = getBlockState();
        if (state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING)) {
            return getBlockState().getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING);
        }

        return Direction.SOUTH;
    }

    // region Ticking Control

    public boolean canAct() {
        if (level == null) {
            return false;
        }

        return !isRedstoneBlocked();
    }

    public boolean canAct(int interval) {
        return level != null && canAct() && level.getGameTime() % interval == 0;
    }

    // endregion

    // region Inventory

    /**
     * Define the slot layout for the machine.
     * @return The slot layout or null for no inventory.
     */
    @Nullable
    protected MachineInventoryLayout createInventoryLayout() {
        return null;
    }

    /**
     * @apiNote inventories should call {@link MachineBlockEntity#onInventoryContentsChanged}!
     */
    protected MachineInventory createMachineInventory(MachineInventoryLayout layout) {
        return new MachineInventory(this, layout) {
            @Override
            protected void onContentsChanged(int slot) {
                onInventoryContentsChanged(slot);
                setChanged();
            }

            @Override
            public void updateMachineState(MachineState state, boolean add) {
                MachineBlockEntity.this.updateMachineState(state, add);
            }
        };
    }

    public final MachineInventory getInventory() {
        if (!hasInventory()) {
            throw new IllegalStateException("This machine does not have an inventory.");
        }

        return inventory;
    }

    public final boolean hasInventory() {
        return inventory != null;
    }

    /**
     * @apiNote Must call this on custom MachineInventory handlers!
     */
    protected void onInventoryContentsChanged(int slot) {
    }

    // endregion

    // region IO Config

    public IOConfig getDefaultIOConfig() {
        return IOConfig.empty();
    }

    public final IOConfig getIOConfig() {
        if (isIOConfigMutable()) {
            return ioConfig;
        }

        return getDefaultIOConfig();
    }

    private void setIOConfig(IOConfig ioConfig) {
        this.ioConfig = ioConfig;

        if (level != null) {
            setChanged();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
    }

    // region IOConfigurable Implementation

    @Override
    public IOMode getIOMode(Direction side) {
        return getIOConfig().getMode(translateIOSide(side));
    }

    @Override
    public final boolean isIOConfigMutable() {
        return isIoConfigMutable;
    }

    @Override
    public boolean shouldRenderIOConfigOverlay() {
        return isIOConfigMutable();
    }

    @Override
    public void setIOMode(Direction side, IOMode mode) {
        if (!isIOConfigMutable()) {
            throw new IllegalStateException("Cannot edit fixed IO mode.");
        }

        if (!supportsIOMode(side, mode)) {
            throw new IllegalStateException("Cannot use this mode on this side.");
        }

        Direction localSide = translateIOSide(side);
        setIOConfig(ioConfig.withMode(localSide, mode));

        // Invalidate caps
        CapabilityUtil.refresh(this);
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
    }

    @Override
    public ModelData getModelData() {
        if (!shouldRenderIOConfigOverlay()) {
            return ModelData.EMPTY;
        }

        return ModelData.builder().with(IO_CONFIG_PROPERTY, this).build();
    }

    @UseOnly(LogicalSide.CLIENT)
    private void clientIOConfigChanged() {
        if (shouldRenderIOConfigOverlay()) {
            requestModelDataUpdate();
        }

        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
    }

    @Override
    public boolean supportsIOMode(Direction side, IOMode state) {
        return true;
    }

    private Direction translateIOSide(Direction side) {
        // The block faces with its southern face. So the back of the machine.
        Direction south = getBlockFacing();
        return switch (side) {
        case NORTH -> south.getOpposite();
        case SOUTH -> south;
        case WEST -> south.getCounterClockWise();
        case EAST -> south.getClockWise();
        default -> side;
        };
    }

    // endregion

    // endregion

    public void setMachineOwner(UUID owner) {
        this.owner = owner;
        setChanged();
    }

    @Nullable
    public UUID getMachineOwner() {
        return this.owner;
    }

    public UUID getMachineOwnerOrRandom() {
        return Objects.requireNonNullElseGet(getMachineOwner(), UUID::randomUUID);
    }

    // region Resource Distribution

    // TODO: I kind of want to rewrite this without relying on getSelfCapability.

    /**
     * If you distribute resources faster than a normal machine, set the minimum interval here.
     */
    protected int distributeResourcesInterval() {
        return 5;
    }

    @UseOnly(LogicalSide.SERVER)
    protected void distributeResources() {
        // TODO: Quick way to see if any sides are set to force.

        for (Direction side : Direction.values()) {
            IOMode mode = getIOMode(side);
            if (mode.canForce()) {
                distributeResources(side);
            }
        }
    }

    protected void distributeResources(Direction side) {
        if (canAct(20)) {
            distributeItems(side);
        }

        if (canAct(5)) {
            distributeFluids(side);
        }
    }

    private void distributeItems(Direction side) {
        IItemHandler selfHandler = getSelfCapability(ForgeCapabilities.ITEM_HANDLER, side);
        IItemHandler otherHandler = getNeighbouringCapability(ForgeCapabilities.ITEM_HANDLER, side);
        if (selfHandler == null || otherHandler == null) {
            return;
        }

        TransferUtil.distributeItems(getIOMode(side), selfHandler, otherHandler);
    }

    private void distributeFluids(Direction side) {
        IFluidHandler selfHandler = getSelfCapability(ForgeCapabilities.FLUID_HANDLER, side);
        IFluidHandler otherHandler = getNeighbouringCapability(ForgeCapabilities.FLUID_HANDLER, side);
        if (selfHandler == null || otherHandler == null) {
            return;
        }

        TransferUtil.distributeFluids(getIOMode(side), selfHandler, otherHandler);
    }

    // endregion

    // region Machine States

    public Set<MachineState> getMachineStates() {
        return states;
    }

    @UseOnly(LogicalSide.CLIENT)
    public void clientSetMachineStates(Set<MachineState> states) {
        if (level == null) {
            return;
        }

        this.states = states;
    }

    protected void updateMachineState(MachineState state, boolean predicate) {
        if (predicate) {
            pushMachineState(state);
        } else {
            popMachineState(state);
        }
    }

    @UseOnly(LogicalSide.SERVER)
    protected void pushMachineState(MachineState state) {
        if (level == null) {
            return;
        }

        states.add(state);
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
    }

    @UseOnly(LogicalSide.SERVER)
    protected void popMachineState(MachineState state) {
        if (level == null) {
            return;
        }

        if (states.remove(state)) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
    }

    // endregion

    // region Redstone Control

    // TODO: Maybe put this into a constructor parameter instead.
    /**
     * Whether the machine can be controlled by Redstone.
     * Do not turn on/off dynamically.
     */
    public boolean supportsRedstoneControl() {
        return true;
    }

    /**
     * @return The redstone control mode for the machine
     * @throws IllegalStateException if the machine does not support redstone control.
     */
    public final RedstoneControl getRedstoneControl() {
        if (!supportsRedstoneControl()) {
            throw new IllegalStateException("This machine does not support redstone control.");
        }

        return redstoneControl;
    }

    public void setRedstoneControl(RedstoneControl control) {
        redstoneControl = control;
        setChanged();
        checkIsRedstoneBlocked();
    }

    // Final, handoff to supportsRedstoneControl now.
    @Override
    protected final boolean supportsRedstonePower() {
        return supportsRedstoneControl();
    }

    @Override
    protected void updateRedstonePower() {
        super.updateRedstonePower();
        checkIsRedstoneBlocked();
    }

    private void checkIsRedstoneBlocked() {
        if (supportsRedstoneControl()) {
            isRedstoneBlocked = !redstoneControl.isActive(isRedstonePowered());
            updateMachineState(MachineState.REDSTONE, isRedstoneBlocked);
        }
    }

    protected boolean isRedstoneBlocked() {
        return isRedstoneBlocked;
    }

    // endregion

    // region Wrenchable Implementation

    @Override
    public InteractionResult onWrenched(UseOnContext context) {
        if (context.getPlayer() == null || level == null) return InteractionResult.PASS;
        if (context.getPlayer().isSecondaryUseActive()) {
            return com.enderio.enderio.foundation.block.MachineWrench.dismantle(context);
        }
        if (!level.isClientSide && isIOConfigMutable()) cycleIOMode(context.getClickedFace());
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    // endregion

    // region Menu Provider Implementation

    @Override
    public Component getDisplayName() {
        // Default the menu title to the name of the machine's block.
        return getBlockState().getBlock().getName();
    }

    // endregion

    // region Serialization

    @Override
    protected void saveAdditional(CompoundTag tag) {
        var registries = level == null ? net.minecraft.core.RegistryAccess.EMPTY : level.registryAccess();
        super.saveAdditional(tag);

        if (supportsRedstoneControl()) {
            tag.put(MachineNBTKeys.REDSTONE_CONTROL, redstoneControl.save(registries));
        }

        if (hasInventory()) {
            tag.put(MachineNBTKeys.ITEMS, inventory.serializeNBT(registries));
        }
    }

    // Opt into network syncing
    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    protected void saveAdditionalSynced(CompoundTag tag) {
        var registries = level == null ? net.minecraft.core.RegistryAccess.EMPTY : level.registryAccess();
        super.saveAdditionalSynced(tag);

        if (isIoConfigMutable && ioConfig != null) {
            tag.put(MachineNBTKeys.IO_CONFIG, ioConfig.save(registries));
        }

        if (owner != null) {
            tag.putUUID(MachineNBTKeys.OWNER, this.owner);
        }
    }

    @SuppressWarnings("removal")
    @Override
    public void load(CompoundTag tag) {
        var registries = level == null ? net.minecraft.core.RegistryAccess.EMPTY : level.registryAccess();
        tag = com.enderio.enderio.foundation.io.LegacyMachineData.withNativeFields(tag);
        super.load(tag);

        if (hasInventory() && tag.contains(MachineNBTKeys.ITEMS)) {
            inventory.deserializeNBT(registries, tag.getCompound(MachineNBTKeys.ITEMS));
        }

        if (tag.contains(MachineNBTKeys.IO_CONFIG)) {
            ioConfig = IOConfig.parseOptional(tag.getCompound(MachineNBTKeys.IO_CONFIG));
            if (level != null && level.isClientSide) clientIOConfigChanged();
        }
        if (supportsRedstoneControl() && tag.contains(MachineNBTKeys.REDSTONE_CONTROL)) {
            redstoneControl = RedstoneControl.parse(Objects.requireNonNull(tag.get(MachineNBTKeys.REDSTONE_CONTROL)));
        }

        if (tag.contains(MachineNBTKeys.OWNER)) {
            owner = tag.getUUID(MachineNBTKeys.OWNER);
        }
    }

    @Override
    protected void readItemData(ItemStack componentInput) {
        super.readItemData(componentInput);

        // A partially migrated item may carry the remaining state in its BlockEntityTag.
        if (hasInventory()) {
            var contents = ITEM_INVENTORY.get(componentInput);
            if (contents != null) inventory.copyFromItem(contents);
        }

        if (isIOConfigMutable()) {
            var config = IOConfig.ITEM_DATA.get(componentInput);
            if (config != null) ioConfig = config;
        }

        if (supportsRedstoneControl()) {
            var redstoneControl = RedstoneControl.ITEM_DATA.get(componentInput);
            if (redstoneControl != null) {
                setRedstoneControl(redstoneControl);
            }
        }
    }

    @Override
    protected void writeItemData(ItemStack components) {
        super.writeItemData(components);

        if (hasInventory()) {
            ITEM_INVENTORY.set(components, this.inventory.toItemContents());
        }

        if (isIOConfigMutable()) {
            IOConfig.ITEM_DATA.set(components, ioConfig);
        }

        if (supportsRedstoneControl()) {
            RedstoneControl.ITEM_DATA.set(components, redstoneControl);
        }
    }

    @SuppressWarnings("deprecation")
    @Override
    public void removeItemDataFromTag(CompoundTag tag) {
        super.removeItemDataFromTag(tag);
        tag.remove(MachineNBTKeys.ITEMS);
        tag.remove(MachineNBTKeys.IO_CONFIG);
        tag.remove(MachineNBTKeys.REDSTONE_CONTROL);
        if (this instanceof com.enderio.enderio.foundation.attachment.FluidTankUser) {
            // Item fluid capabilities may drain the carried stack before it is placed again.
            tag.remove(MachineNBTKeys.FLUIDS);
        }
    }

    // endregion
}
