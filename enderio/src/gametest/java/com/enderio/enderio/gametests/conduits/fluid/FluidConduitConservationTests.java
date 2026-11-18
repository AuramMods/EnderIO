package com.enderio.enderio.gametests.conduits.fluid;

import com.enderio.enderio.api.io.RedstoneControl;
import com.enderio.enderio.content.conduits.ConduitBlockItem;
import com.enderio.enderio.content.conduits.bundle.ConduitBundleBlockEntity;
import com.enderio.enderio.content.conduits.network.ConduitNodeImpl;
import com.enderio.enderio.content.conduits.type.fluid.FluidConduitNodeData;
import com.enderio.enderio.init.EIOConduitTypes;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.function.Consumer;
import com.enderio.enderio.content.conduits.type.fluid.FluidConduitConnectionConfig;
import com.enderio.enderio.content.conduits.type.fluid.FluidConduitTicker;
import com.enderio.enderio.gametests.conduits.ConduitGameTestHelper;
import com.enderio.enderio.gametests.util.FixtureBuilder;
import com.enderio.enderio.gametests.util.GameplayTest;
import com.enderio.enderio.init.EIOConduits;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;

public class FluidConduitConservationTests {
    @GameTest
    public static void partialExecutionDoesNotLoseExtractedFluid(GameplayTest test) { conserve(test, 250, Fixture::retry); }

    @GameTest
    public static void rejectedExecutionDoesNotLoseExtractedFluid(GameplayTest test) { conserve(test, 0, Fixture::retry); }

    private static void conserve(GameplayTest test, int accepted, Consumer<Fixture> check) {
        test.registerGameTestTemplate(() -> FixtureBuilder.withSize(3, 1, 1)
            .set(0, 0, 0, Blocks.BARREL.defaultBlockState()).set(2, 0, 0, Blocks.BARREL.defaultBlockState()));
        test.onGameTest(ConduitGameTestHelper.class, helper -> {
            var conduit = helper.getConduit(EIOConduits.FLUID);
            helper.startSequence().thenExecute(() -> {
                helper.placeConduit(conduit, 1, 1, 0);
                var bundle = helper.getConduitBundle(1, 1, 0, false);
                bundle.setConnectionConfig(conduit, Direction.WEST, FluidConduitConnectionConfig.DEFAULT
                    .withIsExtract(true).withIsInsert(false).withExtractRedstoneControl(RedstoneControl.ALWAYS_ACTIVE));
                bundle.setConnectionConfig(conduit, Direction.EAST, FluidConduitConnectionConfig.DEFAULT
                    .withIsExtract(false).withIsInsert(true));
            }).thenExecuteAfter(3, () -> {
                var source = handler(helper, 0);
                var destination = handler(helper, 2);
                source.outputOnly = true;
                var water = new FluidStack(Fluids.WATER, 3000);
                water.getOrCreateTag().putInt("Conservation", 42);
                source.setFluid(water);
                destination.executionLimit = accepted;
                var node = helper.getConduitBundle(1, 1, 0, false).getConduitNode(conduit);
                var network = node.getNetwork();
                network.ensureCachesReady();
                FluidConduitTicker.INSTANCE.tick(helper.getLevel(), network,
                    (int) Math.floorMod(-helper.getLevel().getGameTime(), 20));
                var fixture = new Fixture(helper, source, destination, accepted);
                helper.assertValueEqual((long) source.amount() + destination.amount() + fixture.retained().amount(), 3000L,
                    "Extracted fluid must remain owned when destination execution accepts less");
                helper.assertValueEqual(source.amount(), 2000, "One update extracted at most 1,000 mB");
                helper.assertValueEqual(destination.amount(), accepted, "Actual execution acceptance");
                helper.assertValueEqual(fixture.retained().amount(), 1000L - accepted, "Entire rejected portion retained");
                helper.assertValueEqual(fixture.retained().first(Direction.WEST).getOrCreateTag().getInt("Conservation"), 42,
                    "Retained fluid preserves source NBT");
                check.accept(fixture);
            }).thenSucceed();
        });
    }

    @GameTest
    public static void nativeUnloadAndReattachRetainFluid(GameplayTest test) {
        conserve(test, 250, fixture -> {
            var original = fixture.bundle();
            var saved = original.saveWithFullMetadata();
            original.onChunkUnloaded();
            fixture.helper.getLevel().removeBlockEntity(original.getBlockPos());
            var restored = BlockEntity.loadStatic(original.getBlockPos(), original.getBlockState(), saved);
            fixture.helper.assertTrue(restored instanceof ConduitBundleBlockEntity && restored != original,
                "Native loader must create a fresh conduit bundle");
            fixture.helper.getLevel().setBlockEntity(restored);
            restored.onLoad();
            fixture.helper.assertValueEqual(fixture.retained().amount(), 750L, "Returned graph node retains fluid");
            fixture.retry();
        });
    }

    @GameTest
    public static void blockEntityRecoveryAndNetworkCodecRetainFluid(GameplayTest test) {
        conserve(test, 250, fixture -> {
            var original = fixture.bundle();
            var node = original.getConduitNode(fixture.helper.getConduit(EIOConduits.FLUID));
            var ops = RegistryOps.create(NbtOps.INSTANCE, fixture.helper.getLevel().registryAccess());
            var encoded = ConduitNodeImpl.CODEC.encodeStart(ops, (ConduitNodeImpl) node).getOrThrow(false, message -> {});
            var decoded = ConduitNodeImpl.CODEC.parse(ops, encoded).getOrThrow(false, message -> {});
            fixture.helper.assertValueEqual(decoded.getNodeData(EIOConduitTypes.NodeData.FLUID.get()).amount(), 750L,
                "Network node codec retains rejected fluid");
            var saved = original.saveWithFullMetadata();
            // Remove the live graph node so the fresh bundle must recover from its saved NodeData.
            original.removeConduit(fixture.helper.getConduit(EIOConduits.FLUID), stack -> {});
            fixture.helper.getLevel().removeBlockEntity(original.getBlockPos());
            var restored = BlockEntity.loadStatic(original.getBlockPos(), original.getBlockState(), saved);
            fixture.helper.getLevel().setBlockEntity(restored);
            restored.onLoad();
            fixture.helper.assertValueEqual(fixture.retained().amount(), 750L, "Block snapshot independently recovers fluid");
            fixture.retry();
        });
    }

    @GameTest
    public static void removalDropRestoresFluidThroughNativePlacement(GameplayTest test) {
        conserve(test, 250, fixture -> {
            var drops = new ArrayList<ItemStack>();
            fixture.bundle().removeConduit(fixture.helper.getConduit(EIOConduits.FLUID), drops::add);
            fixture.helper.assertValueEqual(drops.size(), 1, "One conduit drop owns retained fluid");
            fixture.placeDrop(drops.get(0));
            fixture.retry();
        });
    }

    @GameTest
    public static void lastConduitPlayerBreakDropsRetainedFluidOnce(GameplayTest test) {
        conserve(test, 250, fixture -> {
            var level = fixture.helper.getLevel();
            var pos = fixture.bundle().getBlockPos();
            var state = level.getBlockState(pos);
            var original = fixture.bundle();
            var player = fixture.helper.getPlayer();
            player.getAbilities().instabuild = false;
            fixture.helper.assertTrue(state.onDestroyedByPlayer(level, pos, player, false, state.getFluidState()),
                "Native player break removes last conduit");
            state.getBlock().playerDestroy(level, player, pos, state, original, ItemStack.EMPTY);
            var drops = level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                new net.minecraft.world.phys.AABB(pos).inflate(1), entity -> entity.getItem().getItem() instanceof ConduitBlockItem);
            fixture.helper.assertValueEqual(drops.size(), 1, "Last conduit drops its resource-bearing item once");
            var stack = drops.get(0).getItem().copy();
            drops.get(0).discard();
            fixture.placeDrop(stack);
            fixture.retry();
        });
    }

    @GameTest
    public static void upgradeMergesCarriedAndExistingRetainedFluid(GameplayTest test) {
        conserve(test, 0, fixture -> {
            var fast = fixture.helper.getConduit(EIOConduits.ENERGETIC_FLUID);
            var carriedNode = new ConduitNodeImpl(fast, fixture.bundle().getBlockPos());
            carriedNode.getOrCreateNodeData(EIOConduitTypes.NodeData.FLUID.get())
                .retain(Direction.WEST, new FluidStack(Fluids.LAVA, 500));
            var stack = ConduitBlockItem.getStackFor(fast, 1);
            fast.value().saveItemData(carriedNode, stack);
            var player = fixture.helper.getPlayer();
            player.getInventory().clearContent();
            player.getAbilities().instabuild = false;
            player.setItemInHand(InteractionHand.MAIN_HAND, stack);
            var pos = fixture.bundle().getBlockPos();
            var result = fixture.helper.getLevel().getBlockState(pos).use(fixture.helper.getLevel(), player,
                InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
            fixture.helper.assertTrue(result.consumesAction() && stack.isEmpty(), "Native upgrade consumes carried conduit");
            var retained = fixture.bundle().getConduitNode(fast).getNodeData(EIOConduitTypes.NodeData.FLUID.get());
            fixture.helper.assertValueEqual(retained.amount(), 1500L, "Upgrade preserves existing water and carried lava");
            var returned = player.getInventory().items.stream().filter(item -> item.getItem() instanceof ConduitBlockItem).toList();
            fixture.helper.assertValueEqual(returned.size(), 1, "Previous conduit returned once");
            fixture.helper.assertTrue(!returned.get(0).getOrCreateTag().contains("enderio:retained_fluid"),
                "Returned lower tier must not duplicate resources retained by upgraded node");
            fixture.destination.executionLimit = Integer.MAX_VALUE;
            var network = fixture.bundle().getConduitNode(fast).getNetwork();
            network.ensureCachesReady();
            FluidConduitTicker.INSTANCE.tick(fixture.helper.getLevel(), network,
                (int) Math.floorMod(-fixture.helper.getLevel().getGameTime(), 20));
            fixture.helper.assertValueEqual(fixture.destination.amount(), 1000, "First retained fluid delivered");
            fixture.helper.assertValueEqual(retained.amount(), 500L, "Incompatible carried fluid remains owned");
            fixture.helper.assertTrue(retained.first(Direction.WEST).getFluid() == Fluids.LAVA, "Carried fluid identity remains");
        });
    }

    @GameTest
    public static void waterloggingKeepsRetainedFluidAndConduit(GameplayTest test) {
        conserve(test, 250, fixture -> {
            var level = fixture.helper.getLevel();
            var pos = fixture.bundle().getBlockPos();
            level.setBlock(pos, level.getBlockState(pos).setValue(
                net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED, true), 3);
            fixture.helper.assertTrue(fixture.bundle().hasConduitStrict(fixture.helper.getConduit(EIOConduits.FLUID)),
                "Waterlogging must not clear the conduit bundle");
            fixture.helper.assertValueEqual(fixture.retained().amount(), 750L, "Waterlogging retains conduit-owned fluid");
            fixture.retry();
        });
    }

    @GameTest
    public static void nativeBlockDestructionDropsRetainedFluid(GameplayTest test) {
        conserve(test, 250, fixture -> {
            var level = fixture.helper.getLevel();
            var pos = fixture.bundle().getBlockPos();
            level.destroyBlock(pos, true);
            var drops = level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                new net.minecraft.world.phys.AABB(pos).inflate(1), entity -> entity.getItem().getItem() instanceof ConduitBlockItem);
            fixture.helper.assertValueEqual(drops.size(), 1, "Native block destruction drops resource-bearing conduit once");
            var stack = drops.get(0).getItem().copy();
            drops.get(0).discard();
            fixture.placeDrop(stack);
            fixture.retry();
        });
    }

    @GameTest
    public static void nativeMixedBundleDropsConduitsAndFiltersOnce(GameplayTest test) {
        conserve(test, 0, fixture -> {
            var fluid = fixture.helper.getConduit(EIOConduits.FLUID);
            var energy = fixture.helper.getConduit(EIOConduits.ENERGY);
            var bundle = fixture.bundle();
            bundle.addConduit(energy, null, fixture.helper.getPlayer());
            bundle.getConduitNode(fluid).getInventory(Direction.EAST).setStackInSlot(1,
                new ItemStack(com.enderio.enderio.init.EIOItems.BASIC_FLUID_FILTER.get()));
            var pos = bundle.getBlockPos();
            var level = fixture.helper.getLevel();
            var preview = net.minecraft.world.level.block.Block.getDrops(level.getBlockState(pos), level, pos, bundle);
            fixture.helper.assertValueEqual(preview.size(), 3, "Native drop query includes both conduits and the filter");
            fixture.helper.assertValueEqual(fixture.retained().amount(), 1000L, "Drop query must not consume live resources");
            level.destroyBlock(pos, true);
            var drops = level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                new net.minecraft.world.phys.AABB(pos).inflate(1));
            fixture.helper.assertValueEqual(drops.size(), 3, "Destruction emits each queried item once");
            var fluidDrop = drops.stream().filter(entity -> entity.getItem().getOrCreateTag().contains("enderio:retained_fluid"))
                .findFirst().orElseThrow();
            var stack = fluidDrop.getItem().copy();
            drops.forEach(net.minecraft.world.entity.Entity::discard);
            fixture.placeDrop(stack);
            fixture.retry();
        });
    }

    @GameTest
    public static void retainedRetryHonorsInsertionFilterAndRedstone(GameplayTest test) {
        conserve(test, 250, fixture -> {
            var conduit = fixture.helper.getConduit(EIOConduits.FLUID);
            var bundle = fixture.bundle();
            bundle.setConnectionConfig(conduit, Direction.WEST, FluidConduitConnectionConfig.DEFAULT
                .withIsExtract(true).withIsInsert(false).withExtractRedstoneControl(RedstoneControl.NEVER_ACTIVE));
            fixture.destination.executionLimit = Integer.MAX_VALUE;
            fixture.tick();
            fixture.helper.assertValueEqual(fixture.retained().amount(), 750L, "Disabled extraction retains pending fluid");
            fixture.configure();
            var inventory = bundle.getConduitNode(conduit).getInventory(Direction.EAST);
            var filter = new ItemStack(com.enderio.enderio.init.EIOItems.BASIC_FLUID_FILTER.get());
            com.enderio.enderio.init.EIODataComponents.FLUID_FILTER.set(filter,
                new com.enderio.enderio.content.filters.fluid.EnderFluidFilter(
                    java.util.List.of(new FluidStack(Fluids.LAVA, 1)), false, false));
            inventory.setStackInSlot(1, filter);
            fixture.tick();
            fixture.helper.assertValueEqual(fixture.retained().amount(), 750L, "Insertion filter holds incompatible pending fluid");
            inventory.setStackInSlot(1, ItemStack.EMPTY);
            fixture.retry();
        });
    }

    private record Fixture(ConduitGameTestHelper helper, ChangingFluidHandler source,
                           ChangingFluidHandler destination, int accepted) {
        ConduitBundleBlockEntity bundle() { return helper.getConduitBundle(1, 1, 0, false); }
        FluidConduitNodeData retained() {
            return bundle().getConduitNode(helper.getConduit(EIOConduits.FLUID)).getNodeData(EIOConduitTypes.NodeData.FLUID.get());
        }
        void configure() {
            var conduit = helper.getConduit(EIOConduits.FLUID);
            bundle().setConnectionConfig(conduit, Direction.WEST, FluidConduitConnectionConfig.DEFAULT
                .withIsExtract(true).withIsInsert(false).withExtractRedstoneControl(RedstoneControl.ALWAYS_ACTIVE));
            bundle().setConnectionConfig(conduit, Direction.EAST, FluidConduitConnectionConfig.DEFAULT
                .withIsExtract(false).withIsInsert(true));
        }
        void tick() {
            var network = bundle().getConduitNode(helper.getConduit(EIOConduits.FLUID)).getNetwork();
            network.ensureCachesReady();
            FluidConduitTicker.INSTANCE.tick(helper.getLevel(), network, (int) Math.floorMod(-helper.getLevel().getGameTime(), 20));
        }
        void retry() {
            destination.executionLimit = Integer.MAX_VALUE;
            tick();
            helper.assertValueEqual(source.amount(), 2000, "Retry must not drain the source again");
            helper.assertValueEqual(destination.amount(), 1000, "Retry delivers entire previously extracted batch");
            helper.assertValueEqual(retained().amount(), 0L, "No retained duplicate after successful delivery");
            helper.assertValueEqual(destination.getFluidInTank(0).getOrCreateTag().getInt("Conservation"), 42,
                "Delivered fluid retains NBT");
            tick();
            helper.assertValueEqual(source.amount(), 1000, "Next update resumes normal extraction");
            helper.assertValueEqual(destination.amount(), 2000, "Next update respects normal rate");
        }
        void placeDrop(ItemStack stack) {
            var level = helper.getLevel();
            var pos = helper.absolutePos(new BlockPos(1, 1, 0));
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            var result = ((BlockItem) stack.getItem()).place(new BlockPlaceContext(level, helper.getPlayer(),
                InteractionHand.MAIN_HAND, stack, new BlockHitResult(Vec3.atCenterOf(pos), Direction.DOWN, pos, false)));
            helper.assertTrue(result.consumesAction(), "Native BlockItem placement restores resource-bearing conduit");
            helper.assertValueEqual(retained().amount(), 1000L - accepted, "Placement restores exactly one carried remainder");
            configure();
        }
    }

    private static ChangingFluidHandler handler(ConduitGameTestHelper helper, int x) {
        return (ChangingFluidHandler) helper.getLevel().getBlockEntity(helper.absolutePos(new BlockPos(x, 1, 0)))
            .getCapability(ForgeCapabilities.FLUID_HANDLER).orElseThrow(IllegalStateException::new);
    }
}
