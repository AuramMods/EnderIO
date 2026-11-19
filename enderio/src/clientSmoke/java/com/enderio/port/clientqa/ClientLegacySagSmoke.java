package com.enderio.port.clientqa;

import com.enderio.enderio.api.components.GrindingBallData;
import com.enderio.enderio.client.content.machines.gui.screen.SagMillScreen;
import com.enderio.enderio.init.EIOBlocks;
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
import net.minecraft.world.item.BlockItem;
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

final class ClientLegacySagSmoke {
    private enum Stage { TITLE, JOIN, COPPER, COPPER_PAUSED, COPPER_DONE, PLACE, PLACED, CARRIED, CARRIED_PAUSED, CARRIED_DONE, DONE }

    private static final BlockPos COPPER = new BlockPos(3098, 80, 8);
    private static final BlockPos PLACED = new BlockPos(3110, 80, 8);
    private static final GrindingBallData HISTORICAL_COPPER = new GrindingBallData(1.2f, 1.65f, 0.8f, 40000);
    private static final int COST = 1920;
    private static final int PAID = 600;

    private final Minecraft minecraft = Minecraft.getInstance();
    private final String address = System.getProperty("enderio.clientqa.server");
    private final Path output = Path.of(System.getProperty("enderio.clientqa.output"));
    private final Map<String, Boolean> checks = new LinkedHashMap<>();
    private final Map<String, Object> captures = new LinkedHashMap<>();
    private final long started = System.nanoTime();
    private Stage stage = Stage.TITLE;
    private long titleAt;
    private int useAt = -1;

    ClientLegacySagSmoke() {
        MinecraftForge.EVENT_BUS.addListener(this::render);
    }

    private void render(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage == Stage.DONE) return;
        try {
            if (minecraft.screen instanceof DisconnectedScreen || System.nanoTime() - started > 145_000_000_000L) {
                throw new AssertionError("Legacy SAG Mill test stopped during " + stage);
            }
            if (stage == Stage.TITLE) {
                if (!(minecraft.screen instanceof TitleScreen) || minecraft.getOverlay() != null) return;
                if (titleAt == 0) titleAt = System.nanoTime();
                if (System.nanoTime() - titleAt < 2_000_000_000L) return;
                ConnectScreen.startConnecting(new TitleScreen(), minecraft, ServerAddress.parseString(address),
                    new ServerData("Legacy SAG Mill QA", address, true), false);
                stage = Stage.JOIN;
                return;
            }
            var player = minecraft.player;
            if (player == null || minecraft.level == null) return;
            switch (stage) {
                case JOIN -> {
                    if (minecraft.screen != null || minecraft.getToasts().getToast(SystemToast.class,
                        SystemToast.SystemToastIds.UNSECURE_SERVER_WARNING) != null) return;
                    check("dedicated_survival_tcp", !player.isCreative() && !player.isSpectator()
                        && minecraft.getSingleplayerServer() == null && !minecraft.getConnection().getConnection().isMemoryConnection());
                    signal("connected");
                    stage = Stage.COPPER;
                }
                case COPPER -> {
                    if (open("copper", COPPER)) stage = Stage.COPPER_PAUSED;
                }
                case COPPER_PAUSED -> inspect("copper", false, Stage.COPPER_DONE);
                case COPPER_DONE -> inspect("copper", true, Stage.PLACE);
                case PLACE -> {
                    if (ready("place") && place()) stage = Stage.PLACED;
                }
                case PLACED -> {
                    if (!minecraft.level.getBlockState(PLACED).is(EIOBlocks.SAG_MILL.get())
                        || !player.getMainHandItem().isEmpty()) return;
                    check("survival_placement_consumes_the_single_original_item", !player.getAbilities().instabuild);
                    capture("original-carried-mill-placed");
                    signal("placed");
                    stage = Stage.CARRIED;
                }
                case CARRIED -> {
                    if (open("carried", PLACED)) stage = Stage.CARRIED_PAUSED;
                }
                case CARRIED_PAUSED -> inspect("carried", false, Stage.CARRIED_DONE);
                case CARRIED_DONE -> inspect("carried", true, Stage.DONE);
                default -> { }
            }
        } catch (Throwable failure) {
            failure.printStackTrace();
            finish(failure);
        }
    }

    private boolean place() {
        var player = minecraft.player;
        var item = player.getInventory().getItem(0);
        if (item.isEmpty() || !atInteractionPoint(PLACED)) return false;
        var saved = BlockItem.getBlockEntityData(item);
        check("the_original_drop_has_the_legacy_ball_and_600_fe_of_task_progress",
            item.is(EIOBlocks.SAG_MILL.get().asItem()) && item.getCount() == 1 && saved != null
                && saved.getString("GrindingBallId").equals("enderio:grindingball/copper_alloy_grinding_ball")
                && saved.getInt("GrindingBallDamage") == PAID && saved.getCompound("Task").getInt("ProgressMade") == PAID);
        player.getInventory().selected = 0;
        var floor = new BlockHitResult(Vec3.atBottomCenterOf(PLACED), Direction.UP, PLACED.below(), false);
        check("the_native_placement_packet_uses_the_original_sag_mill_item",
            minecraft.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, floor).consumesAction());
        return true;
    }

    private void inspect(String name, boolean done, Stage next) throws IOException {
        if (!(minecraft.screen instanceof SagMillScreen screen) || (done && !ready(name + "-done"))) return;
        var menu = screen.getMenu();
        int wear = done ? COST : PAID;
        float remaining = 1 - wear / (float) HISTORICAL_COPPER.durability();
        float progress = done ? 0 : PAID / (float) COST;
        if (!menu.getGrindingBallData().equals(HISTORICAL_COPPER)
            || Math.abs(menu.getGrindingBallDamage() - remaining) >= 0.0001
            || Math.abs(menu.getCraftingProgress() - progress) >= 0.0001
            || menu.getEnergyStorage().getEnergyStored() != 0) return;
        var spare = menu.getSlot(6).getItem();
        var coal = menu.getSlot(1).getItem();
        String checkpoint = name + (done ? "-done" : "-paused");
        check(checkpoint + "_syncs_exact_modifiers_wear_progress_and_tagged_spare",
            spare.is(EIOItems.CONDUCTIVE_ALLOY_BALL.get()) && spare.getCount() == 1 && spare.hasTag()
                && spare.getTag().getInt("PortSAG") == 157 && (done ? coal.isEmpty() : coal.is(Items.COAL) && coal.getCount() == 1));
        capture(checkpoint);
        if (done) minecraft.player.closeContainer();
        signal(checkpoint);
        if (next == Stage.DONE) finish(null);
        else stage = next;
    }

    private boolean open(String name, BlockPos pos) {
        if (!ready(name) || !atInteractionPoint(pos)) return false;
        var hit = new BlockHitResult(Vec3.atCenterOf(pos).relative(Direction.SOUTH, 0.5), Direction.SOUTH, pos, false);
        check(name + "_opens_the_native_sag_mill", minecraft.gameMode.useItemOn(minecraft.player, InteractionHand.MAIN_HAND, hit).consumesAction());
        return true;
    }

    private boolean atInteractionPoint(BlockPos pos) {
        var player = minecraft.player;
        if (minecraft.screen != null || player.distanceToSqr(pos.getX() + 0.5, 80, 11.5) > 0.15) return false;
        if (useAt < 0) useAt = player.tickCount + 8;
        if (player.tickCount < useAt) return false;
        useAt = -1;
        return true;
    }

    private boolean ready(String name) {
        return Files.exists(output.resolve("server-" + name));
    }

    private void signal(String name) throws IOException {
        var dir = output.resolve("signals");
        Files.createDirectories(dir);
        var tmp = dir.resolve(name + ".tmp");
        Files.writeString(tmp, "{}");
        Files.move(tmp, dir.resolve(name + ".json"));
    }

    private void capture(String name) throws IOException {
        var frame = ClientSmoke.capture(name + ".png");
        captures.put(name, frame);
        check(name + "_quiet_hidden_frame", Boolean.TRUE.equals(frame.get("passed")));
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
        ClientSmoke.complete("client_legacy_sag", result);
    }
}
