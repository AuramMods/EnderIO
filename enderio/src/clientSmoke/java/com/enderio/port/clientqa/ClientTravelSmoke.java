package com.enderio.port.clientqa;

import com.enderio.enderio.api.travel.TravelTargetApi;
import com.enderio.enderio.client.content.machines.gui.screen.TravelAnchorScreen;
import com.enderio.enderio.content.travel.TravelHandler;
import com.enderio.enderio.content.travel.travel_anchor.AnchorTravelTarget;
import com.enderio.enderio.content.travel.travel_anchor.PaintedTravelAnchorBlockEntity;
import com.enderio.enderio.content.travel.travel_anchor.TravelAnchorMenu;
import com.enderio.enderio.init.EIOItems;
import com.enderio.core.common.energy.ItemStackEnergy;
import com.google.gson.Gson;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.player.Input;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;

/** Travel migration over the real dedicated connection with local game input only. */
final class ClientTravelSmoke {
    private enum Stage { TITLE, JOIN, MENU_START, MENU, ANCHOR_START, WORKSHOP, ELEVATOR_START, LIFT,
        STAFF_START, STAFF_DESTINATION, BLINK_START, BLINK_CROUCH, BLINK_DESTINATION, NETHER, DONE }

    private static final BlockPos HUB = new BlockPos(32, 80, 0);
    private static final BlockPos WORKSHOP = new BlockPos(40, 80, 0);
    private static final BlockPos LIFT = new BlockPos(32, 88, 0);
    private final Minecraft minecraft = Minecraft.getInstance();
    private final String address = System.getProperty("enderio.clientqa.server");
    private final long started = System.nanoTime();
    private final int initialCharge = "verify".equals(System.getProperty("enderio.clientqa.multiplayerPhase")) ? 4000 : 6000;
    private final Map<String, Boolean> checks = new LinkedHashMap<>();
    private final Map<String, Object> captures = new LinkedHashMap<>();
    private long titleReady;
    private boolean sawStaffCooldown;
    private Vec3 blinkDestination;
    private Input walkingInput;
    private Stage stage = Stage.TITLE;

    ClientTravelSmoke() {
        MinecraftForge.EVENT_BUS.addListener(this::render);
    }

    private void render(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage == Stage.DONE) return;
        try {
            if (minecraft.screen instanceof DisconnectedScreen) throw new AssertionError("Disconnected during " + stage);
            if (System.nanoTime() - started > 180_000_000_000L) throw new AssertionError("Timed out during " + stage);
            switch (stage) {
                case TITLE -> {
                    if (!(minecraft.screen instanceof TitleScreen) || minecraft.getOverlay() != null) return;
                    if (titleReady == 0) titleReady = System.nanoTime();
                    if (System.nanoTime() - titleReady < 4_000_000_000L) return;
                    capture("title");
                    stage = Stage.JOIN;
                    ConnectScreen.startConnecting(new TitleScreen(), minecraft, ServerAddress.parseString(address),
                        new ServerData("Ender IO travel QA", address, true), false);
                }
                case JOIN -> {
                    if (!inWorld()) return;
                    var connection = minecraft.getConnection().getConnection();
                    check("dedicated_tcp", !connection.isMemoryConnection() && connection.getRemoteAddress() instanceof InetSocketAddress);
                    signal("connected");
                    stage = Stage.MENU_START;
                }
                case MENU_START -> {
                    if (!at(43.5, 80) || anchor(HUB) == null || anchor(WORKSHOP) == null || anchor(LIFT) == null
                        || !(minecraft.level.getBlockEntity(WORKSHOP) instanceof PaintedTravelAnchorBlockEntity painted)
                        || painted.getPrimaryPaint().isEmpty()) return;
                    check("overworld_hub", anchor(HUB).equals(new AnchorTravelTarget(HUB, "Overworld Hub", Items.COMPASS, true)));
                    check("renamed_icon", anchor(WORKSHOP).equals(new AnchorTravelTarget(WORKSHOP,
                        "Painted Workshop", EIOItems.CONDUCTIVE_ALLOY_INGOT.get(), true)));
                    check("hidden_lift", anchor(LIFT).equals(new AnchorTravelTarget(LIFT, "Hidden Lift", Items.ENDER_PEARL, false))
                        && !anchor(LIFT).canTeleportTo() && anchor(LIFT).canJumpTo());
                    check("painted_destination", painted.getPrimaryPaint().orElseThrow() == Blocks.PURPUR_BLOCK);
                    minecraft.gameMode.useItemOn(minecraft.player, InteractionHand.MAIN_HAND,
                        new BlockHitResult(new Vec3(41, 80.5, 0.5), Direction.EAST, WORKSHOP, false));
                    stage = Stage.MENU;
                }
                case MENU -> {
                    if (!(minecraft.screen instanceof TravelAnchorScreen)
                        || !(minecraft.player.containerMenu instanceof TravelAnchorMenu menu)
                        || menu.getSlot(0).getItem().isEmpty()) return;
                    check("anchor_menu_state", menu.getName().equals("Painted Workshop") && menu.isVisible()
                        && menu.getSlot(0).getItem().is(EIOItems.CONDUCTIVE_ALLOY_INGOT.get()));
                    capture("anchor-menu");
                    minecraft.player.closeContainer();
                    signal("menu_verified");
                    stage = Stage.ANCHOR_START;
                }
                case ANCHOR_START -> {
                    if (!at(32.5, 81) || Math.abs(minecraft.player.getYRot() + 90) > 1) return;
                    check("horizontal_target_selected", TravelHandler.canBlockTeleport(minecraft.player)
                        && TravelHandler.getTeleportAnchorTarget(minecraft.player).map(target -> target.pos().equals(WORKSHOP)).orElse(false));
                    check("horizontal_request_sent", TravelHandler.blockTeleport(minecraft.level, minecraft.player, true));
                    stage = Stage.WORKSHOP;
                }
                case WORKSHOP -> {
                    if (!at(40.5, 81)) return;
                    check("arrived_at_painted_workshop", true);
                    signal("workshop");
                    stage = Stage.ELEVATOR_START;
                }
                case ELEVATOR_START -> {
                    if (!at(32.5, 81)) return;
                    check("elevator_request_sent", TravelHandler.canBlockTeleport(minecraft.player)
                        && TravelHandler.blockElevatorTeleport(minecraft.level, minecraft.player, Direction.UP, true));
                    stage = Stage.LIFT;
                }
                case LIFT -> {
                    if (!at(32.5, 89)) return;
                    check("arrived_at_hidden_lift", true);
                    signal("lift");
                    stage = Stage.STAFF_START;
                }
                case STAFF_START -> {
                    if (!at(48.5, 80) || !minecraft.player.getMainHandItem().is(EIOItems.TRAVEL_STAFF.get())) return;
                    check("historical_staff_charge", staffEnergy() == initialCharge);
                    check("staff_selects_anchor_from_ground", !TravelHandler.canBlockTeleport(minecraft.player)
                        && TravelHandler.getTeleportAnchorTarget(minecraft.player).map(target -> target.pos().equals(WORKSHOP)).orElse(false));
                    minecraft.gameMode.useItem(minecraft.player, InteractionHand.MAIN_HAND);
                    stage = Stage.STAFF_DESTINATION;
                }
                case STAFF_DESTINATION -> {
                    if (!inWorld()) return;
                    sawStaffCooldown |= staffOnCooldown();
                    if (!at(40.5, 81) || staffEnergy() == initialCharge) return;
                    if (!sawStaffCooldown) return;
                    check("staff_charges_one_use", staffEnergy() == initialCharge - 1000);
                    check("staff_cooldown_received", true);
                    signal("staff_workshop");
                    stage = Stage.BLINK_START;
                }
                case BLINK_START -> {
                    if (!at(52.9, 80) || staffOnCooldown()) return;
                    check("staff_cooldown_expired", true);
                    walkingInput = minecraft.player.input;
                    minecraft.player.input = new Input() {
                        @Override
                        public void tick(boolean slow, float factor) {
                            shiftKeyDown = true;
                        }
                    };
                    stage = Stage.BLINK_CROUCH;
                }
                case BLINK_CROUCH -> {
                    if (!inWorld() || minecraft.player.getPose() != Pose.CROUCHING) return;
                    blinkDestination = TravelHandler.teleportPosition(minecraft.level, minecraft.player).orElseThrow();
                    check("blink_selects_expected_platform_block", blinkDestination.equals(new Vec3(56.5, 80, 0.5)));
                    minecraft.gameMode.useItem(minecraft.player, InteractionHand.MAIN_HAND);
                    stage = Stage.BLINK_DESTINATION;
                }
                case BLINK_DESTINATION -> {
                    if (!inWorld() || minecraft.player.distanceToSqr(blinkDestination) > 0.15
                        || staffEnergy() == initialCharge - 1000) return;
                    restoreMovementInput();
                    check("blink_charges_one_use", staffEnergy() == initialCharge - 2000);
                    signal("staff_blink");
                    stage = Stage.NETHER;
                }
                case NETHER -> {
                    if (!inWorld() || minecraft.level.dimension() != Level.NETHER || anchor(HUB) == null) return;
                    check("dimension_specific_targets", anchor(HUB).equals(new AnchorTravelTarget(HUB, "Nether Relay", Items.BLAZE_ROD, true))
                        && anchor(WORKSHOP) == null && anchor(LIFT) == null);
                    capture("nether-relay");
                    complete(true, "");
                }
                default -> { }
            }
        } catch (Throwable failure) {
            failure.printStackTrace();
            complete(false, failure.toString());
        }
    }

    private boolean inWorld() {
        return minecraft.level != null && minecraft.player != null && minecraft.screen == null;
    }

    private boolean at(double x, double y) {
        return inWorld() && minecraft.player.distanceToSqr(x, y, 0.5) < 0.15;
    }

    private int staffEnergy() {
        return ItemStackEnergy.getEnergyStored(minecraft.player.getMainHandItem());
    }

    private boolean staffOnCooldown() {
        return minecraft.player.getCooldowns().isOnCooldown(EIOItems.TRAVEL_STAFF.get());
    }

    private AnchorTravelTarget anchor(BlockPos pos) {
        return TravelTargetApi.INSTANCE.get(minecraft.level, pos)
            .filter(AnchorTravelTarget.class::isInstance).map(AnchorTravelTarget.class::cast).orElse(null);
    }

    private void check(String name, boolean passed) {
        checks.put(name, passed);
        if (!passed) throw new AssertionError(name);
    }

    private void capture(String image) throws Exception {
        var result = ClientSmoke.capture(image + ".png");
        captures.put(image, result);
        check(image + "_silent_hidden_render", Boolean.TRUE.equals(result.get("passed")));
    }

    private void signal(String name) throws Exception {
        var dir = Path.of(System.getProperty("enderio.clientqa.output"), "signals");
        Files.createDirectories(dir);
        var tmp = dir.resolve(name + ".tmp");
        Files.writeString(tmp, new Gson().toJson(Map.of("signal", name)));
        Files.move(tmp, dir.resolve(name + ".json"), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }

    private void complete(boolean passed, String error) {
        stage = Stage.DONE;
        restoreMovementInput();
        ClientSmoke.complete("client_travel", new LinkedHashMap<>(Map.of("passed", passed, "error", error,
            "checks", checks, "captures", captures)));
    }

    private void restoreMovementInput() {
        if (walkingInput != null && minecraft.player != null) minecraft.player.input = walkingInput;
        walkingInput = null;
    }
}
