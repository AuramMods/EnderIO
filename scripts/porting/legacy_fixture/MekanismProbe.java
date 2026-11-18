package porting.mekanismprobe;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import mekanism.api.MekanismAPI;
import mekanism.api.RelativeSide;
import mekanism.api.chemical.IChemicalTank;
import mekanism.api.chemical.gas.GasStack;
import mekanism.api.chemical.infuse.InfusionStack;
import mekanism.api.chemical.pigment.PigmentStack;
import mekanism.api.chemical.slurry.SlurryStack;
import mekanism.api.heat.IHeatHandler;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.lib.transmitter.TransmissionType;
import mekanism.common.tile.TileEntityChemicalTank;
import mekanism.common.tile.component.config.DataType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

@Mod("port_mekanism_probe")
public class MekanismProbe {
    private record Route(int z, TransmissionType family, String chemical) {}

    private static final int BATCH = 1450;
    private static final List<Route> TRANSFERS = List.of(
        new Route(0, TransmissionType.GAS, "oxygen"), new Route(3, TransmissionType.INFUSION, "redstone"),
        new Route(6, TransmissionType.PIGMENT, "red"), new Route(9, TransmissionType.SLURRY, "dirty_iron"));
    private static final Route LOCKED = new Route(15, TransmissionType.GAS, "oxygen");
    private static final Route HYDROGEN = new Route(15, TransmissionType.GAS, "hydrogen");
    private double destinationTemperature;

    public MekanismProbe() {
        MinecraftForge.EVENT_BUS.addListener(this::register);
    }

    private void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("port_mekanism_native").requires(source -> source.hasPermission(4))
            .then(Commands.literal("configure").executes(context -> configure(context.getSource())))
            .then(Commands.literal("batch").executes(context -> batch(context.getSource())))
            .then(Commands.literal("check").then(Commands.argument("total", IntegerArgumentType.integer(1))
                .executes(context -> check(context.getSource(), IntegerArgumentType.getInteger(context, "total")))))
            .then(Commands.literal("seed_hydrogen").executes(context -> {
                seed(context.getSource().getLevel(), HYDROGEN);
                return success(context.getSource(), "Hydrogen batch seeded");
            }))
            .then(Commands.literal("check_hydrogen").executes(context -> checkHydrogen(context.getSource()))));
    }

    private int configure(CommandSourceStack source) {
        var level = source.getLevel();
        for (var route : TRANSFERS) configureRoute(level, route);
        configureRoute(level, LOCKED);
        seed(level, LOCKED);
        return success(source, "Native Mekanism endpoints configured");
    }

    private static void configureRoute(ServerLevel level, Route route) {
        var input = getTank(level, 1153, route);
        var output = getTank(level, 1150, route);
        input.getConfig().getConfig(route.family()).setDataType(DataType.INPUT, RelativeSide.values());
        output.getConfig().getConfig(route.family()).setDataType(DataType.OUTPUT, RelativeSide.values());
        input.markForSave();
        output.markForSave();
    }

    private int batch(CommandSourceStack source) {
        var level = source.getLevel();
        for (var route : TRANSFERS) {
            if (!resource(getTank(level, 1150, route), route.family()).isEmpty()) {
                return failure(source, route.family() + " source still contains material before a fresh batch");
            }
        }
        for (var route : TRANSFERS) seed(level, route);
        var sourceHeat = heat(level, 1150, Direction.EAST);
        destinationTemperature = heat(level, 1153, Direction.WEST).getTotalTemperature();
        sourceHeat.handleHeat(sourceHeat.getTotalHeatCapacity() * 3000);
        return success(source, "Mekanism batch and heat pulse seeded");
    }

    private int check(CommandSourceStack source, int total) {
        var level = source.getLevel();
        for (var route : TRANSFERS) {
            var input = resource(getTank(level, 1153, route), route.family());
            var output = resource(getTank(level, 1150, route), route.family());
            var stack = input.getStack();
            if (!output.isEmpty() || input.getStored() != total ||
                !ResourceLocation.fromNamespaceAndPath("mekanism", route.chemical()).equals(stack.getType().getRegistryName())) {
                return failure(source, route.family() + " source=" + output.getStored() + ", destination=" + input.getStored());
            }
        }
        var refused = getTank(level, 1150, LOCKED).getGasTank().getStack();
        if (refused.getAmount() != BATCH ||
            !ResourceLocation.parse("mekanism:oxygen").equals(refused.getType().getRegistryName()) ||
            !getTank(level, 1153, LOCKED).getGasTank().isEmpty()) {
            return failure(source, "Hydrogen lock did not reject oxygen");
        }
        double destination = heat(level, 1153, Direction.WEST).getTotalTemperature();
        if (destination <= destinationTemperature + 1) {
            return failure(source, "Heat destination did not warm from " + destinationTemperature + " K, now " + destination + " K");
        }
        return success(source, "Native Mekanism chemical totals, lock and heat transfer verified");
    }

    private static int checkHydrogen(CommandSourceStack source) {
        var level = source.getLevel();
        var output = getTank(level, 1150, HYDROGEN).getGasTank();
        var input = getTank(level, 1153, HYDROGEN).getGasTank();
        if (!output.isEmpty() || input.getStored() != BATCH ||
            !ResourceLocation.parse("mekanism:hydrogen").equals(input.getStack().getType().getRegistryName())) {
            return failure(source, "Hydrogen lock has not passed its matching batch");
        }
        return success(source, "Native Mekanism hydrogen lock passed its matching batch");
    }

    private static void seed(ServerLevel level, Route route) {
        var tank = getTank(level, 1150, route);
        var id = ResourceLocation.fromNamespaceAndPath("mekanism", route.chemical());
        switch (route.family()) {
            case GAS -> tank.getGasTank().setStack(new GasStack(MekanismAPI.gasRegistry().getValue(id), BATCH));
            case INFUSION -> tank.getInfusionTank().setStack(new InfusionStack(MekanismAPI.infuseTypeRegistry().getValue(id), BATCH));
            case PIGMENT -> tank.getPigmentTank().setStack(new PigmentStack(MekanismAPI.pigmentRegistry().getValue(id), BATCH));
            case SLURRY -> tank.getSlurryTank().setStack(new SlurryStack(MekanismAPI.slurryRegistry().getValue(id), BATCH));
            default -> throw new IllegalArgumentException("Unexpected chemical family " + route.family());
        }
        tank.markForSave();
    }

    private static IChemicalTank<?, ?> resource(TileEntityChemicalTank tank, TransmissionType family) {
        return switch (family) {
            case GAS -> tank.getGasTank();
            case INFUSION -> tank.getInfusionTank();
            case PIGMENT -> tank.getPigmentTank();
            case SLURRY -> tank.getSlurryTank();
            default -> throw new IllegalArgumentException("Unexpected chemical family " + family);
        };
    }

    private static TileEntityChemicalTank getTank(ServerLevel level, int x, Route route) {
        return (TileEntityChemicalTank) level.getBlockEntity(new BlockPos(x, 80, route.z()));
    }

    private static IHeatHandler heat(ServerLevel level, int x, Direction towardRoute) {
        return level.getBlockEntity(new BlockPos(x, 80, 12))
            .getCapability(Capabilities.HEAT_HANDLER, towardRoute).orElseThrow(IllegalStateException::new);
    }

    private static int success(CommandSourceStack source, String message) {
        source.sendSuccess(() -> Component.literal(message), false);
        return 1;
    }

    private static int failure(CommandSourceStack source, String message) {
        source.sendFailure(Component.literal(message));
        return 0;
    }
}
