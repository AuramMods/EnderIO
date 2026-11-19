package com.enderio.port.clientqa;

import com.enderio.core.client.gui.widgets.IconButton;
import com.enderio.enderio.api.EnderIODataComponents;
import com.enderio.enderio.client.content.conduits.gui.ConduitScreen;
import com.enderio.enderio.client.content.filters.item.LimitedItemFilterScreen;
import com.enderio.enderio.content.conduits.bundle.ConduitBundleBlockEntity;
import com.enderio.enderio.content.conduits.type.item.ItemConduit;
import com.enderio.enderio.content.conduits.type.item.ItemConduitConnectionConfig;
import com.enderio.enderio.content.filters.item.limited.LimitedItemFilterSlot;
import com.enderio.enderio.init.EIOConduits;
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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

final class ClientFilterRecoverySmoke {
    enum Mode {
        BREAK, WRENCH_EDITOR, WRENCH_OPERATOR
    }

    private enum Stage {
        TITLE, JOIN, OPEN, ORIGINAL, ORIGINAL_FILTER, BREAK, PICKUP, PLACE, PLACED, REOPEN, RESTORE, REINSTALLED,
        RESTORED_FILTER, RETURN, OPERATOR_READY, WRENCH, WRENCHED, CONFIRMED, DONE
    }

    private static final BlockPos ENDPOINT = new BlockPos(3280, 80, 1);
    private static final int FILTER_SLOT = ItemConduit.INSERT_FILTER_SLOT;
    private static final ResourceLocation ITEM_CONDUIT = ResourceLocation.parse("enderio:item");

    private final Minecraft minecraft = Minecraft.getInstance();
    private final Path output = Path.of(System.getProperty("enderio.clientqa.output"));
    private final String address = System.getProperty("enderio.clientqa.server");
    private final long started = System.nanoTime();
    private final Map<String, Boolean> checks = new LinkedHashMap<>();
    private final Map<String, Object> captures = new LinkedHashMap<>();
    private final Mode mode;
    private Stage stage = Stage.TITLE;
    private long titleReady;
    private int brokenAt = -1;
    private int crouchTick;
    private int recoveredConduitSlot;
    private boolean insertClicked;
    private ItemStack originalFilter = ItemStack.EMPTY;

    ClientFilterRecoverySmoke() {
        this(Mode.BREAK);
    }

    ClientFilterRecoverySmoke(Mode mode) {
        this.mode = mode;
        MinecraftForge.EVENT_BUS.addListener(this::render);
    }

    private void render(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage == Stage.DONE) return;
        try {
            if (minecraft.screen instanceof DisconnectedScreen || System.nanoTime() - started > 165_000_000_000L) {
                throw new AssertionError("Filter recovery stopped in " + stage);
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
                    check("dedicated_survival_connection", !player.isCreative() && !player.isSpectator()
                        && minecraft.getSingleplayerServer() == null && !minecraft.getConnection().getConnection().isMemoryConnection());
                    signal("connected");
                    stage = mode == Mode.WRENCH_OPERATOR ? Stage.OPERATOR_READY : Stage.OPEN;
                }
                case OPERATOR_READY -> {
                    if (!ready("wrench") || !player.getMainHandItem().is(EIOItems.YETA_WRENCH.get())) return;
                    var bundle = bundle();
                    if (bundle == null || itemConnectionHit(bundle) == null || !hasRedstone(bundle)) return;
                    minecraft.options.keyShift.setDown(true);
                    crouchTick = player.tickCount;
                    stage = Stage.WRENCH;
                }
                case WRENCH -> {
                    if (player.tickCount - crouchTick < 4 || !player.isSteppingCarefully()) return;
                    var bundle = bundle();
                    var hit = bundle == null ? null : itemConnectionHit(bundle);
                    check("crouch_wrench_targets_the_item_part_of_the_mixed_bundle", hit != null && hasRedstone(bundle));
                    var result = minecraft.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit);
                    minecraft.options.keyShift.setDown(false);
                    check("native_wrench_use_is_consumed", result.consumesAction());
                    stage = Stage.WRENCHED;
                }
                case WRENCHED -> {
                    var bundle = bundle();
                    if (bundle == null || hasItem(bundle)) return;
                    check("operator_receives_the_redstone_only_bundle_update", hasRedstone(bundle));
                    signal("wrenched");
                    stage = Stage.CONFIRMED;
                }
                case OPEN -> {
                    if (!ready("initial") || !nearEndpoint() || !openEndpoint()) return;
                    stage = Stage.ORIGINAL;
                }
                case ORIGINAL -> {
                    var screen = endpointScreen();
                    if (screen == null) return;
                    var stack = screen.getMenu().getSlot(FILTER_SLOT).getItem();
                    if (!stack.is(EIOItems.LIMITED_ITEM_FILTER.get()) || !openEditor(screen)) return;
                    originalFilter = stack.copy();
                    stage = Stage.ORIGINAL_FILTER;
                }
                case ORIGINAL_FILTER -> {
                    if (!(minecraft.screen instanceof LimitedItemFilterScreen screen) || !showsStock(screen)) return;
                    check("installed_editor_reads_original_full_stock", nearEndpoint());
                    capture("before-break");
                    signal("editor-open");
                    stage = Stage.BREAK;
                }
                case BREAK -> {
                    if (!ready("broken")) return;
                    if (mode == Mode.WRENCH_EDITOR) {
                        var bundle = bundle();
                        if (bundle == null || hasItem(bundle)) return;
                        check("editor_receives_the_redstone_only_bundle_update", hasRedstone(bundle));
                    } else if (!minecraft.level.getBlockState(ENDPOINT).isAir()) {
                        return;
                    }
                    if (brokenAt == -1) brokenAt = player.tickCount;
                    if (minecraft.screen != null && player.tickCount - brokenAt < 50) return;
                    check("endpoint_removal_closes_the_editor_while_the_player_stays_in_range",
                        minecraft.screen == null && nearEndpoint());
                    signal("editor-closed");
                    stage = Stage.PICKUP;
                }
                case PICKUP -> {
                    var inventory = player.getInventory();
                    int conduitSlot = -1;
                    int filters = 0;
                    for (int i = 0; i < inventory.items.size(); i++) {
                        var stack = inventory.getItem(i);
                        if (ItemStack.matches(originalFilter, stack)) filters += stack.getCount();
                        if (stack.is(EIOItems.CONDUIT.get()) && ITEM_CONDUIT.equals(EnderIODataComponents.CONDUIT.getId(stack))) {
                            conduitSlot = i;
                        }
                    }
                    if (filters == 0 || conduitSlot < 0) return;
                    check("ordinary_pickup_recovers_one_exact_filter_and_one_item_conduit",
                        filters == 1 && inventory.getItem(conduitSlot).getCount() == 1 && conduitSlot < 9);
                    recoveredConduitSlot = conduitSlot;
                    signal("picked-up");
                    stage = Stage.PLACE;
                }
                case PLACE -> {
                    if (!ready("place")) return;
                    player.getInventory().selected = recoveredConduitSlot;
                    var hit = new BlockHitResult(Vec3.atBottomCenterOf(ENDPOINT), Direction.UP, ENDPOINT.below(), false);
                    if (mode == Mode.WRENCH_EDITOR) {
                        var bundle = bundle();
                        check("redstone_is_still_installed_before_adding_the_recovered_conduit",
                            bundle != null && hasRedstone(bundle) && !hasItem(bundle));
                        var redstone = bundle.getConduits().get(0);
                        var box = bundle.getShape().getShapeFor(redstone).toAabbs().get(0);
                        hit = new BlockHitResult(Vec3.atLowerCornerOf(ENDPOINT).add(box.getCenter()),
                            Direction.SOUTH, ENDPOINT, false);
                    }
                    minecraft.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit);
                    stage = Stage.PLACED;
                }
                case PLACED -> {
                    var bundle = bundle();
                    if (bundle == null || !hasItem(bundle)) return;
                    if (player.getInventory().items.stream().anyMatch(stack -> stack.is(EIOItems.CONDUIT.get()))) return;
                    check("replaced_endpoint_uses_the_picked_up_item", true);
                    signal("conduit-placed");
                    stage = Stage.REOPEN;
                }
                case REOPEN -> {
                    if (!ready("reopen") || !openEndpoint()) return;
                    stage = Stage.RESTORE;
                }
                case RESTORE -> {
                    var screen = endpointScreen();
                    if (screen == null) return;
                    var config = (ItemConduitConnectionConfig) screen.getMenu().connectionConfig();
                    if (!config.isInsert()) {
                        if (!insertClicked) {
                            click(screen, screen.getGuiLeft() + 24, screen.getGuiTop() + 9);
                            insertClicked = true;
                        }
                        return;
                    }
                    var source = screen.getMenu().slots.stream().filter(slot -> slot.container == player.getInventory()
                        && ItemStack.matches(originalFilter, slot.getItem())).findFirst().orElse(null);
                    if (source == null) return;
                    clickSlot(screen, source);
                    clickSlot(screen, screen.getMenu().getSlot(FILTER_SLOT));
                    stage = Stage.REINSTALLED;
                }
                case REINSTALLED -> {
                    var screen = endpointScreen();
                    if (screen == null || !ItemStack.matches(originalFilter, screen.getMenu().getSlot(FILTER_SLOT).getItem())
                        || !openEditor(screen)) return;
                    check("reinstallation_uses_the_exact_recovered_filter", screen.getMenu().getCarried().isEmpty());
                    stage = Stage.RESTORED_FILTER;
                }
                case RESTORED_FILTER -> {
                    if (!(minecraft.screen instanceof LimitedItemFilterScreen screen) || !showsStock(screen)) return;
                    check("replacement_editor_displays_the_same_stock_samples", true);
                    capture("after-recovery");
                    click(screen, screen.getGuiLeft() + 5, screen.getGuiTop() + 5);
                    stage = Stage.RETURN;
                }
                case RETURN -> {
                    var screen = endpointScreen();
                    if (screen == null) return;
                    check("back_returns_to_the_replaced_endpoint_with_the_same_filter",
                        ItemStack.matches(originalFilter, screen.getMenu().getSlot(FILTER_SLOT).getItem()));
                    signal("filter-reinstalled");
                    stage = Stage.CONFIRMED;
                }
                case CONFIRMED -> {
                    if (ready("complete")) finish(null);
                }
                default -> { }
            }
        } catch (Throwable failure) {
            failure.printStackTrace();
            finish(failure);
        }
    }

    private boolean nearEndpoint() {
        return minecraft.player.distanceToSqr(3280.5, 80, 3.5) < 0.15;
    }

    private boolean openEndpoint() {
        var bundle = bundle();
        if (minecraft.screen != null || bundle == null) return false;
        var hit = itemConnectionHit(bundle);
        if (hit == null) return false;
        minecraft.gameMode.useItemOn(minecraft.player, InteractionHand.MAIN_HAND, hit);
        return true;
    }

    private ConduitBundleBlockEntity bundle() {
        return minecraft.level.getBlockEntity(ENDPOINT) instanceof ConduitBundleBlockEntity bundle ? bundle : null;
    }

    private static boolean hasItem(ConduitBundleBlockEntity bundle) {
        return bundle.getConduits().stream().anyMatch(holder -> holder.is(EIOConduits.ITEM));
    }

    private static boolean hasRedstone(ConduitBundleBlockEntity bundle) {
        return bundle.getConduits().stream().anyMatch(holder -> holder.is(EIOConduits.REDSTONE));
    }

    private static BlockHitResult itemConnectionHit(ConduitBundleBlockEntity bundle) {
        // The outer edge of the east connector belongs to the item route. Mixed bundles can offset the pipes behind it.
        var hit = new BlockHitResult(Vec3.atCenterOf(ENDPOINT).relative(Direction.EAST, 0.48),
            Direction.SOUTH, ENDPOINT, false);
        var shape = bundle.getShape();
        var connection = shape.getConnectionFromHit(ENDPOINT, hit);
        var aimedConduit = shape.getConduit(ENDPOINT, hit);
        return connection != null && connection.getFirst() == Direction.EAST && connection.getSecond().is(EIOConduits.ITEM)
            && aimedConduit != null && aimedConduit.is(EIOConduits.ITEM) ? hit : null;
    }

    private ConduitScreen endpointScreen() {
        if (!(minecraft.screen instanceof ConduitScreen screen) || !screen.getMenu().getBlockPos().equals(ENDPOINT)
            || screen.getMenu().getSide() != Direction.EAST) return null;
        return screen;
    }

    private static boolean openEditor(ConduitScreen screen) {
        int x = screen.getGuiLeft() + 23;
        int y = screen.getGuiTop() + 89;
        var button = screen.children().stream().filter(IconButton.class::isInstance).map(IconButton.class::cast)
            .filter(widget -> widget.getX() == x && widget.getY() == y).findFirst().orElseThrow();
        if (!button.visible) return false;
        click(screen, x + 2, y + 2);
        return true;
    }

    private static boolean showsStock(LimitedItemFilterScreen screen) {
        var paper = ((LimitedItemFilterSlot) screen.getMenu().getSlot(0)).getResource();
        var cobble = ((LimitedItemFilterSlot) screen.getMenu().getSlot(17)).getResource();
        return screen.getMenu().shouldCompareComponents() && paper.is(Items.PAPER) && paper.getCount() == 64
            && cobble.is(Items.COBBLESTONE) && cobble.getCount() == 137 && cobble.getTag().getInt("StockSample") == 5;
    }

    private static void clickSlot(AbstractContainerScreen<?> screen, Slot slot) {
        click(screen, screen.getGuiLeft() + slot.x + 7, screen.getGuiTop() + slot.y + 7);
    }

    private static void click(AbstractContainerScreen<?> screen, double x, double y) {
        screen.mouseClicked(x, y, 0);
        screen.mouseReleased(x, y, 0);
    }

    private void connect() {
        if (!(minecraft.screen instanceof TitleScreen) || minecraft.getOverlay() != null) return;
        if (titleReady == 0) titleReady = System.nanoTime();
        if (System.nanoTime() - titleReady < 2_000_000_000L) return;
        ConnectScreen.startConnecting(new TitleScreen(), minecraft, ServerAddress.parseString(address),
            new ServerData("Ender IO filter recovery", address, true), false);
        stage = Stage.JOIN;
    }

    private boolean ready(String name) {
        return Files.exists(output.resolve("server-ready-" + name));
    }

    private void signal(String name) throws IOException {
        var signals = output.resolve("signals");
        Files.createDirectories(signals);
        var temp = Files.writeString(signals.resolve(name + ".tmp"), "{}");
        Files.move(temp, signals.resolve(name + ".json"));
    }

    private void capture(String name) throws IOException {
        var capture = ClientSmoke.capture(name + ".png");
        captures.put(name, capture);
        check(name + "_silent_capture", Boolean.TRUE.equals(capture.get("passed")));
    }

    private void check(String name, boolean passed) {
        checks.put(name, passed);
        if (!passed) throw new AssertionError(name);
    }

    private void finish(Throwable failure) {
        stage = Stage.DONE;
        minecraft.options.keyShift.setDown(false);
        var result = new LinkedHashMap<String, Object>();
        result.put("passed", failure == null);
        result.put("checks", checks);
        result.put("captures", captures);
        if (failure != null) result.put("error", failure.toString());
        ClientSmoke.complete("client_filter_recovery", result);
    }
}
