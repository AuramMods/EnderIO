package com.enderio.port.clientqa;

import com.enderio.enderio.client.content.filters.redstone.RedstoneCountFilterScreen;
import com.enderio.enderio.client.content.filters.redstone.RedstoneDoubleChannelFilterScreen;
import com.enderio.enderio.client.content.filters.redstone.RedstoneTimerFilterScreen;
import com.enderio.enderio.client.foundation.widgets.DyeColorPickerWidget;
import com.enderio.enderio.init.EIOItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

final class ClientRedstoneFiltersSmoke {
    private enum Stage { TITLE, JOIN, OPEN, EDIT, CONFIRM, DONE }

    private record Fixture(String name, InteractionHand hand, Supplier<? extends Item> item, Class<? extends Screen> screen) { }

    private static final Fixture[] FIXTURES = {
        new Fixture("timer", InteractionHand.MAIN_HAND, EIOItems.TIMER_FILTER, RedstoneTimerFilterScreen.class),
        new Fixture("counter", InteractionHand.OFF_HAND, EIOItems.COUNT_FILTER, RedstoneCountFilterScreen.class),
        new Fixture("and-gate", InteractionHand.MAIN_HAND, EIOItems.AND_FILTER, RedstoneDoubleChannelFilterScreen.class)
    };

    private final Minecraft minecraft = Minecraft.getInstance();
    private final Path output = Path.of(System.getProperty("enderio.clientqa.output"));
    private final String address = System.getProperty("enderio.clientqa.server");
    private final long started = System.nanoTime();
    private final Map<String, Boolean> checks = new LinkedHashMap<>();
    private final Map<String, Object> captures = new LinkedHashMap<>();
    private Stage stage = Stage.TITLE;
    private long titleReady;
    private int crouchTick = -1;
    private int index;

    ClientRedstoneFiltersSmoke() {
        MinecraftForge.EVENT_BUS.addListener(this::render);
    }

    private void render(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage == Stage.DONE) return;
        try {
            if (minecraft.screen instanceof DisconnectedScreen || System.nanoTime() - started > 170_000_000_000L) {
                throw new AssertionError("Redstone filter client stopped in " + stage + " on fixture " + index);
            }
            if (stage == Stage.TITLE) {
                connect();
                return;
            }
            var player = minecraft.player;
            if (player == null || minecraft.level == null) return;
            var fixture = FIXTURES[index];
            switch (stage) {
                case JOIN -> {
                    if (minecraft.screen != null || minecraft.getToasts().getToast(SystemToast.class,
                        SystemToast.SystemToastIds.UNSECURE_SERVER_WARNING) != null) return;
                    check("real_dedicated_survival_connection", !player.isCreative() && !player.isSpectator()
                        && minecraft.getSingleplayerServer() == null && !minecraft.getConnection().getConnection().isMemoryConnection());
                    signal("connected");
                    stage = Stage.OPEN;
                }
                case OPEN -> {
                    if (minecraft.screen != null || !Files.exists(output.resolve("ready-" + fixture.name()))
                        || !player.getItemInHand(fixture.hand()).is(fixture.item().get())) return;
                    if (crouchTick == -1) {
                        minecraft.options.keyShift.setDown(true);
                        crouchTick = player.tickCount;
                        return;
                    }
                    if (player.tickCount - crouchTick < 4 || !player.isShiftKeyDown()) return;
                    minecraft.gameMode.useItem(player, fixture.hand());
                    minecraft.options.keyShift.setDown(false);
                    crouchTick = -1;
                    stage = Stage.EDIT;
                }
                case EDIT -> {
                    if (!fixture.screen().isInstance(minecraft.screen)) return;
                    edit(minecraft.screen);
                    signal("edited-" + fixture.name());
                    stage = Stage.CONFIRM;
                }
                case CONFIRM -> {
                    if (!Files.exists(output.resolve("confirmed-" + fixture.name()))) return;
                    check(fixture.name() + "_remains_open_after_server_confirmation", fixture.screen().isInstance(minecraft.screen));
                    capture(fixture.name());
                    player.closeContainer();
                    signal("closed-" + fixture.name());
                    if (++index == FIXTURES.length) finish(null);
                    else stage = Stage.OPEN;
                }
                default -> { }
            }
        } catch (Throwable failure) {
            failure.printStackTrace();
            finish(failure);
        }
    }

    private void edit(Screen screen) {
        if (screen instanceof RedstoneTimerFilterScreen timer) {
            check("main_hand_timer_shows_default_period", timer.getMenu().getFilter().getMaxTicks() == 20);
            enterNumber(timer, "20", "40");
        } else if (screen instanceof RedstoneCountFilterScreen counter) {
            check("offhand_counter_menu_reads_the_counter_that_opened_it",
                counter.getMenu().getFilter().getMaxCount() == 8 && counter.getMenu().getFilter().getCount() == 1);
            var picker = widget(counter, DyeColorPickerWidget.class, 0);
            scroll(picker, 1);
            scroll(picker, 1);
            scroll(picker, 1);
            check("counter_picker_selects_white", counter.getMenu().getFilter().getChannel() == DyeColor.WHITE);
            enterNumber(counter, "8", "2");
        } else if (screen instanceof RedstoneDoubleChannelFilterScreen gate) {
            var channels = gate.getMenu().getChannels();
            check("gate_displays_both_default_colors", channels.getFirstChannel() == DyeColor.GREEN
                && channels.getSecondChannel() == DyeColor.BROWN);
            scroll(widget(gate, DyeColorPickerWidget.class, 0), 1);
            scroll(widget(gate, DyeColorPickerWidget.class, 1), -1);
            check("gate_pickers_set_red_and_blue_independently", channels.getFirstChannel() == DyeColor.RED
                && channels.getSecondChannel() == DyeColor.BLUE);
        }
    }

    private void enterNumber(Screen screen, String oldValue, String newValue) {
        var box = widget(screen, EditBox.class, 0);
        check(oldValue + "_is_rendered_in_the_edit_box", oldValue.equals(box.getValue()));
        box.setFocused(true);
        box.moveCursorToEnd();
        box.setHighlightPos(0);
        for (char digit : newValue.toCharArray()) {
            if (!box.charTyped(digit, 0)) throw new AssertionError("Edit box rejected " + digit);
        }
        var button = widget(screen, Button.class, 0);
        check(newValue + "_is_confirmed_by_the_native_button", newValue.equals(box.getValue())
            && button.mouseClicked(button.getX() + 2, button.getY() + 2, 0));
    }

    private static <T extends GuiEventListener> T widget(Screen screen, Class<T> type, int index) {
        return screen.children().stream().filter(type::isInstance).map(type::cast).skip(index).findFirst().orElseThrow();
    }

    private static void scroll(DyeColorPickerWidget picker, int direction) {
        if (!picker.mouseScrolled(picker.getX() + 8, picker.getY() + 8, direction)) {
            throw new AssertionError("The color picker ignored its scroll");
        }
    }

    private void connect() {
        if (!(minecraft.screen instanceof TitleScreen) || minecraft.getOverlay() != null) return;
        if (titleReady == 0) titleReady = System.nanoTime();
        if (System.nanoTime() - titleReady < 2_000_000_000L) return;
        ConnectScreen.startConnecting(new TitleScreen(), minecraft, ServerAddress.parseString(address),
            new ServerData("Ender IO redstone filters", address, true), false);
        stage = Stage.JOIN;
    }

    private void signal(String name) throws IOException {
        var signals = output.resolve("signals");
        Files.createDirectories(signals);
        Files.writeString(signals.resolve(name + ".json"), "{}");
    }

    private void capture(String name) throws IOException {
        var frame = ClientSmoke.capture(name + ".png");
        captures.put(name, frame);
        check(name + "_silent_background_capture", Boolean.TRUE.equals(frame.get("passed")));
    }

    private void check(String name, boolean passed) {
        checks.put(name, passed);
        if (!passed) throw new AssertionError(name);
    }

    private void finish(Throwable failure) {
        minecraft.options.keyShift.setDown(false);
        stage = Stage.DONE;
        var result = new LinkedHashMap<String, Object>();
        result.put("passed", failure == null);
        result.put("checks", checks);
        result.put("captures", captures);
        if (failure != null) result.put("error", failure.toString());
        ClientSmoke.complete("client_redstone_filters", result);
    }
}
