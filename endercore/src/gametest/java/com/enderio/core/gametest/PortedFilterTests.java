package com.enderio.core.gametest;

import com.enderio.core.common.item.ItemDataKey;
import com.enderio.core.common.network.codec.PacketCodec;
import com.enderio.enderio.api.filter.ItemFilterContext;
import com.enderio.enderio.api.filter.ItemFilterDirection;
import com.enderio.enderio.content.filters.fluid.EnderFluidFilter;
import com.enderio.enderio.content.filters.item.existing.ExistingItemFilter;
import com.enderio.enderio.content.filters.item.general.DamageFilterMode;
import com.enderio.enderio.content.filters.item.general.EnderItemFilter;
import com.enderio.enderio.content.filters.item.limited.LimitedItemFilter;
import com.enderio.enderio.content.filters.item.mod_id.ModIdItemFilter;
import com.mojang.serialization.Codec;
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
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.ItemStackHandler;

import java.util.List;

@GameTestHolder("endercore_tests")
@PrefixGameTestTemplate(false)
public class PortedFilterTests {
    private static ItemStack item(int count, String variant) {
        var stack = new ItemStack(Items.STONE, count);
        stack.getOrCreateTag().putString("variant", variant);
        return stack;
    }

    private static <T> T roundTrip(GameTestHelper helper, Codec<T> codec, PacketCodec<T> packetCodec, T source) {
        var saved = codec.encodeStart(NbtOps.INSTANCE, source).result().orElseThrow();
        T loaded = codec.parse(NbtOps.INSTANCE, saved).result().orElseThrow();
        helper.assertTrue(source.equals(loaded) && source.hashCode() == loaded.hashCode(), "Filter save changed contents or hash: " + source.getClass());
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            packetCodec.encode(buffer, loaded);
            T received = packetCodec.decode(buffer);
            helper.assertTrue(source.equals(received) && source.hashCode() == received.hashCode() && buffer.readableBytes() == 0,
                "Filter packet changed contents or hash: " + source.getClass());
            return received;
        } finally { buffer.release(); }
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void allFiveActualFilterCodecsRetainSlotsCountsNbtAndFlags(GameTestHelper helper) {
        var items = List.of(ItemStack.EMPTY, item(9999, "first"), ItemStack.EMPTY, item(128, "second"), ItemStack.EMPTY);
        var itemFilter = roundTrip(helper, EnderItemFilter.CODEC, EnderItemFilter.STREAM_CODEC,
            new EnderItemFilter(items, true, true, DamageFilterMode.NOT_DAMAGEABLE));
        roundTrip(helper, ExistingItemFilter.CODEC, ExistingItemFilter.STREAM_CODEC, new ExistingItemFilter(true, items, true, true));
        roundTrip(helper, LimitedItemFilter.CODEC, LimitedItemFilter.STREAM_CODEC, new LimitedItemFilter(items, true, DamageFilterMode.IGNORE));
        roundTrip(helper, ModIdItemFilter.CODEC, ModIdItemFilter.STREAM_CODEC, new ModIdItemFilter(items, true));
        var fluid = new FluidStack(Fluids.WATER, 9000);
        fluid.getOrCreateTag().putString("variant", "first");
        roundTrip(helper, EnderFluidFilter.CODEC, EnderFluidFilter.STREAM_CODEC,
            new EnderFluidFilter(List.of(FluidStack.EMPTY, fluid, FluidStack.EMPTY), true, true));
        itemFilter.matches().get(1).getTag().putString("variant", "changed");
        helper.assertTrue(items.get(1).getTag().getString("variant").equals("first"), "Restored filter aliases source NBT");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void limitedFilterPersistsAndHonorsInsertExtractStockLevels(GameTestHelper helper) {
        var source = new LimitedItemFilter(List.of(ItemStack.EMPTY, item(1000, "stock")), true, DamageFilterMode.IGNORE);
        var key = new ItemDataKey<>(ItemDataTestContent.id("stock_filter"), LimitedItemFilter.CODEC);
        var filterStack = ItemDataTestContent.ITEM.toStack();
        key.set(filterStack, source);
        var filter = key.get(ItemStack.of(filterStack.save(new CompoundTag())));
        var target = new ItemStackHandler(3);
        target.setStackInSlot(0, item(900, "stock"));
        target.setStackInSlot(1, item(99, "stock"));
        target.setStackInSlot(2, item(1000, "other"));
        var input = item(64, "stock");
        helper.assertTrue(filter.test(input, new ItemFilterContext(target, ItemFilterDirection.INSERT)).getCount() == 1,
            "Insert filter failed to account for stock or NBT variant");
        helper.assertTrue(filter.test(input, new ItemFilterContext(target, ItemFilterDirection.EXTRACT)).isEmpty(), "Extraction removed reserved stock");
        target.setStackInSlot(1, item(109, "stock"));
        helper.assertTrue(filter.test(input, new ItemFilterContext(target, ItemFilterDirection.EXTRACT)).getCount() == 9,
            "Extraction failed to leave the configured stock");
        helper.assertTrue(filter.test(input, new ItemFilterContext(target, ItemFilterDirection.INSERT)).isEmpty(), "Overstock accepted insertion");
        helper.assertTrue(input.getCount() == 64 && target.getStackInSlot(0).getCount() == 900 && filter.matches().get(1).getCount() == 1000,
            "Filter simulation mutated inventories or its stock target");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void limitedFilterDoesNotOverflowAcrossLargeInventorySlots(GameTestHelper helper) {
        var filter = new LimitedItemFilter(List.of(item(1000, "stock")), false, DamageFilterMode.IGNORE);
        var inventory = new ItemStackHandler(2);
        inventory.setStackInSlot(0, item(Integer.MAX_VALUE, "stock"));
        inventory.setStackInSlot(1, item(Integer.MAX_VALUE, "stock"));
        var incoming = item(64, "stock");
        helper.assertTrue(filter.test(incoming, new ItemFilterContext(inventory, ItemFilterDirection.INSERT)).isEmpty(), "Stock sum overflow allowed more insertion");
        helper.assertTrue(filter.test(incoming, new ItemFilterContext(inventory, ItemFilterDirection.EXTRACT)).getCount() == 64,
            "Stock sum overflow blocked valid extraction");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void itemFiltersRetainNbtDamageSnapshotAndNamespaceBehavior(GameTestHelper helper) {
        var filter = new EnderItemFilter(List.of(item(1, "a")), false, true, DamageFilterMode.IGNORE);
        helper.assertTrue(!filter.test(null, item(64, "a")).isEmpty() && filter.test(null, item(64, "b")).isEmpty(), "NBT whitelist changed");
        var deny = new EnderItemFilter(filter.matches(), true, true, DamageFilterMode.IGNORE);
        helper.assertTrue(deny.test(null, item(1, "a")).isEmpty() && !deny.test(null, item(1, "b")).isEmpty(), "NBT deny list changed");
        var sword = new ItemStack(Items.IRON_SWORD);
        sword.setDamageValue(4);
        var damaged = new EnderItemFilter(List.of(new ItemStack(Items.IRON_SWORD)), false, true, DamageFilterMode.ONLY_DAMAGED);
        helper.assertTrue(!damaged.test(null, sword).isEmpty() && damaged.test(null, new ItemStack(Items.IRON_SWORD)).isEmpty(), "Damage predicate or ignored damage NBT changed");
        var target = new ItemStackHandler(1);
        target.setStackInSlot(0, item(1, "b"));
        var snapshot = new ExistingItemFilter(true, List.of(item(1, "a")), true, false);
        var live = new ExistingItemFilter(false, List.of(), true, false);
        helper.assertTrue(!snapshot.test(target, item(1, "a")).isEmpty() && snapshot.test(target, item(1, "b")).isEmpty(), "Snapshot used live inventory");
        helper.assertTrue(!live.test(target, item(1, "b")).isEmpty() && live.test(target, item(1, "a")).isEmpty(), "Live filter ignored inventory");
        var namespace = new ModIdItemFilter(List.of(ItemDataTestContent.ITEM.toStack()), false);
        helper.assertTrue(!namespace.test(null, ItemDataTestContent.BUILDER_ITEM.toStack()).isEmpty()
            && namespace.test(null, new ItemStack(Items.STONE)).isEmpty(), "Namespace filter changed");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void fluidFiltersSeparateNbtIdentityFromQuantity(GameTestHelper helper) {
        var sample = new FluidStack(Fluids.WATER, 1);
        sample.getOrCreateTag().putString("variant", "a");
        var candidate = new FluidStack(Fluids.WATER, 1000, sample.getTag().copy());
        var other = new FluidStack(Fluids.WATER, 1000);
        var strict = new EnderFluidFilter(List.of(sample), false, true);
        helper.assertTrue(strict.test(null, candidate) == candidate && strict.test(null, other).isEmpty(), "Fluid matching used amount or ignored NBT");
        var loose = new EnderFluidFilter(List.of(sample), false, false);
        helper.assertTrue(loose.test(null, other) == other && loose.test(null, new FluidStack(Fluids.LAVA, 1000)).isEmpty(), "Fluid-type matching changed");
        var deny = new EnderFluidFilter(List.of(sample), true, true);
        helper.assertTrue(deny.test(null, candidate).isEmpty() && deny.test(null, other) == other, "Fluid deny-list matching changed");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void oversizedFilterPacketsAreRejectedBeforeAllocatingEntries(GameTestHelper helper) {
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buffer.writeVarInt(257);
            try { LimitedItemFilter.STREAM_CODEC.decode(buffer); helper.fail("Oversized filter accepted"); }
            catch (DecoderException expected) { }
        } finally { buffer.release(); }
        helper.succeed();
    }
}
