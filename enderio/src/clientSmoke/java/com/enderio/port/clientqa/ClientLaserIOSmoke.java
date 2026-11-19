package com.enderio.port.clientqa;

import com.enderio.enderio.client.content.conduits.gui.ConduitScreen;
import com.enderio.enderio.content.conduits.bundle.ConduitBundleBlockEntity;
import com.enderio.enderio.content.conduits.menu.ConduitMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
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

final class ClientLaserIOSmoke {
    private enum Stage { TITLE, JOIN, OPEN, TAKE, RETURN, VERIFIED, DONE }

    private record Fixture(BlockPos pos, Direction side, int slot, String item) {
        int lane() {
            return pos.getZ();
        }

        boolean matches(ItemStack stack) {
            return stack.getCount() == 1 && BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals("laserio:" + item);
        }
    }

    private static final List<Fixture> FIXTURES = List.of(
        new Fixture(new BlockPos(3279, 80, 4), Direction.WEST, 0, "filter_basic"),
        new Fixture(new BlockPos(3280, 80, 7), Direction.EAST, 1, "card_item"),
        new Fixture(new BlockPos(3279, 80, 10), Direction.WEST, 0, "filter_basic"),
        new Fixture(new BlockPos(3280, 80, 13), Direction.EAST, 1, "card_fluid"));

    private final Minecraft minecraft = Minecraft.getInstance();
    private final String address = System.getProperty("enderio.clientqa.server");
    private final Path output = Path.of(System.getProperty("enderio.clientqa.output"));
    private final long started = System.nanoTime();
    private final Map<String, Boolean> checks = new LinkedHashMap<>();
    private final Map<String, Object> captures = new LinkedHashMap<>();
    private Stage stage = Stage.TITLE;
    private int index;
    private long titleReady;
    private ItemStack pickedUp = ItemStack.EMPTY;

    ClientLaserIOSmoke() {
        MinecraftForge.EVENT_BUS.addListener(this::render);
    }

    private void render(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage == Stage.DONE) return;
        try {
            if (minecraft.screen instanceof DisconnectedScreen) throw new AssertionError("Disconnected in " + stage);
            if (System.nanoTime() - started > 165_000_000_000L) throw new AssertionError("Timed out in " + stage + " on fixture " + index);
            if (stage == Stage.TITLE) {
                if (!(minecraft.screen instanceof TitleScreen) || minecraft.getOverlay() != null) return;
                if (titleReady == 0) titleReady = System.nanoTime();
                if (System.nanoTime() - titleReady < 2_000_000_000L) return;
                ConnectScreen.startConnecting(new TitleScreen(), minecraft, ServerAddress.parseString(address),
                    new ServerData("Ender IO LaserIO filter test", address, true), false);
                stage = Stage.JOIN;
                return;
            }
            if (minecraft.player == null || minecraft.level == null) return;
            var fixture = FIXTURES.get(index);
            if (stage == Stage.JOIN) {
                if (minecraft.screen != null) return;
                var connection = minecraft.getConnection().getConnection();
                check("forge_survival_tcp", !minecraft.player.isCreative() && !minecraft.player.isSpectator()
                    && minecraft.getSingleplayerServer() == null && !connection.isMemoryConnection()
                    && connection.getRemoteAddress() instanceof InetSocketAddress);
                signal("connected");
                stage = Stage.OPEN;
            }
            if (stage == Stage.OPEN) {
                open(fixture);
                return;
            }
            if (!(minecraft.screen instanceof ConduitScreen screen)) return;
            var menu = screen.getMenu();
            if (!menu.getBlockPos().equals(fixture.pos()) || menu.getSide() != fixture.side()) return;
            var filter = menu.getSlot(fixture.slot());
            switch (stage) {
                case TAKE -> {
                    if (!fixture.matches(filter.getItem())) return;
                    check("menu_synchronizes_filter_" + fixture.lane(), menu.getCarried().isEmpty());
                    pickedUp = filter.getItem().copy();
                    click(menu, fixture.slot());
                    signal("removed-" + fixture.lane());
                    stage = Stage.RETURN;
                }
                case RETURN -> {
                    if (!marker("removed", fixture) || filter.hasItem() || !fixture.matches(menu.getCarried())) return;
                    check("slot_accepts_laserio_item_" + fixture.lane(), filter.mayPlace(menu.getCarried())
                        && ItemStack.matches(pickedUp, menu.getCarried()));
                    click(menu, fixture.slot());
                    signal("installed-" + fixture.lane());
                    stage = Stage.VERIFIED;
                }
                case VERIFIED -> {
                    if (!marker("installed", fixture) || !fixture.matches(filter.getItem()) || !menu.getCarried().isEmpty()) return;
                    check("returned_stack_keeps_its_data_" + fixture.lane(), ItemStack.matches(pickedUp, filter.getItem()));
                    if (index == FIXTURES.size() - 1) {
                        String name = "laserio-card-in-fluid-conduit";
                        var capture = ClientSmoke.capture(name + ".png");
                        captures.put(name, capture);
                        check("silent_hidden_conduit_menu", Boolean.TRUE.equals(capture.get("passed")));
                        finish(null);
                    } else {
                        minecraft.player.closeContainer();
                        index++;
                        stage = Stage.OPEN;
                    }
                }
                default -> { }
            }
        } catch (Throwable failure) {
            failure.printStackTrace();
            finish(failure);
        }
    }

    private void open(Fixture fixture) {
        var pos = fixture.pos();
        if (!marker("menu", fixture) || minecraft.screen != null
            || minecraft.player.distanceToSqr(pos.getX() + 0.5, 80, fixture.lane() + 2.5) > 0.1
            || !(minecraft.level.getBlockEntity(pos) instanceof ConduitBundleBlockEntity conduit)) return;
        var hit = new BlockHitResult(Vec3.atCenterOf(pos).relative(fixture.side(), 0.45), Direction.SOUTH, pos, false);
        var connection = conduit.getShape().getConnectionFromHit(pos, hit);
        if (connection == null || connection.getFirst() != fixture.side()) return;
        minecraft.gameMode.useItemOn(minecraft.player, InteractionHand.MAIN_HAND, hit);
        stage = Stage.TAKE;
    }

    private boolean marker(String step, Fixture fixture) {
        return Files.exists(output.resolve("server-" + step + "-" + fixture.lane()));
    }

    private void click(ConduitMenu menu, int slot) {
        minecraft.gameMode.handleInventoryMouseClick(menu.containerId, slot, 0, ClickType.PICKUP, minecraft.player);
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
        result.put("passed", failure == null);
        result.put("checks", checks);
        result.put("captures", captures);
        if (failure != null) result.put("error", failure.toString());
        ClientSmoke.complete("client_laserio_filters", result);
    }
}
