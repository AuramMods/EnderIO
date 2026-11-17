package com.enderio.core.network;

import com.enderio.core.common.network.EmitParticlePacket;
import com.enderio.core.common.network.NetworkDataSlot;
import com.enderio.core.common.network.codec.PacketCodecs;
import com.enderio.core.common.network.menu.FluidStackSyncSlot;
import com.enderio.core.common.network.menu.RegistrySyncSlot;
import com.enderio.core.common.network.menu.SyncSlot.ChangeType;
import com.enderio.core.common.network.menu.payload.*;
import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import org.joml.Vector3f;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class SyncSlotTests {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void itemStackPayloadPreservesCountAndCustomTag() {
        var stack = new ItemStack(Items.DIAMOND, 17);
        stack.getOrCreateTag().putString("port_fixture", "kept");
        var decoded = (ItemStackSlotPayload) PacketCodecTests.roundTrip(SlotPayload.STREAM_CODEC, new ItemStackSlotPayload(stack));
        assertTrue(ItemStack.matches(stack, decoded.value()));
        assertTrue(PacketCodecTests.roundTrip(PacketCodecs.ITEM_STACK, ItemStack.EMPTY).isEmpty());
    }

    @Test
    void fluidPayloadPreservesAmountTagAndEmptyState() {
        var stack = new FluidStack(Fluids.WATER, 987);
        stack.getOrCreateTag().putString("port_fixture", "kept");
        var decoded = (FluidStackSlotPayload) PacketCodecTests.roundTrip(SlotPayload.STREAM_CODEC, new FluidStackSlotPayload(stack));
        assertTrue(stack.isFluidStackIdentical(decoded.value()));
        assertTrue(PacketCodecTests.roundTrip(PacketCodecs.FLUID_STACK, FluidStack.EMPTY).isEmpty());
    }

    @Test
    void particlePacketPreservesParticleParametersAndMotion() {
        var dust = new DustParticleOptions(new Vector3f(0.2F, 0.4F, 0.7F), 1.5F);
        var decoded = PacketCodecTests.roundTrip(EmitParticlePacket.STREAM_CODEC,
            new EmitParticlePacket(dust, 1, 2, 3, 0.1, 0.2, 0.3));
        assertTrue(decoded.particleOptions() instanceof DustParticleOptions);
        var actual = (DustParticleOptions) decoded.particleOptions();
        assertEquals(dust.getColor(), actual.getColor());
        assertEquals(dust.getScale(), actual.getScale());
        assertEquals(3, decoded.z());
        assertEquals(0.3, decoded.zSpeed());
    }

    @Test
    void fluidChangeDetectionDistinguishesQuantityAndNbtChanges() {
        var slot = FluidStackSyncSlot.standalone();
        slot.set(new FluidStack(Fluids.WATER, 100));
        assertEquals(ChangeType.FULL, slot.detectChanges());
        assertEquals(ChangeType.NONE, slot.detectChanges());
        slot.get().setAmount(125);
        assertEquals(ChangeType.PARTIAL, slot.detectChanges());
        slot.get().getOrCreateTag().putInt("variant", 2);
        assertEquals(ChangeType.FULL, slot.detectChanges());
        assertEquals(ChangeType.NONE, slot.detectChanges());
    }

    @Test
    void fluidQuantityUpdatePreservesTagWithoutMutatingPreviousStack() {
        var slot = FluidStackSyncSlot.standalone();
        var previous = new FluidStack(Fluids.WATER, 100);
        previous.getOrCreateTag().putInt("variant", 2);
        slot.set(previous);
        slot.unpackPayload(null, new IntSlotPayload(250));
        assertEquals(250, slot.get().getAmount());
        assertEquals(previous.getTag(), slot.get().getTag());
        assertEquals(100, previous.getAmount());
    }

    @Test
    void registrySlotReportsOnlyActualChanges() {
        var slot = RegistrySyncSlot.standalone(Registries.ITEM);
        assertEquals(ChangeType.NONE, slot.detectChanges());
        slot.set(Items.DIAMOND);
        assertEquals(ChangeType.FULL, slot.detectChanges());
        assertEquals(ChangeType.NONE, slot.detectChanges());
        slot.set(null);
        assertEquals(ChangeType.FULL, slot.detectChanges());
    }

    @Test
    void incrementalDataSlotSaveIncludesChangesAndSkipsUnchangedValues() {
        var value = new AtomicInteger(12);
        var slot = NetworkDataSlot.INT.create(value::get, value::set);
        assertNotNull(slot.save(RegistryAccess.EMPTY, false));
        assertFalse(slot.doesNeedUpdate());
        assertNull(slot.save(RegistryAccess.EMPTY, false));
        value.set(13);
        assertTrue(slot.doesNeedUpdate());
        var tag = slot.save(RegistryAccess.EMPTY, false);
        assertNotNull(tag);
        value.set(0);
        slot.parse(RegistryAccess.EMPTY, tag);
        assertEquals(13, value.get());
        assertNotNull(slot.save(RegistryAccess.EMPTY, true));
    }
}
