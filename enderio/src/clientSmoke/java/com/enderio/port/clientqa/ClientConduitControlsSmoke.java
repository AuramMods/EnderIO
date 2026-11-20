package com.enderio.port.clientqa;

import com.enderio.core.client.gui.widgets.BaseEnumPickerWidget;
import com.enderio.core.client.gui.widgets.IconButton;
import com.enderio.core.client.gui.widgets.ToggleIconButton;
import com.enderio.enderio.api.conduits.connection.config.ConnectionConfig;
import com.enderio.enderio.api.io.RedstoneControl;
import com.enderio.enderio.client.content.conduits.gui.ConduitScreen;
import com.enderio.enderio.client.foundation.widgets.DyeColorPickerWidget;
import com.enderio.enderio.client.foundation.widgets.RedstoneControlPickerWidget;
import com.enderio.enderio.content.conduits.bundle.ConduitBundleBlockEntity;
import com.enderio.enderio.content.conduits.type.fluid.FluidConduitConnectionConfig;
import com.mojang.serialization.JsonOps;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class ClientConduitControlsSmoke {
    private enum Stage { TITLE, JOIN, OPEN, EDIT, CAPTURE, FINISH, DONE }

    private record Endpoint(BlockPos pos, Direction side, ResourceLocation conduit) {
        Endpoint(int x, Direction side, String variant) {
            this(new BlockPos(x, 80, 8), side, ResourceLocation.fromNamespaceAndPath("enderio", variant));
        }
    }

    private record Step(String id, Endpoint endpoint, FluidConduitConnectionConfig before, FluidConduitConnectionConfig after) { }

    private static final FluidConduitConnectionConfig DEFAULT = FluidConduitConnectionConfig.DEFAULT;
    private static final FluidConduitConnectionConfig RED_GATE = DEFAULT.withExtractChannel(DyeColor.BLUE)
        .withExtractRedstoneControl(RedstoneControl.ACTIVE_WITH_SIGNAL);
    private static final FluidConduitConnectionConfig BLUE_GATE = RED_GATE.withExtractRedstoneChannel(DyeColor.BLUE);
    private static final FluidConduitConnectionConfig INVERTED_GATE = BLUE_GATE.withExtractRedstoneControl(RedstoneControl.ACTIVE_WITHOUT_SIGNAL);
    private final Minecraft minecraft = Minecraft.getInstance();
    private final Path output = Path.of(System.getProperty("enderio.clientqa.output"));
    private final boolean chemical;
    private final List<Step> steps;
    private final Map<String, Boolean> checks = new LinkedHashMap<>();
    private final Map<String, Object> captures = new LinkedHashMap<>();
    private final long started = System.nanoTime();
    private Stage stage = Stage.TITLE;
    private int index;
    private int captureAt;

    ClientConduitControlsSmoke(boolean chemical) {
        this.chemical = chemical;
        String kind = chemical ? "chemical" : "fluid";
        var outlet = new Endpoint(3280, Direction.EAST, "pressurized_" + kind);
        var inlet = new Endpoint(3279, Direction.WEST, "ender_" + kind);
        var receiver = DEFAULT.withIsInsert(true).withIsExtract(false).withPriority(chemical ? 0 : 2);
        var blueOutlet = receiver.withInsertChannel(DyeColor.BLUE);
        steps = "verify".equals(System.getProperty("enderio.clientqa.multiplayerPhase"))
            ? List.of(new Step("saved-inlet", inlet, INVERTED_GATE, INVERTED_GATE),
                new Step("saved-outlet", outlet, blueOutlet, blueOutlet))
            : List.of(new Step("outlet-blue", outlet, DEFAULT, blueOutlet),
                new Step("gate-red", inlet, DEFAULT, RED_GATE), new Step("gate-blue", inlet, RED_GATE, BLUE_GATE),
                new Step("outlet-green", outlet, blueOutlet, receiver), new Step("outlet-matched", outlet, receiver, blueOutlet),
                new Step("gate-inverted", inlet, BLUE_GATE, INVERTED_GATE));
        MinecraftForge.EVENT_BUS.addListener(this::render);
    }

    private void render(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage == Stage.DONE) return;
        try {
            if (minecraft.screen instanceof DisconnectedScreen || System.nanoTime() - started > 170_000_000_000L) {
                throw new AssertionError("Conduit controls stopped at " + stage + " / " + index);
            }
            if (stage == Stage.TITLE) {
                if (!(minecraft.screen instanceof TitleScreen) || minecraft.getOverlay() != null) return;
                String address = System.getProperty("enderio.clientqa.server");
                ConnectScreen.startConnecting(new TitleScreen(), minecraft, ServerAddress.parseString(address),
                    new ServerData("Conduit controls QA", address, true), false);
                stage = Stage.JOIN;
                return;
            }
            if (minecraft.level == null || minecraft.player == null) return;
            switch (stage) {
                case JOIN -> {
                    if (minecraft.screen != null || minecraft.getToasts().getToast(SystemToast.class,
                        SystemToast.SystemToastIds.UNSECURE_SERVER_WARNING) != null) return;
                    check("native_dedicated_survival_connection", minecraft.getSingleplayerServer() == null
                        && !minecraft.player.isCreative() && !minecraft.player.isSpectator()
                        && !minecraft.getConnection().getConnection().isMemoryConnection());
                    signal("connected");
                    stage = Stage.OPEN;
                }
                case OPEN -> open(steps.get(index));
                case EDIT -> edit(steps.get(index));
                case CAPTURE -> capture(steps.get(index));
                case FINISH -> {
                    if (ready("done")) finish(null);
                }
                default -> { }
            }
        } catch (Throwable failure) {
            failure.printStackTrace();
            finish(failure);
        }
    }

    private void open(Step step) {
        var endpoint = step.endpoint();
        var pos = endpoint.pos();
        var player = minecraft.player;
        if (!ready(step.id()) || minecraft.screen != null || player.distanceToSqr(pos.getX() + 0.5, 80, 10.5) > 0.09
            || !(minecraft.level.getBlockEntity(pos) instanceof ConduitBundleBlockEntity bundle)) return;
        for (double y : new double[]{0.5, 0.375, 0.625}) {
            for (double z : new double[]{0.5, 0.375, 0.625}) {
                // The outer plate is shared by both conduits. Target the individual arm behind it.
                double x = endpoint.side() == Direction.WEST ? 0.2 : 0.8;
                var hit = new BlockHitResult(new Vec3(pos.getX() + x, pos.getY() + y, pos.getZ() + z), Direction.SOUTH, pos, false);
                var connection = bundle.getShape().getConnectionFromHit(pos, hit);
                if (connection == null || connection.getFirst() != endpoint.side() || !connection.getSecond().is(endpoint.conduit())) continue;
                check(step.id() + "_opens_the_native_endpoint_menu", minecraft.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit).consumesAction());
                stage = Stage.EDIT;
                return;
            }
        }
    }

    private void edit(Step step) {
        if (!(minecraft.screen instanceof ConduitScreen screen)) return;
        var menu = screen.getMenu();
        var endpoint = step.endpoint();
        check(step.id() + "_opens_the_correct_side_and_tier", menu.getBlockPos().equals(endpoint.pos())
            && menu.getSide() == endpoint.side() && menu.getConduit().is(endpoint.conduit()));
        var before = step.before();
        var after = step.after();
        if (!matches(menu.connectionConfig(), before)) return;
        check(step.id() + "_reads_authoritative_settings_on_open", true);
        if (before.isInsert() != after.isInsert()) click(screen, widget(screen, ToggleIconButton.class, 22, 7));
        if (before.isExtract() != after.isExtract()) click(screen, widget(screen, ToggleIconButton.class, 112, 7));
        if (before.insertChannel() != after.insertChannel()) {
            choose(widget(screen, DyeColorPickerWidget.class, 22, 27), before.insertChannel(), after.insertChannel());
        }
        if (before.extractChannel() != after.extractChannel()) {
            choose(widget(screen, DyeColorPickerWidget.class, 112, 27), before.extractChannel(), after.extractChannel());
        }
        if (before.extractRedstoneControl() != after.extractRedstoneControl()) {
            choose(widget(screen, RedstoneControlPickerWidget.class, 112, 47), before.extractRedstoneControl(), after.extractRedstoneControl());
        }
        if (before.extractRedstoneChannel() != after.extractRedstoneChannel()) {
            choose(widget(screen, DyeColorPickerWidget.class, 132, 47), before.extractRedstoneChannel(), after.extractRedstoneChannel());
        }
        for (int value = before.insertPriority(); value < after.insertPriority(); value++) {
            click(screen, widget(screen, IconButton.class, 92, 45));
        }
        check(step.id() + "_widgets_show_the_requested_configuration", matches(menu.connectionConfig(), after));
        captureAt = minecraft.player.tickCount + 6;
        stage = Stage.CAPTURE;
    }

    private void capture(Step step) throws IOException {
        if (!(minecraft.screen instanceof ConduitScreen screen) || minecraft.player.tickCount < captureAt) return;
        check(step.id() + "_settings_remain_visible", matches(screen.getMenu().connectionConfig(), step.after()));
        var redstone = widget(screen, DyeColorPickerWidget.class, 132, 47);
        check(step.id() + "_redstone_picker_visibility_follows_mode", redstone.visible == step.after().extractRedstoneControl().isRedstoneSensitive());
        var frame = ClientSmoke.capture(step.id() + ".png");
        captures.put(step.id(), frame);
        check(step.id() + "_hidden_and_muted", Boolean.TRUE.equals(frame.get("passed")));
        minecraft.player.closeContainer();
        signal(step.id());
        stage = ++index < steps.size() ? Stage.OPEN : Stage.FINISH;
    }

    private boolean matches(ConnectionConfig actual, FluidConduitConnectionConfig expected) {
        if (!chemical) return actual.equals(expected);

        // Both codecs use the same names for the shared settings. Only fluid has a priority control.
        var expectedFields = ConnectionConfig.GENERIC_CODEC.encodeStart(JsonOps.INSTANCE, expected)
            .getOrThrow(false, message -> { throw new AssertionError(message); }).getAsJsonObject();
        expectedFields.addProperty("type", "enderio:chemical");
        expectedFields.remove("insert_priority");
        return ConnectionConfig.GENERIC_CODEC.encodeStart(JsonOps.INSTANCE, actual)
            .getOrThrow(false, message -> { throw new AssertionError(message); }).equals(expectedFields);
    }

    private static <T extends AbstractWidget> T widget(ConduitScreen screen, Class<T> type, int x, int y) {
        return screen.children().stream().filter(type::isInstance).map(type::cast)
            .filter(button -> button.getX() == screen.getGuiLeft() + x && button.getY() == screen.getGuiTop() + y)
            .findFirst().orElseThrow(() -> new AssertionError("Missing " + type.getSimpleName() + " at " + x + ", " + y));
    }

    private static void click(ConduitScreen screen, AbstractWidget widget) {
        double x = widget.getX() + 3;
        double y = widget.getY() + 3;
        if (!screen.mouseClicked(x, y, 0)) throw new AssertionError("Conduit control did not accept click");
        screen.mouseReleased(x, y, 0);
    }

    private static <T extends Enum<T>> void choose(BaseEnumPickerWidget<T> widget, T current, T desired) {
        int length = widget.getValues().length;
        int steps = Math.floorMod(widget.getValueIndex(desired) - widget.getValueIndex(current), length);
        int direction = steps <= length / 2 ? -1 : 1;
        for (int n = Math.min(steps, length - steps); n > 0; n--) {
            if (!widget.visible || !widget.mouseScrolled(widget.getX() + 3, widget.getY() + 3, direction)) {
                throw new AssertionError("Conduit picker did not accept scroll");
            }
        }
    }

    private boolean ready(String id) {
        return Files.exists(output.resolve("server-" + id));
    }

    private void signal(String id) throws IOException {
        var directory = output.resolve("signals");
        Files.createDirectories(directory);
        var staging = directory.resolve(id + ".tmp");
        Files.writeString(staging, "{}");
        Files.move(staging, directory.resolve(id + ".json"));
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
        ClientSmoke.complete(chemical ? "client_chemical_conduit_controls" : "client_fluid_conduit_controls", result);
    }
}
