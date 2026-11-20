package com.enderio.port.clientqa;

import com.enderio.core.common.energy.ItemStackEnergy;
import com.enderio.enderio.client.content.machines.gui.screen.EnderfaceScreen;
import com.enderio.enderio.content.enderface.EnderfaceTravelTarget;
import com.enderio.enderio.content.storage.fluid_tank.FluidTankMenu;
import com.enderio.enderio.content.travel.TravelHandler;
import com.enderio.enderio.init.EIOFeatureFlags;
import com.enderio.enderio.init.EIOItems;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

final class ClientEnderfaceSmoke {
    private enum Stage { TITLE, JOIN, STAFF, PREVIEW, OCCLUDED, REVEALED, CHEST, SHIFT_CLICK, SECOND_STAFF, TANK, CLOSED, DONE }

    private static final BlockPos ENDERFACE = new BlockPos(3040, 81, 8);
    private static final BlockPos CHEST = ENDERFACE.east(2).north(5);
    private static final BlockPos OCCLUDER = CHEST.north();
    private static final BlockPos TANK = ENDERFACE.west(2).south(5);
    private static final int CHEST_DISTANCE = 26;
    private static final int TANK_DISTANCE = 20;
    private static final int TANK_YAW = 90;
    private static final int TANK_PITCH = 25;
    private static final int LOOT = 7;

    private final Minecraft minecraft = Minecraft.getInstance();
    private final Path output = Path.of(System.getProperty("enderio.clientqa.output"));
    private final String address = System.getProperty("enderio.clientqa.server");
    private final boolean reopened = "verify".equals(System.getProperty("enderio.clientqa.multiplayerPhase"));
    private final Map<String, Boolean> checks = new LinkedHashMap<>();
    private final Map<String, Map<String, Object>> captures = new LinkedHashMap<>();
    private Stage stage = Stage.TITLE;
    private long titleReady;
    private long stageStarted = System.nanoTime();
    private int uses;
    private boolean previewDrawn;
    private EnderfaceScreen preview;

    ClientEnderfaceSmoke() {
        MinecraftForge.EVENT_BUS.addListener(this::render);
        MinecraftForge.EVENT_BUS.addListener(this::aimPreview);
    }

    private void render(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage == Stage.DONE) return;
        try {
            if (minecraft.screen instanceof DisconnectedScreen disconnected) {
                captures.put("disconnected", ClientSmoke.capture("disconnected.png"));
                throw new AssertionError("Disconnected during " + stage + ", " + disconnected.getNarrationMessage().getString());
            }
            long timeout = stage == Stage.JOIN ? 40_000_000_000L : 9_000_000_000L;
            if (stage != Stage.TITLE && System.nanoTime() - stageStarted > timeout) {
                captures.put("timeout", ClientSmoke.capture("timeout.png"));
                throw new AssertionError("Timed out in " + stage + ", screen=" + minecraft.screen
                    + ", FE=" + staffEnergy());
            }
            switch (stage) {
                case TITLE -> connect();
                case JOIN -> {
                    if (minecraft.player == null || minecraft.screen != null) return;
                    var connection = minecraft.getConnection().getConnection();
                    check("dedicated_survival_connection", minecraft.getSingleplayerServer() == null
                        && !connection.isMemoryConnection() && connection.getRemoteAddress() instanceof InetSocketAddress
                        && !minecraft.player.isCreative());
                    check("enderface_experiment_synced", minecraft.level.enabledFeatures().contains(EIOFeatureFlags.ENDERFACE)
                        && !minecraft.level.enabledFeatures().contains(EIOFeatureFlags.NIARD));
                    signal("connected");
                    enter(Stage.STAFF);
                }
                case STAFF, SECOND_STAFF -> useStaff();
                case PREVIEW -> preview();
                case OCCLUDED -> verifyOccluder();
                case REVEALED -> verifyRevealed();
                case CHEST -> chest();
                case SHIFT_CLICK -> {
                    if (staffEnergy() != 8000 || lootCount() != LOOT) return;
                    check("server_sends_the_shift_clicked_stack", true);
                    minecraft.player.closeContainer();
                    signal("chest-transferred");
                    enter(Stage.SECOND_STAFF);
                }
                case TANK -> tank();
                case CLOSED -> {
                    if (!Files.exists(output.resolve("server-removed")) || !minecraft.level.isEmptyBlock(ENDERFACE)
                        || minecraft.screen != null || minecraft.player.containerMenu != minecraft.player.inventoryMenu) return;
                    check("menu_closes_after_removing_the_enderface", true);
                    check("player_stays_at_the_original_location", atPlayerStart());
                    complete(null);
                }
                case DONE -> { }
            }
        } catch (Throwable failure) {
            failure.printStackTrace();
            complete(failure);
        }
    }

    private void connect() {
        if (!(minecraft.screen instanceof TitleScreen) || minecraft.getOverlay() != null) return;
        if (titleReady == 0) titleReady = System.nanoTime();
        if (System.nanoTime() - titleReady < 2_000_000_000L) return;
        ConnectScreen.startConnecting(new TitleScreen(), minecraft, ServerAddress.parseString(address),
            new ServerData("Enderface packaged test", address, true), false);
        enter(Stage.JOIN);
    }

    private void useStaff() {
        if (!Files.exists(output.resolve("server-ready")) || minecraft.screen != null || !atPlayerStart()
            || !minecraft.player.getMainHandItem().is(EIOItems.TRAVEL_STAFF.get())
            || minecraft.player.getCooldowns().isOnCooldown(EIOItems.TRAVEL_STAFF.get())) return;
        if (stage == Stage.SECOND_STAFF && !Files.exists(output.resolve("server-checked-chest"))) return;
        var target = TravelHandler.getInteractionTarget(minecraft.player).orElse(null);
        if (!(target instanceof EnderfaceTravelTarget) || !target.pos().equals(ENDERFACE)) return;
        uses++;
        check("staff_selects_enderface_" + uses, true);
        BlockPos remote = remoteTarget();
        check("remote_target_beyond_regular_reach_" + uses, remote.distToCenterSqr(minecraft.player.getEyePosition())
            > Math.max(64, Math.pow(minecraft.player.getBlockReach(), 2)));
        check("staff_has_exact_starting_charge_" + uses, staffEnergy() == (reopened ? 7000 : 10000 - uses * 1000));
        minecraft.gameMode.useItem(minecraft.player, InteractionHand.MAIN_HAND);
        previewDrawn = false;
        enter(Stage.PREVIEW);
    }

    private void aimPreview(ScreenEvent.Render.Pre event) {
        if ((stage != Stage.PREVIEW && stage != Stage.OCCLUDED && stage != Stage.REVEALED)
            || !(event.getScreen() instanceof EnderfaceScreen screen)) return;
        if (!previewDrawn) {
            preview = screen;
            int yaw = uses == 1 ? 0 : TANK_YAW;
            int pitch = uses == 1 ? 0 : TANK_PITCH;
            int distance = uses == 1 ? CHEST_DISTANCE : TANK_DISTANCE;
            screen.mouseDragged(screen.width / 2.0, screen.height / 2.0, InputConstants.MOUSE_BUTTON_LEFT,
                screen.width * (45 - yaw) / 180.0, screen.height * (45 + pitch) / 180.0);
            screen.mouseScrolled(screen.width / 2.0, screen.height / 2.0, distance - 10);
            previewDrawn = true;
        }
        screen.render(event.getGuiGraphics(), previewX(screen), previewY(screen), event.getPartialTick());
        event.setCanceled(true);
    }

    private void preview() throws IOException {
        if (!previewDrawn || !(minecraft.screen instanceof EnderfaceScreen screen)) return;
        capture("preview-" + uses);
        if (uses == 1 && !reopened) {
            check("chest_pixels_have_no_occluder_before_server_update", emeraldPixels(screen, "preview-1") == 0);
            signal("preview-ready");
            enter(Stage.OCCLUDED);
            return;
        }
        clickPreview(screen);
    }

    private void verifyOccluder() throws IOException {
        check("preview_stays_open_as_block_changes", minecraft.screen == preview);
        if (!minecraft.level.getBlockState(OCCLUDER).is(Blocks.EMERALD_BLOCK)) return;
        capture("preview-occluded");
        check("added_occluder_is_visible_in_the_existing_preview", emeraldPixels(preview, "preview-occluded") >= 16);
        check("click_hits_the_new_nearest_block", preview.mouseClicked(previewX(preview), previewY(preview),
            InputConstants.MOUSE_BUTTON_RIGHT));
        signal("occluder-clicked");
        enter(Stage.REVEALED);
    }

    private void verifyRevealed() throws IOException {
        check("occluded_click_keeps_the_remote_chest_closed", minecraft.screen == preview
            && minecraft.player.containerMenu == minecraft.player.inventoryMenu);
        if (!minecraft.level.isEmptyBlock(OCCLUDER)) return;
        capture("preview-revealed");
        check("removed_occluder_disappears_from_the_existing_preview", emeraldPixels(preview, "preview-revealed") == 0);
        check("click_uses_its_new_location_instead_of_the_last_hover",
            !preview.mouseClicked(preview.width / 2.0, preview.height / 2.0 - CHEST_DISTANCE * 3,
                InputConstants.MOUSE_BUTTON_RIGHT));
        clickPreview(preview);
    }

    private int emeraldPixels(EnderfaceScreen screen, String capture) {
        int count = 0;
        try (var image = Screenshot.takeScreenshot(minecraft.getMainRenderTarget())) {
            int x = previewX(screen) * image.getWidth() / screen.width;
            int y = previewY(screen) * image.getHeight() / screen.height;
            for (int dy = -4; dy <= 4; dy++) {
                for (int dx = -4; dx <= 4; dx++) {
                    int pixel = image.getPixelRGBA(x + dx, y + dy);
                    int red = pixel & 255;
                    int green = pixel >>> 8 & 255;
                    int blue = pixel >>> 16 & 255;
                    if (green > red + 48 && green > blue + 38) count++;
                }
            }
        }
        captures.get(capture).put("emeraldPixelsAtPointer", count);
        return count;
    }

    private void clickPreview(EnderfaceScreen screen) {
        check("world_preview_accepts_pointer_on_the_target_" + uses,
            screen.mouseClicked(previewX(screen), previewY(screen), InputConstants.MOUSE_BUTTON_RIGHT));
        enter(uses == 1 ? Stage.CHEST : Stage.TANK);
    }

    private void chest() throws IOException {
        if (!(minecraft.screen instanceof ContainerScreen) || !(minecraft.player.containerMenu instanceof ChestMenu menu)
            || staffEnergy() != (reopened ? 6000 : 8000)) return;
        var stack = menu.getSlot(0).getItem();
        if (!reopened && stack.isEmpty()) return;
        check("remote_chest_contents_match_the_saved_stage", reopened ? stack.isEmpty()
            : stack.is(Items.COPPER_INGOT) && stack.getCount() == LOOT);
        capture("remote-chest");
        if (reopened) {
            check("transferred_stack_persists_after_restart", lootCount() == LOOT);
            minecraft.player.closeContainer();
            complete(null);
        } else {
            minecraft.gameMode.handleInventoryMouseClick(menu.containerId, 0, 0, ClickType.QUICK_MOVE, minecraft.player);
            enter(Stage.SHIFT_CLICK);
        }
    }

    private void tank() throws IOException {
        if (!(minecraft.player.containerMenu instanceof FluidTankMenu menu) || minecraft.screen == null
            || staffEnergy() != 7000 || System.nanoTime() - stageStarted < 500_000_000L) return;
        var fluid = menu.getFluidTank().contents();
        if (fluid.isEmpty()) return;
        check("remote_enderio_menu_syncs_the_native_tank", menu.getBlockEntity().getBlockPos().equals(TANK)
            && fluid.getFluid() == Fluids.WATER && fluid.getAmount() == 1750);
        capture("remote-tank");
        signal("tank-opened");
        enter(Stage.CLOSED);
    }

    private int previewX(EnderfaceScreen screen) {
        return uses == 1 ? screen.width / 2 - (CHEST.getX() - ENDERFACE.getX()) * CHEST_DISTANCE
            : screen.width / 2 - (TANK.getZ() - ENDERFACE.getZ()) * TANK_DISTANCE;
    }

    private int previewY(EnderfaceScreen screen) {
        return uses == 1 ? screen.height / 2
            : screen.height / 2 + (int) Math.round((TANK.getX() - ENDERFACE.getX())
                * Math.sin(Math.toRadians(TANK_PITCH)) * TANK_DISTANCE);
    }

    private BlockPos remoteTarget() {
        return uses == 1 ? CHEST : TANK;
    }

    private boolean atPlayerStart() {
        return minecraft.player.distanceToSqr(3040.5, 80, 24.5) < 0.1;
    }

    private int staffEnergy() {
        return minecraft.player == null ? -1 : ItemStackEnergy.getEnergyStored(minecraft.player.getMainHandItem());
    }

    private int lootCount() {
        return minecraft.player.getInventory().countItem(Items.COPPER_INGOT);
    }

    private void enter(Stage next) {
        stage = next;
        stageStarted = System.nanoTime();
    }

    private void capture(String name) throws IOException {
        var frame = ClientSmoke.capture(name + ".png");
        captures.put(name, frame);
        check(name + "_silent_hidden_render", Boolean.TRUE.equals(frame.get("passed")));
    }

    private void signal(String name) throws IOException {
        Path directory = output.resolve("signals");
        Files.createDirectories(directory);
        Path temporary = directory.resolve(name + ".tmp");
        Files.writeString(temporary, "{}");
        Files.move(temporary, directory.resolve(name + ".json"));
    }

    private void check(String name, boolean passed) {
        checks.put(name, passed);
        if (!passed) throw new AssertionError(name);
    }

    private void complete(Throwable failure) {
        stage = Stage.DONE;
        var result = new LinkedHashMap<String, Object>();
        result.put("passed", failure == null);
        result.put("checks", checks);
        result.put("captures", captures);
        if (failure != null) result.put("error", failure.toString());
        ClientSmoke.complete("client_enderface", result);
    }
}
