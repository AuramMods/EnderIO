package com.enderio.core.gametest;

import com.enderio.core.common.item.ItemDataKey;
import com.enderio.core.common.network.codec.PacketCodecs;
import com.enderio.core.common.serialization.StackDataCodecs;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("endercore_tests")
@PrefixGameTestTemplate(false)
public class StackDataTests {
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void savedStacksRetainIntegerCountsNbtAndActualForgeCapabilities(GameTestHelper helper) {
        ItemStack stack = SerializationTestContent.ITEM.toStack();
        stack.setCount(9999);
        stack.getOrCreateTag().putString("variant", "filled");
        stack.getOrCreateTag().putIntArray("typed_array", new int[] {1, 128, 9999});
        stack.getCapability(ForgeCapabilities.ENERGY).orElseThrow(IllegalStateException::new).receiveEnergy(531, false);
        var saved = StackDataCodecs.OPTIONAL_ITEM.encodeStart(NbtOps.INSTANCE, stack).result().orElseThrow();
        helper.assertTrue(((CompoundTag) saved).contains("ForgeCaps"), "Fixture did not exercise real Forge capability persistence");
        ItemStack loaded = StackDataCodecs.OPTIONAL_ITEM.parse(NbtOps.INSTANCE, saved).result().orElseThrow();
        helper.assertTrue(loaded.getCount() == 9999 && ItemStack.isSameItemSameTags(stack, loaded), "Logical count or NBT changed");
        helper.assertTrue(loaded.getCapability(ForgeCapabilities.ENERGY).orElseThrow(IllegalStateException::new).getEnergyStored() == 531,
            "Saved Forge capability state was lost");
        loaded.getOrCreateTag().putString("variant", "changed");
        loaded.getCapability(ForgeCapabilities.ENERGY).orElseThrow(IllegalStateException::new).extractEnergy(200, false);
        helper.assertTrue(stack.getTag().getString("variant").equals("filled") &&
            stack.getCapability(ForgeCapabilities.ENERGY).orElseThrow(IllegalStateException::new).getEnergyStored() == 531,
            "Loaded item shares mutable state with its source");
        var json = StackDataCodecs.OPTIONAL_ITEM.encodeStart(JsonOps.INSTANCE, stack).result().orElseThrow();
        ItemStack fromJson = StackDataCodecs.OPTIONAL_ITEM.parse(JsonOps.INSTANCE, json).result().orElseThrow();
        helper.assertTrue(fromJson.getCount() == 9999 && stack.getTag().equals(fromJson.getTag()) && fromJson.getCapability(ForgeCapabilities.ENERGY)
            .orElseThrow(IllegalStateException::new).getEnergyStored() == 531, "JSON codec lost logical count or capability state");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void itemDataPacketsPreserveLargeCountsEmptySlotsAndIndependentNbt(GameTestHelper helper) {
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            for (int count : new int[] {1, 127, 128, 256, 9999, Integer.MAX_VALUE}) {
                var stack = new ItemStack(Items.STONE, count);
                stack.getOrCreateTag().putString("variant", "source");
                PacketCodecs.ITEM_STACK_DATA.encode(buffer, stack);
                var loaded = PacketCodecs.ITEM_STACK_DATA.decode(buffer);
                helper.assertTrue(loaded.getCount() == count && ItemStack.isSameItemSameTags(stack, loaded), "Packet truncated logical count/NBT: " + count);
                loaded.getTag().putString("variant", "changed");
                helper.assertTrue(stack.getTag().getString("variant").equals("source"), "Packet decoded aliased NBT");
            }
            PacketCodecs.ITEM_STACK_DATA.encode(buffer, ItemStack.EMPTY);
            helper.assertTrue(PacketCodecs.ITEM_STACK_DATA.decode(buffer).isEmpty() && buffer.readableBytes() == 0, "Empty entry did not round-trip");
            buffer.clear();
            buffer.writeVarInt(-1);
            rejects(helper, () -> PacketCodecs.ITEM_STACK_DATA.decode(buffer));
            buffer.clear();
            buffer.writeVarInt(9999);
            PacketCodecs.ITEM_STACK.encode(buffer, ItemStack.EMPTY);
            rejects(helper, () -> PacketCodecs.ITEM_STACK_DATA.decode(buffer));
        } finally { buffer.release(); }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void invalidStackDataIsRejectedWithoutOverwritingSavedState(GameTestHelper helper) {
        var unknown = new CompoundTag();
        unknown.putString("id", "endercore_tests:missing_item");
        unknown.putInt("Count", 1);
        helper.assertTrue(StackDataCodecs.OPTIONAL_ITEM.parse(NbtOps.INSTANCE, unknown).result().isEmpty(), "Unknown item fell back to air");
        unknown.putString("id", "minecraft:stone");
        unknown.putInt("Count", -1);
        helper.assertTrue(StackDataCodecs.OPTIONAL_ITEM.parse(NbtOps.INSTANCE, unknown).result().isEmpty(), "Negative item count accepted");
        unknown.remove("Count");
        helper.assertTrue(StackDataCodecs.OPTIONAL_ITEM.parse(NbtOps.INSTANCE, unknown).result().isEmpty(), "Missing count accepted");
        var key = new ItemDataKey<>(ItemDataTestContent.id("stack_data"), StackDataCodecs.OPTIONAL_ITEM);
        var container = ItemDataTestContent.ITEM.toStack();
        container.getOrCreateTagElement("enderio:data").put("endercore_tests:stack_data", unknown.copy());
        var before = container.save(new CompoundTag());
        try { key.get(container); helper.fail("Malformed typed stack was accepted"); }
        catch (IllegalArgumentException expected) { }
        helper.assertTrue(before.equals(container.save(new CompoundTag())), "Malformed saved state was overwritten");
        var fluid = new FluidStack(Fluids.WATER, 4000);
        fluid.getOrCreateTag().putString("variant", "clean");
        var fluidTag = StackDataCodecs.OPTIONAL_FLUID.encodeStart(NbtOps.INSTANCE, fluid).result().orElseThrow();
        var loaded = StackDataCodecs.OPTIONAL_FLUID.parse(NbtOps.INSTANCE, fluidTag).result().orElseThrow();
        helper.assertTrue(StackDataCodecs.fluidsEqual(fluid, loaded) && StackDataCodecs.hashFluid(fluid) == StackDataCodecs.hashFluid(loaded), "Fluid state changed");
        ((CompoundTag) fluidTag).putString("FluidName", "endercore_tests:missing_fluid");
        helper.assertTrue(StackDataCodecs.OPTIONAL_FLUID.parse(NbtOps.INSTANCE, fluidTag).result().isEmpty(), "Unknown fluid accepted");
        ((CompoundTag) fluidTag).putString("FluidName", "minecraft:water");
        ((CompoundTag) fluidTag).putInt("Amount", -1);
        helper.assertTrue(StackDataCodecs.OPTIONAL_FLUID.parse(NbtOps.INSTANCE, fluidTag).result().isEmpty(), "Negative fluid amount accepted");
        helper.succeed();
    }

    private static void rejects(GameTestHelper helper, Runnable action) {
        try { action.run(); } catch (DecoderException expected) { return; }
        helper.fail("Malformed item data packet accepted");
    }
}
