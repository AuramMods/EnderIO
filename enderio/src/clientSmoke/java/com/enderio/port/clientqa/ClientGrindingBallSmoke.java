package com.enderio.port.clientqa;

import com.enderio.core.common.datamap.ClientDataMaps;
import com.enderio.enderio.api.components.GrindingBallData;
import com.enderio.enderio.client.content.machines.gui.screen.SagMillScreen;
import com.enderio.enderio.content.machines.sag_mill.SagMillMenu;
import com.google.gson.Gson;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class ClientGrindingBallSmoke {
    private enum Stage { TITLE, JOIN, LAYOUT, CHEST, TAKE, TAKEN, TABLE, RECIPE, RESULT, COLLECT,
        INSTALL, INSERT, INSERTED, INSPECT, MENU, VERIFY, DONE }

    private record Ball(String item, int x, float multiplier, float bonus, float power, int durability, int cost, int spares) {
        BlockPos position() { return new BlockPos(x, 80, 8); }
        GrindingBallData modifiers() { return new GrindingBallData(multiplier, bonus, power, durability); }
    }

    private static final BlockPos CHEST = new BlockPos(3211, 80, 10);
    private static final BlockPos TABLE = new BlockPos(3210, 80, 10);
    private static final Vec3 WORKBENCH = new Vec3(3210.5, 80, 12.5);
    private static final String DARK = "enderio:dark_steel_grinding_ball";

    private final Minecraft minecraft = Minecraft.getInstance();
    private final Path output = Path.of(System.getProperty("enderio.clientqa.output"));
    private final boolean restarted = "verify".equals(System.getProperty("enderio.clientqa.multiplayerPhase"));
    private final Map<String, Boolean> checks = new LinkedHashMap<>();
    private final Map<String, Object> captures = new LinkedHashMap<>();
    private final long started = System.nanoTime();
    private List<Ball> balls = List.of();
    private List<Ball> recipes = List.of();
    private Stage stage = Stage.TITLE;
    private long titleAt;
    private int recipeIndex;
    private int inspectIndex;
    private int ingotsBefore;
    private int interactAt = -1;

    ClientGrindingBallSmoke() {
        MinecraftForge.EVENT_BUS.addListener(this::render);
    }

    private void render(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage == Stage.DONE) return;
        try {
            if (minecraft.screen instanceof DisconnectedScreen || System.nanoTime() - started > 185_000_000_000L) {
                throw new AssertionError("Grinding balls stopped at " + stage + ", recipe " + recipeIndex + ", mill " + inspectIndex);
            }
            if (stage == Stage.TITLE) {
                connect();
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
                    stage = Stage.LAYOUT;
                }
                case LAYOUT -> {
                    Path path = output.resolve("layout.json");
                    if (!Files.exists(path)) return;
                    balls = Arrays.asList(new Gson().fromJson(Files.readString(path), Ball[].class));
                    recipes = balls.stream().filter(ball -> ball.item().startsWith("enderio:")).toList();
                    stage = restarted ? Stage.INSPECT : Stage.CHEST;
                }
                case CHEST -> {
                    if (!openAfterTeleport("recipe", WORKBENCH, CHEST)) return;
                    stage = Stage.TAKE;
                }
                case TAKE -> {
                    if (!(minecraft.screen instanceof ContainerScreen screen)) return;
                    var menu = screen.getMenu();
                    var stock = menu.slots.subList(0, 27).stream().filter(Slot::hasItem).toList();
                    if (stock.size() != recipes.size() + 1) return;
                    for (var slot : stock) quickMove(menu, slot.index);
                    stage = Stage.TAKEN;
                }
                case TAKEN -> {
                    if (!(minecraft.screen instanceof ContainerScreen screen)
                        || screen.getMenu().slots.subList(0, 27).stream().anyMatch(Slot::hasItem)) return;
                    player.closeContainer();
                    stage = Stage.TABLE;
                }
                case TABLE -> {
                    if (minecraft.screen != null) return;
                    open(TABLE);
                    stage = Stage.RECIPE;
                }
                case RECIPE, RESULT, COLLECT -> craft(recipes.get(recipeIndex));
                case INSTALL -> {
                    var ball = recipes.stream().filter(example -> example.item().equals(DARK)).findFirst().orElseThrow();
                    if (!openAfterTeleport("install", standingPosition(ball), ball.position())) return;
                    stage = Stage.INSERT;
                }
                case INSERT -> {
                    if (!(minecraft.screen instanceof SagMillScreen screen)) return;
                    var menu = screen.getMenu();
                    var slot = menu.slots.stream().filter(s -> s.container == player.getInventory() && s.getItem().is(item(DARK))).findFirst();
                    if (slot.isEmpty()) return;
                    quickMove(menu, slot.get().index);
                    stage = Stage.INSERTED;
                }
                case INSERTED -> {
                    if (!(minecraft.screen instanceof SagMillScreen screen)) return;
                    var stack = screen.getMenu().getSlot(SagMillMenu.LAST_INDEX).getItem();
                    if (!stack.is(item(DARK)) || stack.getCount() != 24) return;
                    check("native_menu_moves_all_twenty_four_crafted_dark_steel_balls", count(item(DARK)) == 0);
                    capture("dark-steel-stock-installed");
                    player.closeContainer();
                    signal("installed");
                    stage = Stage.VERIFY;
                }
                case INSPECT -> {
                    var ball = balls.get(inspectIndex);
                    if (!openAfterTeleport("inspect-" + ball.x(), standingPosition(ball), ball.position())) return;
                    stage = Stage.MENU;
                }
                case MENU -> {
                    if (!(minecraft.screen instanceof SagMillScreen screen)) return;
                    var ball = balls.get(inspectIndex);
                    var menu = screen.getMenu();
                    if (!ClientSagProgressionSmoke.matches(menu, ball.modifiers(), 300, ball.cost(), 300)) return;
                    var spare = menu.getSlot(SagMillMenu.LAST_INDEX).getItem();
                    check(ball.x() + "_menu_syncs_stock_wear_modifiers_paid_progress_and_spares",
                        spare.is(item(ball.item())) && spare.getCount() == ball.spares()
                            && menu.getSlot(SagMillMenu.INPUTS_INDEX).getItem().is(Items.COAL));
                    verifyTooltip(ball, spare);
                    capture("restored-ball-" + ball.x());
                    player.closeContainer();
                    signal("inspected-" + ball.x());
                    stage = ++inspectIndex == balls.size() ? Stage.VERIFY : Stage.INSPECT;
                }
                case VERIFY -> {
                    if (ready("done")) finish(null);
                }
                default -> {}
            }
        } catch (Throwable failure) {
            failure.printStackTrace();
            finish(failure);
        }
    }

    private void craft(Ball ball) throws IOException {
        if (!(minecraft.screen instanceof CraftingScreen screen)) return;
        var menu = screen.getMenu();
        var grid = menu.slots.subList(1, 10);
        Item ingot = item(ball.item().replace("_grinding_ball", "_ingot"));
        Item result = item(ball.item());
        switch (stage) {
            case RECIPE -> {
                var recipe = minecraft.level.getRecipeManager().byKey(ResourceLocation.parse(ball.item())).orElseThrow();
                if (!minecraft.player.getRecipeBook().contains(recipe)) return;
                ingotsBefore = count(ingot);
                check(ball.x() + "_recipe_unlocks_from_original_ingots", ingotsBefore >= 5 && count(result) == 0);
                minecraft.gameMode.handlePlaceRecipe(menu.containerId, recipe, false);
                stage = Stage.RESULT;
            }
            case RESULT -> {
                var stack = menu.getSlot(0).getItem();
                var used = grid.stream().filter(Slot::hasItem).toList();
                if (!stack.is(result) || used.size() != 5) return;
                check(ball.x() + "_stock_grid_uses_five_ingots", stack.getCount() == 24
                    && used.stream().allMatch(slot -> slot.getItem().is(ingot) && slot.getItem().getCount() == 1));
                verifyTooltip(ball, stack);
                if (ball.item().equals(DARK)) capture("dark-steel-stock-recipe");
                quickMove(menu, 0);
                stage = Stage.COLLECT;
            }
            case COLLECT -> {
                if (grid.stream().anyMatch(Slot::hasItem) || count(result) != 24) return;
                check(ball.x() + "_survival_collects_all_twenty_four_and_spends_five_ingots", count(ingot) == ingotsBefore - 5);
                if (++recipeIndex == recipes.size()) {
                    minecraft.player.closeContainer();
                    signal("crafted");
                    stage = Stage.INSTALL;
                } else {
                    stage = Stage.RECIPE;
                }
            }
            default -> {}
        }
    }

    private void verifyTooltip(Ball ball, ItemStack stack) {
        var lines = stack.getTooltipLines(minecraft.player, TooltipFlag.NORMAL).stream().map(Component::getString).toList();
        check(ball.x() + "_native_tooltip_uses_the_synced_stock_data",
            ball.modifiers().equals(ClientDataMaps.get(GrindingBallData.DATA_MAP_TYPE, stack.getItem()))
                && lines.contains("Main Output " + (int) (ball.multiplier() * 100) + "%")
                && lines.contains("Bonus Output " + (int) (ball.bonus() * 100) + "%")
                && lines.contains("Power Use " + (int) (ball.power() * 100) + "%"));
    }

    private static Vec3 standingPosition(Ball ball) {
        return new Vec3(ball.x() + 0.5, 80, 11.5);
    }

    private boolean openAfterTeleport(String marker, Vec3 playerPosition, BlockPos target) {
        var player = minecraft.player;
        if (!ready(marker) || minecraft.screen != null || player.distanceToSqr(playerPosition) > 0.15) {
            interactAt = -1;
            return false;
        }
        // Let the fixture teleport settle before the server handles the use packet.
        if (interactAt < 0) interactAt = player.tickCount + 8;
        if (player.tickCount < interactAt) return false;
        interactAt = -1;
        open(target);
        return true;
    }

    private void connect() {
        if (!(minecraft.screen instanceof TitleScreen) || minecraft.getOverlay() != null) return;
        if (titleAt == 0) titleAt = System.nanoTime();
        if (System.nanoTime() - titleAt < 2_000_000_000L) return;
        String address = System.getProperty("enderio.clientqa.server");
        ConnectScreen.startConnecting(new TitleScreen(), minecraft, ServerAddress.parseString(address),
            new ServerData("Ender IO Stock Grinding Balls", address, true), false);
        stage = Stage.JOIN;
    }

    private void open(BlockPos pos) {
        var hit = new BlockHitResult(Vec3.atCenterOf(pos).relative(Direction.SOUTH, 0.5), Direction.SOUTH, pos, false);
        check("native_menu_at_" + pos.getX(), minecraft.gameMode.useItemOn(minecraft.player, InteractionHand.MAIN_HAND, hit).consumesAction());
    }

    private static Item item(String id) {
        return BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(id)).orElseThrow();
    }

    private int count(Item item) {
        return minecraft.player.getInventory().countItem(item);
    }

    private void quickMove(AbstractContainerMenu menu, int slot) {
        minecraft.gameMode.handleInventoryMouseClick(menu.containerId, slot, 0, ClickType.QUICK_MOVE, minecraft.player);
    }

    private boolean ready(String label) {
        return Files.exists(output.resolve("server-" + label));
    }

    private void signal(String name) throws IOException {
        var signals = output.resolve("signals");
        Files.createDirectories(signals);
        Files.writeString(signals.resolve(name + ".json"), "{}");
    }

    private void capture(String name) throws IOException {
        var frame = ClientSmoke.capture(name + ".png");
        captures.put(name, frame);
        check(name + "_hidden_muted", Boolean.TRUE.equals(frame.get("passed")));
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
        ClientSmoke.complete("client_stock_grinding_balls", result);
    }
}
