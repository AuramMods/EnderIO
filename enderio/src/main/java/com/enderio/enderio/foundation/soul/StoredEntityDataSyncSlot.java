package com.enderio.enderio.foundation.soul;

import com.enderio.core.common.network.menu.SyncSlot;
import com.enderio.core.common.network.menu.payload.CompoundTagSlotPayload;
import com.enderio.core.common.network.menu.payload.ListSlotPayload;
import com.enderio.core.common.network.menu.payload.ResourceLocationSlotPayload;
import com.enderio.core.common.network.menu.payload.SlotPayload;
import com.enderio.enderio.api.soul.Soul;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

public abstract class StoredEntityDataSyncSlot implements SyncSlot {

    public static StoredEntityDataSyncSlot standalone() {
        return new StoredEntityDataSyncSlot() {
            private Soul value = Soul.EMPTY;

            @Override
            public Soul get() {
                return value;
            }

            @Override
            public void set(Soul value) {
                this.value = value;
            }
        };
    }

    public static StoredEntityDataSyncSlot simple(Supplier<Soul> getter, Consumer<Soul> setter) {
        return new StoredEntityDataSyncSlot() {

            @Override
            public Soul get() {
                return getter.get();
            }

            @Override
            public void set(Soul value) {
                setter.accept(value);
            }
        };
    }

    public static StoredEntityDataSyncSlot readOnly(Supplier<Soul> getter) {
        return new StoredEntityDataSyncSlot() {

            @Override
            public Soul get() {
                return getter.get();
            }

            @Override
            public void set(Soul value) {
                throw new UnsupportedOperationException("Attempt to set a read-only sync slot.");
            }
        };
    }

    private Soul lastValue;
    public abstract Soul get();
    public abstract void set(Soul value);

    @Override
    public ChangeType detectChanges() {
        var currentValue = get();
        if (Objects.equals(currentValue, lastValue)) {
            return ChangeType.NONE;
        }

        lastValue = currentValue;
        return ChangeType.FULL;
    }

    @Override
    public SlotPayload createPayload(Level level, ChangeType changeType) {
        var currentValue = get();
        if (currentValue.isEmpty()) {
            return new ListSlotPayload(List.of());
        }

        return new ListSlotPayload(List.of(
            new ResourceLocationSlotPayload(currentValue.entityTypeId()),
            new CompoundTagSlotPayload(currentValue.entityTag().copy())
        ));
    }

    @Override
    public void unpackPayload(Level level, SlotPayload payload) {
        if (payload instanceof ListSlotPayload listPayload) {
            List<SlotPayload> contents = listPayload.contents();
            if (contents.isEmpty()) {
                set(Soul.EMPTY);
            } else if (contents.get(0) instanceof ResourceLocationSlotPayload locationPayload) {
                var type = BuiltInRegistries.ENTITY_TYPE.getOptional(locationPayload.value());
                var tag = contents.size() > 1 && contents.get(1) instanceof CompoundTagSlotPayload tagPayload
                    ? tagPayload.value().copy() : new CompoundTag();
                set(type.map(entityType -> new Soul(entityType, tag)).orElse(Soul.EMPTY));
            }
        }
    }
}
