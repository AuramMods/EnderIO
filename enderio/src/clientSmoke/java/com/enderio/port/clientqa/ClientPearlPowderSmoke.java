package com.enderio.port.clientqa;

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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
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

import static com.enderio.port.clientqa.ClientAlloyProgressionSmoke.Craft;
import static com.enderio.port.clientqa.ClientAlloyProgressionSmoke.id;
import static com.enderio.port.clientqa.ClientAlloyProgressionSmoke.item;

final class ClientPearlPowderSmoke {
    private enum Stage { TITLE, JOIN, WORKSHOP, CHEST, TABLE, BOOK, GRID, CURSOR, STORE, VERIFY, CONFIRM, DONE }

    private static final BlockPos TABLE = new BlockPos(3630, 80, 8);
    private static final BlockPos CHEST = new BlockPos(3631, 80, 8);
    private static final Vec3 STAND = new Vec3(3630.5, 80, 11.5);
    private static final String PEARL = "powdered_ender_pearl";
    private static final String OBSIDIAN = "powdered_obsidian";
    private static final String CEREAL = "enderios";
    private static final String BUCKET = "minecraft:bucket";
    private static final Craft RECIPE = new Craft(CEREAL, CEREAL,
        Map.of(PEARL, 1, "minecraft:milk_bucket", 1, "minecraft:bowl", 1, "minecraft:wheat", 1), CEREAL, 1, List.of());

    private final Minecraft minecraft = Minecraft.getInstance();
    private final Path output = Path.of(System.getProperty("enderio.clientqa.output"));
    private final long started = System.nanoTime();
    private final Map<String, Boolean> checks = new LinkedHashMap<>();
    private final Map<String, Object> captures = new LinkedHashMap<>();
    private Stage stage = Stage.TITLE;
    private long titleAt;

    ClientPearlPowderSmoke() {
        MinecraftForge.EVENT_BUS.addListener(this::render);
    }

    private void render(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage == Stage.DONE) return;
        try {
            if (minecraft.screen instanceof DisconnectedScreen || System.nanoTime() - started > 150_000_000_000L) {
                throw new AssertionError("Pearl powder scenario stopped at " + stage + " with " + minecraft.screen);
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
                    var recipe = minecraft.level.getRecipeManager().byKey(id(CEREAL)).orElseThrow();
                    check("the_recipe_is_locked_before_collecting_mill_outputs_and_wheat", !player.getRecipeBook().contains(recipe)
                        && count(PEARL) == 0 && count(OBSIDIAN) == 0 && count(CEREAL) == 0 && count(BUCKET) == 0);
                    signal("connected");
                    stage = Stage.WORKSHOP;
                }
                case WORKSHOP -> {
                    if (!ready("workshop") || player.distanceToSqr(STAND) > 0.15) return;
                    open(CHEST, "opens_native_stock_chest");
                    stage = Stage.CHEST;
                }
                case CHEST -> {
                    if (!(minecraft.screen instanceof ContainerScreen screen)) return;
                    var menu = screen.getMenu();
                    if (menu.slots.subList(0, 27).stream().filter(Slot::hasItem).count() != 5) return;
                    check("chest_holds_nine_original_pearl_and_four_obsidian_powders",
                        menu.getSlot(0).getItem().is(item(PEARL)) && menu.getSlot(0).getItem().getCount() == 9
                            && menu.getSlot(1).getItem().is(item(OBSIDIAN)) && menu.getSlot(1).getItem().getCount() == 4);
                    for (int slot = 0; slot < 5; slot++) click(menu, slot, ClickType.QUICK_MOVE);
                    stage = Stage.TABLE;
                }
                case TABLE -> {
                    if (!(minecraft.screen instanceof ContainerScreen screen)
                        || screen.getMenu().slots.subList(0, 27).stream().anyMatch(Slot::hasItem)) return;
                    player.closeContainer();
                    open(TABLE, "opens_native_crafting_table");
                    stage = Stage.BOOK;
                }
                case BOOK, GRID, CURSOR, STORE -> craft();
                case VERIFY -> {
                    if (minecraft.screen != null || count(BUCKET) != 1) return;
                    check("exact_cereal_powders_and_bucket_after_closing_the_menu", count(CEREAL) == 1 && count(PEARL) == 8
                        && count(OBSIDIAN) == 4 && count("minecraft:milk_bucket") == 0 && count("minecraft:bowl") == 0
                        && count("minecraft:wheat") == 0);
                    signal("crafted");
                    stage = Stage.CONFIRM;
                }
                case CONFIRM -> {
                    if (ready("done")) finish(null);
                }
                default -> {}
            }
        } catch (Throwable failure) {
            failure.printStackTrace();
            finish(failure);
        }
    }

    private void craft() throws IOException {
        if (!(minecraft.screen instanceof CraftingScreen screen)) return;
        var menu = screen.getMenu();
        var player = minecraft.player;
        var grid = menu.slots.subList(1, 10);
        switch (stage) {
            case BOOK -> {
                var recipe = minecraft.level.getRecipeManager().byKey(id(CEREAL)).orElseThrow();
                if (!player.getRecipeBook().contains(recipe)) return;
                check("stock_recipe_unlocks_and_only_original_inputs_are_available", grid.stream().noneMatch(Slot::hasItem)
                    && count(PEARL) == 9 && count(OBSIDIAN) == 4 && count("minecraft:bowl") == 1
                    && count("minecraft:milk_bucket") == 1 && count("minecraft:wheat") == 1);
                minecraft.gameMode.handlePlaceRecipe(menu.containerId, recipe, false);
                stage = Stage.GRID;
            }
            case GRID -> {
                var result = menu.getSlot(0).getItem();
                if (!result.is(item(CEREAL))) return;
                check("native_recipe_uses_exactly_one_original_powder", result.getCount() == 1 && RECIPE.matchesGrid(grid));
                var frame = ClientSmoke.capture("original-pearl-powder-enderios.png");
                captures.put("original-pearl-powder-enderios", frame);
                check("recipe_rendered_hidden_and_muted", Boolean.TRUE.equals(frame.get("passed")));
                click(menu, 0, ClickType.PICKUP);
                stage = Stage.CURSOR;
            }
            case CURSOR -> {
                if (!menu.getCarried().is(item(CEREAL)) || menu.getSlot(0).hasItem()) return;
                var remainders = grid.stream().filter(Slot::hasItem).map(Slot::getItem).toList();
                if (remainders.size() != 1 || !remainders.get(0).is(item(BUCKET))) return;
                check("the_original_milk_leaves_one_empty_bucket", remainders.get(0).getCount() == 1 && menu.getCarried().getCount() == 1);
                var slot = menu.slots.stream().filter(target -> target.container == player.getInventory() && !target.hasItem())
                    .findFirst().orElseThrow();
                click(menu, slot.index, ClickType.PICKUP);
                stage = Stage.STORE;
            }
            case STORE -> {
                if (count(CEREAL) != 1 || !menu.getCarried().isEmpty()) return;
                player.closeContainer();
                stage = Stage.VERIFY;
            }
            default -> {}
        }
    }

    private void connect() {
        if (!(minecraft.screen instanceof TitleScreen) || minecraft.getOverlay() != null) return;
        if (titleAt == 0) titleAt = System.nanoTime();
        if (System.nanoTime() - titleAt < 2_000_000_000L) return;
        String address = System.getProperty("enderio.clientqa.server");
        ConnectScreen.startConnecting(new TitleScreen(), minecraft, ServerAddress.parseString(address),
            new ServerData("Ender IO Pearl Powder", address, true), false);
        stage = Stage.JOIN;
    }

    private void open(BlockPos pos, String label) {
        var hit = new BlockHitResult(Vec3.atCenterOf(pos).relative(Direction.SOUTH, 0.5), Direction.SOUTH, pos, false);
        check(label, minecraft.gameMode.useItemOn(minecraft.player, InteractionHand.MAIN_HAND, hit).consumesAction());
    }

    private void click(AbstractContainerMenu menu, int slot, ClickType type) {
        minecraft.gameMode.handleInventoryMouseClick(menu.containerId, slot, 0, type, minecraft.player);
    }

    private int count(String name) {
        return minecraft.player.getInventory().countItem(item(name));
    }

    private boolean ready(String marker) {
        return Files.exists(output.resolve("server-" + marker));
    }

    private void signal(String marker) throws IOException {
        var directory = output.resolve("signals");
        Files.createDirectories(directory);
        var temporary = directory.resolve(marker + ".tmp");
        Files.writeString(temporary, "{}");
        Files.move(temporary, directory.resolve(marker + ".json"));
    }

    private void check(String name, boolean passed) {
        checks.put(name, passed);
        if (!passed) throw new AssertionError(name);
    }

    private void finish(Throwable failure) {
        stage = Stage.DONE;
        var report = new LinkedHashMap<String, Object>();
        report.put("checks", checks);
        report.put("captures", captures);
        report.put("passed", failure == null);
        if (failure != null) report.put("error", failure.toString());
        ClientSmoke.complete("client_pearl_powder", report);
    }
}
