package com.enderio.port.clientqa;

import com.enderio.enderio.api.soul.Soul;
import com.enderio.enderio.client.content.machines.gui.screen.ObeliskScreen;
import com.enderio.enderio.content.machines.obelisks.ObeliskMenu;
import com.enderio.enderio.init.EIODataComponents;
import com.enderio.enderio.init.EIOItems;
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
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Items;
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

final class ClientEntityObeliskSmoke {
    private enum Stage { TITLE, CONNECT, OPEN, VISIBILITY, INCREASE, CAPTURE, FIRST_PEARL, SECOND_PEARL, FINISH, DONE }

    private record Machine(String name, int x, boolean filter) { }

    private static final List<Machine> MACHINES = List.of(
        new Machine("inhibitor", 1700, false), new Machine("aversion", 1703, true),
        new Machine("relocator", 1706, true), new Machine("attractor", 1712, true));

    private final Minecraft minecraft = Minecraft.getInstance();
    private final String address = System.getProperty("enderio.clientqa.server");
    private final Path output = Path.of(System.getProperty("enderio.clientqa.output"));
    private final Map<String, Boolean> checks = new LinkedHashMap<>();
    private final Map<String, Object> captures = new LinkedHashMap<>();
    private final long started = System.nanoTime();
    private Stage stage = Stage.TITLE;
    private long titleReady;
    private int index;

    ClientEntityObeliskSmoke() {
        MinecraftForge.EVENT_BUS.addListener(this::render);
    }

    private void render(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage == Stage.DONE) return;
        try {
            if (minecraft.screen instanceof DisconnectedScreen) throw new AssertionError("Disconnected in " + stage);
            if (System.nanoTime() - started > 200_000_000_000L) throw new AssertionError("Timed out in " + stage + " at obelisk " + index);
            switch (stage) {
                case TITLE -> {
                    if (!(minecraft.screen instanceof TitleScreen) || minecraft.getOverlay() != null) return;
                    if (titleReady == 0) titleReady = System.nanoTime();
                    if (System.nanoTime() - titleReady < 2_000_000_000L) return;
                    ConnectScreen.startConnecting(new TitleScreen(), minecraft, ServerAddress.parseString(address),
                        new ServerData("Ender IO local Entity Obelisk test", address, true), false);
                    stage = Stage.CONNECT;
                }
                case CONNECT -> {
                    if (minecraft.level == null || minecraft.player == null || minecraft.screen != null) return;
                    if (minecraft.getToasts().getToast(SystemToast.class, SystemToast.SystemToastIds.UNSECURE_SERVER_WARNING) != null) return;
                    var connection = minecraft.getConnection().getConnection();
                    check("dedicated_survival_tcp", !minecraft.player.isCreative() && !minecraft.player.isSpectator()
                        && minecraft.getSingleplayerServer() == null && !connection.isMemoryConnection()
                        && connection.getRemoteAddress() instanceof InetSocketAddress);
                    signal("connected");
                    stage = Stage.OPEN;
                }
                case OPEN -> {
                    Machine machine = MACHINES.get(index);
                    if (!marker("server-menu-" + machine.name()) || minecraft.screen != null
                        || minecraft.player.distanceToSqr(machine.x() + 0.5, 80, 11.5) > 0.1) return;
                    var block = new BlockPos(machine.x(), 80, 8);
                    minecraft.gameMode.useItemOn(minecraft.player, InteractionHand.MAIN_HAND,
                        new BlockHitResult(Vec3.atCenterOf(block).add(0, 0, 0.5), Direction.SOUTH, block, false));
                    stage = Stage.VISIBILITY;
                }
                case VISIBILITY -> {
                    if (!(minecraft.screen instanceof ObeliskScreen<?, ?> screen)
                        || !(minecraft.player.containerMenu instanceof ObeliskMenu<?> menu)
                        || menu.getMaxRange() < 5 || menu.getEnergyStorage() == null) return;
                    Machine machine = MACHINES.get(index);
                    if (menu.getBlockEntity().getRange() != 4 || !menu.isCapacitorInstalled()) return;
                    check(machine.name() + "_menu_has_power_range_and_correct_inputs",
                        !menu.isRangeVisible() && menu.getEnergyStorage().energyStored() > 0
                            && menu.getSlot(0).getItem().is(EIOItems.BASIC_CAPACITOR.get())
                            && (!machine.filter() || validFilter(menu)));
                    check(machine.name() + "_visibility_widget_responds",
                        screen.mouseClicked(screen.getGuiLeft() + screen.getXSize() - 14, screen.getGuiTop() + 42, 0));
                    stage = Stage.INCREASE;
                }
                case INCREASE -> {
                    var screen = (ObeliskScreen<?, ?>) minecraft.screen;
                    var menu = screen.getMenu();
                    if (!menu.isRangeVisible()) return;
                    check(MACHINES.get(index).name() + "_range_widget_responds",
                        screen.mouseClicked(screen.getGuiLeft() + screen.getXSize() - 29, screen.getGuiTop() + 37, 0));
                    stage = Stage.CAPTURE;
                }
                case CAPTURE -> {
                    var screen = (ObeliskScreen<?, ?>) minecraft.screen;
                    if (screen.getMenu().getBlockEntity().getRange() != 5) return;
                    var name = MACHINES.get(index).name();
                    check(name + "_server_updates_both_controls", screen.getMenu().isRangeVisible());
                    var frame = ClientSmoke.capture(name + "-obelisk.png");
                    captures.put(name + "-obelisk", frame);
                    check(name + "_silent_hidden_render", Boolean.TRUE.equals(frame.get("passed")));
                    minecraft.player.closeContainer();
                    signal("menu-" + name);
                    stage = ++index == MACHINES.size() ? Stage.FIRST_PEARL : Stage.OPEN;
                }
                case FIRST_PEARL -> {
                    if (!readyToThrow("server-fire-first", 2)) return;
                    throwPearl("first");
                    stage = Stage.SECOND_PEARL;
                }
                case SECOND_PEARL -> {
                    if (!readyToThrow("server-fire-second", 1)) return;
                    throwPearl("second");
                    stage = Stage.FINISH;
                }
                case FINISH -> {
                    if (!marker("server-pearl-verified") || minecraft.player.getX() < 1707) return;
                    check("allowed_pearl_updates_client_position_and_inventory", minecraft.player.getMainHandItem().isEmpty());
                    finish(null);
                }
                default -> { }
            }
        } catch (Throwable failure) {
            failure.printStackTrace();
            finish(failure);
        }
    }

    static boolean validFilter(ObeliskMenu<?> menu) {
        var stack = menu.getSlot(1).getItem();
        var filter = EIODataComponents.SOUL_FILTER.get(stack);
        return stack.is(EIOItems.BASIC_SOUL_FILTER.get()) && filter != null && !filter.isDenyList()
            && filter.test(Soul.of(EntityType.PIG)) && !filter.test(Soul.of(EntityType.COW));
    }

    private boolean readyToThrow(String marker, int count) {
        if (!marker(marker) || minecraft.screen != null
            || minecraft.player.distanceToSqr(1700.5, 80, 11.5) > 0.1
            || Math.abs(minecraft.player.getYRot() + 90) > 0.1) return false;
        minecraft.player.getInventory().selected = 0;
        return minecraft.player.getMainHandItem().is(Items.ENDER_PEARL)
            && minecraft.player.getMainHandItem().getCount() == count
            && !minecraft.player.getCooldowns().isOnCooldown(Items.ENDER_PEARL);
    }

    private void throwPearl(String shot) throws IOException {
        var result = minecraft.gameMode.useItem(minecraft.player, InteractionHand.MAIN_HAND);
        check("native_" + shot + "_pearl_item_use", result.consumesAction());
        signal("pearl-" + shot);
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
        ClientSmoke.complete("client_entity_obelisks", result);
    }
}
