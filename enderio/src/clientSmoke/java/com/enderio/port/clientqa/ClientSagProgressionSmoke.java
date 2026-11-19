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
        TABLE, RECIPE, PREVIEW, CRAFTED, FINISH, DONE }

    private static final BlockPos STOCK = new BlockPos(3063, 80, 8);
    private static final BlockPos AUTHORED = new BlockPos(3071, 80, 8);
    private static final BlockPos TABLE = new BlockPos(3067, 80, 8);
    private static final GrindingBallData FLINT = new GrindingBallData(1.2f, 1.25f, 0.85f, 24000);
    private static final GrindingBallData FEATHER = new GrindingBallData(2, 10, 0.5f, 2200);
    private static final ResourceLocation CAPACITOR_RECIPE = ResourceLocation.parse("enderio:double_layer_capacitor");

    private final Minecraft minecraft = Minecraft.getInstance();
    private final String address = System.getProperty("enderio.clientqa.server");
    private final Path output = Path.of(System.getProperty("enderio.clientqa.output"));
    private final Map<String, Boolean> checks = new LinkedHashMap<>();
    private final Map<String, Object> captures = new LinkedHashMap<>();
    private final long started = System.nanoTime();
    private Stage stage = Stage.TITLE;
    private long titleAt;
    private int useAt = -1;
    private int requestTick;
    private int initialPowder;

    ClientSagProgressionSmoke() {
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
                    stage = Stage.STOCK;
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
                default -> { }
            }
        } catch (Throwable failure) {
            failure.printStackTrace();
            finish(failure);
        }
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

    private static boolean matches(SagMillMenu menu, GrindingBallData ball, int progress, int cost, int wear) {
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
        result.put("passed", failure == null);
        if (failure != null) result.put("error", failure.toString());
        ClientSmoke.complete("client_sag_progression", result);
    }
}
