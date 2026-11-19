package porting.mekanismprobe;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import mekanism.api.MekanismAPI;
import mekanism.api.RelativeSide;
import mekanism.api.chemical.Chemical;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.chemical.IChemicalHandler;
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
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

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
            .then(Commands.literal("samples").executes(context -> samples(context.getSource())))
            .then(Commands.literal("filter_oxygen").executes(context -> seedFilter(context.getSource(), "oxygen")))
            .then(Commands.literal("filter_hydrogen").executes(context -> seedFilter(context.getSource(), "hydrogen")))
            .then(Commands.literal("filter_blocked").executes(context -> checkFilter(context.getSource(), false)))
            .then(Commands.literal("filter_delivered").executes(context -> checkFilter(context.getSource(), true)))
            .then(Commands.literal("heat_pulse").executes(context -> pulse(context.getSource())))
            .then(Commands.literal("heat_blocked").executes(context -> checkHeat(context.getSource(), false)))
            .then(Commands.literal("heat_delivered").executes(context -> checkHeat(context.getSource(), true)))
            .then(Commands.literal("seed_hydrogen").executes(context -> {
                seed(context.getSource().getLevel(), HYDROGEN);
                return success(context.getSource(), "Hydrogen batch seeded");
            }))
            .then(Commands.literal("check_hydrogen").executes(context -> checkHydrogen(context.getSource()))));
    }

    private static int samples(CommandSourceStack source) {
        var player = source.getServer().getPlayerList().getPlayerByName("EnderIOQA");
        if (player == null) return failure(source, "No fixture player for chemical samples");
        var tank = ForgeRegistries.ITEMS.getValue(ResourceLocation.parse("mekanism:basic_chemical_tank"));
        for (int i = 0; i < TRANSFERS.size(); i++) {
            var route = TRANSFERS.get(i);
            var id = ResourceLocation.fromNamespaceAndPath("mekanism", route.chemical());
            var stack = new ItemStack(tank);
            boolean filled = switch (route.family()) {
                case GAS -> fill(stack.getCapability(Capabilities.GAS_HANDLER).orElseThrow(IllegalStateException::new),
                    new GasStack(MekanismAPI.gasRegistry().getValue(id), BATCH));
                case INFUSION -> fill(stack.getCapability(Capabilities.INFUSION_HANDLER).orElseThrow(IllegalStateException::new),
                    new InfusionStack(MekanismAPI.infuseTypeRegistry().getValue(id), BATCH));
                case PIGMENT -> fill(stack.getCapability(Capabilities.PIGMENT_HANDLER).orElseThrow(IllegalStateException::new),
                    new PigmentStack(MekanismAPI.pigmentRegistry().getValue(id), BATCH));
                case SLURRY -> fill(stack.getCapability(Capabilities.SLURRY_HANDLER).orElseThrow(IllegalStateException::new),
                    new SlurryStack(MekanismAPI.slurryRegistry().getValue(id), BATCH));
                default -> throw new IllegalArgumentException("Unexpected chemical family " + route.family());
            };
            if (!filled) return failure(source, "Sample tank rejected " + route.chemical());
            player.getInventory().setItem(i + 1, stack);
        }
        return success(source, "Four native chemical sample tanks created");
    }

    private static <C extends Chemical<C>, S extends ChemicalStack<C>> boolean fill(IChemicalHandler<C, S> handler, S sample) {
        handler.setChemicalInTank(0, sample);
        var held = handler.getChemicalInTank(0);
        return held.getType() == sample.getType() && held.getAmount() == BATCH;
    }

    private static int seedFilter(CommandSourceStack source, String chemical) {
        var level = source.getLevel();
        var route = new Route(0, TransmissionType.GAS, chemical);
        var destination = getTank(level, 1153, route);
        destination.getGasTank().setStack(GasStack.EMPTY);
        destination.markForSave();
        seed(level, route);
        return success(source, "Native chemical filter batch seeded");
    }

    private static int checkFilter(CommandSourceStack source, boolean delivered) {
        var level = source.getLevel();
        var route = TRANSFERS.get(0);
        var upstream = getTank(level, 1150, route).getGasTank().getStack();
        var downstream = getTank(level, 1153, route).getGasTank().getStack();
        var held = delivered ? downstream : upstream;
        var expected = ResourceLocation.parse(delivered ? "mekanism:hydrogen" : "mekanism:oxygen");
        if (held.getAmount() != BATCH || !expected.equals(held.getType().getRegistryName())
            || !(delivered ? upstream : downstream).isEmpty()) {
            return failure(source, "Unexpected gas balances: " + upstream.getAmount() + "/" + downstream.getAmount());
        }
        return success(source, "Native chemical filter matched the exact gas balances");
    }

    private int pulse(CommandSourceStack source) {
        var level = source.getLevel();
        var upstream = heat(level, 1150, Direction.EAST);
        var downstream = heat(level, 1153, Direction.WEST);
        destinationTemperature = downstream.getTotalTemperature();
        upstream.handleHeat(upstream.getTotalHeatCapacity() * 3000);
        return success(source, "Native heat pulse prepared");
    }

    private int checkHeat(CommandSourceStack source, boolean delivered) {
        var change = heat(source.getLevel(), 1153, Direction.WEST).getTotalTemperature() - destinationTemperature;
        if (delivered ? change <= 1 : change > 0.1) return failure(source, "Unexpected destination heat change " + change + " K");
        return success(source, "Native heat destination matched the selected control");
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
