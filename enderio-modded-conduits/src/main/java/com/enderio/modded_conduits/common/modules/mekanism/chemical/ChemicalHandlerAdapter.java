package com.enderio.modded_conduits.common.modules.mekanism.chemical;

import mekanism.api.Action;
import mekanism.api.chemical.ChemicalType;
import mekanism.api.chemical.IChemicalHandler;
import mekanism.common.capabilities.Capabilities;
import net.minecraftforge.common.capabilities.Capability;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.function.Function;

/** Routes Ender IO's chemical operations to the four real Mekanism capabilities. */
public final class ChemicalHandlerAdapter {
    private final EnumMap<ChemicalType, IChemicalHandler<?, ?>> handlers = new EnumMap<>(ChemicalType.class);

    private ChemicalHandlerAdapter() { }

    public static @Nullable ChemicalHandlerAdapter find(Function<Capability<?>, @Nullable Object> lookup) {
        var adapter = new ChemicalHandlerAdapter();
        adapter.add(ChemicalType.GAS, lookup.apply(Capabilities.GAS_HANDLER));
        adapter.add(ChemicalType.INFUSION, lookup.apply(Capabilities.INFUSION_HANDLER));
        adapter.add(ChemicalType.PIGMENT, lookup.apply(Capabilities.PIGMENT_HANDLER));
        adapter.add(ChemicalType.SLURRY, lookup.apply(Capabilities.SLURRY_HANDLER));
        return adapter.handlers.isEmpty() ? null : adapter;
    }

    private void add(ChemicalType family, @Nullable Object handler) {
        if (handler instanceof IChemicalHandler<?, ?> chemicalHandler) handlers.put(family, chemicalHandler);
    }

    public int getChemicalTanks() {
        return handlers.values().stream().mapToInt(IChemicalHandler::getTanks).sum();
    }

    public ChemicalResource getChemicalInTank(int index) {
        if (index < 0) throw new IndexOutOfBoundsException(index);
        for (var handler : handlers.values()) {
            if (index < handler.getTanks()) return ChemicalResource.fromNative(handler.getChemicalInTank(index));
            index -= handler.getTanks();
        }
        throw new IndexOutOfBoundsException(index);
    }

    public ChemicalResource extractChemical(long amount, Action action) {
        if (amount <= 0) return ChemicalResource.EMPTY;
        for (var handler : handlers.values()) {
            var extracted = ChemicalResource.fromNative(handler.extractChemical(amount, action));
            if (!extracted.isEmpty()) return extracted;
        }
        return ChemicalResource.EMPTY;
    }

    // The family selects the exact native handler, so the stack has its required concrete type.
    @SuppressWarnings({"rawtypes", "unchecked"})
    public ChemicalResource extractChemical(ChemicalResource resource, Action action) {
        if (resource.isEmpty()) return ChemicalResource.EMPTY;
        IChemicalHandler handler = handlers.get(resource.family());
        return handler == null ? ChemicalResource.EMPTY : ChemicalResource.fromNative(handler.extractChemical(resource.nativeCopy(), action));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public ChemicalResource insertChemical(ChemicalResource resource, Action action) {
        if (resource.isEmpty()) return ChemicalResource.EMPTY;
        IChemicalHandler handler = handlers.get(resource.family());
        return handler == null ? resource.copy() : ChemicalResource.fromNative(handler.insertChemical(resource.nativeCopy(), action));
    }
}
