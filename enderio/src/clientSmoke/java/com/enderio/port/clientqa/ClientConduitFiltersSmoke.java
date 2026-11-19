package com.enderio.port.clientqa;

import com.enderio.enderio.client.content.conduits.gui.ConduitScreen;
import com.enderio.enderio.content.conduits.bundle.ConduitBundleBlockEntity;
import com.enderio.enderio.content.conduits.menu.ConduitMenu;
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
import net.minecraft.world.inventory.ClickType;
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

final class ClientConduitFiltersSmoke {
    private enum Stage { TITLE, JOIN, OPEN, REMOVE, BACK_TO_INVENTORY, CHECK_WRONG_SLOT, SHIFT_IN, SHIFT_OUT, RESTORE, CAPTURE, DONE }

    private static final BlockPos OUTPUT = new BlockPos(3312, 80, 8);
    private static final int INPUT_SLOT = 0;
    private static final int OUTPUT_SLOT = 1;
    private final Minecraft minecraft = Minecraft.getInstance();
    private final Path output = Path.of(System.getProperty("enderio.clientqa.output"));
    private final String address = System.getProperty("enderio.clientqa.server");
    private final boolean baseline = "verify".equals(System.getProperty("enderio.clientqa.multiplayerPhase"));
    private final long started = System.nanoTime();
    private final Map<String, Boolean> checks = new LinkedHashMap<>();
    private final Map<String, Object> captures = new LinkedHashMap<>();
    private Stage stage = Stage.TITLE;
    private long titleReady;
    private ItemStack inverter = ItemStack.EMPTY;
    private ItemStack andGate = ItemStack.EMPTY;

    ClientConduitFiltersSmoke() {
        MinecraftForge.EVENT_BUS.addListener(this::render);
    }

    private void render(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage == Stage.DONE) return;
        try {
            if (minecraft.screen instanceof DisconnectedScreen || System.nanoTime() - started > 160_000_000_000L) {
                throw new AssertionError("Conduit filter smoke stopped in " + stage);
            }
            if (stage == Stage.TITLE) {
                connect();
                return;
            }
            var player = minecraft.player;
            if (player == null || minecraft.level == null) return;
            if (stage == Stage.JOIN) {
                if (minecraft.screen != null || minecraft.getToasts().getToast(SystemToast.class,
                    SystemToast.SystemToastIds.UNSECURE_SERVER_WARNING) != null) return;
                check("real_dedicated_survival_connection", !player.isCreative() && !player.isSpectator()
                    && minecraft.getSingleplayerServer() == null && !minecraft.getConnection().getConnection().isMemoryConnection());
                signal("connected");
                stage = Stage.OPEN;
                return;
            }
            if (stage == Stage.OPEN) {
                if (!marker("ready") || minecraft.screen != null || player.distanceToSqr(3312.5, 80, 10.5) > 0.1
                    || !(minecraft.level.getBlockEntity(OUTPUT) instanceof ConduitBundleBlockEntity bundle)) return;
                var hit = new BlockHitResult(Vec3.atCenterOf(OUTPUT).relative(Direction.EAST, 0.45), Direction.SOUTH, OUTPUT, false);
                var connection = bundle.getShape().getConnectionFromHit(OUTPUT, hit);
                if (connection == null || connection.getFirst() != Direction.EAST) return;
                minecraft.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit);
                stage = Stage.REMOVE;
                return;
            }
            if (minecraft.screen instanceof ConduitScreen screen) checkSlots(screen.getMenu());
        } catch (Throwable failure) {
            failure.printStackTrace();
            finish(failure);
        }
    }

    private void checkSlots(ConduitMenu menu) throws IOException {
        if (!menu.getBlockPos().equals(OUTPUT) || menu.getSide() != Direction.EAST) return;
        Slot out = menu.getSlot(OUTPUT_SLOT);
        Slot in = menu.getSlot(INPUT_SLOT);
        Slot source = playerSlot(menu, 0);
        Slot recovery = playerSlot(menu, 1);
        switch (stage) {
            case REMOVE -> {
                if (!out.getItem().is(EIOItems.NOT_FILTER.get()) || !source.getItem().is(EIOItems.AND_FILTER.get())) return;
                inverter = out.getItem().copy();
                andGate = source.getItem().copy();
                check("native_menu_syncs_both_configured_stacks", "inverter".equals(inverter.getTag().getString("PortFilter"))
                    && "gate".equals(andGate.getTag().getString("PortFilter")) && in.getItem().isEmpty());
                click(menu, out, ClickType.PICKUP);
                signal("inverter-picked-up");
                stage = Stage.BACK_TO_INVENTORY;
            }
            case BACK_TO_INVENTORY -> {
                if (!marker("inverter-removed") || !ItemStack.matches(inverter, menu.getCarried()) || out.hasItem()) return;
                if (baseline) {
                    check("native_pickup_returns_the_exact_inverter", !in.hasItem());
                    capture();
                    finish(null);
                    return;
                }
                click(menu, recovery, ClickType.PICKUP);
                stage = Stage.CHECK_WRONG_SLOT;
            }
            case CHECK_WRONG_SLOT -> {
                if (!menu.getCarried().isEmpty() || !ItemStack.matches(inverter, recovery.getItem())) return;
                Slot unrelated = playerSlot(menu, 2);
                click(menu, unrelated, ClickType.QUICK_MOVE);
                check("ordinary_item_stays_in_player_inventory", unrelated.getItem().is(Items.STICK) && !in.hasItem() && !out.hasItem());
                click(menu, source, ClickType.PICKUP);
                click(menu, in, ClickType.PICKUP);
                check("extract_slot_rejects_and_filter", !in.hasItem() && ItemStack.matches(menu.getCarried(), andGate));
                click(menu, source, ClickType.PICKUP);
                click(menu, source, ClickType.QUICK_MOVE);
                signal("gate-shifted-in");
                stage = Stage.SHIFT_IN;
            }
            case SHIFT_IN -> {
                if (!marker("gate-installed") || !ItemStack.matches(andGate, out.getItem())) return;
                check("shift_install_moves_only_the_configured_gate", !source.hasItem() && !in.hasItem() && menu.getCarried().isEmpty());
                click(menu, out, ClickType.QUICK_MOVE);
                signal("gate-shifted-out");
                stage = Stage.SHIFT_OUT;
            }
            case SHIFT_OUT -> {
                if (!marker("gate-recovered") || out.hasItem()) return;
                int copies = minecraft.player.getInventory().items.stream().filter(stack -> ItemStack.matches(andGate, stack))
                    .mapToInt(ItemStack::getCount).sum();
                check("shift_recovery_returns_one_exact_gate", copies == 1 && menu.getCarried().isEmpty());
                click(menu, recovery, ClickType.PICKUP);
                click(menu, out, ClickType.PICKUP);
                signal("inverter-restored");
                stage = Stage.RESTORE;
            }
            case RESTORE -> {
                if (!marker("inverter-lit") || !ItemStack.matches(inverter, out.getItem())) return;
                check("native_pickup_restores_inverter_without_duplication", !recovery.hasItem() && menu.getCarried().isEmpty());
                stage = Stage.CAPTURE;
            }
            case CAPTURE -> {
                capture();
                finish(null);
            }
            default -> { }
        }
    }

    private void capture() throws IOException {
        var frame = ClientSmoke.capture("redstone-conduit-filters.png");
        captures.put("redstone-conduit-filters", frame);
        check("conduit_screen_is_silent_and_hidden", Boolean.TRUE.equals(frame.get("passed")));
    }

    private Slot playerSlot(ConduitMenu menu, int index) {
        return menu.slots.stream().filter(slot -> slot.container == minecraft.player.getInventory() && slot.getSlotIndex() == index)
            .findFirst().orElseThrow();
    }

    private void click(ConduitMenu menu, Slot slot, ClickType type) {
        minecraft.gameMode.handleInventoryMouseClick(menu.containerId, slot.index, 0, type, minecraft.player);
    }

    private void connect() {
        if (!(minecraft.screen instanceof TitleScreen) || minecraft.getOverlay() != null) return;
        if (titleReady == 0) titleReady = System.nanoTime();
        if (System.nanoTime() - titleReady < 2_000_000_000L) return;
        ConnectScreen.startConnecting(new TitleScreen(), minecraft, ServerAddress.parseString(address),
            new ServerData("Ender IO conduit filters", address, true), false);
        stage = Stage.JOIN;
    }

    private boolean marker(String name) {
        return Files.exists(output.resolve("server-" + name));
    }

    private void signal(String name) throws IOException {
        var signals = output.resolve("signals");
        Files.createDirectories(signals);
        Files.writeString(signals.resolve(name + ".json"), "{}");
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
        ClientSmoke.complete("client_conduit_filters", result);
    }
}
