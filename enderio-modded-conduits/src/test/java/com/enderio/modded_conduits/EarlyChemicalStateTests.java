package com.enderio.modded_conduits;

import com.enderio.modded_conduits.common.modules.mekanism.chemical.ChemicalResource;
import com.enderio.modded_conduits.common.modules.mekanism.chemical_filter.EnderChemicalFilter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EarlyChemicalStateTests {
    @Test void emptyChemicalAndFilterCodecsInitializeBeforeNativeRegistryBootstrap() {
        assertTrue(ChemicalResource.EMPTY.isEmpty());
        assertEquals(0, ChemicalResource.EMPTY.getAmount());
        assertSame(ChemicalResource.EMPTY, ChemicalResource.EMPTY.copy());
        ChemicalResource.EMPTY.setAmount(0);
        assertEquals(new CompoundTag(), ChemicalResource.OPTIONAL_CODEC.encodeStart(NbtOps.INSTANCE, ChemicalResource.EMPTY)
            .getOrThrow(false, fail -> fail(fail)));
        assertNotNull(EnderChemicalFilter.CODEC);
    }
}
