package com.enderio.core.gametest;

import com.enderio.core.common.registries.BlockEntityTypeDeferredRegister;
import com.enderio.core.common.capability.ItemCapabilityRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import net.minecraftforge.energy.EnergyStorage;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Real registered blocks used only by the headless GameTest mod. */
public final class CapabilityTestBlocks {
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, "endercore_tests");
    private static final BlockEntityTypeDeferredRegister BLOCK_ENTITIES = BlockEntityTypeDeferredRegister.create("endercore_tests");
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, "endercore_tests");
    public static final RegistryObject<Item> ITEM = ITEMS.register("capability_fixture", () -> new Item(new Item.Properties()));
    public static final RegistryObject<FixtureBlock> BLOCK = BLOCKS.register("capability_fixture", FixtureBlock::new);
    public static final RegistryObject<BlockEntityType<FixtureBlockEntity>> BLOCK_ENTITY = BLOCK_ENTITIES
        .builder("capability_fixture", FixtureBlockEntity::new, () -> BLOCK.get())
        .capability(ForgeCapabilities.ENERGY, (entity, side) -> {
            entity.factoryCalls++;
            return entity.exposed && (side == null || side == Direction.WEST) ? entity.storage : null;
        }).build();

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        BLOCK_ENTITIES.register(bus);
        ITEMS.register(bus);
        var items = new ItemCapabilityRegistry(ResourceLocation.fromNamespaceAndPath("endercore_tests", "item_energy"));
        items.register(ForgeCapabilities.ENERGY, (stack, side) ->
            new EnergyStorage(1000, 1000, 1000, stack.hasTag() ? stack.getTag().getInt("energy") : 0), ITEM.getId());
        items.register();
        var secondary = new ItemCapabilityRegistry(ResourceLocation.fromNamespaceAndPath("endercore_tests", "item_fluid"));
        secondary.register(ForgeCapabilities.FLUID_HANDLER, (stack, side) ->
            new FluidTank(stack.hasTag() ? stack.getTag().getInt("energy") : 0), ITEM.getId());
        secondary.register();
        MinecraftForge.EVENT_BUS.addGenericListener(ItemStack.class, CapabilityTestBlocks::attachUnrelatedItemProvider);
        MinecraftForge.EVENT_BUS.addGenericListener(BlockEntity.class, CapabilityTestBlocks::attachUnrelatedBlockProvider);
    }

    private static void attachUnrelatedItemProvider(AttachCapabilitiesEvent<ItemStack> event) {
        if (!ITEM.isPresent() || event.getObject().getItem() != ITEM.get()) return;
        var handler = LazyOptional.of(() -> new ItemStackHandler(1));
        event.addCapability(ResourceLocation.fromNamespaceAndPath("endercore_tests", "unrelated_inventory"), new ICapabilityProvider() {
            @Override
            public <T> LazyOptional<T> getCapability(Capability<T> cap, Direction side) {
                return cap == ForgeCapabilities.ITEM_HANDLER ? handler.cast() : LazyOptional.empty();
            }
        });
        event.addListener(handler::invalidate);
    }

    private static void attachUnrelatedBlockProvider(AttachCapabilitiesEvent<BlockEntity> event) {
        if (!(event.getObject() instanceof FixtureBlockEntity)) return;
        var handler = LazyOptional.of(() -> new ItemStackHandler(1));
        event.addCapability(ResourceLocation.fromNamespaceAndPath("endercore_tests", "unrelated_inventory"), new ICapabilityProvider() {
            @Override
            public <T> LazyOptional<T> getCapability(Capability<T> cap, Direction side) {
                return cap == ForgeCapabilities.ITEM_HANDLER ? handler.cast() : LazyOptional.empty();
            }
        });
        event.addListener(handler::invalidate);
    }

    public static final class FixtureBlock extends BaseEntityBlock {
        public FixtureBlock() {
            super(BlockBehaviour.Properties.of().strength(1));
        }

        @Override
        public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
            return new FixtureBlockEntity(pos, state);
        }
    }

    public static final class FixtureBlockEntity extends BlockEntity {
        public boolean exposed = true;
        public EnergyStorage storage = new EnergyStorage(1000);
        public int factoryCalls;

        public FixtureBlockEntity(BlockPos pos, BlockState state) {
            super(BLOCK_ENTITY.get(), pos, state);
        }
    }
}
