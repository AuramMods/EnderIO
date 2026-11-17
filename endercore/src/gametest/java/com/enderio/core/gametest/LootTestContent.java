package com.enderio.core.gametest;

import com.enderio.enderio.content.paint.block.entity.PaintedBlockEntity;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.common.loot.LootModifier;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.Optional;

public final class LootTestContent {
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, "endercore_tests");
    private static final DeferredRegister<BlockEntityType<?>> TYPES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, "endercore_tests");
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, "enderio");
    private static final DeferredRegister<Codec<? extends IGlobalLootModifier>> MODIFIERS =
        DeferredRegister.create(ForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, "endercore_tests");
    static final RegistryObject<Block> PAINTED = BLOCKS.register("loot_paint", PaintBlock::new);
    static final RegistryObject<BlockEntityType<PaintEntity>> PAINT_TYPE = TYPES.register("loot_paint",
        () -> BlockEntityType.Builder.of(PaintEntity::new, PAINTED.get()).build(null));
    static final RegistryObject<Item> BROKEN_SPAWNER = ITEMS.register("broken_spawner", () -> new Item(new Item.Properties()));

    static void register(IEventBus bus) {
        BLOCKS.register(bus); TYPES.register(bus); ITEMS.register(bus);
        MODIFIERS.register("post_marker", () -> PostMarker.CODEC);
        MODIFIERS.register(bus);
    }

    private static final class PaintBlock extends BaseEntityBlock {
        PaintBlock() { super(BlockBehaviour.Properties.of()); }
        @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new PaintEntity(pos, state); }
    }

    static final class PaintEntity extends BlockEntity implements PaintedBlockEntity {
        Block primary;
        Block secondary;
        PaintEntity(BlockPos pos, BlockState state) { super(PAINT_TYPE.get(), pos, state); }
        @Override public Optional<Block> getPrimaryPaint() { return Optional.ofNullable(primary); }
        @Override public void setPrimaryPaint(Block paint) { primary = paint; }
        @Override public boolean hasSecondaryPaint() { return true; }
        @Override public Optional<Block> getSecondaryPaint() { return Optional.ofNullable(secondary); }
        @Override public void setSecondaryPaint(Block paint) { secondary = paint; }
    }

    public static final class PostMarker extends LootModifier {
        static int calls;
        static final Codec<PostMarker> CODEC = RecordCodecBuilder.create(instance -> codecStart(instance).apply(instance, PostMarker::new));
        PostMarker(LootItemCondition[] conditions) { super(conditions); }
        @Override protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> loot, LootContext context) {
            calls++;
            loot.add(new ItemStack(Items.PAPER));
            return loot;
        }
        @Override public Codec<? extends IGlobalLootModifier> codec() { return CODEC; }
    }
}
