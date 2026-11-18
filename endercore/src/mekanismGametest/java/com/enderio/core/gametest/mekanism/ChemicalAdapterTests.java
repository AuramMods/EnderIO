package com.enderio.core.gametest.mekanism;

import com.enderio.core.common.network.menu.SyncSlot;
import com.enderio.core.common.network.menu.payload.LongSlotPayload;
import com.enderio.core.common.network.menu.payload.SlotPayload;
import com.enderio.modded_conduits.common.modules.mekanism.chemical.ChemicalHandlerAdapter;
import com.enderio.modded_conduits.common.modules.mekanism.chemical.ChemicalResource;
import com.enderio.modded_conduits.common.modules.mekanism.chemical_filter.ChemicalStackSyncSlot;
import com.enderio.modded_conduits.common.modules.mekanism.chemical_filter.EnderChemicalFilter;
import com.mojang.serialization.JsonOps;
import net.minecraft.nbt.NbtOps;
import io.netty.buffer.Unpooled;
import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.chemical.*;
import mekanism.common.capabilities.Capabilities;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@GameTestHolder("endercore_mekanism_tests")
@PrefixGameTestTemplate(false)
public final class ChemicalAdapterTests {
    private static final ResourceLocation ID = ResourceLocation.parse("endercore_mekanism_tests:same");
    private static ChemicalResource resource(ChemicalType family, long amount) {
        return ChemicalResource.fromRegistry(family, ID, amount);
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void allFourNativeFamiliesRoundTripJsonNbtAndPackets(GameTestHelper helper) {
        for (var family : ChemicalType.values()) {
            var expected = resource(family, Long.MAX_VALUE);
            var json = ChemicalResource.OPTIONAL_CODEC.encodeStart(JsonOps.INSTANCE, expected).result().orElseThrow();
            var nbt = ChemicalResource.OPTIONAL_CODEC.encodeStart(NbtOps.INSTANCE, expected).result().orElseThrow();
            helper.assertTrue(ChemicalResource.OPTIONAL_CODEC.parse(JsonOps.INSTANCE, json).result().orElseThrow().equals(expected), "JSON changed " + family);
            helper.assertTrue(ChemicalResource.OPTIONAL_CODEC.parse(NbtOps.INSTANCE, nbt).result().orElseThrow().equals(expected), "NBT changed " + family);
            var buffer = new FriendlyByteBuf(Unpooled.buffer());
            try {
                ChemicalResource.OPTIONAL_STREAM_CODEC.encode(buffer, expected);
                var decoded = ChemicalResource.OPTIONAL_STREAM_CODEC.decode(buffer);
                helper.assertTrue(decoded.equals(expected) && decoded.family() == family && buffer.readableBytes() == 0, "Packet changed family/long amount");
            } finally { buffer.release(); }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void missingFamilyAndInvalidAmountsOrIdsAreRejected(GameTestHelper helper) {
        var json = ChemicalResource.OPTIONAL_CODEC.encodeStart(JsonOps.INSTANCE, resource(ChemicalType.GAS, 1)).result().orElseThrow().getAsJsonObject();
        json.remove("chemical_type");
        helper.assertTrue(ChemicalResource.OPTIONAL_CODEC.parse(JsonOps.INSTANCE, json).error().isPresent(), "Ambiguous family silently selected");
        for (var family : ChemicalType.values()) {
            for (long amount : new long[] { 0, -1, Long.MIN_VALUE }) {
                try { resource(family, amount); helper.fail("Invalid amount accepted"); } catch (IllegalArgumentException expected) { }
            }
            try { ChemicalResource.fromRegistry(family, ResourceLocation.parse("endercore_mekanism_tests:missing"), 1); helper.fail("Unknown ID became empty"); }
            catch (IllegalArgumentException expected) { }
        }
        helper.assertTrue(ChemicalResource.OPTIONAL_CODEC.parse(JsonOps.INSTANCE, new com.google.gson.JsonObject()).result().orElseThrow().isEmpty(), "Empty codec changed");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void copiedResourcesDoNotAliasNativeStacksOrEmptySingleton(GameTestHelper helper) {
        for (var family : ChemicalType.values()) {
            var source = resource(family, 80);
            var nativeStack = source.nativeCopy();
            var copy = ChemicalResource.fromNative(nativeStack);
            nativeStack.setAmount(3);
            var resized = copy.copyWithAmount(12);
            helper.assertTrue(source.getAmount() == 80 && copy.getAmount() == 80 && resized.getAmount() == 12,
                "Native or resized stack aliases the original");
            resized.setAmount(0);
            helper.assertTrue(resized.isEmpty() && source.getAmount() == 80 && ChemicalResource.EMPTY.isEmpty(), "Empty mutation leaked");
        }
        try { ChemicalResource.EMPTY.setAmount(1); helper.fail("Untyped empty became nonempty"); }
        catch (IllegalArgumentException expected) { }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void filterDistinguishesFamiliesWithSameRegistryIdAndRetainsQuantity(GameTestHelper helper) {
        for (var allowed : ChemicalType.values()) {
            var sample = resource(allowed, 1);
            var filter = new EnderChemicalFilter(List.of(sample), false);
            sample.setAmount(3);
            helper.assertTrue(filter.matches().get(0).getAmount() == 1, "Filter retained caller's mutable stack");
            var persisted = EnderChemicalFilter.CODEC.encodeStart(NbtOps.INSTANCE, filter).result().orElseThrow();
            var decoded = EnderChemicalFilter.CODEC.parse(NbtOps.INSTANCE, persisted).result().orElseThrow();
            for (var candidate : ChemicalType.values()) {
                var offered = resource(candidate, 75);
                var result = decoded.test(null, offered);
                helper.assertTrue(candidate == allowed ? result == offered && result.getAmount() == 75 : result.isEmpty(), "Filter conflated native chemical families");
                var deny = new EnderChemicalFilter(decoded.matches(), true).test(null, offered);
                helper.assertTrue(candidate == allowed ? deny.isEmpty() : deny == offered, "Deny list changed");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void syncRetainsFamilyChangesPartialAmountsAndClearing(GameTestHelper helper) {
        var server = ChemicalStackSyncSlot.standalone();
        var client = ChemicalStackSyncSlot.standalone();
        for (var family : ChemicalType.values()) {
            server.set(resource(family, 20));
            helper.assertTrue(server.detectChanges() == SyncSlot.ChangeType.FULL, "Family change did not request full sync");
            send(server, client, helper, SyncSlot.ChangeType.FULL);
            helper.assertTrue(client.get().family() == family && client.get().getAmount() == 20, "Full sync lost family");
            server.get().setAmount(45);
            helper.assertTrue(server.detectChanges() == SyncSlot.ChangeType.PARTIAL, "Amount mutation was not detected");
            send(server, client, helper, SyncSlot.ChangeType.PARTIAL);
            helper.assertTrue(client.get().getAmount() == 45 && server.detectChanges() == SyncSlot.ChangeType.NONE, "Partial sync changed");
        }
        server.set(ChemicalResource.EMPTY);
        helper.assertTrue(server.detectChanges() == SyncSlot.ChangeType.FULL, "Clearing did not request full sync");
        send(server, client, helper, SyncSlot.ChangeType.FULL);
        helper.assertTrue(client.get().isEmpty(), "Clearing left a stale chemical");
        try { client.unpackPayload(helper.getLevel(), new LongSlotPayload(5)); helper.fail("Untyped partial update accepted"); }
        catch (IllegalArgumentException expected) { }
        helper.succeed();
    }

    private static void send(ChemicalStackSyncSlot source, ChemicalStackSyncSlot target, GameTestHelper helper, SyncSlot.ChangeType type) {
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            SlotPayload.STREAM_CODEC.encode(buffer, source.createPayload(helper.getLevel(), type));
            target.unpackPayload(helper.getLevel(), SlotPayload.STREAM_CODEC.decode(buffer));
            helper.assertTrue(buffer.readableBytes() == 0, "Trailing chemical sync bytes");
        } finally { buffer.release(); }
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void adapterRoutesToAllNativeTankFamiliesWithoutSimulationMutation(GameTestHelper helper) {
        var handlers = handlers(100);
        var adapter = ChemicalHandlerAdapter.find(handlers::get);
        helper.assertTrue(adapter != null && adapter.getChemicalTanks() == 4, "Missing native chemical capability family");
        for (var family : ChemicalType.values()) {
            var offered = resource(family, 120);
            helper.assertTrue(adapter.insertChemical(offered, Action.SIMULATE).getAmount() == 20, "Simulated capacity changed");
            helper.assertTrue(adapter.extractChemical(resource(family, 1), Action.SIMULATE).isEmpty(), "Simulation inserted into native tank");
            helper.assertTrue(adapter.insertChemical(offered, Action.EXECUTE).getAmount() == 20 && offered.getAmount() == 120, "Insert mutated input or remainder");
            helper.assertTrue(adapter.extractChemical(resource(family, 70), Action.SIMULATE).getAmount() == 70, "Simulated extract failed");
            helper.assertTrue(adapter.extractChemical(resource(family, 70), Action.EXECUTE).getAmount() == 70
                && adapter.extractChemical(resource(family, 100), Action.SIMULATE).getAmount() == 30, "Transfer accounting changed");
        }
        for (int i = 0; i < 4; i++) helper.assertTrue(adapter.getChemicalInTank(i).family() == ChemicalType.values()[i], "Flattened tank family changed");
        var gasOnly = ChemicalHandlerAdapter.find(cap -> cap == Capabilities.GAS_HANDLER ? handlers.get(cap) : null);
        var pigment = resource(ChemicalType.PIGMENT, 12);
        helper.assertTrue(gasOnly.insertChemical(pigment, Action.EXECUTE).equals(pigment)
            && gasOnly.extractChemical(pigment, Action.EXECUTE).isEmpty(), "Missing family accepted or extracted chemical");
        helper.assertTrue(ChemicalHandlerAdapter.find(cap -> null) == null, "Absent capabilities produced an active handler");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void malformedPayloadsAndOversizedFiltersFailWithoutChangingState(GameTestHelper helper) {
        var json = ChemicalResource.OPTIONAL_CODEC.encodeStart(JsonOps.INSTANCE, resource(ChemicalType.GAS, 1)).result().orElseThrow().getAsJsonObject();
        for (var amount : new String[] { "1.5", "9223372036854775808", "-1" }) {
            json.addProperty("amount", new java.math.BigDecimal(amount));
            helper.assertTrue(ChemicalResource.OPTIONAL_CODEC.parse(JsonOps.INSTANCE, json).error().isPresent(), "Invalid JSON amount accepted: " + amount);
        }
        var slot = ChemicalStackSyncSlot.standalone();
        slot.set(resource(ChemicalType.PIGMENT, 50));
        try { slot.unpackPayload(helper.getLevel(), new LongSlotPayload(-1)); helper.fail("Negative partial amount accepted"); }
        catch (IllegalArgumentException expected) { }
        helper.assertTrue(slot.get().family() == ChemicalType.PIGMENT && slot.get().getAmount() == 50, "Invalid sync destroyed existing state");
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buffer.writeBoolean(true);
            buffer.writeUtf("unknown");
            buffer.writeResourceLocation(ID);
            buffer.writeVarLong(1);
            try { ChemicalResource.OPTIONAL_STREAM_CODEC.decode(buffer); helper.fail("Unknown packet family accepted"); }
            catch (io.netty.handler.codec.DecoderException expected) { }
        } finally { buffer.release(); }
        var boundary = new EnderChemicalFilter(256);
        var encoded = EnderChemicalFilter.CODEC.encodeStart(NbtOps.INSTANCE, boundary).result().orElseThrow();
        helper.assertTrue(EnderChemicalFilter.CODEC.parse(NbtOps.INSTANCE, encoded).result().orElseThrow().matches().size() == 256, "Maximum filter length changed");
        try { new EnderChemicalFilter(257); helper.fail("Oversized filter accepted"); }
        catch (IllegalArgumentException expected) { }
        helper.succeed();
    }

    private static Map<Capability<?>, Object> handlers(long capacity) {
        var handlers = new HashMap<Capability<?>, Object>();
        handlers.put(Capabilities.GAS_HANDLER, new TankHandler<>(ChemicalTankBuilder.GAS.createAllValid(capacity, null)));
        handlers.put(Capabilities.INFUSION_HANDLER, new TankHandler<>(ChemicalTankBuilder.INFUSION.createAllValid(capacity, null)));
        handlers.put(Capabilities.PIGMENT_HANDLER, new TankHandler<>(ChemicalTankBuilder.PIGMENT.createAllValid(capacity, null)));
        handlers.put(Capabilities.SLURRY_HANDLER, new TankHandler<>(ChemicalTankBuilder.SLURRY.createAllValid(capacity, null)));
        return handlers;
    }

    /** Real native handler operations backed by Mekanism's own tank implementation. */
    private record TankHandler<C extends Chemical<C>, S extends ChemicalStack<C>>(IChemicalTank<C, S> tank)
            implements IChemicalHandler<C, S> {
        @Override public int getTanks() { return 1; }
        @Override public S getChemicalInTank(int index) { return tank.getStack(); }
        @Override public void setChemicalInTank(int index, S stack) { tank.setStack(stack); }
        @Override public long getTankCapacity(int index) { return tank.getCapacity(); }
        @Override public boolean isValid(int index, S stack) { return tank.isValid(stack); }
        @Override public S insertChemical(int index, S stack, Action action) { return tank.insert(stack, action, AutomationType.EXTERNAL); }
        @Override public S extractChemical(int index, long amount, Action action) { return tank.extract(amount, action, AutomationType.EXTERNAL); }
        @Override public S getEmptyStack() { return tank.getEmptyStack(); }
    }
}
