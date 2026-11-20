package com.enderio.port.clientqa;

import com.enderio.enderio.api.components.GrindingBallData;
import com.enderio.enderio.client.content.machines.gui.screen.SagMillScreen;
import com.enderio.enderio.content.machines.sag_mill.Forge6GrindingBallRecipe;
import com.enderio.enderio.compat.jei_machines_to_merge.category.SagMillCategory;
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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Items;
import mezz.jei.api.recipe.RecipeIngredientRole;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class ClientLegacySagSmoke {
    private enum Stage { TITLE, JOIN, COPPER, COPPER_PAUSED, COPPER_DONE, PLACE, PLACED, CARRIED, CARRIED_PAUSED, CARRIED_DONE, DONE }

    private static final BlockPos COPPER = new BlockPos(3098, 80, 8);
    private static final BlockPos PLACED = new BlockPos(3110, 80, 8);
    private static final GrindingBallData HISTORICAL_COPPER = new GrindingBallData(1.2f, 1.65f, 0.8f, 40000);
    private static final int PAID = 600;
    private static final ResourceLocation CUSTOM_RECIPE = new ResourceLocation("port_fixture", "grindingball/prismarine");
    private static final GrindingBallData CUSTOM_BALL = new GrindingBallData(2, 1.5f, 0.5f, 10000);

    private final boolean custom;
    private final BlockPos original;
    private final BlockPos placed;
    private final String label;
    private final String legacyRecipeId;
    private final GrindingBallData modifiers;
    private final int cost;

    private final Minecraft minecraft = Minecraft.getInstance();
    private final String address = System.getProperty("enderio.clientqa.server");
    private final Path output = Path.of(System.getProperty("enderio.clientqa.output"));
    private final Map<String, Boolean> checks = new LinkedHashMap<>();
    private final Map<String, Object> captures = new LinkedHashMap<>();
    private final long started = System.nanoTime();
    private Stage stage = Stage.TITLE;
    private long titleAt;
    private int useAt = -1;

    ClientLegacySagSmoke(boolean custom) {
        this.custom = custom;
        original = custom ? new BlockPos(3090, 80, 8) : COPPER;
        placed = custom ? new BlockPos(3100, 80, 8) : PLACED;
        label = custom ? "custom" : "copper";
        legacyRecipeId = custom ? CUSTOM_RECIPE.toString() : "enderio:grindingball/copper_alloy_grinding_ball";
        modifiers = custom ? CUSTOM_BALL : HISTORICAL_COPPER;
        cost = custom ? 1200 : 1920;
        MinecraftForge.EVENT_BUS.addListener(this::render);
    }

    private void render(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage == Stage.DONE) return;
        try {
            if (minecraft.screen instanceof DisconnectedScreen || System.nanoTime() - started > 200_000_000_000L) {
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
                    if (custom && !CustomBallChecks.verify(this)) return;
                    check("dedicated_survival_tcp", !player.isCreative() && !player.isSpectator()
                        && minecraft.getSingleplayerServer() == null && !minecraft.getConnection().getConnection().isMemoryConnection());
                    signal("connected");
                    stage = Stage.COPPER;
                }
                case COPPER -> {
                    if (open(label, original)) stage = Stage.COPPER_PAUSED;
                }
                case COPPER_PAUSED -> inspect(label, false, Stage.COPPER_DONE);
                case COPPER_DONE -> inspect(label, true, Stage.PLACE);
                case PLACE -> {
                    if (ready("place") && place()) stage = Stage.PLACED;
                }
                case PLACED -> {
                    if (!minecraft.level.getBlockState(placed).is(EIOBlocks.SAG_MILL.get())
                        || !player.getMainHandItem().isEmpty()) return;
                    check("survival_placement_consumes_the_single_original_item", !player.getAbilities().instabuild);
                    capture("original-carried-mill-placed");
                    signal("placed");
                    stage = Stage.CARRIED;
                }
                case CARRIED -> {
                    if (open("carried", placed)) stage = Stage.CARRIED_PAUSED;
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
        if (item.isEmpty() || !atInteractionPoint(placed)) return false;
        var saved = BlockItem.getBlockEntityData(item);
        check("the_original_drop_has_the_legacy_ball_and_600_fe_of_task_progress",
            item.is(EIOBlocks.SAG_MILL.get().asItem()) && item.getCount() == 1 && saved != null
                && saved.getString("GrindingBallId").equals(legacyRecipeId)
                && saved.getInt("GrindingBallDamage") == PAID && saved.getCompound("Task").getInt("ProgressMade") == PAID);
        player.getInventory().selected = 0;
        var floor = new BlockHitResult(Vec3.atBottomCenterOf(placed), Direction.UP, placed.below(), false);
        check("the_native_placement_packet_uses_the_original_sag_mill_item",
            minecraft.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, floor).consumesAction());
        return true;
    }

    private void inspect(String name, boolean done, Stage next) throws IOException {
        if (!(minecraft.screen instanceof SagMillScreen screen) || (done && !ready(name + "-done"))) return;
        var menu = screen.getMenu();
        int wear = done ? cost : PAID;
        float remaining = 1 - wear / (float) modifiers.durability();
        float progress = done ? 0 : PAID / (float) cost;
        if (!menu.getGrindingBallData().equals(modifiers)
            || Math.abs(menu.getGrindingBallDamage() - remaining) >= 0.0001
            || Math.abs(menu.getCraftingProgress() - progress) >= 0.0001
            || menu.getEnergyStorage().getEnergyStored() != 0) return;
        var spare = menu.getSlot(6).getItem();
        var coal = menu.getSlot(1).getItem();
        String checkpoint = name + (done ? "-done" : "-paused");
        check(checkpoint + "_syncs_exact_modifiers_wear_progress_and_tagged_spare",
            spare.is(custom ? Items.PRISMARINE_CRYSTALS : EIOItems.CONDUCTIVE_ALLOY_BALL.get()) && spare.getCount() == 1 && spare.hasTag()
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

    private static final class CustomBallChecks {
        private static boolean verify(ClientLegacySagSmoke smoke) {
            var runtime = ClientSoulEngineFuelsSmoke.JeiProbe.runtime();
            if (runtime == null) return false;
            var ball = Items.PRISMARINE_CRYSTALS;
            var level = smoke.minecraft.level;
            var saved = level.getRecipeManager().byKey(CUSTOM_RECIPE).orElse(null);
            smoke.check("forge_syncs_the_original_custom_ball_recipe", saved instanceof Forge6GrindingBallRecipe recipe
                && recipe.modifiers().equals(CUSTOM_BALL) && CUSTOM_BALL.equals(Forge6GrindingBallRecipe.forItem(level, ball)));
            var coalId = new ResourceLocation("enderio", "sag_milling/coal");
            var focus = ClientSagJeiSmoke.focus(runtime, RecipeIngredientRole.CATALYST, ball);
            var manager = runtime.getRecipeManager();
            var recipe = ClientSagJeiSmoke.recipe(runtime, coalId);
            var layout = manager.createRecipeLayoutDrawable(manager.getRecipeCategory(SagMillCategory.TYPE), recipe,
                runtime.getJeiHelpers().getFocusFactory().createFocusGroup(List.of(focus))).orElseThrow();
            var ballSlot = layout.getRecipeSlotsView().getSlotViews(RecipeIngredientRole.CATALYST).get(0);
            smoke.check("jei_shows_the_legacy_custom_ball_and_1200_ui_cost",
                ballSlot.getDisplayedItemStack().orElseThrow().is(ball)
                    && "1,200 µI".equals(ClientSagJeiSmoke.renderedEnergy(smoke.minecraft, layout)));
            var lines = ball.getDefaultInstance().getTooltipLines(smoke.minecraft.player,
                net.minecraft.world.item.TooltipFlag.Default.NORMAL).stream().map(net.minecraft.network.chat.Component::getString).toList();
            smoke.check("the_native_item_tooltip_uses_all_three_custom_modifiers",
                lines.containsAll(List.of("Main Output 200%", "Bonus Output 150%", "Power Use 50%")));
            return true;
        }
    }
}
