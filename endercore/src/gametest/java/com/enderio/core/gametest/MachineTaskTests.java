package com.enderio.core.gametest;

import com.enderio.core.common.recipes.OutputStack;
import com.enderio.core.common.recipes.RecipeRegistry;
import com.enderio.core.common.recipes.SizedIngredient;
import com.enderio.enderio.api.io.energy.EnergyIOMode;
import com.enderio.enderio.content.machines.alloy.AlloySmeltingRecipe;
import com.enderio.enderio.foundation.inventory.MachineInventory;
import com.enderio.enderio.foundation.inventory.MachineInventoryLayout;
import com.enderio.enderio.foundation.inventory.MultiSlotAccess;
import com.enderio.enderio.foundation.inventory.SingleSlotAccess;
import com.enderio.enderio.foundation.io.energy.MachineEnergyStorage;
import com.enderio.enderio.foundation.task.PoweredCraftingMachineTask;
import com.enderio.enderio.foundation.task.host.CraftingMachineTaskHost;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.Level;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

@GameTestHolder("endercore_tests")
@PrefixGameTestTemplate(false)
public class MachineTaskTests {
    private static final ResourceLocation RECIPE_ID = ItemDataTestContent.id("persisted_task");

    private static AlloySmeltingRecipe recipe(Item output) {
        var stack = new ItemStack(output, 2);
        stack.getOrCreateTag().putInt("variant", 7);
        return new AlloySmeltingRecipe(List.of(SizedIngredient.of(Items.STONE, 1)), stack, 1000, 0).withId(RECIPE_ID);
    }

    private static List<Recipe<?>> addRecipe(Level level, AlloySmeltingRecipe recipe) {
        var original = new ArrayList<>(level.getRecipeManager().getRecipes());
        var changed = new ArrayList<>(original);
        changed.removeIf(existing -> existing.getId().equals(RECIPE_ID));
        changed.add(recipe);
        level.getRecipeManager().replaceRecipes(changed);
        return original;
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void blockedTaskRoundTripRetainsProgressOutputsAndConsumesOnlyOnce(GameTestHelper helper) {
        var recipe = recipe(Items.IRON_INGOT);
        var original = addRecipe(helper.getLevel(), recipe);
        try {
            var fixture = new Fixture();
            fixture.input.setStackInSlot(fixture.inventory, new ItemStack(Items.STONE, 2));
            fixture.outputs.get(0).setStackInSlot(fixture.inventory, new ItemStack(Items.DIRT, 64));
            fixture.energy.setEnergyStored(1000);
            var task = new Task(helper.getLevel(), fixture, recipe);
            helper.assertTrue(task.getProgress() == 0 && Float.isFinite(task.getProgress()), "New task has invalid progress");
            for (int tick = 0; tick < 4; tick++) task.tick();
            helper.assertTrue(!task.isCompleted() && task.getProgress() == 1 && fixture.energy.getEnergyStored() == 0
                && fixture.input.getItemStack(fixture.inventory).getCount() == 2, "Blocked output consumed input or lost progress");
            var savedTask = task.serializeNBT();
            var savedCopy = savedTask.copy();
            var restored = new Fixture();
            restored.inventory.deserializeNBT(fixture.inventory.serializeNBT());
            restored.energy.deserializeNBT(fixture.energy.serializeNBT());
            var resumed = new Task(helper.getLevel(), restored, recipe);
            resumed.deserializeNBT(savedTask);
            resumed.tick();
            helper.assertTrue(!resumed.isCompleted() && resumed.getProgress() == 1 && restored.consumed == 0, "Reloaded blocked task consumed input");
            restored.outputs.get(0).setStackInSlot(restored.inventory, ItemStack.EMPTY);
            resumed.tick();
            helper.assertTrue(resumed.isCompleted() && restored.consumed == 1 && restored.input.getItemStack(restored.inventory).getCount() == 1,
                "Resumed task did not consume exactly one input");
            var output = restored.outputs.get(0).getItemStack(restored.inventory);
            helper.assertTrue(output.is(Items.IRON_INGOT) && output.getCount() == 2 && output.getTag().getInt("variant") == 7,
                "Saved determined outputs changed during resume");
            resumed.tick();
            helper.assertTrue(output.getCount() == 2 && restored.consumed == 1 && restored.energy.getEnergyStored() == 0
                && savedTask.equals(savedCopy), "Completed task duplicated resources or mutated its saved tag");
        } finally { helper.getLevel().getRecipeManager().replaceRecipes(original); }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void outputReservationDoesNotAliasOrPartiallyInsertDeterminedStacks(GameTestHelper helper) {
        var fixture = new Fixture();
        var task = new Task(helper.getLevel(), fixture, null);
        var first = OutputStack.of(new ItemStack(Items.IRON_INGOT, 3));
        var second = OutputStack.of(new ItemStack(Items.IRON_INGOT, 4));
        var outputs = List.of(first, second);
        helper.assertTrue(task.place(outputs, true) && fixture.outputs.get(0).getItemStack(fixture.inventory).isEmpty()
            && first.getItem().getCount() == 3 && second.getItem().getCount() == 4, "Output reservation changed real inventory or rolled outputs");
        helper.assertTrue(task.place(outputs, false) && fixture.outputs.get(0).getItemStack(fixture.inventory).getCount() == 7
            && first.getItem().getCount() == 3, "Combining outputs duplicated or aliased recipe stacks");
        fixture.outputs.get(0).setStackInSlot(fixture.inventory, new ItemStack(Items.IRON_INGOT, 60));
        helper.assertTrue(!task.place(outputs, false) && fixture.outputs.get(0).getItemStack(fixture.inventory).getCount() == 60
            && first.getItem().getCount() == 3, "Failed reservation partially inserted outputs or changed determined quantities");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void recipeChangesCancelLiveAndRestoredTasksBeforeResourceMutation(GameTestHelper helper) {
        var first = recipe(Items.IRON_INGOT);
        var original = addRecipe(helper.getLevel(), first);
        try {
            var fixture = new Fixture();
            fixture.input.setStackInSlot(fixture.inventory, new ItemStack(Items.STONE));
            fixture.energy.setEnergyStored(1000);
            var live = new Task(helper.getLevel(), fixture, first);
            live.tick();
            var saved = live.serializeNBT();
            var replacement = recipe(Items.GOLD_INGOT);
            addRecipe(helper.getLevel(), replacement);
            live.tick();
            helper.assertTrue(live.isCompleted() && fixture.energy.getEnergyStored() == 750 && fixture.consumed == 0
                && fixture.outputs.get(0).getItemStack(fixture.inventory).isEmpty(), "Old live recipe crafted after reload");
            var restored = new Task(helper.getLevel(), fixture, replacement);
            restored.deserializeNBT(saved);
            restored.tick();
            helper.assertTrue(restored.isCompleted() && fixture.energy.getEnergyStored() == 750 && fixture.consumed == 0,
                "Saved outputs were attached to changed recipe data");
            var missing = saved.copy();
            missing.putString("RecipeId", "NOT A VALID ID");
            restored.deserializeNBT(missing);
            helper.assertTrue(restored.getRecipe() == null && restored.getRecipeId() == null && restored.getProgress() == 0,
                "Malformed task recipe ID remained usable");
        } finally { helper.getLevel().getRecipeManager().replaceRecipes(original); }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void taskHostPreservesPendingSaveAndClearsStaleLoadsBeforeWorldReady(GameTestHelper helper) {
        var recipe = recipe(Items.IRON_INGOT);
        var original = addRecipe(helper.getLevel(), recipe);
        try {
            var fixture = new Fixture();
            fixture.input.setStackInSlot(fixture.inventory, new ItemStack(Items.STONE));
            fixture.energy.setEnergyStored(1000);
            var initial = new Task(helper.getLevel(), fixture, recipe);
            initial.tick();
            var taskData = initial.serializeNBT();
            var parent = new CompoundTag();
            parent.put("Task", taskData.copy());
            var entity = new MachineTestContent.HostEntity(BlockPos.ZERO, MachineTestContent.BLOCK.get().defaultBlockState());
            var host = new CraftingMachineTaskHost<AlloySmeltingRecipe, AlloySmeltingRecipe.Input>(entity, () -> true,
                RecipeRegistry.type(ResourceLocation.fromNamespaceAndPath("enderio", "alloy_smelting")),
                (level, input, value) -> new Task(level, fixture, input, value), fixture::input);
            host.load(RegistryAccess.EMPTY, parent);
            parent.getCompound("Task").putInt("ProgressMade", 999);
            var pendingSave = new CompoundTag();
            host.save(RegistryAccess.EMPTY, pendingSave);
            helper.assertTrue(pendingSave.getCompound("Task").equals(taskData) && host.getCurrentTask() == null,
                "Pending task was dropped or retained caller-owned NBT before world readiness");
            host.load(RegistryAccess.EMPTY, new CompoundTag());
            host.save(RegistryAccess.EMPTY, pendingSave);
            helper.assertTrue(!pendingSave.contains("Task"), "A later empty load retained a stale pending task");
            var reloaded = new CompoundTag();
            reloaded.put("Task", taskData.copy());
            host.load(RegistryAccess.EMPTY, reloaded);
            entity.setLevel(helper.getLevel());
            host.onLevelReady();
            helper.assertTrue(host.hasTask() && host.getProgress() == 0.25f, "World-ready host did not restore progress");
            for (int tick = 0; tick < 4; tick++) host.tick();
            helper.assertTrue(!host.hasTask() && fixture.consumed == 1 && fixture.energy.getEnergyStored() == 0
                && fixture.outputs.get(0).getItemStack(fixture.inventory).getCount() == 2, "Host resume did not complete exactly once");
        } finally { helper.getLevel().getRecipeManager().replaceRecipes(original); }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void nativeRecipePacketsAndSnapshotsDistinguishLargeOutputCounts(GameTestHelper helper) {
        var small = new AlloySmeltingRecipe(List.of(SizedIngredient.of(Items.STONE, 1)), new ItemStack(Items.IRON_INGOT, 1), 500, 0).withId(RECIPE_ID);
        var large = new AlloySmeltingRecipe(small.inputs(), new ItemStack(Items.IRON_INGOT, 257), 500, 0).withId(RECIPE_ID);
        var registries = helper.getLevel().registryAccess();
        var snapshot = com.enderio.core.common.recipes.RecipeSnapshot.encode(large, registries);
        helper.assertTrue(!java.util.Arrays.equals(com.enderio.core.common.recipes.RecipeSnapshot.encode(small, registries),
            snapshot), "Recipe snapshot truncated output count to a byte");
        var savedData = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.wrappedBuffer(snapshot));
        try {
            savedData.readResourceLocation();
            helper.assertTrue(savedData.readBoolean() && savedData.readNbt().toString().contains("minecraft:iron_ingot"),
                "Saved recipe data uses connection-local numeric item IDs");
        } finally { savedData.release(); }
        var buffer = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try {
            new net.minecraft.network.protocol.game.ClientboundUpdateRecipesPacket(List.of(large)).write(buffer);
            var packet = new net.minecraft.network.protocol.game.ClientboundUpdateRecipesPacket(buffer);
            var received = (AlloySmeltingRecipe) packet.getRecipes().get(0);
            helper.assertTrue(received.output().getCount() == 257 && buffer.readableBytes() == 0, "Native recipe sync truncated a large output");
            buffer.clear();
            buffer.writeVarInt(0);
            try {
                com.enderio.core.common.network.codec.PacketCodecs.REQUIRED_ITEM_STACK_DATA.decode(buffer);
                helper.fail("Empty required recipe output accepted");
            } catch (io.netty.handler.codec.DecoderException expected) { }
        } finally { buffer.release(); }
        helper.succeed();
    }

    private static final class Fixture {
        final SingleSlotAccess input = new SingleSlotAccess();
        final SingleSlotAccess output = new SingleSlotAccess();
        final MachineTestContent.IO io = new MachineTestContent.IO();
        final MachineInventory inventory = new MachineInventory(io, MachineInventoryLayout.builder()
            .inputSlot().slotAccess(input).outputSlot().slotAccess(output).build());
        final MultiSlotAccess outputs = output.wrapToMulti();
        final MachineEnergyStorage energy = new MachineEnergyStorage(io, EnergyIOMode.Both, () -> 2000, () -> 250);
        int consumed;
        AlloySmeltingRecipe.Input input() {
            return new AlloySmeltingRecipe.Input(List.of(input.getItemStack(inventory), ItemStack.EMPTY, ItemStack.EMPTY), 1);
        }
    }

    private static final class Task extends PoweredCraftingMachineTask<AlloySmeltingRecipe, AlloySmeltingRecipe.Input> {
        private final Fixture fixture;
        Task(Level level, Fixture fixture, AlloySmeltingRecipe recipe) { this(level, fixture, fixture.input(), recipe); }
        Task(Level level, Fixture fixture, AlloySmeltingRecipe.Input input, AlloySmeltingRecipe recipe) {
            super(level, fixture.inventory, fixture.energy, input, fixture.outputs, recipe);
            this.fixture = fixture;
        }
        @Override protected void consumeInputs(AlloySmeltingRecipe recipe) {
            fixture.input.extractItem(fixture.inventory, recipe.inputs().get(0).count(), false);
            fixture.consumed++;
        }
        boolean place(List<OutputStack> outputs, boolean simulate) { return placeOutputs(outputs, simulate); }
    }
}
