package com.enderio.core.gametest;

import com.enderio.core.common.network.EmitParticlePacket;
import com.enderio.core.common.network.NetworkDataSlot;
import com.enderio.core.common.network.codec.PacketCodec;
import com.enderio.core.common.network.codec.PacketCodecs;
import com.enderio.core.common.network.menu.FluidStackSyncSlot;
import com.enderio.core.common.network.menu.RegistrySyncSlot;
import com.enderio.core.common.network.menu.SyncSlot.ChangeType;
import com.enderio.core.common.network.menu.payload.*;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.joml.Vector3f;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;


@GameTestHolder("endercore_tests")
@PrefixGameTestTemplate(false)
public class SyncSlotTests {
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void itemStackPayloadPreservesCountAndCustomTag(GameTestHelper helper) {
        var stack = new ItemStack(Items.DIAMOND, 17);
        stack.getOrCreateTag().putString("port_fixture", "kept");
        var decoded = (ItemStackSlotPayload) roundTrip(helper, SlotPayload.STREAM_CODEC, new ItemStackSlotPayload(stack));
        helper.assertTrue(ItemStack.matches(stack, decoded.value()), "Item count or tag changed in transit");
        helper.assertTrue(roundTrip(helper, PacketCodecs.ITEM_STACK, ItemStack.EMPTY).isEmpty(), "Empty item must remain empty");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void fluidPayloadPreservesAmountTagAndEmptyState(GameTestHelper helper) {
        var stack = new FluidStack(Fluids.WATER, 987);
        stack.getOrCreateTag().putString("port_fixture", "kept");
        var decoded = (FluidStackSlotPayload) roundTrip(helper, SlotPayload.STREAM_CODEC, new FluidStackSlotPayload(stack));
        helper.assertTrue(stack.isFluidStackIdentical(decoded.value()), "Fluid amount or tag changed in transit");
        helper.assertTrue(roundTrip(helper, PacketCodecs.FLUID_STACK, FluidStack.EMPTY).isEmpty(), "Empty fluid must remain empty");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void particlePacketPreservesParticleParametersAndMotion(GameTestHelper helper) {
        var dust = new DustParticleOptions(new Vector3f(0.2F, 0.4F, 0.7F), 1.5F);
        var decoded = roundTrip(helper, EmitParticlePacket.STREAM_CODEC,
            new EmitParticlePacket(dust, 1, 2, 3, 0.1, 0.2, 0.3));
        helper.assertTrue(decoded.particleOptions() instanceof DustParticleOptions, "Wrong particle options type");
        var actual = (DustParticleOptions) decoded.particleOptions();
        helper.assertTrue(dust.getColor().equals(actual.getColor()), "Particle color changed");
        helper.assertTrue(dust.getScale() == actual.getScale(), "Particle scale changed");
        helper.assertTrue(decoded.z() == 3.0, "Particle position changed");
        helper.assertTrue(decoded.zSpeed() == 0.3, "Particle speed changed");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void fluidChangeDetectionDistinguishesQuantityAndNbtChanges(GameTestHelper helper) {
        var slot = FluidStackSyncSlot.standalone();
        slot.set(new FluidStack(Fluids.WATER, 100));
        helper.assertTrue(slot.detectChanges() == ChangeType.FULL, "Expected full slot update");
        helper.assertTrue(slot.detectChanges() == ChangeType.NONE, "Unchanged slot must not update");
        slot.get().setAmount(125);
        helper.assertTrue(slot.detectChanges() == ChangeType.PARTIAL, "Quantity-only change must use partial update");
        slot.get().getOrCreateTag().putInt("variant", 2);
        helper.assertTrue(slot.detectChanges() == ChangeType.FULL, "Expected full slot update");
        helper.assertTrue(slot.detectChanges() == ChangeType.NONE, "Unchanged slot must not update");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void fluidQuantityUpdatePreservesTagWithoutMutatingPreviousStack(GameTestHelper helper) {
        var slot = FluidStackSyncSlot.standalone();
        var previous = new FluidStack(Fluids.WATER, 100);
        previous.getOrCreateTag().putInt("variant", 2);
        slot.set(previous);
        slot.unpackPayload(null, new IntSlotPayload(250));
        helper.assertTrue(slot.get().getAmount() == 250, "Partial update did not change quantity");
        helper.assertTrue(Objects.equals(previous.getTag(), slot.get().getTag()), "Partial update lost fluid tag");
        helper.assertTrue(previous.getAmount() == 100, "Partial update mutated previous stack");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void registrySlotReportsOnlyActualChanges(GameTestHelper helper) {
        var slot = RegistrySyncSlot.standalone(Registries.ITEM);
        helper.assertTrue(slot.detectChanges() == ChangeType.NONE, "Unchanged slot must not update");
        slot.set(Items.DIAMOND);
        helper.assertTrue(slot.detectChanges() == ChangeType.FULL, "Expected full slot update");
        helper.assertTrue(slot.detectChanges() == ChangeType.NONE, "Unchanged slot must not update");
        slot.set(null);
        helper.assertTrue(slot.detectChanges() == ChangeType.FULL, "Expected full slot update");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void incrementalDataSlotSaveIncludesChangesAndSkipsUnchangedValues(GameTestHelper helper) {
        var value = new AtomicInteger(12);
        var slot = NetworkDataSlot.INT.create(value::get, value::set);
        helper.assertTrue(slot.save(RegistryAccess.EMPTY, false) != null, "Initial value must be saved");
        helper.assertFalse(slot.doesNeedUpdate(), "Saved value must not be marked changed");
        helper.assertTrue(slot.save(RegistryAccess.EMPTY, false) == null, "Unchanged value must not be saved incrementally");
        value.set(13);
        helper.assertTrue(slot.doesNeedUpdate(), "Modified value must be marked changed");
        var tag = slot.save(RegistryAccess.EMPTY, false);
        helper.assertTrue(tag != null, "Modified value must be saved");
        value.set(0);
        slot.parse(RegistryAccess.EMPTY, tag);
        helper.assertTrue(value.get() == 13, "Saved value was not restored");
        helper.assertTrue(slot.save(RegistryAccess.EMPTY, true) != null, "Full save must include unchanged values");
        helper.succeed();
    }
    private static <T> T roundTrip(GameTestHelper helper, PacketCodec<T> codec, T value) {
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            codec.encode(buffer, value);
            T decoded = codec.decode(buffer);
            helper.assertTrue(buffer.readableBytes() == 0, "Decoder must consume the whole message");
            return decoded;
        } finally {
            helper.assertTrue(buffer.release(), "Packet buffer must be released");
        }
    }
}
