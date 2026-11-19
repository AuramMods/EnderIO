package com.enderio.port.clientqa;

import com.enderio.enderio.api.travel.TravelTargetApi;
import com.enderio.enderio.client.content.machines.gui.screen.TravelAnchorScreen;
import com.enderio.enderio.content.paint.BlockPaintData;
import com.enderio.enderio.content.travel.TravelHandler;
import com.enderio.enderio.content.travel.travel_anchor.AnchorTravelTarget;
import com.enderio.enderio.content.travel.travel_anchor.PaintedTravelAnchorBlockEntity;
import com.enderio.enderio.content.travel.travel_anchor.TravelAnchorMenu;
import com.enderio.enderio.init.EIOBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

final class ClientPaintedTravelSmoke {
    private enum Stage { TITLE, JOIN, ANCHOR, MODEL, MENU, SAVED_MENU, TRAVEL, ARRIVE, FINISH, DONE }

    private static final BlockPos HUB = new BlockPos(2704, 80, 8);
    private static final BlockPos ANCHOR = new BlockPos(2712, 80, 8);
    private static final Vec3 VIEW = new Vec3(2715.5, 80, 8.5);
    private static final String NAME = "Diamond Hub";
    private static final ResourceLocation DIAMOND_SPRITE = ResourceLocation.parse("minecraft:block/diamond_block");

    private final Minecraft minecraft = Minecraft.getInstance();
    private final String address = System.getProperty("enderio.clientqa.server");
    private final Path output = Path.of(System.getProperty("enderio.clientqa.output"));
    private final boolean reopened = "verify".equals(System.getProperty("enderio.clientqa.multiplayerPhase"));
    private final Map<String, Boolean> checks = new LinkedHashMap<>();
    private final Map<String, Object> captures = new LinkedHashMap<>();
    private final long started = System.nanoTime();
    private long titleAt;
    private int renderAt;
    private Stage stage = Stage.TITLE;

    ClientPaintedTravelSmoke() {
        MinecraftForge.EVENT_BUS.addListener(this::render);
    }

    private void render(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage == Stage.DONE) return;
        try {
            if (minecraft.screen instanceof DisconnectedScreen || System.nanoTime() - started > 150_000_000_000L) {
                throw new AssertionError("Painted travel stopped during " + stage);
            }
            if (stage == Stage.TITLE) {
                if (!(minecraft.screen instanceof TitleScreen) || minecraft.getOverlay() != null) return;
                if (titleAt == 0) titleAt = System.nanoTime();
                if (System.nanoTime() - titleAt < 2_000_000_000L) return;
                ConnectScreen.startConnecting(new TitleScreen(), minecraft, ServerAddress.parseString(address),
                    new ServerData("Painted travel QA", address, true), false);
                stage = Stage.JOIN;
                return;
            }
            if (minecraft.level == null || minecraft.player == null) return;
            switch (stage) {
                case JOIN -> {
                    if (minecraft.screen != null || minecraft.getToasts().getToast(SystemToast.class,
                        SystemToast.SystemToastIds.UNSECURE_SERVER_WARNING) != null) return;
                    check("dedicated_survival_tcp", !minecraft.player.isCreative() && !minecraft.player.isSpectator()
                        && minecraft.getSingleplayerServer() == null && !minecraft.getConnection().getConnection().isMemoryConnection());
                    minecraft.options.hideGui = true;
                    signal("connected");
                    stage = Stage.ANCHOR;
                }
                case ANCHOR -> {
                    if (!ready("anchor") || !at(VIEW)) return;
                    if (!reopened) {
                        var stack = minecraft.player.getInventory().getItem(0);
                        if (stack.isEmpty()) return;
                        check("repainted_anchor_item_has_diamond_paint", stack.is(EIOBlocks.PAINTED_TRAVEL_ANCHOR.get().asItem())
                            && stack.getCount() == 1 && BlockPaintData.of(Blocks.DIAMOND_BLOCK).equals(BlockPaintData.ITEM_DATA.get(stack)));
                        minecraft.player.getInventory().selected = 0;
                        var hit = new BlockHitResult(Vec3.atBottomCenterOf(ANCHOR), Direction.UP, ANCHOR.below(), false);
                        check("survival_places_the_actual_repainted_anchor",
                            minecraft.gameMode.useItemOn(minecraft.player, InteractionHand.MAIN_HAND, hit).consumesAction());
                    }
                    stage = Stage.MODEL;
                }
                case MODEL -> {
                    if (!(minecraft.level.getBlockEntity(ANCHOR) instanceof PaintedTravelAnchorBlockEntity painted)
                        || painted.getPrimaryPaint().isEmpty() || !minecraft.player.getMainHandItem().isEmpty()) return;
                    // A placed block entity can have its paint before the chunk mesh has been rebuilt.
                    if (renderAt == 0) renderAt = minecraft.player.tickCount + 20;
                    if (minecraft.player.tickCount < renderAt) return;
                    check("placed_anchor_has_diamond_paint", painted.getPrimaryPaint().orElseThrow() == Blocks.DIAMOND_BLOCK);
                    check("anchor_bakes_the_diamond_sprite", showsDiamond(painted));
                    if (reopened) check("named_destination_is_restored_by_login", namedAnchor());
                    var frame = capture("painted-anchor");
                    int pixels = ClientSmoke.diamondPixels(minecraft);
                    frame.put("diamondPixels", pixels);
                    check("the_world_frame_shows_the_anchor_diamond_surface", pixels > 300);
                    minecraft.options.hideGui = false;
                    var hit = new BlockHitResult(new Vec3(2713, 80.5, 8.5), Direction.EAST, ANCHOR, false);
                    minecraft.gameMode.useItemOn(minecraft.player, InteractionHand.MAIN_HAND, hit);
                    stage = Stage.MENU;
                }
                case MENU -> {
                    if (!(minecraft.screen instanceof TravelAnchorScreen screen)
                        || !(minecraft.player.containerMenu instanceof TravelAnchorMenu menu)) return;
                    check("native_anchor_menu_starts_with_expected_state", menu.isVisible()
                        && menu.getName().equals(reopened ? NAME : ""));
                    if (!reopened) {
                        EditBox name = screen.children().stream().filter(EditBox.class::isInstance)
                            .map(EditBox.class::cast).findFirst().orElseThrow();
                        name.setValue(NAME);
                    }
                    stage = Stage.SAVED_MENU;
                }
                case SAVED_MENU -> {
                    if (!namedAnchor() || !(minecraft.player.containerMenu instanceof TravelAnchorMenu menu)
                        || !menu.getName().equals(NAME)) return;
                    check("native_anchor_menu_shows_the_synced_name", menu.isVisible());
                    capture("anchor-menu");
                    minecraft.player.closeContainer();
                    signal("menu-verified");
                    stage = Stage.TRAVEL;
                }
                case TRAVEL -> {
                    if (!ready("travel") || !at(Vec3.atBottomCenterOf(HUB.above()))
                        || Math.abs(minecraft.player.getYRot() + 90) > 1) return;
                    check("the_named_painted_anchor_is_a_native_travel_target", TravelHandler.canBlockTeleport(minecraft.player)
                        && TravelHandler.getTeleportAnchorTarget(minecraft.player).map(anchor -> anchor.pos().equals(ANCHOR)).orElse(false));
                    check("normal_anchor_travel_request_is_sent", TravelHandler.blockTeleport(minecraft.level, minecraft.player, true));
                    stage = Stage.ARRIVE;
                }
                case ARRIVE -> {
                    if (!at(Vec3.atBottomCenterOf(ANCHOR.above()))) return;
                    check("native_travel_arrives_on_top_of_the_painted_anchor", TravelHandler.canBlockTeleport(minecraft.player));
                    signal("arrived");
                    stage = Stage.FINISH;
                }
                case FINISH -> {
                    if (!ready("done")) return;
                    if (reopened) {
                        if (!minecraft.level.isEmptyBlock(ANCHOR)
                            || TravelTargetApi.INSTANCE.get(minecraft.level, ANCHOR).isPresent()) return;
                        check("removing_the_painted_block_removes_its_synced_destination", true);
                    }
                    finish(null);
                }
                default -> { }
            }
        } catch (Throwable failure) {
            failure.printStackTrace();
            finish(failure);
        }
    }

    private boolean showsDiamond(PaintedTravelAnchorBlockEntity painted) {
        var state = painted.getBlockState();
        var data = painted.getModelData();
        var model = minecraft.getBlockRenderer().getBlockModel(state);
        var quads = new ArrayList<BakedQuad>();
        for (var type : model.getRenderTypes(state, RandomSource.create(42), data)) {
            quads.addAll(model.getQuads(state, null, RandomSource.create(42), data, type));
            for (var side : Direction.values()) quads.addAll(model.getQuads(state, side, RandomSource.create(42), data, type));
        }
        return !quads.isEmpty() && quads.stream().allMatch(quad -> quad.getSprite().contents().name().equals(DIAMOND_SPRITE));
    }

    private boolean namedAnchor() {
        return TravelTargetApi.INSTANCE.get(minecraft.level, ANCHOR).filter(AnchorTravelTarget.class::isInstance)
            .map(AnchorTravelTarget.class::cast).filter(target -> NAME.equals(target.name()) && target.canTeleportTo()).isPresent();
    }

    private boolean at(Vec3 position) {
        return minecraft.player.distanceToSqr(position) < 0.15;
    }

    private boolean ready(String name) {
        return Files.exists(output.resolve("server-" + name));
    }

    private void signal(String name) throws IOException {
        var directory = output.resolve("signals");
        Files.createDirectories(directory);
        var staging = directory.resolve(name + ".tmp");
        Files.writeString(staging, "{}");
        Files.move(staging, directory.resolve(name + ".json"));
    }

    private Map<String, Object> capture(String name) throws IOException {
        var result = ClientSmoke.capture(name + ".png");
        captures.put(name, result);
        check(name + "_quiet_hidden_frame", Boolean.TRUE.equals(result.get("passed")));
        return result;
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
        ClientSmoke.complete("client_painted_travel", result);
    }
}
