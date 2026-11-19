package com.enderio.port.clientqa;

import com.enderio.enderio.client.content.machines.gui.screen.WeatherObeliskScreen;
import com.enderio.enderio.init.EIOBlocks;
import com.enderio.enderio.init.EIODataComponents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

final class ClientObeliskCarrySmoke {
    private enum Stage { TITLE, CONNECT, READY, MINING, PICKUP, PLACEMENT, WEATHER, DONE }

    private record Fixture(String name, int x, Supplier<? extends Block> block, int energy, int range, boolean visible, int fluid) {
        BlockPos source() { return new BlockPos(x, 80, 8); }
        BlockPos destination() { return new BlockPos(x, 80, 12); }
    }

    private static final List<Fixture> FIXTURES = List.of(
        new Fixture("inhibitor", 2310, EIOBlocks.INHIBITOR_OBELISK::get, 6101, 5, true, 0),
        new Fixture("aversion", 2316, EIOBlocks.AVERSION_OBELISK::get, 7302, 4, false, 0),
        new Fixture("relocator", 2322, EIOBlocks.RELOCATOR_OBELISK::get, 8503, 0, true, 0),
        new Fixture("attractor", 2328, EIOBlocks.ATTRACTOR_OBELISK::get, 9704, 3, true, 0),
        new Fixture("xp", 2334, EIOBlocks.XP_OBELISK::get, 0, 0, false, 1573),
        new Fixture("weather", 2340, EIOBlocks.WEATHER_OBELISK::get, 0, 0, false, 1237));

    private final Minecraft minecraft = Minecraft.getInstance();
    private final String address = System.getProperty("enderio.clientqa.server");
    private final Path output = Path.of(System.getProperty("enderio.clientqa.output"));
    private final Map<String, Boolean> checks = new LinkedHashMap<>();
    private final Map<String, Object> captures = new LinkedHashMap<>();
    private final long started = System.nanoTime();
    private Stage stage = Stage.TITLE;
    private long titleReady;
    private int index;
    private int readyTick = -1;
    private int miningTick;

    ClientObeliskCarrySmoke() {
        MinecraftForge.EVENT_BUS.addListener(this::render);
    }

    private void render(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage == Stage.DONE) return;
        try {
            if (minecraft.screen instanceof DisconnectedScreen) throw new AssertionError("Disconnected in " + stage);
            if (System.nanoTime() - started > 190_000_000_000L) throw new AssertionError("Timed out in " + stage + " at obelisk " + index);
            if (stage == Stage.TITLE) {
                if (!(minecraft.screen instanceof TitleScreen) || minecraft.getOverlay() != null) return;
                if (titleReady == 0) titleReady = System.nanoTime();
                if (System.nanoTime() - titleReady < 2_000_000_000L) return;
                ConnectScreen.startConnecting(new TitleScreen(), minecraft, ServerAddress.parseString(address),
                    new ServerData("Ender IO obelisk pickup and placement", address, true), false);
                stage = Stage.CONNECT;
                return;
            }
            if (minecraft.level == null || minecraft.player == null) return;
            var current = FIXTURES.get(index);
            switch (stage) {
                case CONNECT -> {
                    if (minecraft.screen != null
                        || minecraft.getToasts().getToast(SystemToast.class, SystemToast.SystemToastIds.UNSECURE_SERVER_WARNING) != null) return;
                    var connection = minecraft.getConnection().getConnection();
                    check("dedicated_survival_tcp", !minecraft.player.isCreative() && !minecraft.player.isSpectator()
                        && minecraft.getSingleplayerServer() == null && !connection.isMemoryConnection()
                        && connection.getRemoteAddress() instanceof InetSocketAddress);
                    signal("connected");
                    stage = Stage.READY;
                }
                case READY -> {
                    if (!marker("server-mine-" + current.name()) || !positioned(current.x(), 11.5)
                        || !minecraft.player.onGround() || !minecraft.level.getBlockState(current.source()).is(current.block().get())) {
                        readyTick = -1;
                        return;
                    }
                    minecraft.player.getInventory().selected = 0;
                    if (!minecraft.player.getMainHandItem().is(Items.DIAMOND_PICKAXE)) return;
                    if (readyTick == -1) readyTick = minecraft.player.tickCount;
                    if (minecraft.player.tickCount - readyTick < 6) return;
                    check(current.name() + "_normal_survival_mining", minecraft.gameMode.startDestroyBlock(current.source(), Direction.SOUTH));
                    miningTick = minecraft.player.tickCount;
                    readyTick = -1;
                    stage = Stage.MINING;
                }
                case MINING -> {
                    if (!minecraft.level.isEmptyBlock(current.source())) {
                        if (miningTick != minecraft.player.tickCount) {
                            miningTick = minecraft.player.tickCount;
                            minecraft.gameMode.continueDestroyBlock(current.source(), Direction.SOUTH);
                        }
                        return;
                    }
                    signal("mined-" + current.name());
                    stage = Stage.PICKUP;
                }
                case PICKUP -> {
                    if (!marker("server-place-" + current.name()) || !positioned(current.x(), 14.5)) return;
                    var inventory = minecraft.player.getInventory();
                    for (int slot = 0; slot < 9; slot++) {
                        var stack = inventory.getItem(slot);
                        if (!stack.is(current.block().get().asItem())) continue;
                        check(current.name() + "_carried_state_reaches_client", stack.getCount() == 1 && carriedState(current, stack));
                        inventory.selected = slot;
                        var result = minecraft.gameMode.useItemOn(minecraft.player, InteractionHand.MAIN_HAND,
                            new BlockHitResult(Vec3.atBottomCenterOf(current.destination()), Direction.UP, current.destination().below(), false));
                        check(current.name() + "_normal_survival_placement", result.consumesAction());
                        signal("placed-" + current.name());
                        stage = Stage.PLACEMENT;
                        break;
                    }
                }
                case PLACEMENT -> {
                    if (!marker("server-verified-" + current.name()) || !minecraft.player.getMainHandItem().isEmpty()
                        || !minecraft.level.getBlockState(current.destination()).is(current.block().get())) return;
                    check(current.name() + "_client_sees_consumed_item_and_replanted_block", minecraft.level.isEmptyBlock(current.source()));
                    if (index + 1 < FIXTURES.size()) {
                        index++;
                        stage = Stage.READY;
                    } else {
                        var dest = current.destination();
                        minecraft.gameMode.useItemOn(minecraft.player, InteractionHand.MAIN_HAND,
                            new BlockHitResult(Vec3.atCenterOf(dest).add(0, 0, 0.5), Direction.SOUTH, dest, false));
                        stage = Stage.WEATHER;
                    }
                }
                case WEATHER -> {
                    if (!(minecraft.screen instanceof WeatherObeliskScreen screen)) return;
                    var menu = screen.getMenu();
                    if (menu.getFluidTank() == null || !menu.getSlot(0).hasItem()) return;
                    var fluid = menu.getFluidTank().contents();
                    if (fluid.isEmpty()) return;
                    check("weather_menu_receives_replanted_tagged_inputs", fluid.getAmount() == 1237 && fluid.getTag() != null
                        && fluid.getTag().getInt("PortFixture") == 77 && menu.getSlot(0).getItem().is(Items.FIREWORK_ROCKET)
                        && menu.getSlot(0).getItem().getCount() == 2 && menu.getCraftingProgress() == 0);
                    var capture = ClientSmoke.capture("replanted-weather-obelisk.png");
                    captures.put("replanted-weather-obelisk", capture);
                    check("silent_hidden_weather_menu", Boolean.TRUE.equals(capture.get("passed")));
                    finish(null);
                }
                default -> { }
            }
        } catch (Throwable failure) {
            failure.printStackTrace();
            finish(failure);
        }
    }

    private boolean positioned(int x, double z) {
        return minecraft.screen == null && minecraft.player.distanceToSqr(x + 0.5, 80, z) < 0.1;
    }

    private static boolean carriedState(Fixture current, ItemStack stack) {
        if (current.fluid() > 0) {
            var fluid = EIODataComponents.ITEM_FLUID_CONTENT.get(stack);
            return fluid != null && fluid.getAmount() == current.fluid() && fluid.getTag() != null
                && fluid.getTag().getInt("PortFixture") == 77;
        }
        var range = EIODataComponents.ACTION_RANGE.get(stack);
        return range != null && range.range() == current.range() && range.isVisible() == current.visible()
            && EIODataComponents.ENERGY.getOrDefault(stack, 0) == current.energy();
    }

    private boolean marker(String name) {
        return Files.exists(output.resolve(name));
    }

    private void signal(String name) throws IOException {
        var directory = output.resolve("signals");
        Files.createDirectories(directory);
        Files.writeString(directory.resolve(name + ".json"), "{}");
    }

    private void check(String name, boolean passed) {
        checks.put(name, passed);
        if (!passed) throw new AssertionError(name);
    }

    private void finish(Throwable failure) {
        stage = Stage.DONE;
        var result = new LinkedHashMap<String, Object>();
        result.put("checks", checks);
        result.put("captures", captures);
        result.put("passed", failure == null);
        if (failure != null) result.put("error", failure.toString());
        ClientSmoke.complete("client_obelisk_carry", result);
    }
}
