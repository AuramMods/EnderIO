package com.enderio.modded_conduits.common.modules.mekanism.chemical_filter;

import com.enderio.core.common.network.menu.SyncSlot;
import com.enderio.core.common.network.menu.payload.LongSlotPayload;
import com.enderio.core.common.network.menu.payload.PairSlotPayload;
import com.enderio.core.common.network.menu.payload.ResourceLocationSlotPayload;
import com.enderio.core.common.network.menu.payload.SlotPayload;
import mekanism.api.chemical.ChemicalType;
import com.enderio.core.common.network.menu.payload.StringSlotPayload;
import com.enderio.core.common.network.menu.payload.NullSlotPayload;
import com.enderio.modded_conduits.common.modules.mekanism.chemical.ChemicalResource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

public abstract class ChemicalStackSyncSlot implements SyncSlot {

    public static ChemicalStackSyncSlot standalone() {
        return new ChemicalStackSyncSlot() {
            private ChemicalResource value = ChemicalResource.EMPTY;

            @Override
            public ChemicalResource get() {
                return value;
            }

            @Override
            public void set(ChemicalResource value) {
                this.value = value;
            }
        };
    }

    public static ChemicalStackSyncSlot simple(Supplier<ChemicalResource> getter, Consumer<ChemicalResource> setter) {
        return new ChemicalStackSyncSlot() {

            @Override
            public ChemicalResource get() {
                return getter.get();
            }

            @Override
            public void set(ChemicalResource value) {
                setter.accept(value);
            }
        };
    }

    public static ChemicalStackSyncSlot readOnly(Supplier<ChemicalResource> getter) {
        return new ChemicalStackSyncSlot() {

            @Override
            public ChemicalResource get() {
                return getter.get();
            }

            @Override
            public void set(ChemicalResource value) {
                throw new UnsupportedOperationException("Attempt to set a read-only sync slot.");
            }
        };
    }

    private ChemicalResource lastValue = ChemicalResource.EMPTY;

    public abstract ChemicalResource get();

    public abstract void set(ChemicalResource value);

    @Override
    public ChangeType detectChanges() {
        var currentValue = get();
        if (Objects.equals(currentValue, lastValue)) {
            return ChangeType.NONE;
        }

        var changeType = ChemicalResource.isSameChemical(currentValue, lastValue) ? ChangeType.PARTIAL : ChangeType.FULL;
        lastValue = currentValue.copy();
        return changeType;
    }

    @Override
    public SlotPayload createPayload(Level level, ChangeType changeType) {
        if (changeType == ChangeType.PARTIAL) {
            return new LongSlotPayload(get().getAmount());
        }

        var value = get();
        if (value.isEmpty()) return new NullSlotPayload();
        return new PairSlotPayload(new PairSlotPayload(new StringSlotPayload(value.family().getSerializedName()),
            new ResourceLocationSlotPayload(value.getChemical().getRegistryName())), new LongSlotPayload(value.getAmount()));
    }

    @Override
    public void unpackPayload(Level level, SlotPayload payload) {
        if (payload instanceof NullSlotPayload) {
            set(ChemicalResource.EMPTY);
        } else if (payload instanceof LongSlotPayload amountPayload) {
            if (amountPayload.value() < 0 || get().isEmpty()) throw new IllegalArgumentException("Invalid partial chemical update");
            set(get().copyWithAmount(amountPayload.value()));
        } else if (payload instanceof PairSlotPayload pair
                && pair.left() instanceof PairSlotPayload identity
                && identity.left() instanceof StringSlotPayload family
                && identity.right() instanceof ResourceLocationSlotPayload chemical
                && pair.right() instanceof LongSlotPayload amount) {
            set(ChemicalResource.fromRegistry(ChemicalType.fromString(family.value()), chemical.value(), amount.value()));
        } else {
            throw new IllegalArgumentException("Invalid chemical sync payload");
        }
    }
}
