package com.enderio.enderio.content.machines.wireless_charger;

import com.enderio.core.annotations.UseOnly;
import com.enderio.enderio.api.capacitor.CapacitorModifier;
import com.enderio.enderio.api.capacitor.QuadraticScalable;
import com.enderio.enderio.api.io.IOMode;
import com.enderio.enderio.api.io.energy.EnergyIOMode;
import com.enderio.enderio.compat.curios.CuriosCompat;
import com.enderio.enderio.config.machines.MachinesConfig;
import com.enderio.enderio.foundation.MachineNBTKeys;
import com.enderio.enderio.foundation.attachment.ActionRange;
import com.enderio.enderio.foundation.attachment.RangedActor;
import com.enderio.enderio.foundation.block.entity.PoweredMachineBlockEntity;
import com.enderio.enderio.foundation.block.entity.flags.CapacitorSupport;
import com.enderio.enderio.foundation.datamap.RangeExtender;
import com.enderio.enderio.foundation.energy.PoweredMachineEnergyStorage;
import com.enderio.enderio.foundation.inventory.MachineInventoryLayout;
import com.enderio.enderio.foundation.io.IOConfig;
import com.enderio.enderio.foundation.tag.EIOTags;
import com.enderio.enderio.init.EIOBlockEntities;
import com.enderio.enderio.init.EIODataComponents;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public class WirelessChargerBlockEntity extends PoweredMachineBlockEntity implements RangedActor {

    public static final QuadraticScalable CAPACITY = new QuadraticScalable(CapacitorModifier.ENERGY_CAPACITY,
            MachinesConfig.COMMON.ENERGY.WIRELESS_CHARGER_CAPACITY);

    public static final QuadraticScalable USAGE = new QuadraticScalable(CapacitorModifier.ENERGY_USE,
            MachinesConfig.COMMON.ENERGY.WIRELESS_CHARGER_USAGE);

    private final ForgeConfigSpec.IntValue energyUpkeep;

    private ActionRange actionRange;
    private int maxRange;
    private @Nullable com.enderio.core.common.datamap.DataMaps.Snapshot rangeData;

    private @Nullable AABB bounds;

    public WirelessChargerBlockEntity(BlockPos worldPosition, BlockState blockState) {
        super(EIOBlockEntities.WIRELESS_CHARGER.get(), worldPosition, blockState, true, CapacitorSupport.REQUIRED,
                EnergyIOMode.Input, CAPACITY, USAGE);
        actionRange = new ActionRange(MachinesConfig.COMMON.WIRELESS_CHARGER_RANGE.get(), false);
        energyUpkeep = MachinesConfig.COMMON.ENERGY.WIRELESS_CHARGER_UPKEEP;
    }

    @Override
    public IOConfig getDefaultIOConfig() {
        return IOConfig.of(IOMode.PULL);
    }

    public void chargeItem() {
        if (level == null || bounds == null) {
            return;
        }
        PoweredMachineEnergyStorage energyStorage = getEnergyStorage();
        if (energyStorage.getEnergyStored() <= 0) {
            return;
        }
        int toDistribute = Math.min(energyStorage.getEnergyStored(), getMaxEnergyUse());

        List<Player> players = level.getEntitiesOfClass(Player.class, bounds);
        for (Player player : players) {
            Inventory inventory = player.getInventory();
            for (int i = 0; i < inventory.getContainerSize(); i++) {
                ItemStack stack = inventory.getItem(i);
                @Nullable
                IEnergyStorage cap = stack.getCapability(ForgeCapabilities.ENERGY).orElse(null);
                if (cap != null && cap.canReceive()) {
                    int received = cap.receiveEnergy(toDistribute, false);
                    energyStorage.consumeEnergy(received);
                    toDistribute -= received;
                    if (toDistribute <= 0) {
                        return;
                    }
                }
            }

            var items = CuriosCompat.getActiveCurios(player, s -> true);
            if (items.isPresent()) {
                for (ItemStack stack : items.get()) {
                    if (stack.isEmpty()) {
                        continue;
                    }
                    @Nullable
                    IEnergyStorage cap = stack.getCapability(ForgeCapabilities.ENERGY).orElse(null);
                    if (cap != null && cap.canReceive()) {
                        int received = cap.receiveEnergy(toDistribute, false);
                        energyStorage.consumeEnergy(received);
                        toDistribute -= received;
                        if (toDistribute <= 0) {
                            return;
                        }
                    }
                }
            }
        }
    }

    @Override
    public void neighborChanged(Block neighborBlock, BlockPos neighborPos) {
        super.neighborChanged(neighborBlock, neighborPos);
        if (level != null && !level.isClientSide() && getBlockPos().above().equals(neighborPos)) {
            calculateMaxRange();
            if (getRangeExtension() > 0 || actionRange.range() > maxRange) {
                // Antenna placed or removed from the top so update range
                setActionRange(new ActionRange(maxRange, actionRange.isVisible()));
            }
        }
    }

    @Override
    public void serverTick() {
        refreshRangeData();
        super.serverTick();
        if (isActive()) {
            getEnergyStorage().consumeEnergy(energyUpkeep.get());
            chargeItem();
        }
    }

    @Override
    public void clientTick() {
        refreshRangeData();
        if (level instanceof ClientLevel clientLevel) {
            getActionRange().addClientParticle(clientLevel, getBlockPos(),
                    MachinesConfig.CLIENT.BLOCKS.WIRELESS_CHARGER_RANGE_COLOR.get());
        }
        super.clientTick();
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null) rangeData = com.enderio.core.common.datamap.DataMaps.snapshot(level.getRecipeManager());
        calculateMaxRange();
        if (level != null && !level.isClientSide() && actionRange.range() > this.maxRange) {
            setActionRange(new ActionRange(maxRange, actionRange.isVisible()));
        }
        updateBounds();
    }

    @Override
    protected void saveAdditionalSynced(CompoundTag tag) {
        var registries = level == null ? net.minecraft.core.RegistryAccess.EMPTY : level.registryAccess();
        super.saveAdditionalSynced(tag);
        tag.put(MachineNBTKeys.ACTION_RANGE, actionRange.save(registries));
    }

    @Override
    public void load(CompoundTag tag) {
        var registries = level == null ? net.minecraft.core.RegistryAccess.EMPTY : level.registryAccess();
        super.load(tag);
        if (tag.contains(MachineNBTKeys.ACTION_RANGE)) {
            actionRange = ActionRange.parse(registries, Objects.requireNonNull(tag.get(MachineNBTKeys.ACTION_RANGE)));
        } else {
            actionRange = new ActionRange(maxRange, false);
        }
    }

    @Override
    protected void readItemData(ItemStack components) {
        super.readItemData(components);
        var actionRange = EIODataComponents.ACTION_RANGE.get(components);
        if (actionRange != null) {
            this.actionRange = actionRange;
        }
    }

    @Override
    protected void writeItemData(ItemStack components) {
        super.writeItemData(components);
        EIODataComponents.ACTION_RANGE.set(components, actionRange);
    }

    @Override
    public void removeItemDataFromTag(CompoundTag tag) {
        super.removeItemDataFromTag(tag);
        tag.remove(MachineNBTKeys.ACTION_RANGE);
    }

    @Override
    public boolean isActive() {
        return hasEnergy() && canAct();
    }

    private void calculateMaxRange() {
        this.maxRange = MachinesConfig.COMMON.WIRELESS_CHARGER_RANGE.get() + getRangeExtension();
        if (level != null) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
    }

    private void refreshRangeData() {
        if (level == null) return;
        var current = com.enderio.core.common.datamap.DataMaps.snapshot(level.getRecipeManager());
        if (current == rangeData) return;
        rangeData = current;
        int oldMaximum = maxRange;
        calculateMaxRange();
        var updated = new ActionRange(actionRange.range() == oldMaximum ? maxRange : Math.min(actionRange.range(), maxRange),
            actionRange.isVisible());
        if (level.isClientSide()) {
            actionRange = updated;
            updateBounds();
        } else setActionRange(updated);
    }

    private void updateBounds() {
        bounds = new AABB(getBlockPos()).inflate(getRange());
    }

    @Override
    public int getMaxRange() {
        return maxRange;
    }

    private int getRangeExtension() {
        int rangeExtension = 0;
        if (level != null) {
            BlockState bs = level.getBlockState(getBlockPos().above());
            if (bs.is(EIOTags.Blocks.RANGE_EXTENDER)) {
                Map<TagKey<Block>, Integer> map = RangeExtender.DATA_MAP.get(level, bs.getBlock());
                if (map != null) {
                    rangeExtension = map.getOrDefault(EIOTags.Blocks.RANGE_EXTENDER, 0);
                }
            }
        }
        return rangeExtension;
    }

    @Override
    public ActionRange getActionRange() {
        return actionRange;
    }

    @Override
    @UseOnly(LogicalSide.SERVER)
    public void setActionRange(ActionRange actionRange) {
        this.actionRange = actionRange.clamp(0, getMaxRange());
        updateBounds();
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
    }

    @Override
    public MachineInventoryLayout createInventoryLayout() {
        return MachineInventoryLayout.builder().capacitor().build();
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new WirelessChargerMenu(containerId, playerInventory, this);
    }
}
