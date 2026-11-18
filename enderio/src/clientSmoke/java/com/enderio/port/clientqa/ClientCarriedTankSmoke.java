package com.enderio.port.clientqa;

import com.enderio.enderio.content.storage.fluid_tank.FluidTankBlockEntity;
import com.enderio.enderio.init.EIOBlocks;
import com.enderio.enderio.init.EIOFluids;
import com.google.gson.Gson;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;

import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Exercises the packaged item through the normal TCP block-use and placement paths. */
final class ClientCarriedTankSmoke {
    private enum Stage { TITLE, JOIN, ITEM, TRANSFER, PLACED, RESTART, DONE }

    private record Scenario(String name, Item item, FluidStack original, BlockPos receiver, int receiverInitial,
                            int received, BlockPos placed) {
        FluidStack remainder() {
            var fluid = original.copy();
            fluid.shrink(received);
            return fluid.isEmpty() ? FluidStack.EMPTY : fluid;
        }
    }

    private final Minecraft minecraft = Minecraft.getInstance();
    private final String address = System.getProperty("enderio.clientqa.server");
    private final boolean restarted = "verify".equals(System.getProperty("enderio.clientqa.multiplayerPhase"));
    private final long started = System.nanoTime();
    private final Map<String, Boolean> checks = new LinkedHashMap<>();
    private final Map<String, Object> captures = new LinkedHashMap<>();
    private long titleReady;
    private Stage stage = Stage.TITLE;
    private List<Scenario> scenarios;
    private int index;

    ClientCarriedTankSmoke() {
        MinecraftForge.EVENT_BUS.addListener(this::render);
    }

    private void render(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage == Stage.DONE) return;
        try {
            if (minecraft.screen instanceof DisconnectedScreen) throw new AssertionError("Disconnected during " + stage);
            if (System.nanoTime() - started > 165_000_000_000L) throw new AssertionError("Timed out during " + stage + " " + index);
            switch (stage) {
                case TITLE -> {
                    if (!(minecraft.screen instanceof TitleScreen) || minecraft.getOverlay() != null) return;
                    if (titleReady == 0) titleReady = System.nanoTime();
                    if (System.nanoTime() - titleReady < 4_000_000_000L) return;
                    capture("title");
                    stage = Stage.JOIN;
                    ConnectScreen.startConnecting(new TitleScreen(), minecraft, ServerAddress.parseString(address),
                        new ServerData("Ender IO carried tank QA", address, true), false);
                }
                case JOIN -> {
                    if (minecraft.level == null || minecraft.player == null || minecraft.screen != null) return;
                    var connection = minecraft.getConnection().getConnection();
                    check("dedicated_tcp", !connection.isMemoryConnection() && connection.getRemoteAddress() instanceof InetSocketAddress);
                    scenarios = List.of(
                        new Scenario("untouched", EIOBlocks.FLUID_TANK_ITEM.get(), tagged(new FluidStack(Fluids.WATER, 11500), 31),
                            null, 0, 0, new BlockPos(2, 80, 0)),
                        new Scenario("partial", EIOBlocks.PRESSURIZED_FLUID_TANK_ITEM.get(),
                            tagged(new FluidStack(EIOFluids.XP_JUICE.source().get(), 21750), 51), new BlockPos(4, 80, 0), 31000, 1000,
                            new BlockPos(4, 80, 2)),
                        new Scenario("drained", EIOBlocks.FLUID_TANK_ITEM.get(), tagged(new FluidStack(Fluids.WATER, 11500), 31),
                            new BlockPos(0, 80, 0), 0, 11500, new BlockPos(0, 80, 2)));
                    signal("connected");
                    stage = restarted ? Stage.RESTART : Stage.ITEM;
                }
                case ITEM -> {
                    var scenario = scenarios.get(index);
                    var stack = minecraft.player.getMainHandItem();
                    if (!stack.is(scenario.item()) || minecraft.player.distanceToSqr(scenario.placed().getX() + 0.5, 80, 3.5) > 0.15) return;
                    var before = stack.save(new net.minecraft.nbt.CompoundTag());
                    var handler = stack.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM).orElseThrow(IllegalStateException::new);
                    check(scenario.name() + "_old_fluid_visible_in_item", handler.getFluidInTank(0).isFluidStackIdentical(scenario.original()));
                    check(scenario.name() + "_simulation_preserves_item", handler.drain(250, FluidAction.SIMULATE).getAmount() == 250
                        && before.equals(stack.save(new net.minecraft.nbt.CompoundTag())));
                    if (scenario.receiver() == null) {
                        capture("legacy-item-in-hand");
                        place(scenario);
                    } else {
                        var receiver = scenario.receiver();
                        minecraft.gameMode.useItemOn(minecraft.player, InteractionHand.MAIN_HAND,
                            new BlockHitResult(Vec3.atCenterOf(receiver).add(0, 0, 0.5), Direction.SOUTH, receiver, false));
                        stage = Stage.TRANSFER;
                    }
                }
                case TRANSFER -> {
                    var scenario = scenarios.get(index);
                    var handler = minecraft.player.getMainHandItem().getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM).orElse(null);
                    if (handler == null || !handler.getFluidInTank(0).isFluidStackIdentical(scenario.remainder())
                        || !receiverMatches(scenario)) return;
                    check(scenario.name() + "_item_and_receiver_conserve_fluid", true);
                    place(scenario);
                }
                case PLACED -> {
                    var scenario = scenarios.get(index);
                    if (!matches(scenario.placed(), scenario.remainder()) || !minecraft.player.getMainHandItem().isEmpty()) return;
                    check(scenario.name() + "_placed_exact_contents", true);
                    signal(scenario.name());
                    if (++index == scenarios.size()) {
                        capture("after-placement");
                        complete(true, "");
                    } else {
                        stage = Stage.ITEM;
                    }
                }
                case RESTART -> {
                    if (scenarios.stream().anyMatch(scenario -> !matches(scenario.placed(), scenario.remainder()) || !receiverMatches(scenario))) return;
                    check("fresh_connection_sees_all_persisted_tank_contents", true);
                    capture("after-restart");
                    complete(true, "");
                }
                default -> { }
            }
        } catch (Throwable failure) {
            failure.printStackTrace();
            complete(false, failure.toString());
        }
    }

    private void place(Scenario scenario) throws Exception {
        var pos = scenario.placed();
        var floor = pos.below();
        var result = minecraft.gameMode.useItemOn(minecraft.player, InteractionHand.MAIN_HAND,
            new BlockHitResult(Vec3.atBottomCenterOf(pos), Direction.UP, floor, false));
        System.out.println("QA carried tank placement " + scenario.name() + " returned " + result);
        signal(scenario.name() + "_placement_requested");
        stage = Stage.PLACED;
    }

    private boolean receiverMatches(Scenario scenario) {
        if (scenario.receiver() == null) return true;
        var fluid = scenario.original().copy();
        fluid.setAmount(scenario.receiverInitial() + scenario.received());
        return matches(scenario.receiver(), fluid);
    }

    private boolean matches(BlockPos pos, FluidStack fluid) {
        return minecraft.level.getBlockEntity(pos) instanceof FluidTankBlockEntity tank
            && tank.getFluidTank().getFluid().isFluidStackIdentical(fluid);
    }

    private static FluidStack tagged(FluidStack fluid, int value) {
        fluid.getOrCreateTag().putInt("PortLegacyCarried", value);
        return fluid;
    }

    private void check(String name, boolean passed) {
        checks.put(name, passed);
        if (!passed) throw new AssertionError(name);
    }

    private void capture(String image) throws Exception {
        var result = ClientSmoke.capture(image + ".png");
        captures.put(image, result);
        check(image + "_silent_hidden_render", Boolean.TRUE.equals(result.get("passed")));
    }

    private void signal(String name) throws Exception {
        var dir = Path.of(System.getProperty("enderio.clientqa.output"), "signals");
        Files.createDirectories(dir);
        var tmp = dir.resolve(name + ".tmp");
        Files.writeString(tmp, new Gson().toJson(Map.of("signal", name)));
        Files.move(tmp, dir.resolve(name + ".json"), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }

    private void complete(boolean passed, String error) {
        stage = Stage.DONE;
        ClientSmoke.complete("client_carried_tanks", new LinkedHashMap<>(Map.of("passed", passed, "error", error,
            "checks", checks, "captures", captures)));
    }
}
