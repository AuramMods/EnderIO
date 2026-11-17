package com.enderio.core.gametest;

import com.enderio.core.common.item.ItemDataKey;
import com.enderio.core.common.registries.BlockEntityTypeDeferredRegister;
import com.enderio.enderio.api.io.IOMode;
import com.enderio.enderio.api.io.SideConfig;
import com.enderio.enderio.content.misc_blocks.ResettingLeverBlock;
import com.enderio.enderio.content.tools.YetaWrenchItem;
import com.enderio.enderio.foundation.block.MachineBlock;
import com.enderio.enderio.foundation.block.WrenchableBlockHandler;
import com.enderio.enderio.foundation.block.entity.MachineBlockEntity;
import com.enderio.enderio.foundation.inventory.MachineInventoryLayout;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Registered fixtures inheriting the actual production machine interaction and persistence paths. */
public final class MachineInteractionTestContent {
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, "endercore_tests");
    private static final BlockEntityTypeDeferredRegister TYPES = BlockEntityTypeDeferredRegister.create("endercore_tests");
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, "endercore_tests");
    static final RegistryObject<FixtureBlock> BLOCK = BLOCKS.register("interactive_machine", FixtureBlock::new);
    static final RegistryObject<BlockEntityType<FixtureEntity>> TYPE = TYPES.builder("interactive_machine", FixtureEntity::new, () -> BLOCK.get())
        .capability(ForgeCapabilities.ITEM_HANDLER, MachineBlockEntity.ITEM_HANDLER_PROVIDER)
        .capability(SideConfig.CAPABILITY, MachineBlockEntity.SIDE_CONFIG_PROVIDER).build();
    static final RegistryObject<Item> ITEM = ITEMS.register("interactive_machine", () -> new BlockItem(BLOCK.get(), new Item.Properties()));
    static final RegistryObject<Item> WRENCH = ITEMS.register("yeta_wrench", () -> new YetaWrenchItem(new Item.Properties()));
    static final RegistryObject<ForeignWrench> FOREIGN_WRENCH = ITEMS.register("foreign_wrench", ForeignWrench::new);
    static final RegistryObject<ResettingLeverBlock> LEVER = BLOCKS.register("resetting_lever", () -> new ResettingLeverBlock(1, false));
    static final RegistryObject<ResettingLeverBlock> INVERTED_LEVER = BLOCKS.register("inverted_resetting_lever", () -> new ResettingLeverBlock(1, true));
    static final ItemDataKey<Boolean> REMOVED = new ItemDataKey<>(ResourceLocation.fromNamespaceAndPath("endercore_tests", "removed"), Codec.BOOL);

    static void register(IEventBus bus) {
        BLOCKS.register(bus);
        TYPES.register(bus);
        ITEMS.register(bus);
        // The production @Mod subscriber is otherwise absent from this isolated test-mod profile.
        MinecraftForge.EVENT_BUS.register(WrenchableBlockHandler.class);
    }

    public static final class FixtureBlock extends MachineBlock<FixtureEntity> {
        FixtureBlock() { super(() -> TYPE.get(), BlockBehaviour.Properties.of().strength(1)); }

        @Override protected InteractionResult useHeldItem(ItemStack stack, BlockState state, Level level, BlockPos pos,
                Player player, InteractionHand hand, BlockHitResult hit) {
            if (stack.is(Items.SLIME_BALL)) {
                if (!level.isClientSide) ((FixtureEntity) level.getBlockEntity(pos)).itemUses++;
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
            return super.useHeldItem(stack, state, level, pos, player, hand, hit);
        }

        @Override public void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moving) {
            if (!state.is(replacement.getBlock()) && level.getBlockEntity(pos) instanceof FixtureEntity machine) machine.removalSeen = true;
            super.onRemove(state, level, pos, replacement, moving);
        }
    }

    public static final class FixtureEntity extends MachineBlockEntity {
        int menuOpens, itemUses, wrenchUses, modeChanges;
        boolean removalSeen;
        FixtureEntity(BlockPos pos, BlockState state) { super(TYPE.get(), pos, state, true); }
        @Override protected MachineInventoryLayout createInventoryLayout() { return MachineInventoryLayout.builder().storageSlot(2).build(); }
        @Override public boolean isActive() { return false; }
        @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
            menuOpens++;
            return ChestMenu.threeRows(id, inventory, new SimpleContainer(27));
        }
        @Override public InteractionResult onWrenched(UseOnContext context) { wrenchUses++; return super.onWrenched(context); }
        @Override public void setIOMode(Direction side, IOMode mode) { super.setIOMode(side, mode); modeChanges++; }
        @Override protected void writeItemData(ItemStack stack) { super.writeItemData(stack); REMOVED.set(stack, removalSeen); }
    }

    public static final class ForeignWrench extends Item {
        int calls;
        ForeignWrench() { super(new Item.Properties().stacksTo(1)); }
        @Override public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) { calls++; return InteractionResult.SUCCESS; }
        @Override public InteractionResult useOn(UseOnContext context) { calls++; return InteractionResult.SUCCESS; }
    }
}
