package com.enderio.core.gametest;

import com.enderio.core.common.capability.ItemCapabilityRegistry;
import com.enderio.core.common.item.ItemDataKey;
import com.enderio.core.common.registries.BlockDeferredHolder;
import com.enderio.core.common.registries.BlockDeferredRegister;
import com.enderio.core.common.registries.ItemDeferredHolder;
import com.enderio.core.common.registries.ItemDeferredRegister;
import com.mojang.serialization.Codec;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.energy.EnergyStorage;
import net.minecraftforge.eventbus.api.IEventBus;

public final class ItemDataTestContent {
    public static final BlockDeferredRegister BLOCKS = BlockDeferredRegister.create("endercore_tests");
    public static final ItemDeferredRegister ITEMS = ItemDeferredRegister.create("endercore_tests");
    public static final ItemDataKey<Integer> ENERGY = new ItemDataKey<>(id("stored_energy"), Codec.intRange(0, 1000));
    public static final ItemDataKey<CompoundTag> CONFIG = new ItemDataKey<>(id("config"), CompoundTag.CODEC);
    public static int itemFactoryCalls;
    public static int blockFactoryCalls;
    public static ResourceLocation factoryId;

    public static final BlockDeferredHolder<Block> BLOCK = BLOCKS.registerBlock("data_fixture", properties -> {
        blockFactoryCalls++;
        return new Block(properties);
    });
    public static final ItemDeferredHolder<Item> ITEM = ITEMS.register("data_item", id -> {
        itemFactoryCalls++;
        factoryId = id;
        Item item = new Item(new Item.Properties().stacksTo(13));
        ENERGY.withDefault(item, () -> 40);
        return CONFIG.withDefault(item, () -> {
            CompoundTag tag = new CompoundTag();
            tag.putInt("count", 3);
            return tag;
        });
    });
    public static final ItemDeferredHolder<Item> BUILDER_ITEM = ITEMS.builder("builder_fixture", Item::new,
        new Item.Properties().stacksTo(7)).build();
    public static final ItemDeferredHolder<?> BLOCK_ITEM = ITEMS.registerSimpleBlockItem(BLOCK);

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("endercore_tests", path);
    }

    public static void register(IEventBus bus) {
        BLOCKS.addAlias(id("old_data_fixture"), BLOCK.getId());
        ITEMS.addAlias(id("old_data_fixture"), BLOCK_ITEM.getId());
        ITEMS.addAlias(id("old_data_item"), ITEM.getId());
        BLOCKS.register(bus);
        ITEMS.register(bus);
        var capabilities = new ItemCapabilityRegistry(id("data_energy"));
        capabilities.register(ForgeCapabilities.ENERGY, (stack, side) ->
            new EnergyStorage(1000, 1000, 1000, ENERGY.getOrDefault(stack, 0)), ITEM.getId());
        capabilities.register();
    }
}
