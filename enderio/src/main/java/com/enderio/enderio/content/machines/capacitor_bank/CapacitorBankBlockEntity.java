package com.enderio.enderio.content.machines.capacitor_bank;

import com.enderio.core.common.capability.CapabilityUtil;
import com.enderio.core.annotations.UseOnly;
import com.enderio.core.common.energy.ItemStackEnergy;
import com.enderio.enderio.api.io.IOConfigurable;
import com.enderio.enderio.api.io.IOMode;
import com.enderio.enderio.api.io.RedstoneControl;
import com.enderio.enderio.foundation.MachineNBTKeys;
import com.enderio.enderio.foundation.block.EIOBlockEntity;
import com.enderio.enderio.foundation.block.entity.Wrenchable;
import com.enderio.enderio.foundation.block.entity.legacy.LegacyMachineBlockEntity;
import com.enderio.enderio.foundation.block.legacy.LegacyMachineBlock;
import com.enderio.enderio.foundation.io.IOConfig;
import com.enderio.enderio.foundation.io.LegacyMachineData;
import com.enderio.enderio.foundation.tag.EIOTags;
import com.enderio.enderio.init.EIODataComponents;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.fml.LogicalSide;
import com.enderio.core.common.capability.NeighborCapability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import java.util.function.BiFunction;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.client.model.data.ModelProperty;
import net.minecraftforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public class CapacitorBankBlockEntity extends EIOBlockEntity implements MenuProvider, Wrenchable, IOConfigurable {

    public static final BiFunction<CapacitorBankBlockEntity, Direction, IEnergyStorage> ENERGY_STORAGE_PROVIDER = CapacitorBankEnergyStorage::getSided;
    public static final int MAX_SIZE = 4_096;
    public static final String NODE_ID = "NODE_ID";

    private IOConfig ioConfig = IOConfig.empty();

    private final CapacitorTier tier;
    private CapacitorBankNode node;
    private int removedEnergy;
    private int legacyEnergy = 0;
    private boolean legacyBlockData;
    private UUID uuid = UUID.randomUUID();

    private final Map<Direction, NeighborCapability<IEnergyStorage>> energyStorageCaches = new EnumMap<>(Direction.class);
    private final Set<SidedEnergy> validPushTargetCache = new HashSet<>();
    private boolean isValidPushTargetCacheDirty = true;

    private static final ModelProperty<IOConfigurable> IO_CONFIG_PROPERTY = LegacyMachineBlockEntity.IO_CONFIG_PROPERTY;
    private static final String DISPLAY_MODES = "displaymodes";

    private final Map<Direction, DisplayMode> displayModes = Util.make(() -> {
        Map<Direction, DisplayMode> map = new EnumMap<>(Direction.class);
        for (Direction direction : new Direction[] { Direction.NORTH, Direction.EAST, Direction.SOUTH,
            Direction.WEST }) {
            map.put(direction, DisplayMode.NONE);
        }

        return map;
    });

    private RedstoneControl redstoneControl = RedstoneControl.ALWAYS_ACTIVE;
    private boolean isRedstoneBlocked;

    public CapacitorBankBlockEntity(BlockEntityType<?> type, BlockPos worldPosition, BlockState blockState, CapacitorTier tier) {
        super(type, worldPosition, blockState);
        this.tier = tier;
    }

    public CapacitorTier tier() {
        return tier;
    }

    public UUID getUuid() {
        return uuid;
    }

    public CapacitorBankNetwork getNetwork() {
        return node.getNetwork();
    }

    @Override
    public AABB getRenderBoundingBox() {
        BlockPos pos = getBlockPos();
        return AABB.ofSize(new Vec3(pos.getX(), pos.getY(), pos.getZ()), 32, 32, 32);
    }

    @Override
    public void setLevel(Level level) {
        super.setLevel(level);

        if (level instanceof ServerLevel serverLevel) {
            CapacitorBankNode savedNode = CapacitorBankSavedData.get(serverLevel).claimNode(this.getBlockPos());
            if (savedNode != null) {
                this.node = savedNode;
                this.node.attach(this);
                this.redstoneControl = this.getNetwork().getRedstoneControl();
            } else {
                if (this.node == null) { //Create a new node if not created by placing a block
                    this.node = new CapacitorBankNode(this);
                }
                CapacitorBankSavedData.onNetworkCreated(serverLevel, this.node.getNetwork()); //Broadcast the network creation
                // A saved node already contains the migrated charge, even if the tile is still from 6.x.
                if (legacyEnergy > 0) {
                    getNetwork().receiveEnergy(getBlockPos(), null, legacyEnergy, false);
                }
            }
            legacyEnergy = 0;
            checkIsRedstoneBlocked();
        }
    }

    public void removeNode() {
        if (level instanceof ServerLevel serverLevel && hasActiveNode()) {
            var network = node.getNetwork();
            removedEnergy = network.getEnergyForNode(node);
            network.extractEnergy(getBlockPos(), null, removedEnergy, false);
            network.remove(node, created -> CapacitorBankSavedData.onNetworkCreated(serverLevel, created));
        }
    }

    boolean hasActiveNode() {
        return !isRemoved() && node != null && node.isValid() && node.getBlockEntity() == this;
    }

    @Override
    public void onLoad() {
        super.onLoad();

        // Create all energy caches
        if (level instanceof ServerLevel serverLevel) {
            // setLevel runs before the restored chunk can be marked dirty.
            if (legacyBlockData) {
                setChanged();
                legacyBlockData = false;
            }
            if (node != null && node.isValid() && !node.hasBlockEntity()) {
                CapacitorBankSavedData.get(serverLevel).claimNode(getBlockPos());
                node.attach(this);
                setRedstoneControl(node.getNetwork().getRedstoneControl());
            }
            for (Direction side : Direction.values()) {
                if (level.getBlockEntity(getBlockPos().relative(side)) instanceof CapacitorBankBlockEntity bank
                    && bank.hasActiveNode()) {
                    bank.node.getNetwork().connect(bank.node, node,
                        discarded -> CapacitorBankSavedData.onNetworkDiscarded(serverLevel, discarded));
                }
            }
        }
    }

    @Override
    public void neighborChanged(Block neighborBlock, BlockPos neighborPos) {
        super.neighborChanged(neighborBlock, neighborPos);

        if (!level.isClientSide()) {
            isValidPushTargetCacheDirty = true;
        }
    }

    @Override
    public void setRemoved() {
        detachNode();
        closeEnergyCapabilityCaches();
        super.setRemoved();
    }

    @Override
    public void onChunkUnloaded() {
        detachNode();
        closeEnergyCapabilityCaches();
        super.onChunkUnloaded();
    }

    private void detachNode() {
        if (level instanceof ServerLevel serverLevel && node != null && node.isValid() && node.detach(this)) {
            CapacitorBankSavedData.get(serverLevel).releaseNode(node);
        }
    }

    private void closeEnergyCapabilityCaches() {
        energyStorageCaches.values().forEach(NeighborCapability::close);
        energyStorageCaches.clear();
        validPushTargetCache.clear();
        isValidPushTargetCacheDirty = true;
    }

    Set<SidedEnergy> getValidPushTargets() {
        if (!(level instanceof ServerLevel serverLevel)) {
            throw new IllegalStateException("getValidPushTargets can only be called on the server.");
        }

        energyStorageCaches.values().forEach(NeighborCapability::refresh);
        if (isValidPushTargetCacheDirty) {
            validPushTargetCache.clear();
            for (Direction side : Direction.values()) {
                if (side == Direction.UP) {
                    continue;
                }

                var cache = energyStorageCaches.computeIfAbsent(side, ignored -> NeighborCapability.create(
                    ForgeCapabilities.ENERGY,
                    serverLevel,
                    getBlockPos().relative(side),
                    side.getOpposite(),
                    () -> !isRemoved(),
                    () -> isValidPushTargetCacheDirty = true));

                var energyStorage = cache.getCapability();
                if (energyStorage != null && !(energyStorage instanceof CapacitorBankEnergyStorage) && energyStorage.canReceive() &&
                    getIOMode(side).canPush()) {
                    validPushTargetCache.add(new SidedEnergy(energyStorage, new CapacitorBankNetwork.SidedPos(getBlockPos(), side)));
                }
            }
            isValidPushTargetCacheDirty = false;
        }

        return validPushTargetCache;
    }

    private void setIOConfig(IOConfig ioConfig) {
        this.ioConfig = ioConfig;

        if (level != null) {
            setChanged();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
    }

    @Override
    public IOMode getIOMode(Direction side) {
        return ioConfig.getMode(translateIOSide(side));
    }

    @Override
    public boolean isIOConfigMutable() {
        return true;
    }

    @Override
    public boolean shouldRenderIOConfigOverlay() {
        return true;
    }

    @Override
    public void setIOMode(Direction side, IOMode mode) {
        Direction localSide = translateIOSide(side);
        setIOConfig(ioConfig.withMode(localSide, mode));

        // Invalidate caps
        CapabilityUtil.refresh(this);
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
        isValidPushTargetCacheDirty = true;
    }

    @Override
    public boolean supportsIOMode(Direction side, IOMode state) {
        return true;
    }

    @Override
    public ModelData getModelData() {
        return ModelData.builder().with(IO_CONFIG_PROPERTY, this).build();
    }

    @UseOnly(LogicalSide.CLIENT)
    private void clientIOConfigChanged() {
        requestModelDataUpdate();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
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

    public final RedstoneControl getRedstoneControl() {
        return redstoneControl;
    }

    public void setRedstoneControl(RedstoneControl control) {
        redstoneControl = control;
        setChanged();
        checkIsRedstoneBlocked();
    }

    public void setNetworkRedstoneControl(RedstoneControl control) {
        node.getNetwork().setRedstoneControl(control);
    }

    @Override
    protected boolean supportsRedstonePower() {
        return true;
    }

    @Override
    protected void updateRedstonePower() {
        super.updateRedstonePower();
        checkIsRedstoneBlocked();
    }

    private void checkIsRedstoneBlocked() {
        isRedstoneBlocked = !redstoneControl.isActive(isRedstonePowered());
    }

    protected boolean isRedstoneBlocked() {
        return isRedstoneBlocked;
    }

    /**
     * Get the facing direction of the machine.
     */
    protected final Direction getBlockFacing() {
        BlockState state = getBlockState();
        if (state.hasProperty(LegacyMachineBlock.FACING)) {
            return getBlockState().getValue(LegacyMachineBlock.FACING);
        }

        return Direction.SOUTH;
    }

    @Override
    public InteractionResult onWrenched(UseOnContext context) {
        if (context.getPlayer() == null || level == null) return InteractionResult.PASS;
        if (context.getPlayer().isSecondaryUseActive()) {
            return com.enderio.enderio.foundation.block.MachineWrench.dismantle(context);
        }
        if (!level.isClientSide && isIOConfigMutable()) cycleIOMode(context.getClickedFace());
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    public DisplayMode getDisplayMode(Direction direction) {
        if (getLevel() == null || !Block.shouldRenderFace(getBlockState(), getLevel(), worldPosition, direction,
            worldPosition.relative(direction))) {
            return DisplayMode.NONE;
        }

        return displayModes.get(direction);
    }

    public void setDisplayMode(Direction direction, DisplayMode mode) {
        displayModes.put(direction, mode);
    }

    public boolean onShiftRightClick(Direction direction, Player player) {
        if (direction.getAxis().getPlane() == Direction.Plane.VERTICAL) {
            return false;
        }

        if (player.getMainHandItem().getItem() instanceof BlockItem
            || player.getOffhandItem().getItem() instanceof BlockItem) {
            return false;
        }

        if (player.getMainHandItem().is(EIOTags.Items.WRENCH)) {
            return false;
        }

        displayModes.put(direction,
            DisplayMode.values()[(displayModes.get(direction).ordinal() + 1) % DisplayMode.values().length]);
        setChanged();
        level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), Block.UPDATE_ALL);
        return true;
    }

    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int i, Inventory inventory, Player player) {
        return new CapacitorBankMenu(i, inventory, this);
    }

    // Opt into network syncing
    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void load(CompoundTag tag) {
        tag = LegacyMachineData.withNativeFields(tag);
        var registries = level == null ? net.minecraft.core.RegistryAccess.EMPTY : level.registryAccess();
        super.load(tag);

        // TODO: Ender IO 8 - remove.
        if (tag.contains(MachineNBTKeys.IO_CONFIG)) {
            ioConfig = IOConfig.parseOptional(registries, tag.getCompound(MachineNBTKeys.IO_CONFIG));
            isValidPushTargetCacheDirty = true;

            if (level != null && level.isClientSide) {
                clientIOConfigChanged();
            }
        }

        // A chunk restores its legacy balance at setLevel. Item placement already has a live node.
        if (level == null) {
            var energy = tag.contains(MachineNBTKeys.ENERGY, Tag.TAG_COMPOUND)
                ? tag.getCompound(MachineNBTKeys.ENERGY) : tag;
            legacyBlockData = energy.contains(MachineNBTKeys.ENERGY_STORED, Tag.TAG_ANY_NUMERIC);
            legacyEnergy = energy.getInt(MachineNBTKeys.ENERGY_STORED);
        }

        if (tag.contains(NODE_ID)) {
            uuid = tag.getUUID(NODE_ID);
        }

        if (tag.contains(DISPLAY_MODES, Tag.TAG_COMPOUND)) {
            loadDisplayModes(tag.getCompound(DISPLAY_MODES));
        }

        // TODO: Ender IO 8 - remove.
        if (tag.contains(MachineNBTKeys.REDSTONE_CONTROL)) {
            redstoneControl = RedstoneControl.parse(registries,
                Objects.requireNonNull(tag.get(MachineNBTKeys.REDSTONE_CONTROL)));
        }
    }

    public void loadDisplayModes(CompoundTag nbt) {
        displayModes.clear();
        for (String key : nbt.getAllKeys()) {
            @Nullable
            Direction dir = Direction.byName(key);
            if (dir != null) {
                displayModes.put(dir, DisplayMode.values()[nbt.getInt(key)]);
            }
        }
    }

    @Override
    protected void saveAdditionalSynced(CompoundTag tag) {
        var registries = level == null ? net.minecraft.core.RegistryAccess.EMPTY : level.registryAccess();
        super.saveAdditionalSynced(tag);

        if (isIOConfigMutable() && ioConfig != null) {
            tag.put(MachineNBTKeys.IO_CONFIG, ioConfig.save(registries));
        }


        tag.put(DISPLAY_MODES, saveDisplayModes());

        if (node != null && node.isValid()) { //Save data if present
            tag.putUUID(NODE_ID, getNetwork().getUuid());
            tag.put(MachineNBTKeys.REDSTONE_CONTROL, getNetwork().getRedstoneControl().save(registries));
        }
    }

    public CompoundTag saveDisplayModes() {
        CompoundTag nbt = new CompoundTag();
        for (var entry : displayModes.entrySet()) {
            nbt.putInt(entry.getKey().getName(), entry.getValue().ordinal());
        }

        return nbt;
    }

    @Override
    protected void readItemData(ItemStack components) {
        super.readItemData(components);

        if (isIOConfigMutable()) {
            ioConfig = EIODataComponents.IO_CONFIG.getOrDefault(components, IOConfig.empty());
        }

        if (this.node == null) { //Called before being placed, so a node needs to be made
            this.node = new CapacitorBankNode(this);
        }

        getNetwork().receiveEnergy(this.getBlockPos(), null, EIODataComponents.ENERGY.getOrDefault(components, 0), false);
    }

    void restoreLegacyItemData(ItemStack stack) {
        if (!(level instanceof ServerLevel serverLevel) || !hasActiveNode()) return;
        var legacy = BlockItem.getBlockEntityData(stack);
        if (legacy == null) return;

        var registries = serverLevel.registryAccess();
        if (legacy.contains("IoConfig", Tag.TAG_COMPOUND) && !EIODataComponents.IO_CONFIG.has(stack)) {
            setIOConfig(IOConfig.parseOptional(registries, legacy.getCompound("IoConfig")));
        }
        if (legacy.contains(MachineNBTKeys.REDSTONE_CONTROL, Tag.TAG_ANY_NUMERIC)) {
            if (getNetwork().positions().size() == 1) {
                setNetworkRedstoneControl(RedstoneControl.parse(registries, legacy.get(MachineNBTKeys.REDSTONE_CONTROL)));
            } else {
                setRedstoneControl(getNetwork().getRedstoneControl());
            }
        }
        if (!EIODataComponents.ENERGY.has(stack)) {
            int energy = Math.min(tier.getStorageCapacity(), ItemStackEnergy.getEnergyStored(stack));
            getNetwork().receiveEnergy(getBlockPos(), null, energy, false);
        }
    }

    @Override
    protected void writeItemData(ItemStack components) {
        super.writeItemData(components);

        if (isIOConfigMutable()) {
            EIODataComponents.IO_CONFIG.set(components, ioConfig);
        }

        // Normal mining exports after removal. Commands can evaluate loot before removal.
        int energy = hasActiveNode() ? getNetwork().getEnergyForNode(node) : removedEnergy;
        if (energy != 0) {
            EIODataComponents.ENERGY.set(components, energy);
        }
    }

    @Override
    public void removeItemDataFromTag(CompoundTag tag) {
        super.removeItemDataFromTag(tag);
        tag.remove(MachineNBTKeys.IO_CONFIG);
    }

    public record SidedEnergy(IEnergyStorage storage, CapacitorBankNetwork.SidedPos sidedPos) {}
}
