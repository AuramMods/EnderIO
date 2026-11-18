package com.enderio.modded_conduits;

import com.enderio.enderio.api.EnderIORegistries;
import com.enderio.enderio.api.conduits.Conduit;
import com.enderio.enderio.api.io.RedstoneControl;
import com.enderio.enderio.content.conduits.ConduitBlockItem;
import com.enderio.enderio.content.conduits.bundle.ConduitBundleBlockEntity;
import com.enderio.enderio.content.conduits.network.ConduitNodeImpl;
import com.enderio.modded_conduits.common.modules.mekanism.MekanismModule;
import com.enderio.modded_conduits.common.modules.mekanism.chemical.ChemicalConduit;
import com.enderio.modded_conduits.common.modules.mekanism.chemical.ChemicalConduitConnectionConfig;
import com.enderio.modded_conduits.common.modules.mekanism.chemical_filter.EnderChemicalFilter;
import com.enderio.modded_conduits.common.modules.mekanism.chemical.ChemicalResource;
import com.enderio.modded_conduits.common.modules.mekanism.chemical.ChemicalTicker;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.chemical.ChemicalType;
import mekanism.common.capabilities.Capabilities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

@GameTestHolder("enderio_modded_conduits_tests")
@PrefixGameTestTemplate(false)
public class ChemicalConservationTests {
    @GameTest(template = "network")
    public static void partiallyRejectedGasIsConserved(GameTestHelper helper) {
        scenario(helper, ChemicalType.GAS, "oxygen", 1250, Fixture::retry);
    }

    @GameTest(template = "network")
    public static void completelyRejectedInfusionIsConserved(GameTestHelper helper) {
        scenario(helper, ChemicalType.INFUSION, "redstone", 0, Fixture::retry);
    }

    @GameTest(template = "network")
    public static void partlyRejectedPigmentIsConserved(GameTestHelper helper) {
        scenario(helper, ChemicalType.PIGMENT, "red", 1250, Fixture::retry);
    }

    @GameTest(template = "network")
    public static void completelyRejectedSlurryIsConserved(GameTestHelper helper) {
        scenario(helper, ChemicalType.SLURRY, "dirty_iron", 0, Fixture::retry);
    }

    @GameTest(template = "network")
    public static void splitAndReconnectedRouteKeepsOwnershipOnTheSourceNode(GameTestHelper helper) {
        scenario(helper, ChemicalType.GAS, "oxygen", 0, fixture -> {
            helper.setBlock(4, 1, 2, Blocks.BARREL);
            helper.setBlock(3, 1, 2, Blocks.AIR);
            var extension = ConduitNetworkIntegrationTests.place(helper, fixture.conduit, 3);
            extension.setConnectionConfig(fixture.conduit, Direction.EAST, new ChemicalConduitConnectionConfig(
                true, DyeColor.GREEN, false, DyeColor.GREEN, RedstoneControl.NEVER_ACTIVE, DyeColor.RED));
            var sourceBundle = fixture.bundle();
            var sourceNode = sourceBundle.getConduitNode(fixture.conduit);
            var extensionNode = extension.getConduitNode(fixture.conduit);
            helper.assertTrue(sourceNode.getNetwork() == extensionNode.getNetwork(), "Expected a real merged graph");

            sourceBundle.disableNeighborConnection(com.mojang.datafixers.util.Pair.of(Direction.EAST, fixture.conduit));
            helper.assertTrue(sourceNode.getNetwork() != extensionNode.getNetwork(), "Disabling the link must split the graph");
            fixture.tick();
            helper.assertTrue(fixture.pending().getAmount() == 3750 && fixture.source.getChemicalInTank(0).getAmount() == 6250,
                "A split graph must retain its earlier extraction on the source node");

            helper.assertTrue(sourceBundle.tryConnectTo(fixture.conduit, Direction.EAST, true), "Forced reconnection failed");
            helper.assertTrue(sourceNode.getNetwork() == extensionNode.getNetwork(), "Reconnecting must merge the graph");
            var destination = tank(helper, 4, ChemicalType.GAS);
            new Fixture(helper, fixture.conduit, fixture.source, destination, fixture.chemical, 3750, 0).retry();
        });
    }

    @GameTest(template = "network")
    public static void retainedLongQuantitySurvivesSavedNodeAndItemNbt(GameTestHelper helper) {
        var largeConduit = ResourceKey.create(EnderIORegistries.Keys.CONDUIT,
            ResourceLocation.fromNamespaceAndPath("enderio_modded_conduits_tests", "large_chemical_rate"));
        scenario(helper, largeConduit, ChemicalType.PIGMENT, "red", Long.MAX_VALUE, Long.MAX_VALUE, 7, fixture -> {
            var bundle = fixture.bundle();
            var node = (ConduitNodeImpl) bundle.getConduitNode(fixture.conduit);
            var ops = RegistryOps.create(NbtOps.INSTANCE, helper.getLevel().registryAccess());
            var tag = ConduitNodeImpl.CODEC.encodeStart(ops, node).getOrThrow(false, message -> {});
            var restored = ConduitNodeImpl.CODEC.parse(ops, tag).getOrThrow(false, message -> {});
            helper.assertTrue(fixture.pending().equals(restored.getNodeData(MekanismModule.CHEMICAL_NODE_DATA.get()).first(Direction.WEST)),
                "Saved network node must preserve chemical family, ID and exact long amount");

            var world = helper.getLevel();
            var pos = bundle.getBlockPos();
            world.destroyBlock(pos, true);
            var drops = world.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(1),
                entity -> entity.getItem().getItem() instanceof ConduitBlockItem);
            helper.assertTrue(drops.size() == 1, "Expected one retained chemical conduit item");
            // Use Forge/vanilla ItemStack NBT round trip, not the original live node or item.
            var stack = ItemStack.of(drops.get(0).getItem().save(new CompoundTag()));
            drops.get(0).discard();
            var player = FakePlayerFactory.getMinecraft(world);
            var result = ((ConduitBlockItem) stack.getItem()).place(new BlockPlaceContext(world, player,
                InteractionHand.MAIN_HAND, stack, new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false)));
            helper.assertTrue(result.consumesAction(), "Native placement of a saved conduit drop failed");
            configure(fixture.bundle(), fixture.conduit);
            helper.assertTrue(fixture.pending().equals(fixture.chemical.copyWithAmount(Long.MAX_VALUE - 7)),
                "Native placement must restore the exact carried amount");
            fixture.retry();
        });
    }

    @GameTest(template = "network")
    public static void retainedInfusionSurvivesReloadAndWaitsForAValidRoute(GameTestHelper helper) {
        scenario(helper, ChemicalType.INFUSION, "redstone", 0, fixture -> {
            var bundle = fixture.bundle();
            var pos = bundle.getBlockPos();
            var world = helper.getLevel();
            var saved = bundle.saveWithFullMetadata();
            bundle.onChunkUnloaded();
            world.removeBlockEntity(pos);
            var restored = BlockEntity.loadStatic(pos, bundle.getBlockState(), saved);
            helper.assertTrue(restored instanceof ConduitBundleBlockEntity && restored != bundle, "Native bundle reload failed");
            world.setBlockEntity(restored);
            restored.onLoad();
            helper.assertTrue(fixture.pending().equals(fixture.chemical.copyWithAmount(3750)),
                "Native reload must restore rejected infusion");

            var inventory = fixture.bundle().getConduitNode(fixture.conduit).getInventory(Direction.EAST);
            var filter = new ItemStack(MekanismModule.BASIC_CHEMICAL_FILTER.get());
            var gas = ChemicalResource.fromRegistry(ChemicalType.GAS, ResourceLocation.fromNamespaceAndPath("mekanism", "oxygen"), 1);
            MekanismModule.CHEMICAL_FILTER.set(filter, new EnderChemicalFilter(List.of(gas), false));
            inventory.setStackInSlot(ChemicalConduit.INSERT_FILTER_SLOT, filter);
            fixture.destination.executionLimit = Long.MAX_VALUE;
            fixture.tick();
            helper.assertTrue(fixture.pending().getAmount() == 3750 && fixture.destination.getChemicalInTank(0).isEmpty(),
                "Wrong-family insertion filter must keep rejected infusion in the conduit");
            inventory.setStackInSlot(ChemicalConduit.INSERT_FILTER_SLOT, ItemStack.EMPTY);
            fixture.bundle().setConnectionConfig(fixture.conduit, Direction.WEST, new ChemicalConduitConnectionConfig(
                false, DyeColor.GREEN, true, DyeColor.GREEN, RedstoneControl.NEVER_ACTIVE, DyeColor.RED));
            fixture.tick();
            helper.assertTrue(fixture.pending().getAmount() == 3750 && fixture.destination.getChemicalInTank(0).isEmpty(),
                "Disabled extraction route must retain its owned chemical");
            configure(fixture.bundle(), fixture.conduit);
            fixture.retry();
        });
    }

    @GameTest(template = "network")
    public static void tierUpgradeTransfersOwnershipWithoutDuplicatingTheReturnedConduit(GameTestHelper helper) {
        scenario(helper, ChemicalType.SLURRY, "dirty_iron", 0, fixture -> {
            var level = helper.getLevel();
            var bundle = fixture.bundle();
            var fast = level.registryAccess().registryOrThrow(EnderIORegistries.Keys.CONDUIT)
                .getHolderOrThrow(MekanismModule.PRESSURIZED_CHEMICAL);
            var stack = ConduitBlockItem.getStackFor(fast, 1);
            var player = FakePlayerFactory.getMinecraft(level);
            player.getInventory().clearContent();
            player.getAbilities().instabuild = false;
            player.setItemInHand(InteractionHand.MAIN_HAND, stack);
            var pos = bundle.getBlockPos();
            var result = bundle.getBlockState().use(level, player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
            helper.assertTrue(result.consumesAction() && stack.isEmpty(), "Native tier upgrade must consume the new conduit");
            var returned = player.getInventory().items.stream().filter(item -> item.getItem() instanceof ConduitBlockItem).toList();
            helper.assertTrue(returned.size() == 1 && !returned.get(0).getOrCreateTag().getCompound("enderio:data")
                .contains("enderio:retained_chemical"), "Returned conduit cannot duplicate retained slurry");
            var upgraded = new Fixture(helper, fast, fixture.source, fixture.destination, fixture.chemical, 3750, 0);
            helper.assertTrue(upgraded.pending().equals(fixture.chemical.copyWithAmount(3750)),
                "New tier must own the entire previously rejected slurry");
            upgraded.retry();
        });
    }

    private static void scenario(GameTestHelper helper, ChemicalType family, String name, long accepted,
            Consumer<Fixture> check) {
        scenario(helper, MekanismModule.CHEMICAL, family, name, 10000, 3750, accepted, check);
    }

    private static void scenario(GameTestHelper helper, ResourceKey<Conduit<?, ?>> key, ChemicalType family, String name,
            long initial, long budget, long accepted, Consumer<Fixture> check) {
        var conduit = helper.getLevel().registryAccess().registryOrThrow(EnderIORegistries.Keys.CONDUIT).getHolderOrThrow(key);
        var bundle = new AtomicReference<ConduitBundleBlockEntity>();
        helper.startSequence().thenExecute(() -> {
            helper.setBlock(1, 1, 2, Blocks.BARREL);
            helper.setBlock(3, 1, 2, Blocks.BARREL);
            bundle.set(ConduitNetworkIntegrationTests.place(helper, conduit, 2));
            configure(bundle.get(), conduit);
        }).thenIdle(3).thenExecute(() -> {
            var source = tank(helper, 1, family);
            var destination = tank(helper, 3, family);
            var chemical = ChemicalResource.fromRegistry(family, ResourceLocation.fromNamespaceAndPath("mekanism", name), initial);
            seed(source, chemical.nativeCopy());
            source.outputOnly = true;
            destination.executionLimit = accepted;
            tick(helper, bundle.get(), conduit);
            var fixture = new Fixture(helper, conduit, source, destination, chemical, budget, accepted);
            helper.assertTrue(source.getChemicalInTank(0).getAmount() == initial - budget, "Source extraction must respect its budget");
            helper.assertTrue(destination.getChemicalInTank(0).getAmount() == accepted, "Actual destination acceptance differs");
            helper.assertTrue(fixture.pending().equals(chemical.copyWithAmount(budget - accepted)),
                "Entire rejected quantity must remain owned with the exact family/identity");
            check.accept(fixture);
        }).thenSucceed();
    }

    private static void configure(ConduitBundleBlockEntity bundle, Holder<Conduit<?, ?>> conduit) {
        bundle.setConnectionConfig(conduit, Direction.WEST, new ChemicalConduitConnectionConfig(
            false, DyeColor.GREEN, true, DyeColor.GREEN, RedstoneControl.ALWAYS_ACTIVE, DyeColor.RED));
        bundle.setConnectionConfig(conduit, Direction.EAST, new ChemicalConduitConnectionConfig(
            true, DyeColor.GREEN, false, DyeColor.GREEN, RedstoneControl.NEVER_ACTIVE, DyeColor.RED));
    }

    private static void tick(GameTestHelper helper, ConduitBundleBlockEntity bundle, Holder<Conduit<?, ?>> conduit) {
        var network = bundle.getConduitNode(conduit).getNetwork();
        network.ensureCachesReady();
        ChemicalTicker.INSTANCE.tick(helper.getLevel(), network, (int) Math.floorMod(-helper.getLevel().getGameTime(), 20));
    }

    private static ChangingChemicalTank<?, ?> tank(GameTestHelper helper, int x, ChemicalType family) {
        var barrel = helper.getLevel().getBlockEntity(helper.absolutePos(new BlockPos(x, 1, 2)));
        return (ChangingChemicalTank<?, ?>) switch (family) {
            case GAS -> barrel.getCapability(Capabilities.GAS_HANDLER).orElseThrow(IllegalStateException::new);
            case INFUSION -> barrel.getCapability(Capabilities.INFUSION_HANDLER).orElseThrow(IllegalStateException::new);
            case PIGMENT -> barrel.getCapability(Capabilities.PIGMENT_HANDLER).orElseThrow(IllegalStateException::new);
            case SLURRY -> barrel.getCapability(Capabilities.SLURRY_HANDLER).orElseThrow(IllegalStateException::new);
        };
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void seed(ChangingChemicalTank tank, ChemicalStack stack) {
        tank.setChemicalInTank(0, stack);
    }

    private record Fixture(GameTestHelper helper, Holder<Conduit<?, ?>> conduit, ChangingChemicalTank<?, ?> source,
                           ChangingChemicalTank<?, ?> destination, ChemicalResource chemical, long budget, long accepted) {
        ConduitBundleBlockEntity bundle() {
            return (ConduitBundleBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(new BlockPos(2, 1, 2)));
        }

        ChemicalResource pending() {
            var data = bundle().getConduitNode(conduit).getNodeData(MekanismModule.CHEMICAL_NODE_DATA.get());
            return data == null ? ChemicalResource.EMPTY : data.first(Direction.WEST);
        }

        void tick() {
            ChemicalConservationTests.tick(helper, bundle(), conduit);
        }

        void retry() {
            // First retry accepts only a portion. A later update accepts the rest.
            destination.executionLimit = 1;
            tick();
            helper.assertTrue(destination.getChemicalInTank(0).getAmount() == accepted + 1
                && pending().equals(chemical.copyWithAmount(budget - accepted - 1)), "Partial retry must also conserve ownership");
            destination.executionLimit = Long.MAX_VALUE;
            tick();
            helper.assertTrue(source.getChemicalInTank(0).getAmount() == chemical.getAmount() - budget,
                "Retry must not drain another source batch");
            helper.assertTrue(ChemicalResource.fromNative(destination.getChemicalInTank(0)).equals(chemical.copyWithAmount(budget)),
                "Destination must receive exactly one original batch with the correct chemical family and ID");
            helper.assertTrue(pending().isEmpty(), "Successful retry must clear retained ownership");
        }
    }
}
