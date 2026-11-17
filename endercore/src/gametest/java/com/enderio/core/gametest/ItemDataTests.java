package com.enderio.core.gametest;

import com.enderio.core.common.item.ItemDataKey;
import com.mojang.serialization.Codec;
import io.netty.buffer.Unpooled;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.RegistryFixedCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.concurrent.atomic.AtomicInteger;

import static com.enderio.core.gametest.ItemDataTestContent.*;

@GameTestHolder("endercore_tests")
@PrefixGameTestTemplate(false)
public class ItemDataTests {
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void defaultsDoNotMutateOrShareData(GameTestHelper helper) {
        var stack = ITEM.toStack();
        helper.assertTrue(ENERGY.get(stack) == 40, "Missing item default");
        var data = CONFIG.get(stack);
        data.putInt("count", 99);
        helper.assertTrue(CONFIG.get(stack).getInt("count") == 3, "Mutable default escaped into another read");
        helper.assertTrue(!stack.hasTag(), "Reading defaults mutated the stack");
        ENERGY.set(stack, 70);
        ENERGY.set(stack, 40);
        helper.assertTrue(ItemStack.isSameItemSameTags(stack, ITEM.toStack()), "Setting the default broke stacking with a fresh item");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void removalSurvivesSaveAndResetRestoresDefaults(GameTestHelper helper) {
        var stack = ITEM.toStack();
        ENERGY.remove(stack);
        helper.assertTrue(!ENERGY.has(stack), "Removal exposed the default");
        var loaded = ItemStack.of(stack.save(new CompoundTag()));
        helper.assertTrue(!ENERGY.has(loaded), "Save/reload lost explicit removal");
        ENERGY.reset(loaded);
        helper.assertTrue(ENERGY.get(loaded) == 40 && !loaded.hasTag(), "Reset failed to restore a clean default stack");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void copySaveAndNetworkPreserveIndependentState(GameTestHelper helper) {
        var stack = ITEM.toStack(5);
        var value = new CompoundTag();
        value.putInt("count", 12);
        CONFIG.set(stack, value);
        value.putInt("count", 200);
        helper.assertTrue(CONFIG.get(stack).getInt("count") == 12, "Write retained mutable caller state");
        var read = CONFIG.get(stack);
        read.putInt("count", 300);
        helper.assertTrue(CONFIG.get(stack).getInt("count") == 12, "Read leaked mutable NBT");
        var copy = stack.copy();
        ENERGY.set(copy, 75);
        helper.assertTrue(ENERGY.get(stack) == 40, "Copy changed the original");
        var saved = ItemStack.of(copy.save(new CompoundTag()));
        helper.assertTrue(ItemStack.matches(copy, saved), "Native save/load lost state or count");
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buffer.writeItem(saved);
            var received = buffer.readItem();
            helper.assertTrue(ItemStack.matches(saved, received), "Native stack network encoding lost data");
            helper.assertTrue(received.getCapability(ForgeCapabilities.ENERGY).orElseThrow(IllegalStateException::new)
                .getEnergyStored() == 75, "Received capability used stale data");
        } finally {
            buffer.release();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void writesRefreshSnapshotCapabilitiesAfterCommit(GameTestHelper helper) {
        var stack = ITEM.toStack();
        var old = stack.getCapability(ForgeCapabilities.ENERGY);
        old.orElseThrow(IllegalStateException::new);
        var callbacks = new AtomicInteger();
        old.addListener(ignored -> {
            helper.assertTrue(ENERGY.get(stack) == 91, "Listener observed pre-write data");
            helper.assertTrue(stack.getCapability(ForgeCapabilities.ENERGY).orElseThrow(IllegalStateException::new)
                .getEnergyStored() == 91, "Listener observed a stale provider");
            callbacks.incrementAndGet();
        });
        ENERGY.set(stack, 91);
        helper.assertTrue(callbacks.get() == 1 && !old.isPresent(), "Write failed to invalidate the snapshot");
        var current = stack.getCapability(ForgeCapabilities.ENERGY);
        ENERGY.set(stack, 91);
        helper.assertTrue(current.isPresent(), "Identical writes invalidated an unchanged snapshot");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void failedWritesLeaveDataAndCapabilitiesIntact(GameTestHelper helper) {
        var stack = ITEM.toStack();
        ENERGY.set(stack, 80);
        var before = stack.save(new CompoundTag());
        var provider = stack.getCapability(ForgeCapabilities.ENERGY);
        provider.orElseThrow(IllegalStateException::new);
        expectInvalid(helper, () -> ENERGY.set(stack, -1));
        helper.assertTrue(before.equals(stack.save(new CompoundTag())), "Failed encoding mutated the stack");
        helper.assertTrue(provider.isPresent(), "Failed encoding invalidated a working provider");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void liveDataWritesRetainExistingProviders(GameTestHelper helper) {
        var live = new ItemDataKey<>(id("live_value"), Codec.INT, false);
        var stack = ITEM.toStack();
        var provider = stack.getCapability(ForgeCapabilities.ENERGY);
        provider.orElseThrow(IllegalStateException::new);
        live.set(stack, 10);
        helper.assertTrue(provider.isPresent() && live.get(stack) == 10, "Live data unnecessarily replaced the provider");
        live.remove(stack);
        helper.assertTrue(provider.isPresent() && !live.has(stack), "Removing live data unnecessarily replaced the provider");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void registryValuesRoundTripWithExplicitWorldContext(GameTestHelper helper) {
        var key = new ItemDataKey<Holder<Biome>>(id("biome"), RegistryFixedCodec.create(Registries.BIOME));
        var stack = ITEM.toStack();
        var registries = helper.getLevel().registryAccess();
        var biome = registries.registryOrThrow(Registries.BIOME).getHolderOrThrow(Biomes.PLAINS);
        key.set(stack, registries, biome);
        var saved = ItemStack.of(stack.save(new CompoundTag()));
        helper.assertTrue(key.get(saved, registries).is(Biomes.PLAINS), "Dynamic registry holder changed identity");
        helper.assertTrue(saved.getTag().getCompound("enderio:data").getString(key.id().toString()).equals("minecraft:plains"),
            "Registry value was not stored by its stable name");
        expectInvalid(helper, () -> key.get(saved));
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void aliasesCanonicalizeWithoutDiscardingOtherTags(GameTestHelper helper) {
        var key = new ItemDataKey<>(id("new_value"), Codec.INT, true, id("old_value"));
        var stack = ITEM.toStack();
        var data = new CompoundTag();
        data.putInt(id("old_value").toString(), 6);
        stack.getOrCreateTag().put("enderio:data", data);
        stack.getOrCreateTag().putString("other_mod", "keep");
        helper.assertTrue(key.get(stack) == 6, "Old name was not read");
        key.set(stack, 8);
        helper.assertTrue(!stack.getTag().getCompound("enderio:data").contains(id("old_value").toString()), "Write retained old alias");
        key.remove(stack);
        helper.assertTrue(!stack.getTag().contains("enderio:data") && stack.getTag().getString("other_mod").equals("keep"),
            "Removal left empty storage or discarded unrelated data");
        key.withDefault(ITEM.get(), () -> 6);
        var removed = new CompoundTag();
        removed.putBoolean(id("old_value").toString(), true);
        stack.getOrCreateTag().put("enderio:removed_data", removed);
        helper.assertTrue(!key.has(stack), "Renaming a removed key resurrected its default");
        key.reset(stack);
        helper.assertTrue(key.get(stack) == 6 && !stack.getTag().contains("enderio:removed_data"), "Reset failed to clear an aliased removal");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void malformedDataIsReportedWithoutOverwritingIt(GameTestHelper helper) {
        var stack = ITEM.toStack();
        stack.getOrCreateTag().putString("enderio:data", "damaged");
        var before = stack.getTag().copy();
        expectInvalid(helper, () -> ENERGY.get(stack));
        expectInvalid(helper, () -> ENERGY.set(stack, 1));
        helper.assertTrue(before.equals(stack.getTag()), "Malformed container was silently discarded");
        var invalidValue = new CompoundTag();
        invalidValue.putString(ENERGY.id().toString(), "bad integer");
        stack.getOrCreateTag().put("enderio:data", invalidValue);
        expectInvalid(helper, () -> ENERGY.get(stack));
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void emptyStacksStayEmpty(GameTestHelper helper) {
        helper.assertTrue(!ENERGY.has(ItemStack.EMPTY), "Empty stack exposed item state");
        expectInvalid(helper, () -> ENERGY.set(ItemStack.EMPTY, 1));
        ENERGY.remove(ItemStack.EMPTY);
        ENERGY.reset(ItemStack.EMPTY);
        helper.assertTrue(!ItemStack.EMPTY.hasTag(), "Global empty stack was mutated");
        helper.succeed();
    }

    private static void expectInvalid(GameTestHelper helper, Runnable action) {
        boolean rejected = false;
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        helper.assertTrue(rejected, "Invalid item state was silently accepted");
    }
}
