package com.enderio.enderio.content.machines.obelisks;

import com.enderio.enderio.integration.gamestages.StageFeature;
import net.minecraft.world.item.ItemStack;
import com.enderio.enderio.api.EnderIOCapabilities;
import com.enderio.core.annotations.UseOnly;
import com.enderio.enderio.api.capacitor.CapacitorScalable;
import com.enderio.enderio.api.filter.SoulFilter;
import com.enderio.enderio.api.io.IOMode;
import com.enderio.enderio.api.io.energy.EnergyIOMode;
import com.enderio.enderio.content.machines.MachinesLang;
import com.enderio.enderio.foundation.MachineNBTKeys;
import com.enderio.enderio.foundation.attachment.ActionRange;
import com.enderio.enderio.foundation.attachment.RangedActor;
import com.enderio.enderio.foundation.block.entity.PoweredMachineBlockEntity;
import com.enderio.enderio.foundation.block.entity.flags.CapacitorSupport;
import com.enderio.enderio.foundation.inventory.SingleSlotAccess;
import com.enderio.enderio.foundation.io.Forge6MachineItemData;
import com.enderio.enderio.foundation.io.IOConfig;
import com.enderio.enderio.foundation.state.MachineState;
import com.enderio.enderio.foundation.state.MachineStateType;
import com.enderio.enderio.init.EIODataComponents;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.fml.LogicalSide;

import javax.annotation.Nullable;
import java.util.Objects;

public abstract class ObeliskBlockEntity<T extends ObeliskBlockEntity<T>> extends PoweredMachineBlockEntity
        implements RangedActor {

    @Override
    public StageFeature automationStageFeature() {
        return StageFeature.OBELISK;
    }


    private @Nullable AABB aabb;
    private @Nullable ObeliskAreaManager<T> registeredManager;
    public static final SingleSlotAccess FILTER = new SingleSlotAccess();

    private static final ActionRange DEFAULT_RANGE = new ActionRange(0, false);

    private @Nullable ActionRange actionRange;

    public ObeliskBlockEntity(BlockEntityType<?> type, BlockPos worldPosition, BlockState blockState,
                              boolean isIoConfigMutable, CapacitorSupport capacitorSupport, EnergyIOMode energyIOMode,
                              CapacitorScalable scalableEnergyCapacity, CapacitorScalable scalableMaxEnergyUse) {
        super(type, worldPosition, blockState, isIoConfigMutable, capacitorSupport, energyIOMode,
                scalableEnergyCapacity, scalableMaxEnergyUse);
    }

    @Nullable
    protected abstract ObeliskAreaManager<T> getAreaManager(ServerLevel level);

    @Override
    public void setLevel(Level level) {
        var savedRange = this.level == null ? actionRange : null;
        if (this.level != level) {
            unregisterFromManager();
        }
        super.setLevel(level);
        if (savedRange != null && !level.isClientSide() && isCapacitorInstalled()) {
            // The first capacitor update initializes new obelisks at maximum range.
            setActionRangeInternal(savedRange);
        }
        updateFilterState();
    }

    @Override
    public void setRemoved() {
        unregisterFromManager();
        super.setRemoved();
    }

    @Override
    public void onChunkUnloaded() {
        unregisterFromManager();
        super.onChunkUnloaded();
    }

    @SuppressWarnings("unchecked")
    private void unregisterFromManager() {
        if (registeredManager != null) {
            registeredManager.unregister((T) this);
            registeredManager = null;
        }
    }

    @SuppressWarnings("unchecked")
    protected void updateLocations() {
        aabb = new AABB(getBlockPos()).inflate(getRange());

        if (registeredManager != null) {
            registeredManager.update((T) this);
        }
    }

    @Override
    public boolean isActive() {
        return canAct() && (!requiresCapacitor() || isCapacitorInstalled())
                && getEnergyStorage().getEnergyStored() >= getPerTickEnergyCost()
                && (!requiresFilter() || getSoulFilter() != null);
    }

    @Nullable
    protected SoulFilter getSoulFilter() {
        return FILTER.getItemStack(this).getCapability(EnderIOCapabilities.SOUL_FILTER).orElse(null);
    }

    @Override
    public ActionRange getActionRange() {
        return actionRange == null ? DEFAULT_RANGE : actionRange;
    }

    /**
     * Internal method that updates the action range without triggering setChanged() or block updates.
     * This is safe to call during chunk loading (e.g. from setLevel/updateCapacitorData)
     * where calling setChanged() would cause a deadlock by trying to load neighboring chunks.
     */
    private void setActionRangeInternal(ActionRange actionRange) {
        this.actionRange = actionRange.clamp(0, getMaxRange());
        updateLocations();
    }

    @Override
    @UseOnly(LogicalSide.SERVER)
    public void setActionRange(ActionRange actionRange) {
        setActionRangeInternal(actionRange);
        setChanged();

        if (level != null) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
    }

    protected void updateFilterState() {
        updateMachineState(new MachineState(MachineStateType.ERROR, MachinesLang.OBELISK_NO_SOUL_FILTER),
                requiresFilter() && getSoulFilter() == null);
    }

    @Override
    protected void updateCapacitorData() {
        if (level == null || level.isClientSide()) {
            super.updateCapacitorData();
            return;
        }

        int oldMaxRange = getMaxRange();
        super.updateCapacitorData();
        int newMaxRange = getMaxRange();
        if (newMaxRange != oldMaxRange) {
            int newRange = getRange();
            if (newRange <= 0 || getRange() == oldMaxRange) {
                // If the range was 0 (not cap) or was already at max auto increase to the new
                // max
                newRange = newMaxRange;
            } else {
                // Either reduced the max or had a custom range so just ensure it's in bounds
                newRange = Math.min(getRange(), newMaxRange);
            }
            // Use internal method to avoid setChanged() which would cause a deadlock
            // during chunk loading by trying to load neighboring chunks via
            // Level.updateNeighbourForOutputSignal().
            setActionRangeInternal(new ActionRange(newRange, getActionRange().isVisible()));
        }
    }

    @Override
    protected void onInventoryContentsChanged(int slot) {
        super.onInventoryContentsChanged(slot);
        updateFilterState();
    }

    @Override
    public int getMaxRange() {
        return 32;
    }

    @Override
    public void serverTick() {
        updateMachineState(MachineState.ACTIVE, isActive()); // No powered model state, so it needs to be done manually
        super.serverTick();
        if (isActive()) {
            int amount = getPerTickEnergyCost();
            getEnergyStorage().consumeEnergy(amount);
        }
    }

    public int getPerTickEnergyCost() {
        int maxRange = getMaxRange();
        return maxRange == 0 ? 0 : Mth.ceil(getMaxEnergyUse() * ((float) getRange() / maxRange));
    }

    @Override
    public void clientTick() {
        if (level instanceof ClientLevel clientLevel) {
            getActionRange().addClientParticle(clientLevel, getBlockPos(), getColor());
        }

        super.clientTick();
    }

    public abstract String getColor();

    public @Nullable AABB getAABB() {
        return aabb;
    }

    public boolean requiresFilter() {
        return true;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        // Forge assigns the level before native NBT loading is complete. Only
        // join the live index when onLoad runs, and rebuild it after chunk reload.
        if (!isRemoved() && level instanceof ServerLevel serverLevel) {
            registeredManager = getAreaManager(serverLevel);
        }
        updateLocations();
    }

    @Override
    public IOConfig getDefaultIOConfig() {
        return IOConfig.of(IOMode.PULL);
    }

    @Override
    protected void saveAdditionalSynced(CompoundTag tag) {
        var registries = level == null ? net.minecraft.core.RegistryAccess.EMPTY : level.registryAccess();
        super.saveAdditionalSynced(tag);

        if (actionRange != null) {
            tag.put(MachineNBTKeys.ACTION_RANGE, actionRange.save(registries));
        }
    }

    @Override
    public void load(CompoundTag tag) {
        tag = com.enderio.enderio.foundation.io.LegacyMachineData.withNativeFields(tag);
        var registries = level == null ? net.minecraft.core.RegistryAccess.EMPTY : level.registryAccess();
        super.load(tag);

        // TODO: Ender IO 8 - remove support for old attachment loading
        if (tag.contains(MachineNBTKeys.ACTION_RANGE)) {
            actionRange = ActionRange.parse(registries, Objects.requireNonNull(tag.get(MachineNBTKeys.ACTION_RANGE)));
        } else {
            actionRange = Forge6MachineItemData.readActionRange(tag);
            if (actionRange != null && level instanceof ServerLevel && isCapacitorInstalled()) {
                actionRange = actionRange.clamp(0, getMaxRange());
            }
        }
        updateLocations();
    }

    @Override
    protected void readItemData(ItemStack components) {
        super.readItemData(components);

        var actionRange = EIODataComponents.ACTION_RANGE.get(components);
        if (actionRange != null) {
            setActionRangeInternal(actionRange);
        }
    }

    @Override
    protected void writeItemData(ItemStack components) {
        super.writeItemData(components);
        if (actionRange != null) EIODataComponents.ACTION_RANGE.set(components, actionRange);
    }

    @Override
    public void removeItemDataFromTag(CompoundTag tag) {
        super.removeItemDataFromTag(tag);
        tag.remove(MachineNBTKeys.ACTION_RANGE);
    }
}
