package com.enderio.port.clientqa;

import com.enderio.enderio.content.machines.powered_spawner.PoweredSpawnerMenu;
import com.enderio.enderio.content.machines.powered_spawner.PoweredSpawnerMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.ModList;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class ClientInventorySorterSmoke {
    private enum Stage { TITLE, JOIN, OPEN, CHECK, WAIT, DONE }

    private record Fixture(String name, int z, String menu, boolean handheld, int... slots) {}

    private static final int X = 3400;
    private static final List<Fixture> FIXTURES = List.of(
        new Fixture("barrel", 4, "minecraft:generic_9x3", false, 5),
        new Fixture("crafter", 7, "enderio:crafter", false, 1, 11, 20),
        new Fixture("enchanter", 10, "enderio:enchanter", false, 3),
        new Fixture("powered_spawner", 13, "enderio:powered_spawner", false, 1, 2),
        new Fixture("basic_item_filter", 16, "enderio:basic_item_filter", true, 0),
        new Fixture("limited_item_filter", 16, "enderio:limited_item_filter", true, 0),
        new Fixture("basic_fluid_filter", 16, "enderio:basic_fluid_filter", true, 0));

    private final Minecraft minecraft = Minecraft.getInstance();
    private final boolean withSorter;
    private final String address = System.getProperty("enderio.clientqa.server");
    private final Path output = Path.of(System.getProperty("enderio.clientqa.output"));
    private final long started = System.nanoTime();
    private final Map<String, Boolean> checks = new LinkedHashMap<>();
    private final Map<String, Object> captures = new LinkedHashMap<>();
    private Stage stage = Stage.TITLE;
    private int index;
    private int openTick = -1;
    private int crouchTick = -1;
    private long titleReady;

    ClientInventorySorterSmoke(boolean withSorter) {
        this.withSorter = withSorter;
        MinecraftForge.EVENT_BUS.addListener(this::render);
    }

    private void render(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage == Stage.DONE) return;
        try {
            if (minecraft.screen instanceof DisconnectedScreen) throw new AssertionError("Disconnected in " + stage);
            if (System.nanoTime() - started > 175_000_000_000L) throw new AssertionError("Timed out in " + stage + " at fixture " + index);
            if (stage == Stage.TITLE) {
                connect();
                return;
            }
            if (minecraft.player == null || minecraft.level == null) return;
            var fixture = FIXTURES.get(index);
            switch (stage) {
                case JOIN -> {
                    if (minecraft.screen != null) return;
                    var conn = minecraft.getConnection().getConnection();
                    check("real_dedicated_survival_tcp", !minecraft.player.isCreative() && !minecraft.player.isSpectator()
                        && minecraft.getSingleplayerServer() == null && !conn.isMemoryConnection()
                        && conn.getRemoteAddress() instanceof InetSocketAddress);
                    check("expected_inventory_sorter_installation", ModList.get().isLoaded("inventorysorter") == withSorter);
                    signal("connected");
                    stage = Stage.OPEN;
                }
                case OPEN -> open(fixture);
                case CHECK -> {
                    if (!(minecraft.screen instanceof AbstractContainerScreen<?> screen)
                        || !BuiltInRegistries.MENU.getKey(screen.getMenu().getType()).toString().equals(fixture.menu())) return;
                    if (openTick == -1) openTick = minecraft.player.tickCount;
                    if (minecraft.player.tickCount - openTick < 6) return;
                    if (screen.getMenu() instanceof PoweredSpawnerMenu spawner
                        && spawner.getMode() != PoweredSpawnerMode.CAPTURE) return;
                    probe(fixture, screen);
                    signal("probed-" + fixture.name());
                    stage = Stage.WAIT;
                }
                case WAIT -> {
                    if (!marker("verified", fixture)) return;
                    if (index == FIXTURES.size() - 1) {
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

    private void connect() {
        if (!(minecraft.screen instanceof TitleScreen) || minecraft.getOverlay() != null) return;
        if (titleReady == 0) titleReady = System.nanoTime();
        if (System.nanoTime() - titleReady < 2_000_000_000L) return;
        ConnectScreen.startConnecting(new TitleScreen(), minecraft, ServerAddress.parseString(address),
            new ServerData("Ender IO Inventory Sorter", address, true), false);
        stage = Stage.JOIN;
    }

    private void open(Fixture fixture) {
        int z = fixture.z();
        if (!marker("open", fixture) || minecraft.screen != null
            || minecraft.player.distanceToSqr(X + 0.5, 80, z + 2.5) > 0.1) return;
        if (fixture.handheld()) {
            if (!BuiltInRegistries.ITEM.getKey(minecraft.player.getMainHandItem().getItem()).toString().equals(fixture.menu())) return;
            if (crouchTick == -1) {
                minecraft.options.keyShift.setDown(true);
                crouchTick = minecraft.player.tickCount;
                return;
            }
            if (minecraft.player.tickCount - crouchTick < 4 || !minecraft.player.isShiftKeyDown()) return;
            minecraft.gameMode.useItem(minecraft.player, InteractionHand.MAIN_HAND);
            minecraft.options.keyShift.setDown(false);
            crouchTick = -1;
        } else {
            var pos = new BlockPos(X, 80, z);
            if (minecraft.level.getBlockEntity(pos) == null) return;
            var hit = new BlockHitResult(new Vec3(X + 0.5, 80.6, z + 1), Direction.SOUTH, pos, false);
            minecraft.gameMode.useItemOn(minecraft.player, InteractionHand.MAIN_HAND, hit);
        }
        openTick = -1;
        stage = Stage.CHECK;
    }

    private void probe(Fixture fixture, AbstractContainerScreen<?> screen) throws IOException {
        for (int index : fixture.slots()) {
            var slot = screen.getMenu().getSlot(index);
            int mouseX = screen.getGuiLeft() + slot.x + 8;
            int mouseY = screen.getGuiTop() + slot.y + 8;
            var graphics = new GuiGraphics(minecraft, minecraft.renderBuffers().bufferSource());
            screen.render(graphics, mouseX, mouseY, 0);
            graphics.flush();
            String key = fixture.name() + "_" + index;
            check("native_hover_" + key, slot.isActive() && screen.getSlotUnderMouse() == slot);
            boolean handled = MinecraftForge.EVENT_BUS.post(new ScreenEvent.MouseButtonPressed.Pre(
                screen, mouseX, mouseY, GLFW.GLFW_MOUSE_BUTTON_MIDDLE));
            boolean normalSlot = fixture.name().equals("barrel");
            checks.put("sort_action_" + key, handled == (normalSlot && withSorter));
            if (!normalSlot) {
                checks.put("scroll_action_" + key, !MinecraftForge.EVENT_BUS.post(
                    new ScreenEvent.MouseScrolled.Pre(screen, mouseX, mouseY, 1)));
            }
        }
        if (fixture.name().equals("crafter") || fixture.name().equals("powered_spawner")) {
            var capture = ClientSmoke.capture(fixture.name() + ".png");
            captures.put(fixture.name(), capture);
            check("silent_hidden_capture_" + fixture.name(), Boolean.TRUE.equals(capture.get("passed")));
        }
    }

    private boolean marker(String step, Fixture fixture) {
        return Files.exists(output.resolve("server-" + step + "-" + fixture.name()));
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
        minecraft.options.keyShift.setDown(false);
        var result = new LinkedHashMap<String, Object>();
        result.put("passed", failure == null && checks.values().stream().allMatch(Boolean::booleanValue));
        result.put("checks", checks);
        result.put("captures", captures);
        if (failure != null) result.put("error", failure.toString());
        ClientSmoke.complete("client_inventory_sorter", result);
    }
}
