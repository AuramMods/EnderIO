package com.enderio.port.clientqa;

import com.enderio.enderio.api.components.GrindingBallData;
import com.enderio.enderio.client.content.machines.gui.screen.SagMillScreen;
import com.enderio.enderio.content.machines.sag_mill.SagMillMenu;
import com.enderio.enderio.init.EIOItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
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

final class ClientSagProgressionSmoke {
    private enum Stage { TITLE, JOIN, STOCK, STOCK_PAUSED, STOCK_RESUMED, AUTHORED, AUTHORED_PAUSED, BALL_EXPIRED,
        TABLE, RECIPE, PREVIEW, CRAFTED, FINISH, SWAP_OPEN, SWAP_PAID, SWAP_INPUT, SWAP_OUTPUT, SWAP_SETTLED, DONE }

    private record SwapTrial(String name, BlockPos mill, Item replacement, int hotbar) { }

    private static final BlockPos STOCK = new BlockPos(3063, 80, 8);
    private static final BlockPos AUTHORED = new BlockPos(3071, 80, 8);
    private static final BlockPos TABLE = new BlockPos(3067, 80, 8);
    private static final GrindingBallData FLINT = new GrindingBallData(1.2f, 1.25f, 0.85f, 24000);
    private static final GrindingBallData FEATHER = new GrindingBallData(2, 10, 0.5f, 2200);
    private static final GrindingBallData REDSTONE_ALLOY = new GrindingBallData(1, 1, 0.35f, 30000);
    private static final int PAID_COAL_COST = 840;
    private static final int FIRST_OUTPUT = SagMillMenu.INPUTS_INDEX + SagMillMenu.INPUT_COUNT;
    private static final SwapTrial STONE_SWAP = new SwapTrial("stone", new BlockPos(4980, 80, 8), Items.STONE, 0);
    private static final SwapTrial CHARCOAL_SWAP = new SwapTrial("charcoal", new BlockPos(4988, 80, 8), Items.CHARCOAL, 1);
    private static final ResourceLocation CAPACITOR_RECIPE = ResourceLocation.parse("enderio:double_layer_capacitor");

    private final Minecraft minecraft = Minecraft.getInstance();
    private final String address = System.getProperty("enderio.clientqa.server");
    private final Path output = Path.of(System.getProperty("enderio.clientqa.output"));
    private final Map<String, Boolean> checks = new LinkedHashMap<>();
    private final Map<String, Object> captures = new LinkedHashMap<>();
    private final boolean backpressure;
    private final long started = System.nanoTime();
    private Stage stage = Stage.TITLE;
    private SwapTrial trial = STONE_SWAP;
    private long titleAt;
    private int useAt = -1;
    private int requestTick;
    private int initialPowder;

    ClientSagProgressionSmoke(boolean backpressure) {
        this.backpressure = backpressure;
        MinecraftForge.EVENT_BUS.addListener(this::render);
    }

    private void render(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage == Stage.DONE) return;
        try {
            if (minecraft.screen instanceof DisconnectedScreen || System.nanoTime() - started > 150_000_000_000L) {
                throw new AssertionError("SAG progression stopped during " + stage);
            }
            if (stage == Stage.TITLE) {
                if (!(minecraft.screen instanceof TitleScreen) || minecraft.getOverlay() != null) return;
                if (titleAt == 0) titleAt = System.nanoTime();
                if (System.nanoTime() - titleAt < 2_000_000_000L) return;
                ConnectScreen.startConnecting(new TitleScreen(), minecraft, ServerAddress.parseString(address),
                    new ServerData("SAG progression QA", address, true), false);
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
                    stage = backpressure ? Stage.SWAP_OPEN : Stage.STOCK;
                }
                case STOCK -> {
                    if (open("stock", STOCK)) stage = Stage.STOCK_PAUSED;
                }
                case STOCK_PAUSED, STOCK_RESUMED, AUTHORED_PAUSED, BALL_EXPIRED -> checkMill();
                case AUTHORED -> {
                    if (open("authored", AUTHORED)) stage = Stage.AUTHORED_PAUSED;
                }
                case TABLE -> {
                    if (!open("table", TABLE)) return;
                    initialPowder = count(EIOItems.POWDERED_COAL.get());
                    check("survival_player_receives_the_stock_hopper_output", initialPowder >= 2);
                    requestTick = player.tickCount;
                    stage = Stage.RECIPE;
                }
                case RECIPE, PREVIEW, CRAFTED -> craft();
                case FINISH -> {
                    if (ready("complete")) finish(null);
                }
                case SWAP_OPEN -> {
                    if (open(trial.name + "-open", trial.mill)) stage = Stage.SWAP_PAID;
                }
                case SWAP_PAID, SWAP_INPUT, SWAP_OUTPUT, SWAP_SETTLED -> checkSwap();
                default -> { }
            }
        } catch (Throwable failure) {
            failure.printStackTrace();
            finish(failure);
        }
    }

    private void checkSwap() throws IOException {
        if (!(minecraft.screen instanceof SagMillScreen screen)) return;
        var menu = screen.getMenu();
        var player = minecraft.player;
        var input = menu.getSlot(SagMillMenu.INPUTS_INDEX).getItem();
        var first = menu.getSlot(FIRST_OUTPUT).getItem();
        String name = trial.name;
        switch (stage) {
            case SWAP_PAID -> {
                if (!matches(menu, REDSTONE_ALLOY, PAID_COAL_COST, PAID_COAL_COST, PAID_COAL_COST)
                    || !tagged(input, Items.COAL, 1, "PortSagInput") || !tagged(first, Items.IRON_INGOT, 64, "PortSagStock")) return;
                check(name + "_reloads_the_full_paid_job_and_spare_ball", hasSpareBall(menu));
                capture(name + "-paid");
                minecraft.gameMode.handleInventoryMouseClick(menu.containerId, SagMillMenu.INPUTS_INDEX, trial.hotbar, ClickType.SWAP, player);
                stage = Stage.SWAP_INPUT;
            }
            case SWAP_INPUT -> {
                if (!tagged(input, trial.replacement, 1, "PortSagReplacement")
                    || !tagged(player.getInventory().getItem(trial.hotbar), Items.COAL, 1, "PortSagInput")) return;
                if (!ready(name + "-clear")) {
                    if (!Files.exists(output.resolve("signals/" + name + "-swapped.json"))) signal(name + "-swapped");
                    return;
                }
                check(name + "_swap_keeps_all_original_output_and_no_extra_wear", tagged(first, Items.IRON_INGOT, 64, "PortSagStock")
                    && close(menu.getGrindingBallDamage(), 1 - PAID_COAL_COST / (float) REDSTONE_ALLOY.durability()) && hasSpareBall(menu));
                minecraft.gameMode.handleInventoryMouseClick(menu.containerId, FIRST_OUTPUT, 0, ClickType.QUICK_MOVE, player);
                stage = Stage.SWAP_OUTPUT;
            }
            case SWAP_OUTPUT -> {
                int stockTaken = player.getInventory().items.stream()
                    .filter(stack -> tagged(stack, Items.IRON_INGOT, 64, "PortSagStock")).mapToInt(ItemStack::getCount).sum();
                if (stockTaken != (trial == STONE_SWAP ? 64 : 128)) return;
                signal(name + "-cleared");
                stage = Stage.SWAP_SETTLED;
            }
            case SWAP_SETTLED -> {
                if (!ready(name + "-settled")) return;
                int expected = Integer.parseInt(Files.readString(output.resolve(name + "-powder")));
                boolean balanced = expected == 0 ? first.isEmpty() && tagged(input, Items.STONE, 1, "PortSagReplacement")
                    : first.is(EIOItems.POWDERED_COAL.get()) && first.getCount() == expected && input.isEmpty();
                checks.put(name + "_shows_expected_input_output_and_unchanged_wear",
                    balanced && matches(menu, REDSTONE_ALLOY, 0, 1, PAID_COAL_COST) && hasSpareBall(menu));
                capture(name + "-settled");
                player.closeContainer();
                signal(name + "-done");
                if (trial == CHARCOAL_SWAP) {
                    finish(null);
                } else {
                    trial = CHARCOAL_SWAP;
                    stage = Stage.SWAP_OPEN;
                }
            }
            default -> { }
        }
    }

    private static boolean tagged(ItemStack stack, Item item, int count, String key) {
        return stack.is(item) && stack.getCount() == count && stack.hasTag() && stack.getTag().getInt(key) == 233;
    }

    private static boolean hasSpareBall(SagMillMenu menu) {
        var spare = menu.getSlot(SagMillMenu.LAST_INDEX).getItem();
        return spare.is(EIOItems.REDSTONE_ALLOY_BALL.get()) && spare.getCount() == 1;
    }

    private void checkMill() throws IOException {
        if (!(minecraft.screen instanceof SagMillScreen screen)) return;
        var menu = screen.getMenu();
        switch (stage) {
            case STOCK_PAUSED -> {
                if (!matches(menu, FLINT, 600, 2040, 600)) return;
                check("stock_flint_menu_restores_coal_spare_ball_and_exact_progress", menu.getSlot(1).getItem().is(Items.COAL)
                    && menu.getSlot(6).getItem().is(Items.FLINT) && menu.getSlot(6).getItem().getCount() == 1);
                capture("stock-flint-paused");
                signal("stock-paused");
                stage = Stage.STOCK_RESUMED;
            }
            case STOCK_RESUMED -> {
                if (!ready("stock-finished") || !matches(menu, FLINT, 0, 1, 2040) || menu.getSlot(1).hasItem()) return;
                check("open_menu_updates_to_new_stock_wear_and_empty_input", menu.getSlot(6).hasItem());
                capture("stock-flint-resumed");
                minecraft.player.closeContainer();
                signal("stock-finished");
                stage = Stage.AUTHORED;
            }
            case AUTHORED_PAUSED -> {
                if (!matches(menu, FEATHER, 600, 1200, 1800)) return;
                check("authored_modifiers_and_saved_wear_sync_over_tcp", menu.getSlot(1).getItem().is(Items.COAL)
                    && !menu.getSlot(6).hasItem());
                capture("authored-ball-paused");
                signal("authored-paused");
                stage = Stage.BALL_EXPIRED;
            }
            case BALL_EXPIRED -> {
                if (!ready("authored-finished") || !matches(menu, GrindingBallData.IDENTITY, 0, 1, 0)
                    || menu.getSlot(1).hasItem()) return;
                check("open_menu_clears_the_worn_ball_and_finishes_the_next_coal", !menu.getSlot(6).hasItem());
                capture("authored-ball-exhausted");
                minecraft.player.closeContainer();
                signal("authored-finished");
                stage = Stage.TABLE;
            }
            default -> { }
        }
    }

    static boolean matches(SagMillMenu menu, GrindingBallData ball, int progress, int cost, int wear) {
        float remaining = ball.isIdentity() ? 0 : 1 - wear / (float) ball.durability();
        return menu.getGrindingBallData().equals(ball) && close(menu.getGrindingBallDamage(), remaining)
            && close(menu.getCraftingProgress(), progress / (float) cost) && menu.getEnergyStorage().getEnergyStored() == 0;
    }

    private static boolean close(float actual, float expected) {
        return Math.abs(actual - expected) < 0.0001;
    }

    private void craft() throws IOException {
        var player = minecraft.player;
        if (player.tickCount - requestTick > 120) throw new AssertionError("Capacitor crafting stopped during " + stage);
        if (!(minecraft.screen instanceof CraftingScreen screen)) return;
        var menu = screen.getMenu();
        if (stage == Stage.RECIPE) {
            var recipe = minecraft.level.getRecipeManager().byKey(CAPACITOR_RECIPE).orElseThrow();
            if (!player.getRecipeBook().contains(recipe)) return;
            minecraft.gameMode.handlePlaceRecipe(menu.containerId, recipe, false);
            stage = Stage.PREVIEW;
        } else if (stage == Stage.PREVIEW) {
            if (!menu.getSlot(0).getItem().is(EIOItems.DOUBLE_LAYER_CAPACITOR.get())
                || !menu.getSlot(5).getItem().is(EIOItems.POWDERED_COAL.get())) return;
            check("native_capacitor_recipe_uses_one_of_the_actual_powdered_coal_items", menu.getSlot(5).getItem().getCount() == 1);
            capture("ground-coal-capacitor-recipe");
            minecraft.gameMode.handleInventoryMouseClick(menu.containerId, 0, 0, ClickType.QUICK_MOVE, player);
            stage = Stage.CRAFTED;
        } else if (count(EIOItems.DOUBLE_LAYER_CAPACITOR.get()) == 1
            && menu.slots.subList(0, 10).stream().noneMatch(Slot::hasItem)) {
            check("taking_the_capacitor_spends_exactly_one_ground_coal", count(EIOItems.POWDERED_COAL.get()) == initialPowder - 1);
            player.closeContainer();
            signal("crafted");
            stage = Stage.FINISH;
        }
    }

    private int count(Item item) {
        return minecraft.player.getInventory().items.stream().filter(stack -> stack.is(item)).mapToInt(ItemStack::getCount).sum();
    }

    private boolean open(String name, BlockPos pos) {
        var player = minecraft.player;
        if (!ready(name) || minecraft.screen != null || player.distanceToSqr(pos.getX() + 0.5, 80, 11.5) > 0.15) return false;
        // Wait for the fixture teleport to be acknowledged before interacting.
        if (useAt < 0) useAt = player.tickCount + 8;
        if (player.tickCount < useAt) return false;
        useAt = -1;
        var hit = new BlockHitResult(Vec3.atCenterOf(pos).relative(Direction.SOUTH, 0.5), Direction.SOUTH, pos, false);
        check(name + "_uses_native_block_interaction", minecraft.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit).consumesAction());
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
        result.put("passed", failure == null && checks.values().stream().allMatch(Boolean.TRUE::equals));
        if (failure != null) result.put("error", failure.toString());
        ClientSmoke.complete("client_sag_progression", result);
    }
}
