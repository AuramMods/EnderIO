package com.enderio.port.clientqa;

import com.direwolf20.laserio.client.screens.FilterBasicScreen;
import com.direwolf20.laserio.common.items.filters.BaseFilter;
import com.direwolf20.laserio.common.items.filters.FilterBasic;
import com.enderio.enderio.client.content.conduits.gui.ConduitScreen;
import com.enderio.enderio.content.conduits.bundle.ConduitBundleBlockEntity;
import mekanism.common.capabilities.Capabilities;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.enderio.port.clientqa.ClientAlloyProgressionSmoke.id;
import static com.enderio.port.clientqa.ClientAlloyProgressionSmoke.item;

final class ClientLaserIOEditorSmoke {
    private enum Stage { TITLE, JOIN, OPEN_EDITOR, SELECT, GHOST, STORE, CLOSE, OPEN_CONDUIT, INSTALL, INSTALLED, FINISH, DONE }

    private record Fixture(String name, int z, int filterSlot, int sampleSlot) {
        BlockPos conduit() { return new BlockPos(3279, 80, z); }
        boolean chemical() { return name.equals("chemical"); }
    }

    private static final List<Fixture> FIXTURES = List.of(new Fixture("item", 4, 0, 1), new Fixture("chemical", 10, 2, 3));
    private static final int GHOST_SLOT = 3;

    private final Minecraft minecraft = Minecraft.getInstance();
    private final Path output = Path.of(System.getProperty("enderio.clientqa.output"));
    private final long started = System.nanoTime();
    private final Map<String, Boolean> checks = new LinkedHashMap<>();
    private final Map<String, Object> captures = new LinkedHashMap<>();
    private Stage stage = Stage.TITLE;
    private long titleAt;
    private int settleBy = -1;
    private int index;
    private ItemStack sample = ItemStack.EMPTY;

    ClientLaserIOEditorSmoke() {
        MinecraftForge.EVENT_BUS.addListener(this::render);
    }

    private void render(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage == Stage.DONE) return;
        try {
            if (minecraft.screen instanceof DisconnectedScreen || System.nanoTime() - started > 175_000_000_000L) {
                throw new AssertionError("LaserIO editor stopped at " + stage + " for fixture " + index);
            }
            if (stage == Stage.TITLE) {
                connect();
                return;
            }
            var player = minecraft.player;
            if (player == null || minecraft.level == null) return;
            if (stage == Stage.FINISH) {
                if (ready("finish")) finish(null);
                return;
            }
            var fixture = FIXTURES.get(index);
            switch (stage) {
                case JOIN -> {
                    if (minecraft.screen != null || minecraft.getToasts().getToast(SystemToast.class,
                        SystemToast.SystemToastIds.UNSECURE_SERVER_WARNING) != null) return;
                    check("native_survival_tcp", !player.isCreative() && !player.isSpectator()
                        && !minecraft.getConnection().getConnection().isMemoryConnection());
                    signal("connected");
                    stage = Stage.OPEN_EDITOR;
                }
                case OPEN_EDITOR -> {
                    if (!ready(fixture.name() + "-editor") || minecraft.screen != null) return;
                    var original = player.getInventory().getItem(fixture.filterSlot());
                    if (!original.is(item("laserio:filter_basic")) || player.getInventory().getItem(fixture.sampleSlot()).isEmpty()) return;
                    check(fixture.name() + "_starts_with_an_empty_stock_filter",
                        FilterBasic.getInventory(original.copy()).getStackInSlot(GHOST_SLOT).isEmpty());
                    sample = player.getInventory().getItem(fixture.sampleSlot()).copy();
                    check(fixture.name() + "_uses_a_real_matching_sample", validSample(fixture, sample));
                    player.getInventory().selected = fixture.filterSlot();
                    minecraft.gameMode.useItem(player, InteractionHand.MAIN_HAND);
                    stage = Stage.SELECT;
                }
                case SELECT, GHOST, STORE -> edit(fixture);
                case CLOSE -> {
                    var stack = player.getInventory().getItem(fixture.filterSlot());
                    if (!configured(fixture, stack)) return;
                    check(fixture.name() + "_editor_keeps_the_original_physical_sample",
                        ItemStack.matches(sample, player.getInventory().getItem(fixture.sampleSlot())));
                    signal(fixture.name() + "-edited");
                    stage = Stage.OPEN_CONDUIT;
                }
                case OPEN_CONDUIT -> openConduit(fixture);
                case INSTALL -> {
                    if (!(minecraft.screen instanceof ConduitScreen screen)) return;
                    var menu = screen.getMenu();
                    if (!menu.getBlockPos().equals(fixture.conduit()) || menu.getSide() != Direction.WEST) return;
                    check(fixture.name() + "_native_extraction_slot_accepts_the_edited_filter",
                        !menu.getSlot(0).hasItem() && menu.getSlot(0).mayPlace(player.getInventory().getItem(fixture.filterSlot())));
                    click(menu, 0, fixture.filterSlot(), ClickType.SWAP);
                    stage = Stage.INSTALLED;
                }
                case INSTALLED -> {
                    if (!(minecraft.screen instanceof ConduitScreen screen) || !configured(fixture, screen.getMenu().getSlot(0).getItem())
                        || !player.getInventory().getItem(fixture.filterSlot()).isEmpty()) return;
                    check(fixture.name() + "_native_conduit_menu_preserves_the_ghost_sample_and_settings",
                        screen.getMenu().getCarried().isEmpty());
                    capture(fixture.name() + "-laserio-filter-installed");
                    player.closeContainer();
                    signal(fixture.name() + "-installed");
                    stage = ++index == FIXTURES.size() ? Stage.FINISH : Stage.OPEN_EDITOR;
                }
                default -> {}
            }
        } catch (Throwable failure) {
            failure.printStackTrace();
            finish(failure);
        }
    }

    private void edit(Fixture fixture) throws Exception {
        if (!(minecraft.screen instanceof FilterBasicScreen screen)) return;
        var menu = screen.getMenu();
        // LaserIO adds fifteen ghost slots followed by the vanilla inventory and hotbar.
        int sampleSlot = 15 + 27 + fixture.sampleSlot();
        switch (stage) {
            case SELECT -> {
                if (!ItemStack.matches(sample, menu.getSlot(sampleSlot).getItem())) return;
                if (!fixture.chemical()) {
                    check("native_editor_can_select_deny_mode", clickWidget(screen, 13, 13));
                    check("native_editor_can_enable_exact_nbt_matching", clickWidget(screen, 13, 33));
                }
                click(menu, sampleSlot, 0, ClickType.PICKUP);
                stage = Stage.GHOST;
            }
            case GHOST -> {
                if (!ItemStack.matches(sample, menu.getCarried())) return;
                var ghost = menu.getSlot(GHOST_SLOT);
                check(fixture.name() + "_uses_the_native_laserio_ghost_slot_click", clickWidget(screen, ghost.x + 8, ghost.y + 8));
                stage = Stage.STORE;
            }
            case STORE -> {
                if (!ItemStack.matches(sample, menu.getSlot(GHOST_SLOT).getItem())) return;
                check(fixture.name() + "_ghost_slot_copies_without_consuming_the_cursor",
                    ItemStack.matches(sample, menu.getCarried()));
                capture(fixture.name() + "-native-laserio-editor");
                click(menu, sampleSlot, 0, ClickType.PICKUP);
                screen.onClose();
                stage = Stage.CLOSE;
            }
            default -> {}
        }
    }

    private boolean clickWidget(FilterBasicScreen screen, int relativeX, int relativeY) {
        int x = screen.getGuiLeft() + relativeX;
        int y = screen.getGuiTop() + relativeY;
        // LaserIO reads the slot that was hovered during the last frame.
        var graphics = new GuiGraphics(minecraft, minecraft.renderBuffers().bufferSource());
        screen.render(graphics, x, y, 0);
        graphics.flush();
        return screen.mouseClicked(x, y, 0);
    }

    private boolean configured(Fixture fixture, ItemStack stack) {
        if (!stack.is(item("laserio:filter_basic"))) return false;
        var copy = stack.copy();
        return BaseFilter.getAllowList(copy) == fixture.chemical()
            && BaseFilter.getCompareNBT(copy) != fixture.chemical()
            && ItemStack.matches(sample, FilterBasic.getInventory(copy).getStackInSlot(GHOST_SLOT));
    }

    private boolean validSample(Fixture fixture, ItemStack stack) {
        if (!fixture.chemical()) return stack.is(Items.PAPER) && stack.hasTag() && stack.getTag().getInt("PortLaser") == 1;
        var handler = stack.getCapability(Capabilities.GAS_HANDLER).orElse(null);
        if (handler == null) return false;
        var gas = handler.getChemicalInTank(0);
        return gas.getAmount() == 1450 && id("mekanism:oxygen").equals(gas.getType().getRegistryName());
    }

    private void openConduit(Fixture fixture) throws Exception {
        var pos = fixture.conduit();
        var player = minecraft.player;
        if (!ready(fixture.name() + "-conduit") || minecraft.screen != null
            || player.distanceToSqr(Vec3.atBottomCenterOf(pos.south(2))) > 0.12) return;
        if (settleBy < 0) settleBy = player.tickCount + 6;
        if (player.tickCount < settleBy) return;
        settleBy = -1;
        if (!(minecraft.level.getBlockEntity(pos) instanceof ConduitBundleBlockEntity bundle)) return;
        var hit = new BlockHitResult(Vec3.atCenterOf(pos).relative(Direction.WEST, 0.45), Direction.SOUTH, pos, false);
        var face = bundle.getShape().getConnectionFromHit(pos, hit);
        check(fixture.name() + "_opens_the_actual_extraction_face", face != null && face.getFirst() == Direction.WEST);
        player.getInventory().selected = 8;
        minecraft.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit);
        stage = Stage.INSTALL;
    }

    private void click(AbstractContainerMenu menu, int slot, int button, ClickType type) {
        minecraft.gameMode.handleInventoryMouseClick(menu.containerId, slot, button, type, minecraft.player);
    }

    private void connect() {
        if (!(minecraft.screen instanceof TitleScreen) || minecraft.getOverlay() != null) return;
        if (titleAt == 0) titleAt = System.nanoTime();
        if (System.nanoTime() - titleAt < 2_000_000_000L) return;
        String address = System.getProperty("enderio.clientqa.server");
        ConnectScreen.startConnecting(new TitleScreen(), minecraft, ServerAddress.parseString(address),
            new ServerData("Ender IO LaserIO Editor", address, true), false);
        stage = Stage.JOIN;
    }

    private boolean ready(String name) { return Files.exists(output.resolve("server-" + name)); }

    private void signal(String name) throws Exception {
        var directory = output.resolve("signals");
        Files.createDirectories(directory);
        Files.writeString(directory.resolve(name + ".json"), "{}");
    }

    private void capture(String name) throws Exception {
        var frame = ClientSmoke.capture(name + ".png");
        captures.put(name, frame);
        check(name + "_renders_hidden_and_muted", Boolean.TRUE.equals(frame.get("passed")));
    }

    private void check(String name, boolean passed) {
        checks.put(name, passed);
        if (!passed) throw new AssertionError(name);
    }

    private void finish(Throwable failure) {
        stage = Stage.DONE;
        var result = new LinkedHashMap<String, Object>();
        result.put("passed", failure == null);
        result.put("checks", checks);
        result.put("captures", captures);
        if (failure != null) result.put("error", failure.toString());
        ClientSmoke.complete("client_laserio_editor", result);
    }
}
