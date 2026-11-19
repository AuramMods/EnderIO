package com.enderio.port.clientqa;

import com.enderio.core.client.gui.widgets.IconButton;
import com.enderio.core.client.gui.widgets.ToggleIconButton;
import com.enderio.enderio.client.content.conduits.gui.ConduitScreen;
import com.enderio.enderio.client.content.filters.fluid.EnderFluidFilterScreen;
import com.enderio.enderio.client.content.filters.item.EnderItemFilterScreen;
import com.enderio.enderio.content.conduits.bundle.ConduitBundleBlockEntity;
import com.enderio.enderio.content.filters.fluid.FluidFilterSlot;
import com.enderio.enderio.init.EIOItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

final class ClientTransferFilterMenusSmoke {
    private enum Stage {
        TITLE, JOIN, OPEN_ITEM, ITEM_SAMPLE, ITEM_FLAGS, ITEM_SAVE, OPEN_CONDUIT, CONFIGURE_FLUID, FLUID_SAMPLE,
        FLUID_CLEAR, FLUID_FLAGS, FLUID_SAVE, BACK, CONFIRM, DONE
    }

    private static final BlockPos FLUID_CONDUIT = new BlockPos(4639, 80, 8);
    private final Minecraft minecraft = Minecraft.getInstance();
    private final Path output = Path.of(System.getProperty("enderio.clientqa.output"));
    private final String address = System.getProperty("enderio.clientqa.server");
    private final long started = System.nanoTime();
    private final Map<String, Boolean> checks = new LinkedHashMap<>();
    private final Map<String, Object> captures = new LinkedHashMap<>();
    private Stage stage = Stage.TITLE;
    private long titleReady;
    private int crouchTick = -1;
    private int menuOpenedAt;

    ClientTransferFilterMenusSmoke() {
        MinecraftForge.EVENT_BUS.addListener(this::render);
    }

    private void render(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage == Stage.DONE) return;
        try {
            if (minecraft.screen instanceof DisconnectedScreen || System.nanoTime() - started > 170_000_000_000L) {
                throw new AssertionError("Transfer filter client stopped in " + stage);
            }
            if (stage == Stage.TITLE) {
                connect();
                return;
            }
            var player = minecraft.player;
            if (player == null || minecraft.level == null) return;
            switch (stage) {
                case JOIN -> {
                    if (minecraft.screen != null || minecraft.getToasts().getToast(SystemToast.class,
                        SystemToast.SystemToastIds.UNSECURE_SERVER_WARNING) != null) return;
                    check("dedicated_survival_connection", !player.isCreative() && minecraft.getSingleplayerServer() == null
                        && !minecraft.getConnection().getConnection().isMemoryConnection());
                    signal("connected");
                    stage = Stage.OPEN_ITEM;
                }
                case OPEN_ITEM -> {
                    if (!ready("item") || !player.getOffhandItem().is(EIOItems.ADVANCED_ITEM_FILTER.get())
                        || !player.getMainHandItem().is(EIOItems.ADVANCED_ITEM_FILTER.get())) return;
                    if (crouchTick < 0) {
                        minecraft.options.keyShift.setDown(true);
                        crouchTick = player.tickCount;
                    } else if (player.tickCount - crouchTick >= 4 && player.isShiftKeyDown()) {
                        minecraft.gameMode.useItem(player, InteractionHand.OFF_HAND);
                        minecraft.options.keyShift.setDown(false);
                        menuOpenedAt = player.tickCount;
                        stage = Stage.ITEM_SAMPLE;
                    }
                }
                case ITEM_SAMPLE -> {
                    if (minecraft.screen instanceof EnderItemFilterScreen screen && screen.getMenu().isInverted()
                        && screen.getMenu().shouldCompareComponents() && screen.getMenu().getSlot(9).hasItem()) {
                        var menu = screen.getMenu();
                        check("offhand_editor_reads_the_migrated_sample_and_flags", menu.getSlot(9).getItem().is(Items.IRON_PICKAXE)
                            && menu.getSlot(9).getItem().getDamageValue() == 27);
                        var sample = playerSlot(screen, 1);
                        clickSlot(screen, sample);
                        clickSlot(screen, menu.getSlot(0));
                        clickSlot(screen, sample);
                        stage = Stage.ITEM_FLAGS;
                    } else if (player.tickCount - menuOpenedAt > 70) {
                        check("offhand_editor_stays_open_for_its_stack", false);
                    }
                }
                case ITEM_FLAGS -> {
                    if (!(minecraft.screen instanceof EnderItemFilterScreen screen)) {
                        check("offhand_editor_stays_open_for_its_stack", false);
                        return;
                    }
                    var ghost = screen.getMenu().getSlot(0).getItem();
                    if (!ghost.is(Items.GOLD_INGOT)) return;
                    check("ghost_sample_retains_tag_without_consuming_the_source", ghost.getCount() == 1
                        && ghost.getTag().getInt("MenuSample") == 71 && playerSlot(screen, 1).getItem().getCount() == 7
                        && screen.getMenu().getCarried().isEmpty());
                    toggle(screen, 1);
                    stage = Stage.ITEM_SAVE;
                }
                case ITEM_SAVE -> {
                    if (!(minecraft.screen instanceof EnderItemFilterScreen screen)) {
                        check("offhand_editor_stays_open_for_its_stack", false);
                        return;
                    }
                    if (screen.getMenu().isInverted()) return;
                    check("item_editor_synchronizes_allow_mode", screen.getMenu().shouldCompareComponents());
                    capture("offhand-item-filter");
                    player.closeContainer();
                    signal("item-edited");
                    stage = Stage.OPEN_CONDUIT;
                }
                case OPEN_CONDUIT -> {
                    if (!ready("fluid") || minecraft.screen != null || player.distanceToSqr(4639.5, 80, 10.5) > 0.1
                        || !(minecraft.level.getBlockEntity(FLUID_CONDUIT) instanceof ConduitBundleBlockEntity bundle)) return;
                    var hit = new BlockHitResult(Vec3.atCenterOf(FLUID_CONDUIT).relative(Direction.WEST, 0.45),
                        Direction.SOUTH, FLUID_CONDUIT, false);
                    var endpoint = bundle.getShape().getConnectionFromHit(FLUID_CONDUIT, hit);
                    if (endpoint == null || endpoint.getFirst() != Direction.WEST) return;
                    minecraft.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit);
                    stage = Stage.CONFIGURE_FLUID;
                }
                case CONFIGURE_FLUID -> {
                    if (!(minecraft.screen instanceof ConduitScreen screen) || !screen.getMenu().getBlockPos().equals(FLUID_CONDUIT)
                        || !screen.getMenu().getSlot(0).getItem().is(EIOItems.BASIC_FLUID_FILTER.get())) return;
                    // The extraction-side configure widget is to the right of the center divider.
                    var button = screen.children().stream().filter(IconButton.class::isInstance).map(IconButton.class::cast)
                        .filter(widget -> widget.getX() == screen.getGuiLeft() + 113 && widget.getY() == screen.getGuiTop() + 89)
                        .findFirst().orElseThrow();
                    if (!button.visible) capture("disabled-fluid-configure");
                    check("installed_fluid_filter_exposes_its_configure_button", button.visible);
                    click(screen, button.getX() + 2, button.getY() + 2);
                    stage = Stage.FLUID_SAMPLE;
                }
                case FLUID_SAMPLE -> {
                    if (!(minecraft.screen instanceof EnderFluidFilterScreen screen) || !screen.getMenu().shouldCompareComponents()
                        || fluidSlot(screen, 4).getResource().isEmpty()) return;
                    var original = fluidSlot(screen, 4).getResource();
                    check("installed_fluid_menu_displays_original_tag_and_amount", original.getFluid() == Fluids.WATER
                        && original.getAmount() == 37 && original.getTag().getInt("Sample") == 43 && !screen.getMenu().isInverted());
                    var bucket = playerSlot(screen, 2);
                    if (!bucket.getItem().is(Items.WATER_BUCKET)) return;
                    clickSlot(screen, bucket);
                    clickSlot(screen, fluidSlot(screen, 0));
                    clickSlot(screen, bucket);
                    stage = Stage.FLUID_CLEAR;
                }
                case FLUID_CLEAR -> {
                    if (!(minecraft.screen instanceof EnderFluidFilterScreen screen)
                        || fluidSlot(screen, 0).getResource().isEmpty()) return;
                    var sample = fluidSlot(screen, 0).getResource();
                    check("bucket_copies_fluid_without_consuming_the_item", sample.getFluid() == Fluids.WATER
                        && sample.getAmount() == 1000 && playerSlot(screen, 2).getItem().is(Items.WATER_BUCKET)
                        && screen.getMenu().getCarried().isEmpty());
                    clickSlot(screen, fluidSlot(screen, 4));
                    stage = Stage.FLUID_FLAGS;
                }
                case FLUID_FLAGS -> {
                    if (!(minecraft.screen instanceof EnderFluidFilterScreen screen) || !fluidSlot(screen, 4).getResource().isEmpty()) return;
                    check("installed_fluid_filter_clears_the_old_sample", true);
                    toggle(screen, 0);
                    stage = Stage.FLUID_SAVE;
                }
                case FLUID_SAVE -> {
                    if (!(minecraft.screen instanceof EnderFluidFilterScreen screen) || screen.getMenu().shouldCompareComponents()) return;
                    capture("installed-fluid-filter");
                    click(screen, screen.getGuiLeft() + 5, screen.getGuiTop() + 5);
                    stage = Stage.BACK;
                }
                case BACK -> {
                    if (!(minecraft.screen instanceof ConduitScreen screen)
                        || !screen.getMenu().getSlot(0).getItem().is(EIOItems.BASIC_FLUID_FILTER.get())) return;
                    check("back_returns_to_the_same_conduit_endpoint", screen.getMenu().getBlockPos().equals(FLUID_CONDUIT)
                        && screen.getMenu().getSide() == Direction.WEST);
                    signal("fluid-edited");
                    stage = Stage.CONFIRM;
                }
                case CONFIRM -> {
                    if (ready("complete")) finish(null);
                }
                default -> { }
            }
        } catch (Throwable failure) {
            failure.printStackTrace();
            finish(failure);
        }
    }

    private static FluidFilterSlot fluidSlot(EnderFluidFilterScreen screen, int index) {
        return (FluidFilterSlot) screen.getMenu().getSlot(index);
    }

    private Slot playerSlot(AbstractContainerScreen<?> screen, int index) {
        return screen.getMenu().slots.stream().filter(slot -> slot.container == minecraft.player.getInventory()
            && slot.getSlotIndex() == index).findFirst().orElseThrow();
    }

    private static void clickSlot(AbstractContainerScreen<?> screen, Slot slot) {
        click(screen, screen.getGuiLeft() + slot.x + 6, screen.getGuiTop() + slot.y + 6);
    }

    private static void click(AbstractContainerScreen<?> screen, double x, double y) {
        screen.mouseClicked(x, y, 0);
        screen.mouseReleased(x, y, 0);
    }

    private static void toggle(AbstractContainerScreen<?> screen, int index) {
        var widget = screen.children().stream().filter(ToggleIconButton.class::isInstance).map(ToggleIconButton.class::cast)
            .skip(index).findFirst().orElseThrow();
        click(screen, widget.getX() + 2, widget.getY() + 2);
    }

    private void connect() {
        if (!(minecraft.screen instanceof TitleScreen) || minecraft.getOverlay() != null) return;
        if (titleReady == 0) titleReady = System.nanoTime();
        if (System.nanoTime() - titleReady < 2_000_000_000L) return;
        ConnectScreen.startConnecting(new TitleScreen(), minecraft, ServerAddress.parseString(address),
            new ServerData("Ender IO transfer filters", address, true), false);
        stage = Stage.JOIN;
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
        ClientSmoke.complete("client_transfer_filter_menus", result);
    }
}
