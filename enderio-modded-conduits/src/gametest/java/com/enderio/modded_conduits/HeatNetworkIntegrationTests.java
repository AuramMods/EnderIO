package com.enderio.modded_conduits;

import com.enderio.enderio.api.EnderIORegistries;
import com.enderio.enderio.api.conduits.Conduit;
import com.enderio.enderio.api.io.RedstoneControl;
import com.enderio.enderio.content.conduits.bundle.ConduitBundleBlockEntity;
import com.enderio.modded_conduits.common.modules.mekanism.MekanismModule;
import com.enderio.modded_conduits.common.modules.mekanism.heat.HeatConduitConnectionConfig;
import com.enderio.modded_conduits.common.modules.mekanism.heat.HeatTicker;
import mekanism.common.capabilities.heat.BasicHeatCapacitor;
import mekanism.common.capabilities.heat.ITileHeatHandler;
import mekanism.common.registries.MekanismBlocks;
import mekanism.common.tile.transmitter.TileEntityThermodynamicConductor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.concurrent.atomic.AtomicReference;

@GameTestHolder("enderio_modded_conduits_tests")
@PrefixGameTestTemplate(false)
public class HeatNetworkIntegrationTests {
    private static final BlockPos HEATER = new BlockPos(1, 1, 2);
    private static final BlockPos EAST = new BlockPos(3, 1, 2);
    private static final BlockPos NORTH = new BlockPos(2, 1, 1);
    private static final BlockPos REMOTE = new BlockPos(5, 1, 2);
    private static final double HOT = 650;
    private static final double COOL = 300;

    @GameTest(template = "network", timeoutTicks = 80)
    public static void unequalHeatCapacitiesReachEquilibriumWithoutOvershoot(GameTestHelper helper) {
        var conduit = heatConduit(helper);
        var bundle = new AtomicReference<ConduitBundleBlockEntity>();
        helper.startSequence()
            .thenExecute(() -> {
                placeHeater(helper);
                placeConductor(helper, EAST);
                bundle.set(placeSource(helper, conduit));
                input(bundle.get(), conduit, Direction.EAST);
            })
            .thenIdle(5)
            .thenExecute(() -> {
                var source = heater(helper);
                var destination = conductor(helper, EAST).getTransmitter().buffer;
                setTemperature(source, HOT);
                setTemperature(destination, COOL);
                helper.assertTrue(source.getHeatCapacity() > destination.getHeatCapacity(), "Fixture requires unequal native heat capacities");
                double before = source.getHeat() + destination.getHeat();
                double equilibrium = before / (source.getHeatCapacity() + destination.getHeatCapacity());
                step(helper, bundle.get(), conduit, source, destination);
                near(helper, source.getHeat() + destination.getHeat(), before, "Conduit heat must be conserved");
                near(helper, destination.getTemperature(), equilibrium, "The cooler conductor overshot equilibrium");
                near(helper, source.getTemperature(), equilibrium, "Both endpoints should settle at the same temperature");
                step(helper, bundle.get(), conduit, source, destination);
                near(helper, destination.getTemperature(), equilibrium, "A second transfer moved an equilibrated conductor");
            })
            .thenSucceed();
    }

    @GameTest(template = "network", timeoutTicks = 80)
    public static void heatBranchesUseTheSourceBalanceFromEarlierTransfers(GameTestHelper helper) {
        var conduit = heatConduit(helper);
        var bundle = new AtomicReference<ConduitBundleBlockEntity>();
        helper.startSequence()
            .thenExecute(() -> {
                placeHeater(helper);
                placeConductor(helper, EAST);
                placeConductor(helper, NORTH);
                bundle.set(placeSource(helper, conduit));
                input(bundle.get(), conduit, Direction.EAST);
                input(bundle.get(), conduit, Direction.NORTH);
            })
            .thenIdle(5)
            .thenExecute(() -> {
                var source = heater(helper);
                var east = conductor(helper, EAST).getTransmitter().buffer;
                var north = conductor(helper, NORTH).getTransmitter().buffer;
                setTemperature(source, HOT);
                setTemperature(east, COOL);
                setTemperature(north, COOL);
                double before = source.getHeat() + east.getHeat() + north.getHeat();
                double first = equilibrium(source, HOT, east);
                double second = equilibrium(source, first, north);
                step(helper, bundle.get(), conduit, source, east, north);
                near(helper, source.getHeat() + east.getHeat() + north.getHeat(), before, "Both branches must conserve heat");
                near(helper, Math.max(east.getTemperature(), north.getTemperature()), first, "The first branch should use the initial source temperature");
                near(helper, Math.min(east.getTemperature(), north.getTemperature()), second, "The second branch should use the reduced source temperature");
                near(helper, source.getTemperature(), second, "Native deferred updates must match the final source balance");
            })
            .thenSucceed();
    }

    @GameTest(template = "network", timeoutTicks = 150)
    public static void heatStopsAtAPhysicalBreakAndReturnsAfterPlacement(GameTestHelper helper) {
        var conduit = heatConduit(helper);
        var ambient = new AtomicReference<Double>();
        helper.startSequence()
            .thenExecute(() -> {
                placeHeater(helper);
                placeConductor(helper, REMOTE);
                placeSource(helper, conduit);
                ConduitNetworkIntegrationTests.place(helper, conduit, 3);
                input(ConduitNetworkIntegrationTests.place(helper, conduit, 4), conduit, Direction.EAST);
            })
            .thenIdle(5)
            .thenExecute(() -> ambient.set(resetRoute(helper)))
            .thenIdle(20)
            .thenExecute(() -> {
                helper.assertTrue(conductor(helper, REMOTE).getTransmitter().buffer.getTemperature() > ambient.get() + 1,
                    "The connected native route did not warm its destination");
                helper.setBlock(EAST, Blocks.AIR);
                ambient.set(resetRoute(helper));
            })
            .thenIdle(20)
            .thenExecute(() -> {
                near(helper, conductor(helper, REMOTE).getTransmitter().buffer.getTemperature(), ambient.get(),
                    "A physically split route still transferred heat");
                ConduitNetworkIntegrationTests.place(helper, conduit, 3);
                ambient.set(resetRoute(helper));
            })
            .thenIdle(20)
            .thenExecute(() -> helper.assertTrue(conductor(helper, REMOTE).getTransmitter().buffer.getTemperature() > ambient.get() + 1,
                "The restored route did not heat the original destination"))
            .thenSucceed();
    }

    private static Holder<Conduit<?, ?>> heatConduit(GameTestHelper helper) {
        return helper.getLevel().registryAccess().registryOrThrow(EnderIORegistries.Keys.CONDUIT).getHolderOrThrow(MekanismModule.HEAT);
    }

    private static ConduitBundleBlockEntity placeSource(GameTestHelper helper, Holder<Conduit<?, ?>> conduit) {
        var bundle = ConduitNetworkIntegrationTests.place(helper, conduit, 2);
        bundle.setConnectionConfig(conduit, Direction.WEST,
            new HeatConduitConnectionConfig(false, true, RedstoneControl.ALWAYS_ACTIVE, DyeColor.RED));
        return bundle;
    }

    private static void input(ConduitBundleBlockEntity bundle, Holder<Conduit<?, ?>> conduit, Direction side) {
        bundle.setConnectionConfig(conduit, side, new HeatConduitConnectionConfig(true, false, RedstoneControl.NEVER_ACTIVE, DyeColor.RED));
    }

    private static void placeHeater(GameTestHelper helper) {
        helper.setBlock(HEATER, MekanismBlocks.RESISTIVE_HEATER.getBlock());
    }

    private static void placeConductor(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, MekanismBlocks.BASIC_THERMODYNAMIC_CONDUCTOR.getBlock());
    }

    private static BasicHeatCapacitor heater(GameTestHelper helper) {
        var tile = (ITileHeatHandler) helper.getBlockEntity(HEATER);
        return (BasicHeatCapacitor) tile.getHeatCapacitor(0, Direction.EAST);
    }

    private static TileEntityThermodynamicConductor conductor(GameTestHelper helper, BlockPos pos) {
        return (TileEntityThermodynamicConductor) helper.getBlockEntity(pos);
    }

    private static void step(GameTestHelper helper, ConduitBundleBlockEntity bundle, Holder<Conduit<?, ?>> conduit,
            BasicHeatCapacitor... capacitors) {
        var level = helper.getLevel();
        var network = bundle.getConduitNode(conduit).getNetwork();
        network.ensureCachesReady();
        HeatTicker.INSTANCE.tick(level, network, Math.floorMod(-level.getGameTime(), 5));
        for (var capacitor : capacitors) capacitor.update();
    }

    private static double resetRoute(GameTestHelper helper) {
        var destination = conductor(helper, REMOTE).getTransmitter();
        double ambient = destination.getAmbientTemperature(Direction.WEST);
        setTemperature(heater(helper), HOT);
        setTemperature(destination.buffer, ambient);
        return ambient;
    }

    private static void setTemperature(BasicHeatCapacitor capacitor, double temperature) {
        capacitor.update();
        capacitor.setHeat(temperature * capacitor.getHeatCapacity());
    }

    private static double equilibrium(BasicHeatCapacitor source, double sourceTemperature, BasicHeatCapacitor destination) {
        return (sourceTemperature * source.getHeatCapacity() + destination.getHeat())
            / (source.getHeatCapacity() + destination.getHeatCapacity());
    }

    private static void near(GameTestHelper helper, double actual, double expected, String message) {
        helper.assertTrue(Math.abs(actual - expected) < 0.01, message + ": expected " + expected + ", observed " + actual);
    }
}
