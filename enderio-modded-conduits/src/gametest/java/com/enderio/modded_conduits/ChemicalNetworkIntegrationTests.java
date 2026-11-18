package com.enderio.modded_conduits;

import com.enderio.modded_conduits.common.modules.mekanism.chemical.ChemicalConduit;
import com.enderio.modded_conduits.common.modules.mekanism.chemical.ChemicalConduitConnectionConfig;
import com.enderio.modded_conduits.common.modules.mekanism.chemical.ChemicalHandlerAdapter;
import com.enderio.modded_conduits.common.modules.mekanism.chemical.ChemicalResource;
import com.enderio.modded_conduits.common.modules.mekanism.chemical.ChemicalTicker;

import com.enderio.enderio.api.EnderIORegistries;
import com.enderio.enderio.api.io.RedstoneControl;
import com.enderio.enderio.content.conduits.bundle.ConduitBundleBlockEntity;
import com.enderio.modded_conduits.common.modules.mekanism.MekanismModule;
import mekanism.api.Action;
import mekanism.api.RelativeSide;
import mekanism.api.chemical.ChemicalType;
import mekanism.common.lib.transmitter.TransmissionType;
import mekanism.common.registries.MekanismBlocks;
import mekanism.common.tile.TileEntityChemicalTank;
import mekanism.common.tile.component.config.DataType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@GameTestHolder("enderio_modded_conduits_tests")
@PrefixGameTestTemplate(false)
public class ChemicalNetworkIntegrationTests {
    @GameTest(template = "network", timeoutTicks = 100)
    public static void gasTransfersConfiguredPerTickRateThroughNativeTanks(GameTestHelper helper) {
        transfer(helper, ChemicalType.GAS, "oxygen", TransmissionType.GAS);
    }
    @GameTest(template = "network", timeoutTicks = 100)
    public static void infusionTransfersConfiguredPerTickRateThroughNativeTanks(GameTestHelper helper) {
        transfer(helper, ChemicalType.INFUSION, "redstone", TransmissionType.INFUSION);
    }
    @GameTest(template = "network", timeoutTicks = 100)
    public static void pigmentTransfersConfiguredPerTickRateThroughNativeTanks(GameTestHelper helper) {
        transfer(helper, ChemicalType.PIGMENT, "red", TransmissionType.PIGMENT);
    }
    @GameTest(template = "network", timeoutTicks = 100)
    public static void slurryTransfersConfiguredPerTickRateThroughNativeTanks(GameTestHelper helper) {
        transfer(helper, ChemicalType.SLURRY, "dirty_iron", TransmissionType.SLURRY);
    }

    @GameTest(template = "network", timeoutTicks = 100)
    public static void largeDatapackTransferRateCannotOverflowIntoAnEmptyBudget(GameTestHelper helper) {
        transfer(helper, ChemicalType.GAS, "oxygen", TransmissionType.GAS,
            net.minecraft.resources.ResourceKey.create(EnderIORegistries.Keys.CONDUIT,
                ResourceLocation.fromNamespaceAndPath("enderio_modded_conduits_tests", "large_chemical_rate")), 10000);
    }

    private static void transfer(GameTestHelper helper, ChemicalType family, String id, TransmissionType transmission) {
        transfer(helper, family, id, transmission, MekanismModule.CHEMICAL, 3750);
    }

    private static void transfer(GameTestHelper helper, ChemicalType family, String id, TransmissionType transmission,
            net.minecraft.resources.ResourceKey<com.enderio.enderio.api.conduits.Conduit<?, ?>> key, long expected) {
        var conduit = helper.getLevel().registryAccess().registryOrThrow(EnderIORegistries.Keys.CONDUIT)
            .getHolderOrThrow(key);
        var source = new AtomicReference<ChemicalHandlerAdapter>();
        var sourceTank = new AtomicReference<TileEntityChemicalTank>();
        var destination = new AtomicReference<ChemicalHandlerAdapter>();
        var bundle = new AtomicReference<ConduitBundleBlockEntity>();
        helper.startSequence()
            .thenExecute(() -> {
                helper.setBlock(1, 1, 2, MekanismBlocks.ULTIMATE_CHEMICAL_TANK.getBlock());
                helper.setBlock(3, 1, 2, MekanismBlocks.ULTIMATE_CHEMICAL_TANK.getBlock());
                var sourceTile = (TileEntityChemicalTank) helper.getLevel().getBlockEntity(helper.absolutePos(new BlockPos(1, 1, 2)));
                var destinationTile = (TileEntityChemicalTank) helper.getLevel().getBlockEntity(helper.absolutePos(new BlockPos(3, 1, 2)));
                sourceTank.set(sourceTile);
                sourceTile.getConfig().getConfig(transmission).setDataType(DataType.OUTPUT, RelativeSide.values());
                destinationTile.getConfig().getConfig(transmission).setDataType(DataType.INPUT, RelativeSide.values());
                source.set(ChemicalHandlerAdapter.find(cap -> sourceTile.getCapability(cap).orElse(null)));
                destination.set(ChemicalHandlerAdapter.find(cap -> destinationTile.getCapability(cap).orElse(null)));
                helper.assertTrue(source.get() != null && destination.get() != null, "Native chemical tanks expose no chemical capabilities");
                bundle.set(ConduitNetworkIntegrationTests.place(helper, conduit, 2));
                bundle.get().setConnectionConfig(conduit, Direction.WEST,
                    new ChemicalConduitConnectionConfig(false, DyeColor.GREEN, true, DyeColor.GREEN, RedstoneControl.ALWAYS_ACTIVE, DyeColor.RED));
                bundle.get().setConnectionConfig(conduit, Direction.EAST,
                    new ChemicalConduitConnectionConfig(true, DyeColor.GREEN, false, DyeColor.GREEN, RedstoneControl.NEVER_ACTIVE, DyeColor.RED));
            })
            .thenIdle(3)
            .thenExecute(() -> {
                var offered = ChemicalResource.fromRegistry(family, new ResourceLocation("mekanism", id), 10000);
                // Seed the native tank internally; its configured output face correctly rejects external insertion.
                switch (family) {
                    case GAS -> sourceTank.get().getGasTank().setStack((mekanism.api.chemical.gas.GasStack) offered.nativeCopy());
                    case INFUSION -> sourceTank.get().getInfusionTank().setStack((mekanism.api.chemical.infuse.InfusionStack) offered.nativeCopy());
                    case PIGMENT -> sourceTank.get().getPigmentTank().setStack((mekanism.api.chemical.pigment.PigmentStack) offered.nativeCopy());
                    case SLURRY -> sourceTank.get().getSlurryTank().setStack((mekanism.api.chemical.slurry.SlurryStack) offered.nativeCopy());
                }
                var network = bundle.get().getConduitNode(conduit).getNetwork();
                network.ensureCachesReady();
                int offset = (int) Math.floorMod(-helper.getLevel().getGameTime(), 20);
                ChemicalTicker.INSTANCE.tick(helper.getLevel(), network, offset);
                long received = amount(destination.get(), offered);
                long retained = amount(source.get(), offered);
                helper.assertTrue(received == expected, "Expected " + expected + " " + family + " per network tick, received " + received);
                helper.assertTrue(retained + received == offered.getAmount(), "Native chemical transfer lost or duplicated contents");
            })
            .thenSucceed();
    }
    private static long amount(ChemicalHandlerAdapter handler, ChemicalResource resource) {
        long total = 0;
        for (int i = 0; i < handler.getChemicalTanks(); i++) {
            var stored = handler.getChemicalInTank(i);
            if (ChemicalResource.isSameChemical(stored, resource)) total += stored.getAmount();
        }
        return total;
    }

    @GameTest(template = "network", timeoutTicks = 100)
    public static void partiallyFullBranchCannotExceedTheChemicalExtractionBudget(GameTestHelper helper) {
        var conduit = helper.getLevel().registryAccess().registryOrThrow(EnderIORegistries.Keys.CONDUIT)
            .getHolderOrThrow(MekanismModule.CHEMICAL);
        var bundle = new AtomicReference<ConduitBundleBlockEntity>();
        helper.startSequence()
            .thenExecute(() -> {
                for (var pos : List.of(new BlockPos(1, 1, 2), new BlockPos(3, 1, 2), new BlockPos(2, 1, 1))) {
                    helper.setBlock(pos, MekanismBlocks.ULTIMATE_CHEMICAL_TANK.getBlock());
                    var tank = (TileEntityChemicalTank) helper.getLevel().getBlockEntity(helper.absolutePos(pos));
                    tank.getConfig().getConfig(TransmissionType.GAS).setDataType(
                        pos.getX() == 1 ? DataType.OUTPUT : DataType.INPUT, RelativeSide.values());
                }
                bundle.set(ConduitNetworkIntegrationTests.place(helper, conduit, 2));
                bundle.get().setConnectionConfig(conduit, Direction.WEST,
                    new ChemicalConduitConnectionConfig(false, DyeColor.GREEN, true, DyeColor.GREEN, RedstoneControl.ALWAYS_ACTIVE, DyeColor.RED));
                for (var side : List.of(Direction.EAST, Direction.NORTH)) bundle.get().setConnectionConfig(conduit, side,
                    new ChemicalConduitConnectionConfig(true, DyeColor.GREEN, false, DyeColor.GREEN, RedstoneControl.NEVER_ACTIVE, DyeColor.RED));
            })
            .thenIdle(3)
            .thenExecute(() -> {
                var source = (TileEntityChemicalTank) helper.getLevel().getBlockEntity(helper.absolutePos(new BlockPos(1, 1, 2)));
                var network = bundle.get().getConduitNode(conduit).getNetwork();
                network.ensureCachesReady();
                var paths = network.insertConnectionsFrom(network.extractConnections(DyeColor.GREEN).iterator().next());
                helper.assertTrue(paths.size() == 2, "Expected two real chemical destination paths");
                var first = (TileEntityChemicalTank) helper.getLevel().getBlockEntity(paths.get(0).end().connectedBlockPos());
                var second = (TileEntityChemicalTank) helper.getLevel().getBlockEntity(paths.get(1).end().connectedBlockPos());
                var gas = mekanism.common.registries.MekanismGases.OXYGEN.get();
                source.getGasTank().setStack(new mekanism.api.chemical.gas.GasStack(gas, 10000));
                long prefilled = first.getGasTank().getCapacity() - 1000;
                first.getGasTank().setStack(new mekanism.api.chemical.gas.GasStack(gas, prefilled));
                ChemicalTicker.INSTANCE.tick(helper.getLevel(), network, (int) Math.floorMod(-helper.getLevel().getGameTime(), 20));
                long gained = first.getGasTank().getStored() + second.getGasTank().getStored() - prefilled;
                helper.assertTrue(gained == 3750, "Chemical branch exceeded or missed its 3750-unit budget: " + gained);
                helper.assertTrue(first.getGasTank().getStored() == first.getGasTank().getCapacity(), "First native destination was not filled");
                helper.assertTrue(source.getGasTank().getStored() + gained == 10000, "Branch transfer lost or duplicated native gas");
            })
            .thenSucceed();
    }

}
