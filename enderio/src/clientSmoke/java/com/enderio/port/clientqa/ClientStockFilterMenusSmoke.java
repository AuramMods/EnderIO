package com.enderio.port.clientqa;

import com.enderio.core.client.gui.widgets.ToggleIconButton;
import com.enderio.enderio.client.content.filters.item.DamageFilterModePickerWidget;
import com.enderio.enderio.client.content.filters.item.EnderItemFilterScreen;
import com.enderio.enderio.client.content.filters.item.LimitedItemFilterScreen;
import com.enderio.enderio.content.filters.item.general.DamageFilterMode;
import com.enderio.enderio.content.filters.item.limited.LimitedItemFilterSlot;
import com.enderio.enderio.init.EIOItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

final class ClientStockFilterMenusSmoke {
    private enum Stage { TITLE, JOIN, OPEN, SAMPLE, EDITED, CONFIRMED, REOPEN, SAVED, DONE }

    private record Fixture(String name, Supplier<? extends Item> filter, Item sample) {}

    private static final List<Fixture> FIXTURES = List.of(
        new Fixture("limited", EIOItems.LIMITED_ITEM_FILTER, Items.PAPER),
        new Fixture("large", EIOItems.BIG_ITEM_FILTER, Items.PAPER),
        new Fixture("large-advanced", EIOItems.BIG_ADVANCED_ITEM_FILTER, Items.IRON_PICKAXE));

    private final Minecraft minecraft = Minecraft.getInstance();
    private final Path output = Path.of(System.getProperty("enderio.clientqa.output"));
    private final String address = System.getProperty("enderio.clientqa.server");
    private final boolean reopening = "verify".equals(System.getProperty("enderio.clientqa.multiplayerPhase"));
    private final long started = System.nanoTime();
    private final Map<String, Boolean> checks = new LinkedHashMap<>();
    private final Map<String, Object> captures = new LinkedHashMap<>();
    private Stage stage = Stage.TITLE;
    private int fixtureIndex;
    private int crouchStart = -1;
    private int openedAt;
    private long titleReady;

    ClientStockFilterMenusSmoke() {
        MinecraftForge.EVENT_BUS.addListener(this::render);
    }

    private void render(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage == Stage.DONE) return;
        try {
            if (minecraft.screen instanceof DisconnectedScreen || System.nanoTime() - started > 170_000_000_000L) {
                throw new AssertionError("Stock filter client stopped in " + stage + " on fixture " + fixtureIndex);
            }
            if (stage == Stage.TITLE) {
                connect();
                return;
            }
            var player = minecraft.player;
            if (player == null || minecraft.level == null) return;
            var fixture = FIXTURES.get(fixtureIndex);
            switch (stage) {
                case JOIN -> {
                    if (minecraft.screen != null || minecraft.getToasts().getToast(SystemToast.class,
                        SystemToast.SystemToastIds.UNSECURE_SERVER_WARNING) != null) return;
                    check("real_dedicated_survival_connection", !player.isCreative() && !player.isSpectator()
                        && minecraft.getSingleplayerServer() == null && !minecraft.getConnection().getConnection().isMemoryConnection());
                    signal("connected");
                    stage = reopening ? Stage.REOPEN : Stage.OPEN;
                }
                case OPEN, REOPEN -> {
                    if (minecraft.screen != null || !ready(fixture.name()) || !player.getMainHandItem().is(fixture.filter().get())) return;
                    if (crouchStart == -1) {
                        minecraft.options.keyShift.setDown(true);
                        crouchStart = player.tickCount;
                    } else if (player.tickCount - crouchStart >= 4 && player.isShiftKeyDown()) {
                        minecraft.gameMode.useItem(player, InteractionHand.MAIN_HAND);
                        minecraft.options.keyShift.setDown(false);
                        crouchStart = -1;
                        openedAt = player.tickCount;
                        stage = reopening ? Stage.SAVED : Stage.SAMPLE;
                    }
                }
                case SAMPLE -> editSample(fixture);
                case EDITED -> finishEdit(fixture);
                case CONFIRMED -> {
                    if (!ready(fixture.name() + "-confirmed")) return;
                    check(fixture.name() + "_confirmed_while_editor_is_open", minecraft.screen instanceof AbstractContainerScreen<?>);
                    capture(fixture.name());
                    player.closeContainer();
                    signal(fixture.name() + "-closed");
                    if (fixtureIndex == FIXTURES.size() - 1) finish(null);
                    else {
                        fixtureIndex++;
                        stage = Stage.OPEN;
                    }
                }
                case SAVED -> {
                    if (!(minecraft.screen instanceof LimitedItemFilterScreen screen) || !screen.getMenu().shouldCompareComponents()
                        || limited(screen, 17).getResource().getCount() != 137 || limited(screen, 0).getResource().getCount() != 64) return;
                    check("reopened_inventory_editor_preserves_full_stock_count_and_tag",
                        limited(screen, 17).getResource().getTag().getInt("StockSample") == 5);
                    capture("reopened-limited");
                    finish(null);
                }
                default -> { }
            }
        } catch (Throwable failure) {
            failure.printStackTrace();
            finish(failure);
        }
    }

    private void editSample(Fixture fixture) {
        if (fixtureIndex == 0 && minecraft.screen instanceof LimitedItemFilterScreen screen) {
            var stock = limited(screen, 17).getResource();
            if (stock.getCount() != 137) {
                if (minecraft.player.tickCount - openedAt > 65) {
                    throw new AssertionError("The limited editor received " + stock.getCount() + " of the saved 137");
                }
                return;
            }
            check("limited_editor_reads_the_full_saved_stock_and_tag",
                stock.is(Items.COBBLESTONE) && stock.getTag().getInt("StockSample") == 5);
            if (!playerSlot(screen, 2).getItem().is(Items.COBBLESTONE)) return;
            copyGhost(screen, playerSlot(screen, 2), limited(screen, 17), false);
            copyGhost(screen, playerSlot(screen, 1), limited(screen, 0), true);
            toggle(screen, 0);
            stage = Stage.EDITED;
        } else if (fixtureIndex != 0 && minecraft.screen instanceof EnderItemFilterScreen screen) {
            var slot = screen.getMenu().getSlot(35);
            check(fixture.name() + "_exposes_four_rows_and_the_last_slot", screen.getMenu().type.slotCount() == 36
                && screen.getMenu().type.rowCount() == 4 && slot.y > screen.getMenu().getSlot(26).y);
            copyGhost(screen, playerSlot(screen, 1), slot, false);
            toggle(screen, 0);
            if (fixtureIndex == 2) {
                var picker = screen.children().stream().filter(DamageFilterModePickerWidget.class::isInstance)
                    .map(DamageFilterModePickerWidget.class::cast).findFirst().orElseThrow();
                check("large_advanced_uses_the_damage_picker", picker.mouseScrolled(picker.getX() + 8, picker.getY() + 8, -1));
            }
            stage = Stage.EDITED;
        }
    }

    private void finishEdit(Fixture fixture) throws IOException {
        if (fixtureIndex == 0 && minecraft.screen instanceof LimitedItemFilterScreen screen) {
            if (!screen.getMenu().shouldCompareComponents() || limited(screen, 0).getResource().getCount() != 64) return;
            check("ordinary_limited_clicks_preserve_the_real_stack_and_the_loaded_stock",
                limited(screen, 17).getResource().getCount() == 137 && playerSlot(screen, 1).getItem().getCount() == 32
                    && playerSlot(screen, 2).getItem().getCount() == 4
                    && screen.getMenu().getCarried().isEmpty());
        } else if (fixtureIndex != 0 && minecraft.screen instanceof EnderItemFilterScreen screen) {
            if (!screen.getMenu().getSlot(35).getItem().is(fixture.sample())) return;
            if (fixtureIndex == 1 && !screen.getMenu().isInverted()) return;
            if (fixtureIndex == 2 && (!screen.getMenu().shouldCompareComponents()
                || screen.getMenu().damageFilterMode() != DamageFilterMode.UP_TO_25)) return;
            check(fixture.name() + "_ghost_keeps_the_original_inventory_stack",
                screen.getMenu().getSlot(35).getItem().getCount() == 1 && playerSlot(screen, 1).getItem().is(fixture.sample())
                    && screen.getMenu().getCarried().isEmpty());
        } else {
            return;
        }
        signal(fixture.name() + "-edited");
        stage = Stage.CONFIRMED;
    }

    private void connect() {
        if (!(minecraft.screen instanceof TitleScreen) || minecraft.getOverlay() != null) return;
        if (titleReady == 0) titleReady = System.nanoTime();
        if (System.nanoTime() - titleReady < 2_000_000_000L) return;
        ConnectScreen.startConnecting(new TitleScreen(), minecraft, ServerAddress.parseString(address),
            new ServerData("Ender IO stock filters", address, true), false);
        stage = Stage.JOIN;
    }

    private Slot playerSlot(AbstractContainerScreen<?> screen, int index) {
        return screen.getMenu().slots.stream().filter(slot -> slot.container == minecraft.player.getInventory()
            && slot.getSlotIndex() == index).findFirst().orElseThrow();
    }

    private static LimitedItemFilterSlot limited(LimitedItemFilterScreen screen, int index) {
        return (LimitedItemFilterSlot) screen.getMenu().getSlot(index);
    }

    private static void copyGhost(AbstractContainerScreen<?> screen, Slot sample, Slot ghost, boolean addAgain) {
        clickSlot(screen, sample, 0);
        clickSlot(screen, ghost, 0);
        if (addAgain) clickSlot(screen, ghost, 1);
        clickSlot(screen, sample, 0);
    }

    private static void clickSlot(AbstractContainerScreen<?> screen, Slot slot, int button) {
        click(screen, screen.getGuiLeft() + slot.x + 7, screen.getGuiTop() + slot.y + 7, button);
    }

    private static void click(AbstractContainerScreen<?> screen, double x, double y, int button) {
        screen.mouseClicked(x, y, button);
        screen.mouseReleased(x, y, button);
    }

    private static void toggle(AbstractContainerScreen<?> screen, int index) {
        var button = screen.children().stream().filter(ToggleIconButton.class::isInstance).map(ToggleIconButton.class::cast)
            .skip(index).findFirst().orElseThrow();
        click(screen, button.getX() + 2, button.getY() + 2, 0);
    }

    private boolean ready(String name) {
        return Files.exists(output.resolve("server-ready-" + name));
    }

    private void signal(String name) throws IOException {
        var signals = output.resolve("signals");
        Files.createDirectories(signals);
        Files.writeString(signals.resolve(name + ".json"), "{}");
    }

    private void capture(String name) throws IOException {
        var capture = ClientSmoke.capture(name + ".png");
        captures.put(name, capture);
        check(name + "_silent_background_capture", Boolean.TRUE.equals(capture.get("passed")));
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
        ClientSmoke.complete("client_stock_filter_menus", result);
    }
}
