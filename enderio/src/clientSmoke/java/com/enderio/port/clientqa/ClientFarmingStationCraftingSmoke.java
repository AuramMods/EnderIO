package com.enderio.port.clientqa;

import com.enderio.core.common.blockentity.EnderBlockEntity;
import com.enderio.enderio.api.io.RedstoneControl;
import com.enderio.enderio.api.soul.Soul;
import com.enderio.enderio.client.content.machines.gui.screen.FarmingStationScreen;
import com.enderio.enderio.foundation.attachment.ActionRange;
import com.enderio.enderio.init.EIOBlocks;
import com.enderio.enderio.init.EIODataComponents;
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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static com.enderio.port.clientqa.ClientAlloyProgressionSmoke.Craft;
import static com.enderio.port.clientqa.ClientAlloyProgressionSmoke.id;

final class ClientFarmingStationCraftingSmoke {
    private enum Stage {
        TITLE, JOIN, LOCKED, TABLE, RECIPE, CRAFT_RESULT, CRAFT_TAKE, PLACE, MINE, MINING, PICKUP,
        ERASE_TABLE, ERASE_RECIPE, ERASE_RESULT, ERASE_TAKE, REPLANT, OPEN, DEFAULTS, DONE
    }

    private static final Craft RECIPE = Craft.shaped("farming_station", 1, "EGESCSDZD", Map.of(
        'E', "energized_gear", 'G', "pulsating_crystal", 'S', "soularium_ingot", 'C', "ensouled_chassis",
        'D', "dark_steel_ingot", 'Z', "z_logic_controller"));
    private static final BlockPos TABLE = new BlockPos(4324, 80, 8);
    private static final BlockPos SOURCE = new BlockPos(4328, 80, 8);
    private static final BlockPos DESTINATION = new BlockPos(4333, 80, 8);
    private static final ActionRange SAVED_RANGE = new ActionRange(2, false);
    private static final ActionRange DEFAULT_RANGE = new ActionRange(5, false);

    private final Minecraft minecraft = Minecraft.getInstance();
    private final Path output = Path.of(System.getProperty("enderio.clientqa.output"));
    private final Map<String, Boolean> checks = new LinkedHashMap<>();
    private final Map<String, Object> captures = new LinkedHashMap<>();
    private final long started = System.nanoTime();
    private Stage stage = Stage.TITLE;
    private long titleAt;
    private int positionTick = -1;
    private int miningTick;

    ClientFarmingStationCraftingSmoke() {
        MinecraftForge.EVENT_BUS.addListener(this::render);
    }

    private void render(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage == Stage.DONE) return;
        try {
            if (minecraft.screen instanceof DisconnectedScreen || System.nanoTime() - started > 180_000_000_000L) {
                throw new AssertionError("Farming Station crafting stopped at " + stage);
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
                    check("default_pack_world_allows_native_dedicated_survival_crafting", !player.isCreative() && !player.isSpectator()
                        && !minecraft.getConnection().getConnection().isMemoryConnection()
                        && EIOBlocks.FARMING_STATION.get().isEnabled(minecraft.level.enabledFeatures()));
                    signal("connected");
                    stage = Stage.LOCKED;
                }
                case LOCKED -> {
                    if (!ready("missing-soularium") || !player.getInventory().contains(new ItemStack(EIOItems.Z_LOGIC_CONTROLLER.get()))) return;
                    var manager = minecraft.level.getRecipeManager();
                    check("both_stock_recipes_start_locked_without_their_advancement_inputs",
                        !player.getRecipeBook().contains(manager.byKey(id(RECIPE.recipe())).orElseThrow())
                            && !player.getRecipeBook().contains(manager.byKey(id("erase_farming_station")).orElseThrow()));
                    signal("locked");
                    stage = Stage.TABLE;
                }
                case TABLE -> openWhenReady("table", TABLE, Stage.RECIPE);
                case RECIPE, CRAFT_RESULT, CRAFT_TAKE, ERASE_RECIPE, ERASE_RESULT, ERASE_TAKE -> craft();
                case PLACE -> place("source", SOURCE, "placed", Stage.MINE);
                case MINE -> {
                    if (!settled("mine", SOURCE) || !player.getInventory().getItem(0).is(Items.DIAMOND_PICKAXE)) return;
                    player.getInventory().selected = 0;
                    check("native_survival_mining_starts", minecraft.gameMode.startDestroyBlock(SOURCE, Direction.SOUTH));
                    miningTick = player.tickCount;
                    stage = Stage.MINING;
                }
                case MINING -> {
                    if (!minecraft.level.isEmptyBlock(SOURCE)) {
                        if (miningTick != player.tickCount) {
                            miningTick = player.tickCount;
                            minecraft.gameMode.continueDestroyBlock(SOURCE, Direction.SOUTH);
                        }
                        return;
                    }
                    signal("mined");
                    stage = Stage.PICKUP;
                }
                case PICKUP -> {
                    int slot = hotbarStation();
                    if (slot < 0) return;
                    check("ordinary_pickup_delivers_the_populated_item", carried(player.getInventory().getItem(slot)));
                    signal("picked-up");
                    stage = Stage.ERASE_TABLE;
                }
                case ERASE_TABLE -> openWhenReady("erase", TABLE, Stage.ERASE_RECIPE);
                case REPLANT -> place("destination", DESTINATION, "replanted", Stage.OPEN);
                case OPEN -> openWhenReady("open", DESTINATION, Stage.DEFAULTS);
                case DEFAULTS -> verifyDefaults();
                default -> {}
            }
        } catch (Throwable failure) {
            failure.printStackTrace();
            finish(failure);
        }
    }

    private void craft() throws Exception {
        if (!(minecraft.screen instanceof CraftingScreen screen)) return;
        var player = minecraft.player;
        var menu = screen.getMenu();
        var grid = menu.slots.subList(1, 10);
        var result = menu.getSlot(0).getItem();
        switch (stage) {
            case RECIPE -> {
                var recipe = minecraft.level.getRecipeManager().byKey(id(RECIPE.recipe())).orElseThrow();
                if (!player.getRecipeBook().contains(recipe)) return;
                check("soularium_unlocks_the_original_station_recipe",
                    player.getInventory().countItem(EIOItems.SOULARIUM_INGOT.get()) == 2);
                minecraft.gameMode.handlePlaceRecipe(menu.containerId, recipe, false);
                stage = Stage.CRAFT_RESULT;
            }
            case CRAFT_RESULT -> {
                if (!station(result) || grid.stream().filter(Slot::hasItem).count() != 9) return;
                check("native_recipe_book_places_all_nine_original_ingredients",
                    RECIPE.matchesGrid(grid) && grid.stream().allMatch(slot -> slot.getItem().getCount() == 1) && !result.hasTag());
                capture("farming-station-stock-recipe");
                click(menu, 0, 0, ClickType.QUICK_MOVE);
                stage = Stage.CRAFT_TAKE;
            }
            case CRAFT_TAKE -> take(grid, "crafted", Stage.PLACE);
            case ERASE_RECIPE -> {
                var recipe = minecraft.level.getRecipeManager().byKey(id("erase_farming_station")).orElseThrow();
                int slot = hotbarStation();
                if (slot < 0 || !player.getRecipeBook().contains(recipe)) return;
                check("owning_the_carried_station_unlocks_the_stock_reset",
                    carried(player.getInventory().getItem(slot)) && grid.stream().noneMatch(Slot::hasItem));
                click(menu, 1, slot, ClickType.SWAP);
                stage = Stage.ERASE_RESULT;
            }
            case ERASE_RESULT -> {
                if (!station(result) || !carried(menu.getSlot(1).getItem())) return;
                check("one_populated_machine_previews_one_untagged_machine",
                    !result.hasTag() && grid.stream().filter(Slot::hasItem).count() == 1);
                capture("farming-station-stock-reset");
                click(menu, 0, 0, ClickType.QUICK_MOVE);
                stage = Stage.ERASE_TAKE;
            }
            case ERASE_TAKE -> take(grid, "erased", Stage.REPLANT);
            default -> {}
        }
    }

    private void take(java.util.List<Slot> grid, String signal, Stage next) throws Exception {
        var player = minecraft.player;
        int slot = hotbarStation();
        if (slot < 0 || grid.stream().anyMatch(Slot::hasItem)) return;
        check(signal + "_collects_one_untagged_machine", !player.getInventory().getItem(slot).hasTag()
            && player.getInventory().countItem(EIOBlocks.FARMING_STATION.get().asItem()) == 1);
        player.closeContainer();
        signal(signal);
        stage = next;
    }

    private void verifyDefaults() throws Exception {
        if (!(minecraft.screen instanceof FarmingStationScreen screen)) return;
        var menu = screen.getMenu();
        if (menu.getEnergyStorage() == null) return;
        var station = menu.getBlockEntity();
        check("the_placed_reset_station_syncs_factory_defaults",
            menu.getEnergyStorage().energyStored() == 0 && !menu.isCapacitorInstalled()
                && menu.getRedstoneControl() == RedstoneControl.ALWAYS_ACTIVE && DEFAULT_RANGE.equals(station.getActionRange())
                && station.getBoundSoul() == Soul.EMPTY && menu.slots.subList(0, 16).stream().noneMatch(Slot::hasItem));
        capture("farming-station-reset-menu");
        finish(null);
    }

    private static boolean carried(ItemStack stack) {
        var contents = EnderBlockEntity.ITEM_INVENTORY.get(stack);
        var soul = EIODataComponents.SOUL.getOrDefault(stack, Soul.EMPTY);
        return station(stack) && EIODataComponents.ENERGY.getOrDefault(stack, 0) == 6177
            && SAVED_RANGE.equals(EIODataComponents.ACTION_RANGE.get(stack))
            && soul.entityType() == EntityType.BEE && soul.entityTag().getInt("PortFixture") == 81
            && contents != null && contents.getInt("Size") == 16;
    }

    private static boolean station(ItemStack stack) {
        return stack.is(EIOBlocks.FARMING_STATION.get().asItem()) && stack.getCount() == 1;
    }

    private int hotbarStation() {
        var inventory = minecraft.player.getInventory();
        for (int slot = 0; slot < 9; slot++) {
            if (station(inventory.getItem(slot))) return slot;
        }
        return -1;
    }

    private void place(String name, BlockPos pos, String signal, Stage next) throws Exception {
        if (!settled(name, pos)) return;
        int slot = hotbarStation();
        if (slot < 0) return;
        minecraft.player.getInventory().selected = slot;
        check(name + "_accepts_normal_survival_placement", use(pos.below(), Direction.UP));
        signal(signal);
        stage = next;
    }

    private void openWhenReady(String name, BlockPos pos, Stage next) {
        if (!settled(name, pos)) return;
        minecraft.player.getInventory().selected = 8;
        check(name + "_opens_the_native_menu", minecraft.player.getMainHandItem().isEmpty() && use(pos, Direction.SOUTH));
        stage = next;
    }

    private boolean settled(String name, BlockPos pos) {
        var player = minecraft.player;
        if (!ready(name) || minecraft.screen != null || player.distanceToSqr(pos.getX() + 0.5, 80, pos.getZ() + 2.5) > 0.12) {
            positionTick = -1;
            return false;
        }
        if (positionTick < 0) positionTick = player.tickCount;
        if (player.tickCount - positionTick < 6) return false;
        positionTick = -1;
        return true;
    }

    private boolean use(BlockPos pos, Direction face) {
        var hit = new BlockHitResult(Vec3.atCenterOf(pos).relative(face, 0.5), face, pos, false);
        return minecraft.gameMode.useItemOn(minecraft.player, InteractionHand.MAIN_HAND, hit).consumesAction();
    }

    private void click(CraftingMenu menu, int slot, int button, ClickType type) {
        minecraft.gameMode.handleInventoryMouseClick(menu.containerId, slot, button, type, minecraft.player);
    }

    private void connect() {
        if (!(minecraft.screen instanceof TitleScreen) || minecraft.getOverlay() != null) return;
        if (titleAt == 0) titleAt = System.nanoTime();
        if (System.nanoTime() - titleAt < 2_000_000_000L) return;
        String address = System.getProperty("enderio.clientqa.server");
        ConnectScreen.startConnecting(new TitleScreen(), minecraft, ServerAddress.parseString(address),
            new ServerData("Ender IO Farming Station Crafting", address, true), false);
        stage = Stage.JOIN;
    }

    private boolean ready(String name) {
        return Files.exists(output.resolve("server-" + name));
    }

    private void signal(String name) throws Exception {
        var signals = output.resolve("signals");
        Files.createDirectories(signals);
        Files.writeString(signals.resolve(name + ".json"), "{}");
    }

    private void capture(String name) throws Exception {
        var frame = ClientSmoke.capture(name + ".png");
        captures.put(name, frame);
        check(name + "_renders_hidden_and_muted", Boolean.TRUE.equals(frame.get("passed")));
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
        ClientSmoke.complete("client_farming_station_crafting", result);
    }
}
