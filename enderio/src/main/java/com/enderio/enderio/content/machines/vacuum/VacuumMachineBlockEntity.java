package com.enderio.enderio.content.machines.vacuum;

import net.minecraft.world.item.ItemStack;
import com.enderio.core.annotations.UseOnly;
import com.enderio.enderio.api.io.IOMode;
import com.enderio.enderio.foundation.MachineNBTKeys;
import com.enderio.enderio.foundation.attachment.ActionRange;
import com.enderio.enderio.foundation.attachment.RangedActor;
import com.enderio.enderio.foundation.block.entity.MachineBlockEntity;
import com.enderio.enderio.foundation.inventory.SingleSlotAccess;
import com.enderio.enderio.foundation.io.IOConfig;
import com.enderio.enderio.foundation.util.AttractionUtil;
import com.enderio.enderio.init.EIODataComponents;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.fml.LogicalSide;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

// TODO: I want to review the vacuum stuff too.
public abstract class VacuumMachineBlockEntity<T extends Entity> extends MachineBlockEntity implements RangedActor {
    private static final double COLLISION_DISTANCE_SQ = 1.3 * 1.3;
    protected static final double SPEED = 0.025;
    protected static final double SPEED_4 = SPEED * 4;
    private final List<WeakReference<T>> entities = new ArrayList<>();
    private final Class<T> targetClass;
    public static final SingleSlotAccess FILTER = new SingleSlotAccess();

    private static final ActionRange DEFAULT_RANGE = new ActionRange(5, false);

    private ActionRange actionRange = DEFAULT_RANGE;

    public VacuumMachineBlockEntity(BlockEntityType<?> type, BlockPos worldPosition, BlockState blockState,
            Class<T> targetClass) {
        super(type, worldPosition, blockState, false);
        this.targetClass = targetClass;
    }

    @Override
    public boolean isActive() {
        // No active state.
        return false;
    }

    public abstract String getColor();

    @Override
    public void serverTick() {
        if (!this.isRedstoneBlocked()) {
            this.attractEntities(this.getLevel(), this.getBlockPos(), this.getRange());
        }

        super.serverTick();
    }

    @Override
    public void clientTick() {
        if (!this.isRedstoneBlocked()) {
            this.attractEntities(this.getLevel(), this.getBlockPos(), this.getRange());
        }

        if (level.isClientSide && level instanceof ClientLevel clientLevel) {
            getActionRange().addClientParticle(clientLevel, getBlockPos(), getColor());
        }

        super.clientTick();
    }

    @Override
    public IOConfig getDefaultIOConfig() {
        return IOConfig.of(IOMode.PUSH);
    }

    public Predicate<T> getFilter() {
        return (e -> true);
    }

    private void attractEntities(Level level, BlockPos pos, int range) {
        if ((level.getGameTime() + pos.asLong()) % 5 == 0) {
            getEntities(level, pos, range, getFilter());
        }
        Iterator<WeakReference<T>> iterator = entities.iterator();
        while (iterator.hasNext()) {
            WeakReference<T> ref = iterator.next();
            if (ref.get() == null) { // If the entity no longer exists, remove from the list
                iterator.remove();
                continue;
            }
            T entity = ref.get();
            if (entity.isRemoved()) { // If the entity no longer exists, remove from the list
                iterator.remove();
                continue;
            }
            if (AttractionUtil.moveToPos(entity, pos, SPEED, SPEED_4, COLLISION_DISTANCE_SQ)) {
                handleEntity(entity);
            }
        }
    }

    public abstract void handleEntity(T entity);

    private void getEntities(Level level, BlockPos pos, int range, Predicate<T> filter) {
        this.entities.clear();
        AABB area = new AABB(pos).inflate(range);
        for (T ie : level.getEntitiesOfClass(targetClass, area, filter)) {
            this.entities.add(new WeakReference<>(ie));
        }
    }

    @Override
    public ActionRange getActionRange() {
        return actionRange;
    }

    @Override
    @UseOnly(LogicalSide.SERVER)
    public void setActionRange(ActionRange actionRange) {
        this.actionRange = actionRange;
        setChanged();

        if (level != null) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
    }

    @Override
    public int getMaxRange() {
        return 6;
    }

    @Override
    public void onLoad() {
        if (this.entities.isEmpty()) {
            getEntities(getLevel(), getBlockPos(), getRange(), getFilter());
        }

        super.onLoad();
    }

    @Override
    protected void saveAdditionalSynced(CompoundTag tag) {
        var registries = level == null ? net.minecraft.core.RegistryAccess.EMPTY : level.registryAccess();
        super.saveAdditionalSynced(tag);

        if (!actionRange.equals(DEFAULT_RANGE)) {
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
            actionRange = DEFAULT_RANGE;
        }
    }

    @Override
    protected void readItemData(ItemStack componentInput) {
        super.readItemData(componentInput);

        var actionRange = EIODataComponents.ACTION_RANGE.get(componentInput);
        if (actionRange != null) {
            this.actionRange = actionRange;
        }
    }

    @Override
    protected void writeItemData(ItemStack components) {
        super.writeItemData(components);

        // Only if unchanged.
        if (!actionRange.equals(DEFAULT_RANGE)) {
            EIODataComponents.ACTION_RANGE.set(components, actionRange);
        }
    }

    @Override
    public void removeItemDataFromTag(CompoundTag tag) {
        super.removeItemDataFromTag(tag);
        tag.remove(MachineNBTKeys.ACTION_RANGE);
    }
}
