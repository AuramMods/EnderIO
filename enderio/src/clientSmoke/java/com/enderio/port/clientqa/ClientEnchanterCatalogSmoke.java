package com.enderio.port.clientqa;

import com.enderio.enderio.client.content.machines.gui.screen.EnchanterScreen;
import com.enderio.enderio.content.enchanter.EnchanterMenu;
import com.enderio.enderio.content.enchanter.EnchanterRecipe;
import com.google.gson.Gson;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

final class ClientEnchanterCatalogSmoke {
    private enum Stage { TITLE, JOIN, OPEN, PREVIEW, COLLECT, VERIFY, DONE }

    private record Trial(String name, String recipe, String enchantment, int level, int xp, String catalyst,
                         int catalystCount, int catalystLeft, int lapis, boolean capture) {}

    private static final BlockPos STATION = new BlockPos(4151, 80, 8);
    private static final Vec3 STAND = new Vec3(4151.5, 80, 10.5);
    private static final int START_XP = 100;
    private static final int PRODUCT_SLOT = 8;

    private final Minecraft minecraft = Minecraft.getInstance();
    private final Path output = Path.of(System.getProperty("enderio.clientqa.output"));
    private final long started = System.nanoTime();
    private final Map<String, Boolean> checks = new LinkedHashMap<>();
    private final Map<String, Object> captures = new LinkedHashMap<>();
    private Stage stage = Stage.TITLE;
    private Trial[] trials;
    private int index;
    private int positioned = -1;
    private int sentAt = -1;
    private long titleAt;

    ClientEnchanterCatalogSmoke() {
        MinecraftForge.EVENT_BUS.addListener(this::render);
    }

    private void render(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage == Stage.DONE) return;
        try {
            if (minecraft.screen instanceof DisconnectedScreen || System.nanoTime() - started > 230_000_000_000L) {
                throw new AssertionError("Stock Enchanter stopped at " + stage + " on trial " + index);
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
                    check("packaged_dedicated_survival_tcp", !player.isCreative() && !player.isSpectator()
                        && !minecraft.getConnection().getConnection().isMemoryConnection());
                    trials = new Gson().fromJson(Files.readString(output.resolve("layout.json")), Trial[].class);
                    signal("connected");
                    stage = Stage.OPEN;
                }
                case OPEN -> {
                    if (!ready("open") || minecraft.screen != null || player.distanceToSqr(STAND) > 0.12
                        || minecraft.level.getBlockEntity(STATION) == null) return;
                    if (positioned == -1) positioned = player.tickCount;
                    if (player.tickCount - positioned < 6) return;
                    var hit = new BlockHitResult(new Vec3(4151.5, 80.5, 9), Direction.SOUTH, STATION, false);
                    check("player_opens_the_stock_enchanter", minecraft.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit).consumesAction());
                    stage = Stage.PREVIEW;
                }
                case PREVIEW, COLLECT -> {
                    if (minecraft.screen instanceof EnchanterScreen screen) exercise(screen.getMenu(), trials[index]);
                }
                case VERIFY -> {
                    if (ready("complete")) finish(null);
                }
                default -> {}
            }
        } catch (Throwable failure) {
            failure.printStackTrace();
            finish(failure);
        }
    }

    private void exercise(EnchanterMenu menu, Trial trial) throws Exception {
        var player = minecraft.player;
        int tick = player.tickCount;
        var product = player.getInventory().getItem(PRODUCT_SLOT);
        var catalyst = menu.getSlot(1).getItem();
        var lapis = menu.getSlot(2).getItem();
        var preview = menu.getSlot(3).getItem();
        if (stage == Stage.PREVIEW) {
            if (!ready("ready-" + trial.name())) return;
            if (sentAt == -1) sentAt = tick;
            boolean synced = player.experienceLevel == START_XP && product.isEmpty()
                && menu.getSlot(0).getItem().is(Items.WRITABLE_BOOK) && catalyst.getCount() == trial.catalystCount()
                && key(catalyst).equals(trial.catalyst()) && lapis.is(Items.LAPIS_LAZULI) && lapis.getCount() == trial.lapis() + 1
                && matches(preview, trial) && menu.getCurrentCost() == trial.xp();
            if (!synced && tick - sentAt < 70) return;
            if (!synced) {
                throw new AssertionError(trial.name() + " expected " + trial + " but got cost " + menu.getCurrentCost()
                    + ", player XP " + player.experienceLevel + ", slots " + menu.getItems().subList(0, 4) + ", result " + preview.getTag());
            }
            var recipe = minecraft.level.getRecipeManager().byKey(ResourceLocation.tryParse(trial.recipe())).orElseThrow();
            check(trial.name() + "_stock_recipe_and_cost_sync", recipe instanceof EnchanterRecipe);
            if (trial.capture()) capture(trial.name());
            minecraft.gameMode.handleInventoryMouseClick(menu.containerId, 3, 0, ClickType.QUICK_MOVE, player);
            sentAt = tick;
            stage = Stage.COLLECT;
            return;
        }

        boolean paid = matches(product, trial) && player.experienceLevel == START_XP - trial.xp()
            && menu.getSlot(0).getItem().isEmpty() && preview.isEmpty() && menu.getCurrentCost() == -1
            && catalyst.getCount() == trial.catalystLeft() && lapis.is(Items.LAPIS_LAZULI) && lapis.getCount() == 1
            && menu.getCarried().isEmpty();
        if (!paid && tick - sentAt < 70) return;
        check(trial.name() + "_native_take_spends_exact_xp_and_inputs", paid);
        signal("took-" + trial.name());
        sentAt = -1;
        stage = ++index == trials.length ? Stage.VERIFY : Stage.PREVIEW;
    }

    private static boolean matches(ItemStack book, Trial trial) {
        if (!book.is(Items.ENCHANTED_BOOK) || book.getCount() != 1) return false;
        var enchantment = BuiltInRegistries.ENCHANTMENT.get(ResourceLocation.tryParse(trial.enchantment()));
        return EnchantmentHelper.getEnchantments(book).equals(Map.of(enchantment, trial.level()));
    }

    private static String key(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    private void connect() {
        if (!(minecraft.screen instanceof TitleScreen) || minecraft.getOverlay() != null) return;
        if (titleAt == 0) titleAt = System.nanoTime();
        if (System.nanoTime() - titleAt < 2_000_000_000L) return;
        String address = System.getProperty("enderio.clientqa.server");
        ConnectScreen.startConnecting(new TitleScreen(), minecraft, ServerAddress.parseString(address),
            new ServerData("Ender IO Stock Enchanter", address, true), false);
        stage = Stage.JOIN;
    }

    private boolean ready(String label) {
        return Files.exists(output.resolve("server-" + label));
    }

    private void signal(String label) throws Exception {
        var signals = output.resolve("signals");
        Files.createDirectories(signals);
        Files.writeString(signals.resolve(label + ".json"), "{}");
    }

    private void capture(String label) throws Exception {
        var frame = ClientSmoke.capture(label + ".png");
        captures.put(label, frame);
        check(label + "_renders_hidden_and_muted", Boolean.TRUE.equals(frame.get("passed")));
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
        ClientSmoke.complete("client_enchanter_catalog", result);
    }
}
