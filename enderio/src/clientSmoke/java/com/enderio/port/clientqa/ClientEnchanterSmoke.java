package com.enderio.port.clientqa;

import com.enderio.enderio.client.content.machines.gui.screen.EnchanterScreen;
import com.enderio.enderio.content.enchanter.EnchanterMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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

final class ClientEnchanterSmoke {
    private enum Stage { TITLE, JOIN, OPEN, RESTRICTED, WAIT_RESTRICTED, UNLOCK, COLLECT, VERIFY, CONFIRM, DONE }

    private static final int OUTPUT_SLOT = 3;
    private static final int FIRST_PLAYER_SLOT = 4;
    private static final int XP_COST = 11;
    private final boolean restarted = System.getProperty("enderio.clientqa.multiplayerPhase").equals("verify");
    private final BlockPos pos = new BlockPos(restarted ? 3454 : 3450, 80, 8);
    private final Minecraft minecraft = Minecraft.getInstance();
    private final String address = System.getProperty("enderio.clientqa.server");
    private final Path output = Path.of(System.getProperty("enderio.clientqa.output"));
    private final long started = System.nanoTime();
    private final Map<String, Boolean> checks = new LinkedHashMap<>();
    private final Map<String, Object> captures = new LinkedHashMap<>();
    private Stage stage = Stage.TITLE;
    private long titleReady;
    private int openTick;
    private int restrictedTick;
    private int collectionTick;

    ClientEnchanterSmoke() {
        MinecraftForge.EVENT_BUS.addListener(this::render);
    }

    private void render(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage == Stage.DONE) return;
        try {
            if (minecraft.screen instanceof DisconnectedScreen) throw new AssertionError("Disconnected in " + stage);
            if (System.nanoTime() - started > 130_000_000_000L) throw new AssertionError("Timed out in " + stage);
            if (stage == Stage.TITLE) {
                connect();
                return;
            }
            if (minecraft.player == null || minecraft.level == null) return;
            if (stage == Stage.JOIN && minecraft.screen == null) {
                var connection = minecraft.getConnection().getConnection();
                check("real_dedicated_survival_session", !minecraft.player.isCreative() && !minecraft.player.isSpectator()
                    && minecraft.getSingleplayerServer() == null && !connection.isMemoryConnection()
                    && connection.getRemoteAddress() instanceof InetSocketAddress);
                signal("connected");
                stage = Stage.OPEN;
            }
            if (stage == Stage.OPEN) {
                open();
            } else if (minecraft.screen instanceof EnchanterScreen screen) {
                exercise(screen.getMenu());
            } else if (stage == Stage.RESTRICTED && minecraft.player.tickCount - openTick > 90) {
                throw new AssertionError("Native use did not open the Enchanter, screen=" + minecraft.screen);
            }
        } catch (Throwable failure) {
            failure.printStackTrace();
            finish(failure);
        }
    }

    private void connect() {
        if (!(minecraft.screen instanceof TitleScreen) || minecraft.getOverlay() != null) return;
        if (titleReady == 0) titleReady = System.nanoTime();
        if (System.nanoTime() - titleReady < 2_000_000_000L) return;
        ConnectScreen.startConnecting(new TitleScreen(), minecraft, ServerAddress.parseString(address),
            new ServerData("Ender IO Enchanter", address, true), false);
        stage = Stage.JOIN;
    }

    private void open() {
        double x = pos.getX() + 0.5;
        if (!marker("ready") || minecraft.screen != null || minecraft.player.distanceToSqr(x, 80, 10.5) > 0.1
            || minecraft.level.getBlockEntity(pos) == null) return;
        var hit = new BlockHitResult(new Vec3(x, 80.6, 9), Direction.SOUTH, pos, false);
        minecraft.gameMode.useItemOn(minecraft.player, InteractionHand.MAIN_HAND, hit);
        openTick = minecraft.player.tickCount;
        stage = Stage.RESTRICTED;
    }

    private void exercise(EnchanterMenu menu) throws IOException {
        var player = minecraft.player;
        var inventory = player.getInventory();
        int xpBefore = restarted ? 19 : 30;
        int bookSlot = restarted ? 8 : 9;
        switch (stage) {
            case RESTRICTED -> {
                var preview = menu.getSlot(OUTPUT_SLOT).getItem();
                boolean ready = player.experienceLevel == (restarted ? 19 : 10) && sharpnessBook(preview)
                    && menu.getSlot(1).getItem().getCount() == 25 && menu.getSlot(2).getItem().getCount() == 10;
                if (!ready) {
                    if (player.tickCount - openTick > 90) {
                        throw new AssertionError("Prepared menu did not sync, xp=" + player.experienceLevel + ", cost=" + menu.getCurrentCost()
                            + ", slots=" + menu.getItems().subList(0, OUTPUT_SLOT + 1) + ", preview tag=" + preview.getTag());
                    }
                    return;
                }
                check("preview_shows_exact_level_cost", menu.getCurrentCost() == XP_COST);
                if (restarted) {
                    check("full_inventory_after_restart", inventory.getFreeSlot() == -1 && sharpnessBook(inventory.getItem(9)));
                } else {
                    click(menu, OUTPUT_SLOT, ClickType.PICKUP);
                }
                click(menu, OUTPUT_SLOT, ClickType.QUICK_MOVE);
                capture("restricted-enchanting");
                restrictedTick = player.tickCount;
                stage = Stage.WAIT_RESTRICTED;
            }
            case WAIT_RESTRICTED -> {
                if (player.tickCount - restrictedTick < 8) return;
                check("restricted_clicks_leave_preview_and_levels_unchanged", menu.getCarried().isEmpty()
                    && player.experienceLevel == (restarted ? 19 : 10) && sharpnessBook(menu.getSlot(OUTPUT_SLOT).getItem()));
                signal("restricted-attempt");
                stage = Stage.UNLOCK;
            }
            case UNLOCK -> {
                if (!marker("unlocked") || player.experienceLevel != xpBefore || !inventory.getItem(bookSlot).isEmpty()) return;
                check("unblocked_preview_still_matches", sharpnessBook(menu.getSlot(OUTPUT_SLOT).getItem())
                    && menu.getCarried().isEmpty() && menu.getCurrentCost() == XP_COST);
                collectionTick = player.tickCount;
                click(menu, OUTPUT_SLOT, restarted ? ClickType.QUICK_MOVE : ClickType.PICKUP);
                stage = Stage.COLLECT;
            }
            case COLLECT -> {
                boolean received = sharpnessBook(restarted ? inventory.getItem(bookSlot) : menu.getCarried());
                if (!received && player.tickCount - collectionTick < 70) return;
                check("menu_receives_the_sharpness_ii_book", received);
                if (!restarted) click(menu, FIRST_PLAYER_SLOT, ClickType.PICKUP);
                stage = Stage.VERIFY;
            }
            case VERIFY -> {
                if (!sharpnessBook(inventory.getItem(bookSlot)) || player.experienceLevel != xpBefore - XP_COST
                    || !menu.getCarried().isEmpty()) return;
                check("exact_charge_and_ingredient_remainders", menu.getSlot(0).getItem().isEmpty()
                    && menu.getSlot(1).getItem().is(Items.QUARTZ) && menu.getSlot(1).getItem().getCount() == 1
                    && menu.getSlot(2).getItem().is(Items.LAPIS_LAZULI) && menu.getSlot(2).getItem().getCount() == 4
                    && menu.getSlot(OUTPUT_SLOT).getItem().isEmpty() && menu.getCurrentCost() == -1);
                capture("collected-enchanted-book");
                signal("collected");
                stage = Stage.CONFIRM;
            }
            case CONFIRM -> {
                if (marker("confirmed")) finish(null);
            }
            default -> { }
        }
    }

    private static boolean sharpnessBook(ItemStack stack) {
        return stack.is(Items.ENCHANTED_BOOK) && stack.getCount() == 1
            && EnchantmentHelper.getEnchantments(stack).equals(Map.of(Enchantments.SHARPNESS, 2));
    }

    private void click(EnchanterMenu menu, int slot, ClickType type) {
        minecraft.gameMode.handleInventoryMouseClick(menu.containerId, slot, 0, type, minecraft.player);
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

    private void finish(Throwable failure) {
        stage = Stage.DONE;
        var result = new LinkedHashMap<String, Object>();
        result.put("passed", failure == null);
        result.put("checks", checks);
        result.put("captures", captures);
        if (failure != null) result.put("error", failure.toString());
        ClientSmoke.complete("client_enchanter", result);
    }
}
