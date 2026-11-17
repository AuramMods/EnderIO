package com.enderio.core.gametest;

import com.enderio.core.common.blockentity.EnderBlockEntity;
import com.enderio.core.common.item.ItemDataKey;
import com.enderio.enderio.api.io.IOMode;
import com.enderio.enderio.api.io.RedstoneControl;
import com.enderio.enderio.foundation.inventory.MachineInventory;
import com.enderio.enderio.foundation.inventory.MachineInventoryLayout;
import com.enderio.enderio.foundation.io.IOConfig;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class MachineLifecycleTestContent {
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, "endercore_tests");
    private static final DeferredRegister<BlockEntityType<?>> TYPES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, "endercore_tests");
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, "endercore_tests");
    static final RegistryObject<Block> BLOCK = BLOCKS.register("stored_machine", StoredBlock::new);
    static final RegistryObject<BlockEntityType<StoredEntity>> TYPE = TYPES.register("stored_machine",
        () -> BlockEntityType.Builder.of(StoredEntity::new, BLOCK.get()).build(null));
    static final RegistryObject<Item> ITEM = ITEMS.register("stored_machine", () -> new BlockItem(BLOCK.get(), new Item.Properties()));
    static final ItemDataKey<Integer> ENERGY = new ItemDataKey<>(id("energy"), Codec.INT);
    static final ItemDataKey<FluidStack> FLUID = new ItemDataKey<>(id("fluid"), FluidStack.CODEC);
    static final ItemDataKey<IOConfig> IO = new ItemDataKey<>(id("io"), IOConfig.CODEC);
    static final ItemDataKey<RedstoneControl> REDSTONE = new ItemDataKey<>(id("redstone"), RedstoneControl.CODEC);
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("endercore_tests", path); }

    static void register(IEventBus bus) { BLOCKS.register(bus); TYPES.register(bus); ITEMS.register(bus); }

    private static final class StoredBlock extends BaseEntityBlock {
        StoredBlock() { super(BlockBehaviour.Properties.of()); }
        @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new StoredEntity(pos, state); }
        @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
            ((StoredEntity) level.getBlockEntity(pos)).energySeenByPlacement = ((StoredEntity) level.getBlockEntity(pos)).energy;
        }
    }

    /** Minimal registered fixture using the production base and actual machine inventory. */
    public static final class StoredEntity extends EnderBlockEntity {
        final MachineInventory inventory = new MachineInventory(new MachineTestContent.IO(), MachineInventoryLayout.builder().storageSlot(2).build());
        IOConfig io = IOConfig.of(IOMode.NONE);
        RedstoneControl redstone = RedstoneControl.ALWAYS_ACTIVE;
        FluidStack fluid = FluidStack.EMPTY;
        int energy, worldOnly, energySeenByPlacement, itemReads;
        StoredEntity(BlockPos pos, BlockState state) { super(TYPE.get(), pos, state); }

        @Override protected void saveAdditional(CompoundTag tag) {
            super.saveAdditional(tag);
            tag.put("Items", inventory.serializeNBT());
            tag.putInt("Energy", energy);
            tag.put("Fluid", fluid.writeToNBT(new CompoundTag()));
            tag.put("IO", io.save());
            tag.put("Redstone", redstone.save());
            tag.putInt("WorldOnly", worldOnly);
        }

        @Override public void load(CompoundTag tag) {
            super.load(tag);
            inventory.deserializeNBT(tag.getCompound("Items"));
            energy = tag.getInt("Energy");
            fluid = FluidStack.loadFluidStackFromNBT(tag.getCompound("Fluid"));
            io = IOConfig.parseOptional(tag.getCompound("IO"));
            if (tag.contains("Redstone")) redstone = RedstoneControl.parse(tag.get("Redstone"));
            worldOnly = tag.getInt("WorldOnly");
        }

        @Override protected void readItemData(ItemStack stack) {
            itemReads++;
            inventory.copyFromItem(ITEM_INVENTORY.getOrDefault(stack, new CompoundTag()));
            energy = ENERGY.getOrDefault(stack, 0);
            fluid = FLUID.getOrDefault(stack, FluidStack.EMPTY);
            io = IO.getOrDefault(stack, IOConfig.empty());
            redstone = REDSTONE.getOrDefault(stack, RedstoneControl.ALWAYS_ACTIVE);
        }

        @Override protected void writeItemData(ItemStack stack) {
            ITEM_INVENTORY.set(stack, inventory.toItemContents());
            ENERGY.set(stack, energy);
            if (!fluid.isEmpty()) FLUID.set(stack, fluid);
            IO.set(stack, io);
            REDSTONE.set(stack, redstone);
        }

        @Override protected void removeItemDataFromTag(CompoundTag tag) {
            for (String key : new String[] {"Items", "Energy", "Fluid", "IO", "Redstone"}) tag.remove(key);
        }
    }
}
