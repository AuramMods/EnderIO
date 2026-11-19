package com.enderio.port.clientqa;

import com.enderio.enderio.client.content.machines.gui.screen.EnchanterScreen;
import com.enderio.enderio.content.enchanter.EnchanterMenu;
import com.enderio.enderio.content.enchanter.EnchanterRecipe;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

final class ClientEnchanterReloadSmoke {
    private enum Stage { TITLE, JOIN, OPEN, INITIAL, DENIED, REMOVED, REPLACED, TAKE, PAID, OPEN_SECOND, SECOND, DONE }

    private static final ResourceLocation RECIPE = ResourceLocation.fromNamespaceAndPath("port_enchanter", "amethyst");
    private static final int OUTPUT = 3;
    private final Minecraft minecraft = Minecraft.getInstance();
    private final Path output = Path.of(System.getProperty("enderio.clientqa.output"));
    private final String address = System.getProperty("enderio.clientqa.server");
    private final long started = System.nanoTime();
    private final Map<String, Boolean> checks = new LinkedHashMap<>();
    private final Map<String, Object> captures = new LinkedHashMap<>();
    private final Map<String, Object> observed = new LinkedHashMap<>();
    private Stage stage = Stage.TITLE;
    private long titleReady;
    private int stageTick;
    private int reloadTick = -1;

    ClientEnchanterReloadSmoke() {
        MinecraftForge.EVENT_BUS.addListener(this::render);
    }

    private void render(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage == Stage.DONE) return;
        try {
            if (minecraft.screen instanceof DisconnectedScreen) throw new AssertionError("Disconnected in " + stage);
            if (System.nanoTime() - started > 140_000_000_000L) throw new AssertionError("Timed out in " + stage);
            if (stage == Stage.TITLE) {
                if (!(minecraft.screen instanceof TitleScreen) || minecraft.getOverlay() != null) return;
                if (titleReady == 0) titleReady = System.nanoTime();
                if (System.nanoTime() - titleReady < 2_000_000_000L) return;
                ConnectScreen.startConnecting(new TitleScreen(), minecraft, ServerAddress.parseString(address),
                    new ServerData("Ender IO Enchanter reload", address, true), false);
                stage = Stage.JOIN;
                return;
            }
            if (minecraft.level == null || minecraft.player == null) return;
            if (marker("stop")) {
                finish(null);
            } else if (stage == Stage.JOIN && minecraft.screen == null) {
                var conn = minecraft.getConnection().getConnection();
                check("survival_tcp", !minecraft.player.isCreative() && !minecraft.player.isSpectator()
                    && minecraft.getSingleplayerServer() == null && !conn.isMemoryConnection()
                    && conn.getRemoteAddress() instanceof InetSocketAddress);
                signal("connected");
                enter(Stage.OPEN);
            } else if (stage == Stage.OPEN) {
                open(3472, "ready", Stage.INITIAL);
            } else if (stage == Stage.OPEN_SECOND) {
                open(3476, "second", Stage.SECOND);
            } else if (minecraft.screen instanceof EnchanterScreen screen) {
                exercise(screen.getMenu());
            }
        } catch (Throwable error) {
            error.printStackTrace();
            finish(error);
        }
    }

    private void open(int x, String ready, Stage next) {
        var pos = new BlockPos(x, 80, 8);
        if (!marker(ready) || minecraft.screen != null || minecraft.player.distanceToSqr(x + 0.5, 80, 10.5) > 0.1
            || minecraft.level.getBlockEntity(pos) == null) return;
        var hit = new BlockHitResult(new Vec3(x + 0.5, 80.6, 9), Direction.SOUTH, pos, false);
        minecraft.gameMode.useItemOn(minecraft.player, InteractionHand.MAIN_HAND, hit);
        enter(next);
    }

    private void exercise(EnchanterMenu menu) throws IOException {
        var player = minecraft.player;
        var preview = menu.getSlot(OUTPUT).getItem();
        int tick = player.tickCount;
        switch (stage) {
            case INITIAL -> {
                if (tick - stageTick < 8 || player.experienceLevel != 19 || !book(preview, Enchantments.SHARPNESS, 2)) return;
                observe("initial", menu);
                checks.put("initial_cost_comes_from_server_configuration", menu.getCurrentCost() == 20);
                capture("initial-cost");
                clickOutput(menu);
                enter(Stage.DENIED);
            }
            case DENIED -> {
                if (tick - stageTick < 8) return;
                check("19_levels_cannot_take_the_20_level_result", player.experienceLevel == 19
                    && book(preview, Enchantments.SHARPNESS, 2) && menu.getCarried().isEmpty()
                    && player.getInventory().getItem(8).isEmpty());
                signal("initial-denied");
                enter(Stage.REMOVED);
            }
            case REMOVED -> {
                if (minecraft.level.getRecipeManager().byKey(RECIPE).isPresent() || !settledReload()) return;
                observe("removed", menu);
                checks.put("recipe_removal_clears_preview_and_price", preview.isEmpty() && menu.getCurrentCost() == -1);
                signal("recipe-removed");
                enter(Stage.REPLACED);
            }
            case REPLACED -> {
                var recipe = minecraft.level.getRecipeManager().byKey(RECIPE).orElse(null);
                if (!(recipe instanceof EnchanterRecipe replacement) || replacement.enchantment().value() != Enchantments.MENDING
                    || !settledReload()) return;
                observe("replaced", menu);
                checks.put("reloaded_menu_has_mending_and_server_price", book(preview, Enchantments.MENDING, 1) && menu.getCurrentCost() == 33);
                capture("replacement-cost");
                signal("replacement-seen");
                enter(Stage.TAKE);
            }
            case TAKE -> {
                if (!marker("take") || player.experienceLevel != 40) return;
                check("replacement_is_collectible_at_40_levels", book(preview, Enchantments.MENDING, 1) && menu.getCurrentCost() == 33);
                clickOutput(menu);
                enter(Stage.PAID);
            }
            case PAID -> {
                if (marker("paid")) {
                    player.closeContainer();
                    enter(Stage.OPEN_SECOND);
                    return;
                }
                if (checks.containsKey("exact_reload_payment") || !book(player.getInventory().getItem(8), Enchantments.MENDING, 1)
                    || player.experienceLevel != 7 || !preview.isEmpty() || menu.getCurrentCost() != -1) return;
                check("exact_reload_payment", menu.getSlot(0).getItem().isEmpty() && menu.getSlot(1).getItem().getCount() == 4
                    && menu.getSlot(2).getItem().getCount() == 2);
                signal("collected");
            }
            case SECOND -> {
                if (tick - stageTick < 8 || checks.containsKey("previously_closed_enchanter_refreshes_on_open")) return;
                observe("newly-opened", menu);
                check("previously_closed_enchanter_refreshes_on_open", book(preview, Enchantments.MENDING, 1) && menu.getCurrentCost() == 33);
                signal("second-preview");
            }
            default -> { }
        }
    }

    private void observe(String step, EnchanterMenu menu) {
        observed.put(step, Map.of("xpCost", menu.getCurrentCost(), "preview", menu.getSlot(OUTPUT).getItem().save(new net.minecraft.nbt.CompoundTag()).toString()));
    }

    private boolean settledReload() {
        if (reloadTick == -1) reloadTick = minecraft.player.tickCount;
        return minecraft.player.tickCount - reloadTick >= 8;
    }

    private void enter(Stage next) {
        stage = next;
        stageTick = minecraft.player.tickCount;
        reloadTick = -1;
    }

    private void clickOutput(EnchanterMenu menu) {
        minecraft.gameMode.handleInventoryMouseClick(menu.containerId, OUTPUT, 0, ClickType.QUICK_MOVE, minecraft.player);
    }

    private static boolean book(ItemStack stack, Enchantment enchantment, int level) {
        return stack.is(Items.ENCHANTED_BOOK) && stack.getCount() == 1 && EnchantmentHelper.getEnchantments(stack).equals(Map.of(enchantment, level));
    }

    private boolean marker(String name) {
        return Files.exists(output.resolve("server-" + name));
    }

    private void signal(String name) throws IOException {
        var directory = output.resolve("signals");
        Files.createDirectories(directory);
        Files.writeString(directory.resolve(name + ".json"), "{}");
    }

    private void capture(String name) throws IOException {
        var frame = ClientSmoke.capture(name + ".png");
        captures.put(name, frame);
        check("silent_hidden_" + name, Boolean.TRUE.equals(frame.get("passed")));
    }

    private void check(String name, boolean passed) {
        checks.put(name, passed);
        if (!passed) throw new AssertionError(name);
    }

    private void finish(Throwable error) {
        stage = Stage.DONE;
        var result = new LinkedHashMap<String, Object>();
        result.put("passed", error == null && checks.values().stream().allMatch(Boolean::booleanValue));
        result.put("checks", checks);
        result.put("captures", captures);
        result.put("observed", observed);
        if (error != null) result.put("error", error.toString());
        ClientSmoke.complete("client_enchanter_reload", result);
    }
}
