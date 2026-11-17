package com.enderio.enderio.foundation.block.entity.legacy;

import com.enderio.core.common.capability.CapabilityUtil;
import com.enderio.core.common.blockentity.EnderBlockEntity;
import com.enderio.core.common.network.NetworkDataSlot;
import com.enderio.enderio.api.io.IOConfigurable;
import com.enderio.enderio.api.io.IOMode;
import com.enderio.enderio.api.io.RedstoneControl;
import com.enderio.enderio.api.io.SideConfig;
import com.enderio.enderio.foundation.MachineNBTKeys;
import com.enderio.enderio.foundation.block.entity.MachineInventoryHolder;
import com.enderio.enderio.foundation.block.entity.Wrenchable;
import com.enderio.enderio.foundation.block.legacy.LegacyMachineBlock;
import com.enderio.enderio.foundation.inventory.MachineInventory;
import com.enderio.enderio.foundation.inventory.MachineInventoryLayout;
import com.enderio.enderio.foundation.io.IOConfig;
import com.enderio.enderio.foundation.io.SidedIOConfigurable;
import com.enderio.enderio.foundation.io.TransferUtil;
import com.enderio.enderio.foundation.state.MachineState;
import com.enderio.enderio.init.EIODataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
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

@Deprecated(forRemoval = true, since = "7.1")
public abstract class LegacyMachineBlockEntity extends EnderBlockEntity
        implements MenuProvider, Wrenchable, IOConfigurable, MachineInventoryHolder {

    public static final BiFunction<LegacyMachineBlockEntity, Direction, SideConfig> SIDE_CONFIG_PROVIDER = SidedIOConfigurable::new;

    public static final BiFunction<LegacyMachineBlockEntity, Direction, IItemHandler> ITEM_HANDLER_PROVIDER = (
            be, side) -> be.inventory != null ? be.inventory.getForSide(side) : null;

    // region IO Configuration

    private final IOConfig defaultIOConfig;

    public static final ModelProperty<IOConfigurable> IO_CONFIG_PROPERTY = com.enderio.enderio.foundation.block.entity.MachineBlockEntity.IO_CONFIG_PROPERTY;

    private ModelData modelData = ModelData.EMPTY;

    // endregion

    // region Items

    @Nullable
    private final MachineInventory inventory;
    @Nullable
    private final MachineInventoryLayout inventoryLayout;

    // endregion

    private boolean isRedstoneBlocked;

    // region Common Dataslots

    public static final NetworkDataSlot.CodecType<RedstoneControl> REDSTONE_CONTROL_DATA_SLOT_TYPE = new NetworkDataSlot.CodecType<>(
            RedstoneControl.CODEC, RedstoneControl.STREAM_CODEC);

    private final NetworkDataSlot<RedstoneControl> redstoneControlDataSlot;
    private final @Nullable NetworkDataSlot<IOConfig> ioConfigDataSlot;

    // endregion

    private IOConfig ioConfig;
    private RedstoneControl redstoneControl = RedstoneControl.ALWAYS_ACTIVE;

    private Set<MachineState> states = new HashSet<>();

    public LegacyMachineBlockEntity(BlockEntityType<?> type, BlockPos worldPosition, BlockState blockState) {
        super(type, worldPosition, blockState);

        // Create IO Config.
        this.defaultIOConfig = getDefaultIOConfig();

        this.ioConfig = IOConfig.copyOf(defaultIOConfig);

        // If the machine declares an inventory layout, use it to create a handler
        inventoryLayout = createInventoryLayout();
        if (inventoryLayout != null) {
            inventory = createMachineInventory(inventoryLayout);
        } else {
            inventory = null;
        }

        if (supportsRedstoneControl()) {
            redstoneControlDataSlot = addUpdatableDataSlot(
                    REDSTONE_CONTROL_DATA_SLOT_TYPE.create(this::getRedstoneControl, this::internalSetRedstoneControl));
        } else {
            redstoneControlDataSlot = null;
        }

        // Register sync slot for ioConfig and setup model data.
        if (isIOConfigMutable()) {
            ioConfigDataSlot = addUpdatableDataSlot(IOConfig.DATA_SLOT_TYPE.create(this::getIOConfig, v -> {
                setIOConfig(v);

                if (level != null && level.isClientSide()) {
                    onIOConfigChanged();
                }

                CapabilityUtil.refresh(this);
            }));
        } else {
            ioConfigDataSlot = null;
        }

        addDataSlot(MachineState.DATA_SLOT_TYPE.create(this::getMachineStates, l -> states = l));
    }

    // region New IO Config

    public IOConfig getDefaultIOConfig() {
        return IOConfig.empty();
    }

    private IOConfig getIOConfig() {
        if (isIOConfigMutable()) {
            return ioConfig;
        }

        return defaultIOConfig;
    }

    /**
     * Avoid state-based conditionals in here as this is called once in the constructor to determine whether to sync.
     * @return Whether the player can edit the IO Config.
     */
    public boolean isIOConfigMutable() {
        return true;
    }

    /**
     * @return Whether the block model should show the IO config states.
     */
    public boolean shouldRenderIOConfigOverlay() {
        return isIOConfigMutable();
    }

    public final IOMode getIOMode(Direction side) {
        return getIOConfig().getMode(translateIOSide(side));
    }

    /**
     * Override to declare custom constraints on IOMode's for sides of blocks.
     */
    @SuppressWarnings("unused")
    public boolean supportsIOMode(Direction side, IOMode mode) {
        return true;
    }

    public final void setIOMode(Direction side, IOMode mode) {
        if (!isIOConfigMutable()) {
            throw new IllegalStateException("Cannot edit fixed IO mode.");
        }

        if (!supportsIOMode(side, mode)) {
            throw new IllegalStateException("Cannot use this mode on this side.");
        }

        Direction localSide = translateIOSide(side);

        var ioConfig = getIOConfig();
        var oldMode = ioConfig.getMode(localSide);
        var newIOConfig = ioConfig.withMode(localSide, mode);
        setIOConfig(newIOConfig);

        // Fire change event
        onIOConfigChanged(side, oldMode, mode);
    }

    private void setIOConfig(IOConfig config) {
        if (!isIOConfigMutable()) {
            throw new IllegalStateException("Cannot set IO config when isIOConfigMutable is false.");
        }

        ioConfig = config;

        if (level == null) {
            return;
        }

        // Mark entity as changed.
        setChanged();

        // Invalidate capabilities
        CapabilityUtil.refresh(this);

        // Notify neighbors of update
        level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
    }

    protected void onIOConfigChanged(Direction side, IOMode oldMode, IOMode newMode) {
        if (level != null && level.isClientSide() && isIOConfigMutable()) {
            clientUpdateSlot(ioConfigDataSlot, getIOConfig());
        }
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

    protected Direction getBlockFacing() {
        BlockState state = getBlockState();
        if (state.hasProperty(LegacyMachineBlock.FACING)) {
            return getBlockState().getValue(LegacyMachineBlock.FACING);
        }

        return Direction.SOUTH;
    }

    @Override
    public ModelData getModelData() {
        return shouldRenderIOConfigOverlay() ? modelData : ModelData.EMPTY;
    }

    private void onIOConfigChanged() {
        if (this.level == null) {
            return;
        }

        if (shouldRenderIOConfigOverlay()) {
            modelData = modelData.derive().with(IO_CONFIG_PROPERTY, this).build();
            requestModelDataUpdate();
        }

        this.level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
    }

    // endregion

    // region Redstone Control

    /**
     * Whether this block entity supports redstone control
     */
    public boolean supportsRedstoneControl() {
        return true;
    }

    public boolean isRedstoneBlocked() {
        return isRedstoneBlocked;
    }

    public RedstoneControl getRedstoneControl() {
        if (!supportsRedstoneControl()) {
            throw new IllegalStateException("This machine does not support redstone control.");
        }

        return redstoneControl;
    }

    public void setRedstoneControl(RedstoneControl redstoneControl) {
        if (!supportsRedstoneControl()) {
            throw new IllegalStateException("This machine does not support redstone control.");
        }

        if (level != null && level.isClientSide()) {
            clientUpdateSlot(redstoneControlDataSlot, redstoneControl);
        } else {
            internalSetRedstoneControl(redstoneControl);
        }
    }

    private void internalSetRedstoneControl(RedstoneControl redstoneControl) {
        this.redstoneControl = redstoneControl;
        setChanged();
        updateRedstone();
    }

    // endregion

    // region Inventory

    /**
     * Create the block entity's inventory slot layout.
     */
    @Nullable
    public MachineInventoryLayout createInventoryLayout() {
        return null;
    }

    /**
     * Get the block entity's inventory slot layout.
     */
    @Nullable
    public MachineInventoryLayout getInventoryLayout() {
        return inventoryLayout;
    }

    @Nullable
    public final MachineInventory getInventory() {
        return inventory;
    }

    @Override
    public boolean hasInventory() {
        return inventory != null;
    }

    /**
     * Only call this if you're sure your machine has an inventory.
     */
    protected final MachineInventory getInventoryNN() {
        return Objects.requireNonNull(inventory);
    }

    /**
     * Called to create an item handler if a slot layout is provided.
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
                LegacyMachineBlockEntity.this.updateMachineState(state, add);
            }
        };
    }

    /**
     * @apiNote Must call this on custom MachineInventory handlers!
     */
    protected void onInventoryContentsChanged(int slot) {
    }

    // endregion

    // region Block Entity ticking

    @Override
    public void serverTick() {
        if (canActSlow()) {
            forceResources();
        }

        super.serverTick();
    }

    public boolean canAct() {
        if (this.level == null) {
            return false;
        }

        return !isRedstoneBlocked;
    }

    public boolean canActSlow() {
        if (this.level == null) {
            return false;
        }

        return canAct() && this.level.getGameTime() % 5 == 0;
    }

    // endregion

    // region Resource movement

    /**
     * Push and pull resources to/from other blocks.
     */
    private void forceResources() {
        for (Direction direction : Direction.values()) {
            if (getIOMode(direction).canForce()) {
                // TODO: Maybe some kind of resource distributor so that items are transmitted
                // evenly around? rather than taking the order of Direction.values()
                moveItems(direction);
                moveFluids(direction);
            }
        }
    }

    /**
     * Move items to and from via the given side.
     */
    private void moveItems(Direction side) {
        IItemHandler selfHandler = getSelfCapability(ForgeCapabilities.ITEM_HANDLER, side);
        IItemHandler otherHandler = getNeighbouringCapability(ForgeCapabilities.ITEM_HANDLER, side);
        if (selfHandler == null || otherHandler == null) {
            return;
        }

        TransferUtil.distributeItems(getIOMode(side), selfHandler, otherHandler);
    }

    /**
     * Move fluids to and from via the given side.
     */
    private void moveFluids(Direction side) {
        IFluidHandler selfHandler = getSelfCapability(ForgeCapabilities.FLUID_HANDLER, side);
        IFluidHandler otherHandler = getNeighbouringCapability(ForgeCapabilities.FLUID_HANDLER, side);
        if (selfHandler == null || otherHandler == null) {
            return;
        }

        TransferUtil.distributeFluids(getIOMode(side), selfHandler, otherHandler);
    }

    // endregion

    // region Serialization

    @Override
    public void onLoad() {
        super.onLoad();
        updateRedstone();
    }

    @Override
    public void saveAdditional(CompoundTag tag) {
        var lookupProvider = level == null ? net.minecraft.core.RegistryAccess.EMPTY : level.registryAccess();
        super.saveAdditional(tag);
        if (isIOConfigMutable()) tag.put(MachineNBTKeys.IO_CONFIG, ioConfig.save());
        if (supportsRedstoneControl()) tag.put(MachineNBTKeys.REDSTONE_CONTROL, redstoneControl.save());

        if (this.inventory != null) {
            tag.put(MachineNBTKeys.ITEMS, inventory.serializeNBT(lookupProvider));
        }
    }

    @Override
    public void load(CompoundTag tag) {
        tag = com.enderio.enderio.foundation.io.LegacyMachineData.withNativeFields(tag);
        if (tag.contains(MachineNBTKeys.IO_CONFIG)) ioConfig = IOConfig.parseOptional(tag.getCompound(MachineNBTKeys.IO_CONFIG));
        if (tag.contains(MachineNBTKeys.REDSTONE_CONTROL)) redstoneControl = RedstoneControl.parse(tag.get(MachineNBTKeys.REDSTONE_CONTROL));
        var lookupProvider = level == null ? net.minecraft.core.RegistryAccess.EMPTY : level.registryAccess();
        if (this.inventory != null) {
            inventory.deserializeNBT(lookupProvider, tag.getCompound(MachineNBTKeys.ITEMS));
        }

        // For rendering io overlays after placed by an nbt filled block item
        if (this.level != null) {
            onIOConfigChanged();
        }

        super.load(tag);
    }

    @Override
    protected void readItemData(ItemStack components) {
        super.readItemData(components);
        if (supportsRedstoneControl()) redstoneControl = EIODataComponents.REDSTONE_CONTROL.getOrDefault(components, RedstoneControl.ALWAYS_ACTIVE);

        if (this.inventory != null) {
            this.inventory.copyFromItem(ITEM_INVENTORY.getOrDefault(components, new CompoundTag()));
        }

        if (isIOConfigMutable()) {
            ioConfig = EIODataComponents.IO_CONFIG.getOrDefault(components, IOConfig.empty());
        }
    }

    @Override
    protected void writeItemData(ItemStack components) {
        super.writeItemData(components);
        if (supportsRedstoneControl()) EIODataComponents.REDSTONE_CONTROL.set(components, redstoneControl);

        if (this.inventory != null) {
            ITEM_INVENTORY.set(components, this.inventory.toItemContents());
        }

        if (isIOConfigMutable()) {
            EIODataComponents.IO_CONFIG.set(components, ioConfig);
        }
    }

    @SuppressWarnings("deprecation")
    @Override
    public void removeItemDataFromTag(CompoundTag tag) {
        super.removeItemDataFromTag(tag);
        tag.remove(MachineNBTKeys.ITEMS);
        tag.remove(MachineNBTKeys.IO_CONFIG);
        tag.remove(MachineNBTKeys.REDSTONE_CONTROL);
    }

    // endregion

    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    // TODO: Rename to onBlockEntityItemUsed?
    // called when a player uses the block entity, before menu is may open.
    public InteractionResult onBlockEntityUsed(BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        return InteractionResult.PASS;
    }

    public boolean stillValid(Player player) {
        if (this.level == null) {
            return false;
        }

        if (this.level.getBlockEntity(this.worldPosition) != this) {
            return false;
        }

        return player.canReach(this.worldPosition, 1.5);
    }

    // TODO: should be enabled once the client side call in WrenchableBlockHandler
    // is removed
    // @EnsureSide(EnsureSide.Side.SERVER)
    @Override
    public InteractionResult onWrenched(UseOnContext context) {
        if (context.getPlayer() == null || level == null) return InteractionResult.PASS;
        if (context.getPlayer().isSecondaryUseActive()) {
            return com.enderio.enderio.foundation.block.MachineWrench.dismantle(context);
        }
        if (!level.isClientSide && isIOConfigMutable()) cycleIOMode(context.getClickedFace());
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    public boolean canOpenMenu() {
        return true;
    }

    public int getLightEmission() {
        return getBlockState().getLightEmission();
    }

    public Set<MachineState> getMachineStates() {
        return states;
    }

    public void updateMachineState(MachineState state, boolean add) {
        if (level != null && level.isClientSide) {
            return;
        }
        if (add) {
            states.add(state);
        } else {
            states.remove(state);
        }
    }

    public void neighborChanged(BlockState state, LevelReader level, BlockPos pos, BlockPos neighbor) {
        updateRedstone();
    }

    private void updateRedstone() {
        if (supportsRedstoneControl()) {
            boolean active = getRedstoneControl().isActive(this.level.hasNeighborSignal(worldPosition));
            updateMachineState(MachineState.REDSTONE, !active);
            this.isRedstoneBlocked = !active;
        }
    }
}
