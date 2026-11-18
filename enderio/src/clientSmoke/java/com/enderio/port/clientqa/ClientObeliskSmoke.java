package com.enderio.port.clientqa;

import com.enderio.enderio.client.content.machines.gui.screen.WeatherObeliskScreen;
import com.enderio.enderio.client.content.machines.gui.screen.XPObeliskScreen;
import com.enderio.enderio.content.machines.obelisks.weather.WeatherObeliskMenu;
import com.enderio.enderio.content.machines.obelisks.xp.XPObeliskMenu;
import com.enderio.enderio.init.EIOBlocks;
import com.enderio.enderio.init.EIOFluids;
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

final class ClientObeliskSmoke {
    private enum Stage { TITLE, CONNECT, WAIT_XP, XP_BUTTONS, OPEN_WEATHER, WEATHER_PAUSED, WEATHER_RAIN, DONE }
    private record Button(String check, int x, int y, int points, int level, int fluid) { }

    private static final BlockPos XP = new BlockPos(1570, 80, 8);
    private static final BlockPos WEATHER = new BlockPos(1573, 80, 8);
    private static final BlockPos REPLANTED = new BlockPos(1570, 80, 11);
    private static final List<Button> BUTTONS = List.of(
        new Button("withdraw_one_level_keeps_sub_point_fluid", 56, 34, 6, 0, 7),
        new Button("store_all_moves_six_points", 120, 82, 0, 0, 127),
        new Button("withdraw_all_leaves_fractional_fluid", 120, 34, 6, 0, 7),
        new Button("store_ten_levels_crosses_level_boundaries", 88, 82, 16, 2, 4007),
        new Button("withdraw_ten_levels_restores_the_exact_balance", 88, 34, 216, 12, 7),
        new Button("store_one_level_keeps_the_remainder", 56, 82, 187, 11, 587),
        new Button("withdraw_one_level_restores_level_twelve", 56, 34, 216, 12, 7));

    private final Minecraft minecraft = Minecraft.getInstance();
    private final String address = System.getProperty("enderio.clientqa.server");
    private final Path output = Path.of(System.getProperty("enderio.clientqa.output"));
    private final Map<String, Boolean> checks = new LinkedHashMap<>();
    private final Map<String, Object> captures = new LinkedHashMap<>();
    private final long started = System.nanoTime();
    private Stage stage = Stage.TITLE;
    private long titleReady;
    private long clickedAt;
    private int buttonIndex;
    private boolean placedLegacy;

    ClientObeliskSmoke() {
        MinecraftForge.EVENT_BUS.addListener(this::render);
    }

    private void render(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage == Stage.DONE) return;
        try {
            if (minecraft.screen instanceof DisconnectedScreen) throw new AssertionError("Disconnected during " + stage);
            if (System.nanoTime() - started > 200_000_000_000L) throw new AssertionError("Timed out in " + stage);
            switch (stage) {
                case TITLE -> {
                    if (!(minecraft.screen instanceof TitleScreen) || minecraft.getOverlay() != null) return;
                    if (titleReady == 0) titleReady = System.nanoTime();
                    if (System.nanoTime() - titleReady < 2_000_000_000L) return;
                    ConnectScreen.startConnecting(new TitleScreen(), minecraft, ServerAddress.parseString(address),
                        new ServerData("Ender IO local Obelisk test", address, true), false);
                    stage = Stage.CONNECT;
                }
                case CONNECT -> {
                    if (minecraft.level == null || minecraft.player == null || minecraft.screen != null) return;
                    if (minecraft.getToasts().getToast(SystemToast.class, SystemToast.SystemToastIds.UNSECURE_SERVER_WARNING) != null) return;
                    if (!minecraft.level.getBlockState(XP).is(EIOBlocks.XP_OBELISK.get())) return;
                    var connection = minecraft.getConnection().getConnection();
                    check("real_dedicated_survival_tcp", !minecraft.player.isCreative() && !minecraft.player.isSpectator()
                        && minecraft.getSingleplayerServer() == null && !connection.isMemoryConnection()
                        && connection.getRemoteAddress() instanceof InetSocketAddress);
                    signal("connected");
                    stage = Stage.WAIT_XP;
                }
                case WAIT_XP -> {
                    if (minecraft.player.totalExperience != 5) return;
                    open(XP);
                    stage = Stage.XP_BUTTONS;
                }
                case XP_BUTTONS -> {
                    if (!(minecraft.screen instanceof XPObeliskScreen screen)
                        || !(minecraft.player.containerMenu instanceof XPObeliskMenu menu)) return;
                    exerciseXp(screen, menu);
                }
                case OPEN_WEATHER -> {
                    if (minecraft.screen != null) return;
                    if (!placedLegacy && Files.exists(output.resolve("server-place-legacy"))) {
                        if (!minecraft.player.getInventory().getItem(0).is(EIOBlocks.XP_OBELISK.asItem())) return;
                        minecraft.player.getInventory().selected = 0;
                        var result = minecraft.gameMode.useItemOn(minecraft.player, InteractionHand.MAIN_HAND,
                            new BlockHitResult(Vec3.atBottomCenterOf(REPLANTED), Direction.UP, REPLANTED.below(), false));
                        check("native_survival_placement_uses_legacy_item", result.consumesAction());
                        placedLegacy = true;
                        signal("legacy-placed");
                    }
                    if (!Files.exists(output.resolve("server-open-weather"))
                        || !minecraft.level.getBlockState(WEATHER).is(EIOBlocks.WEATHER_OBELISK.get())) return;
                    open(WEATHER);
                    stage = Stage.WEATHER_PAUSED;
                }
                case WEATHER_PAUSED -> {
                    if (!(minecraft.screen instanceof WeatherObeliskScreen)
                        || !(minecraft.player.containerMenu instanceof WeatherObeliskMenu menu)
                        || menu.getFluidTank() == null || menu.getCraftingProgress() <= 0) return;
                    check("paused_weather_menu_restores_inputs_and_progress", menu.getFluidTank().capacity() == 3000
                        && menu.getFluidTank().contents().getAmount() == 1237
                        && menu.getSlot(0).getItem().is(Items.FIREWORK_ROCKET)
                        && menu.getSlot(0).getItem().getCount() == 2 && !minecraft.level.isRaining());
                    capture("weather-paused");
                    signal("weather-open");
                    stage = Stage.WEATHER_RAIN;
                }
                case WEATHER_RAIN -> {
                    if (!Files.exists(output.resolve("server-rain")) || !minecraft.level.isRaining()) return;
                    var menu = (WeatherObeliskMenu) minecraft.player.containerMenu;
                    if (menu.getFluidTank().contents().getAmount() != 237 || menu.getSlot(0).getItem().getCount() != 1) return;
                    check("weather_menu_shows_exact_remainders_after_completion", menu.getCraftingProgress() == 0
                        && !minecraft.level.isThundering());
                    capture("weather-rain");
                    finish(null);
                }
                default -> { }
            }
        } catch (Throwable failure) {
            failure.printStackTrace();
            finish(failure);
        }
    }

    private void exerciseXp(XPObeliskScreen screen, XPObeliskMenu menu) throws IOException {
        if (clickedAt == 0) {
            if (buttonIndex == 0) {
                if (menu.getFluid().getAmount() != 27) return;
                check("xp_menu_receives_tagged_fluid", menu.getFluid().getFluid() == EIOFluids.XP_JUICE.source().get()
                    && menu.getFluid().getTag() != null && menu.getFluid().getTag().getInt("PortFixture") == 72);
                capture("xp-obelisk");
            }
            if (buttonIndex == 3 && (!Files.exists(output.resolve("server-xp-tens"))
                || minecraft.player.totalExperience != 216 || minecraft.player.experienceLevel != 12)) return;
            Button button = BUTTONS.get(buttonIndex);
            check("button_" + button.check(), screen.mouseClicked(screen.getGuiLeft() + button.x(), screen.getGuiTop() + button.y(), 0));
            clickedAt = System.nanoTime();
            return;
        }

        Button button = BUTTONS.get(buttonIndex);
        boolean synced = menu.getFluid().getAmount() == button.fluid() && minecraft.player.totalExperience == button.points()
            && minecraft.player.experienceLevel == button.level();
        if (!synced && System.nanoTime() - clickedAt < 10_000_000_000L) return;
        if (!synced) {
            throw new AssertionError(button.check() + " got " + menu.getFluid().getAmount() + " mB, "
                + minecraft.player.totalExperience + " points, level " + minecraft.player.experienceLevel);
        }
        check(button.check(), true);
        clickedAt = 0;
        if (++buttonIndex == 3) signal("xp-round-trip");
        if (buttonIndex == BUTTONS.size()) {
            signal("xp-tens");
            minecraft.player.closeContainer();
            stage = Stage.OPEN_WEATHER;
        }
    }

    private void open(BlockPos pos) {
        minecraft.gameMode.useItemOn(minecraft.player, InteractionHand.MAIN_HAND,
            new BlockHitResult(Vec3.atCenterOf(pos).add(0, 0, 0.5), Direction.SOUTH, pos, false));
    }

    private void signal(String name) throws IOException {
        var directory = output.resolve("signals");
        Files.createDirectories(directory);
        Files.writeString(directory.resolve(name + ".json"), "{}");
    }

    private void capture(String name) throws IOException {
        var frame = ClientSmoke.capture(name + ".png");
        captures.put(name, frame);
        check("silent_hidden_render_" + name, Boolean.TRUE.equals(frame.get("passed")));
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
        ClientSmoke.complete("client_obelisks", result);
    }
}
