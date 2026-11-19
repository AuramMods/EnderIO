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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.TooltipFlag;
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

/** Takes machine products through the survival recipe book, crafting grid and native placement. */
final class ClientGlassProgressionSmoke {
    private enum Stage { TITLE, JOIN, CHEST, TAKE, TABLE, RECIPE, RESULT, COLLECT, PLACE, PLACED, VERIFY, DONE }

    private record Craft(String id, String base, String reagent, String result) {}
    private record Placement(String id, BlockPos pos, String name, String light, String collision, boolean blastResistant) {}

    private static final BlockPos CHEST = new BlockPos(2840, 80, 8);
    private static final BlockPos TABLE = new BlockPos(2842, 80, 8);
    private static final Vec3 STAND = new Vec3(2841.5, 80, 11.5);
    private static final List<Craft> RECIPES = List.of(
        new Craft("collision_token_clear_glass_dp", "clear_glass_d", "enderio:player_token", "clear_glass_dp"),
        new Craft("invert_clear_glass_dnp", "clear_glass_dp", "minecraft:redstone_torch", "clear_glass_dnp"),
        new Craft("recolor_clear_glass_dnp_blue", "clear_glass_dnp", "minecraft:blue_dye", "clear_glass_dnp_blue"),
        new Craft("recolor_clear_glass_dnp_cyan", "clear_glass_dnp_blue", "minecraft:cyan_dye", "clear_glass_dnp_cyan"),
        new Craft("collision_token_fused_quartz_ea", "fused_quartz_e", "enderio:animal_token", "fused_quartz_ea"),
        new Craft("invert_fused_quartz_ena", "fused_quartz_ea", "minecraft:redstone_torch", "fused_quartz_ena"),
        new Craft("recolor_fused_quartz_ena_yellow", "fused_quartz_ena", "minecraft:yellow_dye", "fused_quartz_ena_yellow"),
        new Craft("collision_token_fused_quartz_dm", "fused_quartz_d", "enderio:monster_token", "fused_quartz_dm"),
        new Craft("invert_fused_quartz_dnm", "fused_quartz_dm", "minecraft:redstone_torch", "fused_quartz_dnm"),
        new Craft("recolor_fused_quartz_dnm_red", "fused_quartz_dnm", "minecraft:red_dye", "fused_quartz_dnm_red")
    );
    private static final List<Placement> PLACEMENTS = List.of(
        new Placement("clear_glass_dnp_cyan", new BlockPos(2839, 80, 9), "Cyan Dark Clear Glass", "Blocks Light",
            "Only solid to players", false),
        new Placement("fused_quartz_ena_yellow", new BlockPos(2841, 80, 8), "Yellow Enlightened Fused Quartz", "Emits Light",
            "Only solid to animals", true),
        new Placement("fused_quartz_dnm_red", new BlockPos(2843, 80, 9), "Red Dark Fused Quartz", "Blocks Light",
            "Only solid to monsters", true)
    );

    private final Minecraft minecraft = Minecraft.getInstance();
    private final Path output = Path.of(System.getProperty("enderio.clientqa.output"));
    private final Map<String, Boolean> checks = new LinkedHashMap<>();
    private final Map<String, Object> captures = new LinkedHashMap<>();
    private final long started = System.nanoTime();
    private long titleAt;
    private Stage stage = Stage.TITLE;
    private int recipeIndex;
    private int placementIndex;
    private int until;
    private int reagentBefore;

    ClientGlassProgressionSmoke() {
        MinecraftForge.EVENT_BUS.addListener(this::render);
    }

    private void render(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage == Stage.DONE) return;
        try {
            if (minecraft.screen instanceof DisconnectedScreen || System.nanoTime() - started > 158_000_000_000L) {
                throw new AssertionError("Glass progression stopped at " + stage + ", recipe " + recipeIndex);
            }
            if (stage == Stage.TITLE) {
                if (!(minecraft.screen instanceof TitleScreen) || minecraft.getOverlay() != null) return;
                if (titleAt == 0) titleAt = System.nanoTime();
                if (System.nanoTime() - titleAt < 2_000_000_000L) return;
                String address = System.getProperty("enderio.clientqa.server");
                ConnectScreen.startConnecting(new TitleScreen(), minecraft, ServerAddress.parseString(address),
                    new ServerData("Glass progression QA", address, true), false);
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
                    stage = Stage.CHEST;
                }
                case CHEST -> {
                    if (!Files.exists(output.resolve("server-ready")) || player.distanceToSqr(STAND) > 0.1) return;
                    open(CHEST);
                    stage = Stage.TAKE;
                }
                case TAKE -> {
                    if (!(minecraft.screen instanceof ContainerScreen screen)) return;
                    var menu = screen.getMenu();
                    if (menu.slots.subList(0, 27).stream().filter(Slot::hasItem).count() != 11) return;
                    capture("collected-machine-ingredients");
                    for (int slot = 0; slot < 11; slot++) quickMove(menu, slot);
                    stage = Stage.TABLE;
                }
                case TABLE -> {
                    if (!(minecraft.screen instanceof ContainerScreen screen)
                        || screen.getMenu().slots.subList(0, 27).stream().anyMatch(Slot::hasItem)) return;
                    check("survival_collects_all_machine_products", count(modItem("clear_glass_d")) == 8
                        && count(modItem("fused_quartz_e")) == 8 && count(modItem("fused_quartz_d")) == 8
                        && List.of("player_token", "animal_token", "monster_token").stream()
                            .allMatch(token -> count(modItem(token)) == 1));
                    player.closeContainer();
                    open(TABLE);
                    until = player.tickCount + 160;
                    stage = Stage.RECIPE;
                }
                case RECIPE, RESULT, COLLECT -> craft(RECIPES.get(recipeIndex));
                case PLACE, PLACED -> place(PLACEMENTS.get(placementIndex));
                case VERIFY -> {
                    if (!Files.exists(output.resolve("server-done")) || player.tickCount < until) return;
                    capture("placed-glass");
                    finish(null);
                }
                default -> {}
            }
        } catch (Throwable failure) {
            failure.printStackTrace();
            finish(failure);
        }
    }

    private void craft(Craft step) throws IOException {
        var player = minecraft.player;
        if (player.tickCount > until) throw new AssertionError(step.id() + " stalled at " + stage);
        if (!(minecraft.screen instanceof CraftingScreen screen)) return;
        var menu = screen.getMenu();
        var grid = menu.slots.subList(1, 10);
        var from = modItem(step.base());
        var reagent = item(step.reagent());
        var result = modItem(step.result());
        switch (stage) {
            case RECIPE -> {
                var recipe = minecraft.level.getRecipeManager().byKey(ResourceLocation.parse("enderio:" + step.id())).orElseThrow();
                if (!player.getRecipeBook().contains(recipe)) return;
                reagentBefore = count(reagent);
                check(step.id() + "_unlocks_from_the_survival_inputs", count(from) == 8 && reagentBefore >= 1 && count(result) == 0);
                minecraft.gameMode.handlePlaceRecipe(menu.containerId, recipe, false);
                stage = Stage.RESULT;
            }
            case RESULT -> {
                var stack = menu.getSlot(0).getItem();
                if (!stack.is(result) || grid.stream().filter(Slot::hasItem).count() != 9) return;
                check(step.id() + "_eight_native_results",
                    stack.getCount() == 8 && grid.stream().filter(slot -> slot.getItem().is(from) && slot.getItem().getCount() == 1).count() == 8
                        && grid.stream().anyMatch(slot -> slot.getItem().is(reagent) && slot.getItem().getCount() == 1));
                if (step.id().startsWith("invert_") || step.result().equals("clear_glass_dnp_cyan")) capture(step.id());
                quickMove(menu, 0);
                stage = Stage.COLLECT;
            }
            case COLLECT -> {
                if (grid.stream().anyMatch(Slot::hasItem) || count(result) != 8) return;
                check(step.id() + "_collects_exactly_eight_and_spends_nine_inputs",
                    !menu.getSlot(0).hasItem() && menu.getCarried().isEmpty() && count(from) == 0 && count(reagent) == reagentBefore - 1);
                until = player.tickCount + 160;
                if (++recipeIndex == RECIPES.size()) {
                    player.closeContainer();
                    stage = Stage.PLACE;
                } else {
                    stage = Stage.RECIPE;
                }
            }
            default -> {}
        }
    }

    private void place(Placement placement) throws IOException {
        var player = minecraft.player;
        var inventory = player.getInventory();
        var item = modItem(placement.id());
        if (stage == Stage.PLACE) {
            if (minecraft.screen != null) return;
            for (int slot = 0; slot < Inventory.getSelectionSize(); slot++) {
                if (!inventory.getItem(slot).is(item)) continue;
                inventory.selected = slot;
                var stack = inventory.getSelected();
                var tooltip = stack.getTooltipLines(player, TooltipFlag.NORMAL).stream().map(Component::getString).toList();
                check(placement.id() + "_name_and_rule_tooltips", stack.getHoverName().getString().equals(placement.name())
                    && tooltip.contains(placement.light()) && tooltip.contains(placement.collision())
                    && tooltip.contains("Blast resistant") == placement.blastResistant());
                var pos = placement.pos();
                var hit = new BlockHitResult(Vec3.atBottomCenterOf(pos), Direction.UP, pos.below(), false);
                check(placement.id() + "_uses_survival_placement",
                    minecraft.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit).consumesAction());
                stage = Stage.PLACED;
                return;
            }
            throw new AssertionError(placement.id() + " is missing from the hotbar");
        }
        if (!minecraft.level.getBlockState(placement.pos()).is(BuiltInRegistries.BLOCK.get(ResourceLocation.parse("enderio:" + placement.id())))
            || count(item) != 7) return;
        if (++placementIndex == PLACEMENTS.size()) {
            signal("placed");
            until = player.tickCount + 12;
            stage = Stage.VERIFY;
        } else {
            stage = Stage.PLACE;
        }
    }

    private void open(BlockPos pos) {
        var hit = new BlockHitResult(Vec3.atCenterOf(pos).relative(Direction.SOUTH, 0.5), Direction.SOUTH, pos, false);
        minecraft.gameMode.useItemOn(minecraft.player, InteractionHand.MAIN_HAND, hit);
    }

    private void quickMove(AbstractContainerMenu menu, int slot) {
        minecraft.gameMode.handleInventoryMouseClick(menu.containerId, slot, 0, ClickType.QUICK_MOVE, minecraft.player);
    }

    private static Item modItem(String path) {
        return item("enderio:" + path);
    }

    private static Item item(String id) {
        return BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(id)).orElseThrow();
    }

    private int count(Item item) {
        return minecraft.player.getInventory().countItem(item);
    }

    private void capture(String name) throws IOException {
        var frame = ClientSmoke.capture(name + ".png");
        captures.put(name, frame);
        check("hidden_muted_" + name, Boolean.TRUE.equals(frame.get("passed")));
    }

    private void signal(String name) throws IOException {
        var signals = output.resolve("signals");
        Files.createDirectories(signals);
        Files.writeString(signals.resolve(name + ".json"), "{}");
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
        ClientSmoke.complete("client_glass_progression", result);
    }
}
