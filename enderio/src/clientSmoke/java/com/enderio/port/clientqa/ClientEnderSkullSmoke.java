package com.enderio.port.clientqa;

import com.enderio.enderio.client.content.machines.gui.screen.SlicerScreen;
import com.enderio.enderio.client.content.misc_blocks.EnderSkullRenderer;
import com.enderio.enderio.content.machines.slicer.SlicerMenu;
import com.enderio.enderio.foundation.block.entity.EnderSkullBlockEntity;
import com.enderio.enderio.init.EIOBlocks;
import com.enderio.enderio.init.EIOItems;
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
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.WallSkullBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static com.enderio.port.clientqa.ClientAlloyProgressionSmoke.Craft;
import static com.enderio.port.clientqa.ClientAlloyProgressionSmoke.id;

final class ClientEnderSkullSmoke {
    private enum Stage { TITLE, JOIN, CHEST, TAKE, TABLE, RECIPE, RESULT, CRAFTED, READY, ATTACK,
        FLOOR, FLOOR_PLACED, WALL, WALL_PLACED, RESTORED, BREAK, MINING, SLICER, INSERT, INSERTED, PAUSED, DONE_WAIT, DONE }

    private static final BlockPos CHEST = new BlockPos(3321, 80, 10);
    private static final BlockPos TABLE = new BlockPos(3320, 80, 10);
    private static final BlockPos SKULL = new BlockPos(3323, 80, 10);
    private static final BlockPos SLICER = new BlockPos(3340, 80, 8);
    private static final Vec3 WORKSHOP = new Vec3(3320.5, 80, 12.5);
    private static final Vec3 ARENA = new Vec3(3328.5, 80, 10.5);
    private static final Vec3 DISPLAY = new Vec3(3323.5, 80, 12.5);
    private static final Vec3 MACHINE = new Vec3(3340.5, 80, 11.5);
    private static final int HEAD_SLOT = SlicerMenu.INPUTS_INDEX + 1;
    private static final Craft SWORD_RECIPE = Craft.shaped("dark_steel_sword", 1, " I  I  S ",
        Map.of('I', "dark_steel_ingot", 'S', "minecraft:stick"));

    private final Minecraft minecraft = Minecraft.getInstance();
    private final Path output = Path.of(System.getProperty("enderio.clientqa.output"));
    private final boolean restarted = "verify".equals(System.getProperty("enderio.clientqa.multiplayerPhase"));
    private final Map<String, Boolean> checks = new LinkedHashMap<>();
    private final Map<String, Object> captures = new LinkedHashMap<>();
    private final long started = System.nanoTime();
    private Stage stage = Stage.TITLE;
    private long titleAt;
    private int actionAt = -1;
    private int attackAt;
    private int miningTick;
    private int batch;
    private int attacks;
    private int placementBy;
    private boolean sawTargets;

    ClientEnderSkullSmoke() {
        MinecraftForge.EVENT_BUS.addListener(this::render);
    }

    private void render(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage == Stage.DONE) return;
        try {
            if (minecraft.screen instanceof DisconnectedScreen || System.nanoTime() - started > 210_000_000_000L) {
                throw new AssertionError("Ender Skull stopped at " + stage + ", attacks " + attacks + ", batch " + batch);
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
                    stage = restarted ? Stage.RESTORED : Stage.CHEST;
                }
                case CHEST -> {
                    if (!settled("craft", WORKSHOP)) return;
                    check("native_workshop_chest", use(CHEST, Direction.SOUTH));
                    stage = Stage.TAKE;
                }
                case TAKE -> {
                    if (!(minecraft.screen instanceof ContainerScreen screen)) return;
                    var menu = screen.getMenu();
                    if (menu.slots.subList(0, 27).stream().filter(Slot::hasItem).count() != 3) return;
                    for (int slot = 0; slot < 3; slot++) quickMove(menu, slot);
                    stage = Stage.TABLE;
                }
                case TABLE -> {
                    if (minecraft.screen != null) {
                        if (!(minecraft.screen instanceof ContainerScreen screen)
                            || screen.getMenu().slots.subList(0, 27).stream().anyMatch(Slot::hasItem)) return;
                        player.closeContainer();
                        return;
                    }
                    check("the_original_pair_of_dark_steel_ingots_reaches_survival_inventory",
                        count(EIOItems.DARK_STEEL_INGOT.get()) == 2 && count(Items.STICK) == 1 && count(Items.IRON_PICKAXE) == 1);
                    check("native_crafting_table", use(TABLE, Direction.SOUTH));
                    stage = Stage.RECIPE;
                }
                case RECIPE, RESULT, CRAFTED -> craft();
                case READY -> {
                    if (!settled("fight", ARENA) || !equip(EIOItems.DARK_STEEL_SWORD.get())) return;
                    check("the_survival_sword_starts_unworn", player.getMainHandItem().getDamageValue() == 0 && count(head()) == 0);
                    signal("fight-ready");
                    stage = Stage.ATTACK;
                }
                case ATTACK -> fight();
                case FLOOR -> placeHead(false);
                case WALL -> placeHead(true);
                case FLOOR_PLACED -> {
                    if (!checkPlacement(false)) return;
                    check("floor_placement_spends_the_one_original_head", count(head()) == 0);
                    verifyRenderer("floor-head");
                    signal("floor-placed");
                    stage = Stage.BREAK;
                }
                case WALL_PLACED, RESTORED -> {
                    if (stage == Stage.RESTORED && !settled("wall", DISPLAY)) return;
                    if (!checkPlacement(true)) return;
                    check("wall_placement_has_south_facing_and_no_spare_stack", count(head()) == 0);
                    verifyRenderer(restarted ? "wall-head-restored" : "wall-head");
                    signal(restarted ? "wall-restored" : "wall-placed");
                    stage = restarted ? Stage.BREAK : Stage.DONE_WAIT;
                }
                case BREAK -> {
                    if (!ready("mine") || minecraft.screen != null || !equip(Items.IRON_PICKAXE)) return;
                    check("normal_survival_begins_head_mining", minecraft.gameMode.startDestroyBlock(SKULL, Direction.SOUTH));
                    miningTick = player.tickCount;
                    stage = Stage.MINING;
                }
                case MINING -> {
                    if (!minecraft.level.getBlockState(SKULL).isAir()) {
                        if (player.tickCount != miningTick) {
                            minecraft.gameMode.continueDestroyBlock(SKULL, Direction.SOUTH);
                            miningTick = player.tickCount;
                        }
                        return;
                    }
                    signal("head-mined");
                    stage = restarted ? Stage.SLICER : Stage.WALL;
                }
                case SLICER -> {
                    if (!settled("slicer", MACHINE)) return;
                    check("native_slicer_opens_with_the_actual_wall_drop_in_inventory", count(head()) == 1 && use(SLICER, Direction.SOUTH));
                    stage = Stage.INSERT;
                }
                case INSERT, INSERTED, PAUSED -> {
                    if (minecraft.screen instanceof SlicerScreen screen) useSlicer(screen.getMenu());
                }
                case DONE_WAIT -> {
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
        var grid = menu.slots.subList(1, 10);
        var sword = EIOItems.DARK_STEEL_SWORD.get();
        switch (stage) {
            case RECIPE -> {
                var recipe = minecraft.level.getRecipeManager().byKey(id(SWORD_RECIPE.recipe())).orElseThrow();
                if (!minecraft.player.getRecipeBook().contains(recipe)) return;
                minecraft.gameMode.handlePlaceRecipe(menu.containerId, recipe, false);
                stage = Stage.RESULT;
            }
            case RESULT -> {
                if (!menu.getSlot(0).getItem().is(sword) || grid.stream().filter(Slot::hasItem).count() != 3) return;
                check("the_stock_sword_grid_uses_two_ingots_and_one_stick", SWORD_RECIPE.matchesGrid(grid));
                capture("dark-steel-sword-recipe");
                quickMove(menu, 0);
                stage = Stage.CRAFTED;
            }
            case CRAFTED -> {
                if (count(sword) != 1 || grid.stream().anyMatch(Slot::hasItem)) return;
                check("survival_spends_both_original_ingots_and_the_stick", count(EIOItems.DARK_STEEL_INGOT.get()) == 0 && count(Items.STICK) == 0);
                minecraft.player.closeContainer();
                signal("crafted");
                stage = Stage.READY;
            }
            default -> {}
        }
    }

    private void fight() throws IOException {
        if (!ready("batch-" + batch)) return;
        var player = minecraft.player;
        if (player.getMainHandItem().getDamageValue() != attacks) return;
        var search = player.getBoundingBox().inflate(4);
        boolean found = count(head()) > 0 || !minecraft.level.getEntitiesOfClass(ItemEntity.class, search,
            entity -> entity.getItem().is(head())).isEmpty();
        if (found) {
            check("the_first_head_arrives_from_a_real_attack_with_the_crafted_sword", attacks > 0 && player.getMainHandItem().is(EIOItems.DARK_STEEL_SWORD.get()));
            signal("combat-" + batch, true);
            stage = Stage.FLOOR;
            return;
        }
        var targets = minecraft.level.getEntitiesOfClass(EnderMan.class, search, entity -> entity.isAlive());
        if (targets.isEmpty()) {
            if (sawTargets) {
                signal("combat-" + batch, false);
                batch++;
                sawTargets = false;
            }
            return;
        }
        sawTargets = true;
        if (player.tickCount < attackAt) return;
        var target = targets.stream().filter(entity -> player.distanceToSqr(entity) < 9).findFirst();
        if (target.isEmpty()) return;
        minecraft.gameMode.attack(player, target.get());
        player.swing(InteractionHand.MAIN_HAND);
        attacks++;
        attackAt = player.tickCount + 4;
    }

    private void placeHead(boolean wall) {
        if (!settled(wall ? "wall" : "floor", DISPLAY) || !equip(head())) return;
        var support = SKULL.relative(wall ? Direction.NORTH : Direction.DOWN);
        check(wall ? "native_wall_item_placement" : "native_floor_item_placement", use(support, wall ? Direction.SOUTH : Direction.UP));
        placementBy = minecraft.player.tickCount + 40;
        stage = wall ? Stage.WALL_PLACED : Stage.FLOOR_PLACED;
    }

    private boolean checkPlacement(boolean wall) {
        if (wall ? wallAppears() : floorAppears()) return true;
        if (minecraft.player.tickCount < placementBy) return false;
        throw new AssertionError("Expected the " + (wall ? "wall" : "floor") + " skull, observed " + minecraft.level.getBlockState(SKULL));
    }

    private boolean floorAppears() {
        var state = minecraft.level.getBlockState(SKULL);
        return state.is(EIOBlocks.ENDERMAN_HEAD.get()) && state.getValue(SkullBlock.ROTATION) == 8;
    }

    private boolean wallAppears() {
        var state = minecraft.level.getBlockState(SKULL);
        return state.is(EIOBlocks.WALL_ENDERMAN_HEAD.get()) && state.getValue(WallSkullBlock.FACING) == Direction.SOUTH;
    }

    private void verifyRenderer(String frame) throws IOException {
        check(frame + "_has_the_mod_skull_renderer", minecraft.level.getBlockEntity(SKULL) instanceof EnderSkullBlockEntity skull
            && minecraft.getBlockEntityRenderDispatcher().getRenderer(skull) instanceof EnderSkullRenderer);
        capture(frame);
    }

    private void useSlicer(SlicerMenu menu) throws IOException {
        var skullSlot = menu.getSlot(HEAD_SLOT);
        switch (stage) {
            case INSERT -> {
                var player = minecraft.player;
                var slot = menu.slots.stream().filter(s -> s.container == player.getInventory() && s.getItem().is(head())).findFirst();
                if (slot.isEmpty()) return;
                quickMove(menu, slot.get().index);
                stage = Stage.INSERTED;
            }
            case INSERTED -> {
                if (!skullSlot.getItem().is(head()) || count(head()) != 0) return;
                check("native_shift_click_places_the_original_head_in_the_recipe_slot", skullSlot.getItem().getCount() == 1);
                signal("head-inserted");
                stage = Stage.PAUSED;
            }
            case PAUSED -> {
                if (!ready("partial") || Math.abs(menu.getCraftingProgress() - 3600f / 20000) > 0.0001) return;
                check("paid_slicer_menu_retains_the_original_head_and_exact_progress", skullSlot.getItem().is(head())
                    && menu.getEnergyStorage().energyStored() == 0);
                capture("original-head-in-slicer");
                signal("partial-seen");
                minecraft.player.closeContainer();
                stage = Stage.DONE_WAIT;
            }
            default -> {}
        }
    }

    private boolean settled(String marker, Vec3 position) {
        var player = minecraft.player;
        if (!ready(marker) || minecraft.screen != null || player.distanceToSqr(position) > 0.15) {
            actionAt = -1;
            return false;
        }
        if (actionAt < 0) actionAt = player.tickCount + 8;
        if (player.tickCount < actionAt) return false;
        actionAt = -1;
        return true;
    }

    private boolean equip(Item item) {
        var player = minecraft.player;
        var inventory = player.getInventory();
        for (int slot = 0; slot < 9; slot++) {
            if (!inventory.getItem(slot).is(item)) continue;
            inventory.selected = slot;
            return true;
        }
        for (int slot = 9; slot < 36; slot++) {
            if (!inventory.getItem(slot).is(item)) continue;
            minecraft.gameMode.handleInventoryMouseClick(player.inventoryMenu.containerId, slot, 0, ClickType.SWAP, player);
            return false;
        }
        throw new AssertionError("Missing fixture item " + item);
    }

    private void connect() {
        if (!(minecraft.screen instanceof TitleScreen) || minecraft.getOverlay() != null) return;
        if (titleAt == 0) titleAt = System.nanoTime();
        if (System.nanoTime() - titleAt < 2_000_000_000L) return;
        String address = System.getProperty("enderio.clientqa.server");
        ConnectScreen.startConnecting(new TitleScreen(), minecraft, ServerAddress.parseString(address),
            new ServerData("Ender IO Skull Progression", address, true), false);
        stage = Stage.JOIN;
    }

    private static Item head() {
        return EIOBlocks.ENDERMAN_HEAD.asItem();
    }

    private int count(Item item) {
        return minecraft.player.getInventory().countItem(item);
    }

    private boolean use(BlockPos pos, Direction face) {
        var hit = new BlockHitResult(Vec3.atCenterOf(pos).relative(face, 0.5), face, pos, false);
        return minecraft.gameMode.useItemOn(minecraft.player, InteractionHand.MAIN_HAND, hit).consumesAction();
    }

    private void quickMove(AbstractContainerMenu menu, int slot) {
        minecraft.gameMode.handleInventoryMouseClick(menu.containerId, slot, 0, ClickType.QUICK_MOVE, minecraft.player);
    }

    private boolean ready(String name) {
        return Files.exists(output.resolve("server-" + name));
    }

    private void signal(String name) throws IOException {
        writeSignal(name, "{}");
    }

    private void signal(String name, boolean found) throws IOException {
        writeSignal(name, "{\"found\":" + found + ",\"attacks\":" + attacks + "}");
    }

    private void writeSignal(String name, String value) throws IOException {
        var directory = output.resolve("signals");
        Files.createDirectories(directory);
        var temporary = directory.resolve(name + ".tmp");
        Files.writeString(temporary, value);
        Files.move(temporary, directory.resolve(name + ".json"));
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
        result.put("passed", failure == null);
        result.put("checks", checks);
        result.put("captures", captures);
        if (failure != null) result.put("error", failure.toString());
        ClientSmoke.complete("client_ender_skull", result);
    }
}
