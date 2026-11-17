package com.enderio.core.gametest;

import com.enderio.core.common.capability.ItemCapabilityRegistry;
import com.enderio.core.common.recipes.RecipeRegistry;
import com.enderio.core.common.registries.BlockEntityTypeDeferredRegister;
import com.enderio.enderio.api.capacitor.CapacitorData;
import com.enderio.enderio.api.capacitor.CapacitorModifier;
import com.enderio.enderio.api.capacitor.FixedScalable;
import com.enderio.enderio.api.capacitor.ICapacitorExtension;
import com.enderio.enderio.api.capacitor.QuadraticScalable;
import com.enderio.enderio.api.io.energy.EnergyIOMode;
import com.enderio.enderio.content.capacitors.CapacitorItem;
import com.enderio.enderio.content.machines.alloy.AlloySmeltingRecipe;
import com.enderio.enderio.foundation.block.ProgressMachineBlock;
import com.enderio.enderio.foundation.block.entity.PoweredMachineBlockEntity;
import com.enderio.enderio.foundation.block.entity.flags.CapacitorSupport;
import com.enderio.enderio.foundation.inventory.MachineInventoryLayout;
import com.enderio.enderio.foundation.inventory.SingleSlotAccess;
import com.enderio.enderio.foundation.task.PoweredCraftingMachineTask;
import com.enderio.enderio.foundation.task.host.CraftingMachineTaskHost;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.List;

/** Actual powered-machine base with registered native energy and item capabilities. */
public final class PoweredMachineTestContent {
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, "endercore_tests");
    private static final BlockEntityTypeDeferredRegister TYPES = BlockEntityTypeDeferredRegister.create("endercore_tests");
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, "endercore_tests");
    static final RegistryObject<Block> INPUT = BLOCKS.register("powered_input", PoweredMachineTestContent::block);
    static final RegistryObject<Block> OUTPUT = BLOCKS.register("powered_output", PoweredMachineTestContent::block);
    static final RegistryObject<Block> BOTH = BLOCKS.register("powered_both", PoweredMachineTestContent::block);
    static final RegistryObject<Block> NO_CAPACITOR = BLOCKS.register("powered_without_capacitor", PoweredMachineTestContent::block);
    static final RegistryObject<BlockEntityType<Machine>> TYPE = TYPES.builder("powered_machine", Machine::new, INPUT, OUTPUT, BOTH, NO_CAPACITOR)
        .capability(ForgeCapabilities.ENERGY, PoweredMachineBlockEntity.ENERGY_STORAGE_PROVIDER)
        .capability(ForgeCapabilities.ITEM_HANDLER, PoweredMachineBlockEntity.ITEM_HANDLER_PROVIDER).build();
    static final RegistryObject<Item> MACHINE_ITEM = ITEMS.register("powered_input", () -> new BlockItem(INPUT.get(), new Item.Properties()));
    static final RegistryObject<Item> CAPACITOR = ITEMS.register("test_capacitor", () -> new CapacitorItem(new Item.Properties()));
    static final RegistryObject<Item> NULL_EXTENSION = ITEMS.register("empty_capacitor_extension", () -> new Item(new Item.Properties()));
    static final RegistryObject<Item> DYNAMIC_EXTENSION = ITEMS.register("dynamic_capacitor_extension", () -> new Item(new Item.Properties()));
    static final SingleSlotAccess INPUT_SLOT = new SingleSlotAccess();
    static final SingleSlotAccess OUTPUT_SLOT = new SingleSlotAccess();

    private static Block block() { return new ProgressMachineBlock<>(() -> TYPE.get(), BlockBehaviour.Properties.of()); }
    static ItemStack capacitor(float strength) {
        var item = new ItemStack(CAPACITOR.get());
        CapacitorData.ITEM_DATA.set(item, CapacitorData.simple(strength));
        return item;
    }

    static void register(IEventBus bus) {
        BLOCKS.register(bus); TYPES.register(bus); ITEMS.register(bus);
        var capabilities = new ItemCapabilityRegistry(ResourceLocation.fromNamespaceAndPath("endercore_tests", "powered_capacitors"));
        capabilities.register(ICapacitorExtension.CAPABILITY, (stack, side) -> new ICapacitorExtension() {
            @Override public CapacitorData getCapacitorData(ItemStack item, net.minecraft.core.HolderLookup.Provider registries) { return null; }
            @Override public void onMachineUsed(ItemStack item, ServerLevel level) {}
        }, NULL_EXTENSION.getId());
        capabilities.register(ICapacitorExtension.CAPABILITY, (stack, side) -> new ICapacitorExtension() {
            @Override public CapacitorData getCapacitorData(ItemStack item, net.minecraft.core.HolderLookup.Provider registries) {
                return CapacitorData.simple(item.getOrCreateTag().getInt("strength"));
            }
            @Override public void onMachineUsed(ItemStack item, ServerLevel level) { item.getOrCreateTag().putInt("strength", 1); }
        }, DYNAMIC_EXTENSION.getId());
        capabilities.register();
    }

    public static final class Machine extends PoweredMachineBlockEntity {
        int dirtyCalls;
        final CraftingMachineTaskHost<AlloySmeltingRecipe, AlloySmeltingRecipe.Input> tasks;
        Machine(BlockPos pos, BlockState state) {
            super(TYPE.get(), pos, state, true, state.is(NO_CAPACITOR.get()) ? CapacitorSupport.NONE : CapacitorSupport.REQUIRED,
                state.is(OUTPUT.get()) ? EnergyIOMode.Output : state.is(BOTH.get()) ? EnergyIOMode.Both : EnergyIOMode.Input,
                state.is(NO_CAPACITOR.get()) ? new FixedScalable(() -> 1000) : new QuadraticScalable(CapacitorModifier.ENERGY_CAPACITY, () -> 1000),
                state.is(NO_CAPACITOR.get()) ? new FixedScalable(() -> 10) : new QuadraticScalable(CapacitorModifier.ENERGY_USE, () -> 10));
            tasks = new CraftingMachineTaskHost<>(this, () -> hasEnergy() && canAct(),
                RecipeRegistry.type(ResourceLocation.fromNamespaceAndPath("enderio", "alloy_smelting")),
                (level, input, recipe) -> new PoweredCraftingMachineTask<>(level, getInventory(), getEnergyStorage(), input, OUTPUT_SLOT, recipe) {
                    @Override protected void consumeInputs(AlloySmeltingRecipe value) {
                        getInventory().extractItem(INPUT_SLOT.getIndex(), value.inputs().get(0).count(), false);
                    }
                }, () -> new AlloySmeltingRecipe.Input(List.of(INPUT_SLOT.getItemStack(getInventory()), ItemStack.EMPTY, ItemStack.EMPTY), 1));
        }
        @Override protected MachineInventoryLayout createInventoryLayout() {
            var layout = MachineInventoryLayout.builder().inputSlot().slotAccess(INPUT_SLOT).outputSlot().slotAccess(OUTPUT_SLOT);
            return (getBlockState().is(NO_CAPACITOR.get()) ? layout : layout.capacitor()).build();
        }
        @Override public boolean isActive() { return hasEnergy() && canAct() && tasks.hasTask(); }
        @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) { return ChestMenu.threeRows(id, inventory, new SimpleContainer(27)); }
        @Override public void setChanged() { super.setChanged(); dirtyCalls++; }
        @Override protected void onInventoryContentsChanged(int slot) { super.onInventoryContentsChanged(slot); if (tasks != null) tasks.newTaskAvailable(); }
        @Override public void serverTick() { super.serverTick(); if (canAct()) tasks.tick(); }
        @Override public void onLoad() { super.onLoad(); tasks.onLevelReady(); }
        @Override protected void saveAdditional(CompoundTag tag) { super.saveAdditional(tag); tasks.save(level == null ? net.minecraft.core.RegistryAccess.EMPTY : level.registryAccess(), tag); }
        @Override public void load(CompoundTag tag) { super.load(tag); tasks.load(level == null ? net.minecraft.core.RegistryAccess.EMPTY : level.registryAccess(), tag); }
        void completeOperation() { onMachineUsed(); }
    }
}
