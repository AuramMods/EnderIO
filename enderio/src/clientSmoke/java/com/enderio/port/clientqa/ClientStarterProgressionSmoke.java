package com.enderio.port.clientqa;

import com.enderio.enderio.api.EnderIODataComponents;
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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
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

final class ClientStarterProgressionSmoke {
    private enum Stage { TITLE, JOIN, TABLE, RECIPE, PREVIEW, TAKEN, PLACE, PLACED, VERIFY, DONE }

    private record Craft(String recipe, String item, int count) {
        Craft(String id) { this(id, id, 1); }

        Item result() { return BuiltInRegistries.ITEM.get(id(item)); }
    }

    private record Placement(String block, BlockPos pos, int inputCount) {
        Block stateBlock() { return BuiltInRegistries.BLOCK.get(id(block)); }
    }

    private static final BlockPos TABLE = new BlockPos(3031, 80, 8);
    private static final List<Craft> RECIPES = List.of(new Craft("iron_gear"), new Craft("iron_gear"),
        new Craft("iron_gear"), new Craft("iron_gear"), new Craft("void_chassis"), new Craft("void_chassis"),
        new Craft("basic_capacitor"), new Craft("basic_capacitor"), new Craft("stirling_generator"),
        new Craft("alloy_smelter"), new Craft("conduit_binder_composite", "conduit_binder_composite", 8),
        new Craft("energy_conduit", "conduit", 8));
    private static final List<Placement> PLACEMENTS = List.of(
        new Placement("stirling_generator", new BlockPos(3039, 80, 8), 1),
        new Placement("alloy_smelter", new BlockPos(3040, 80, 8), 1),
        new Placement("conduit", new BlockPos(3039, 80, 9), 8),
        new Placement("conduit", new BlockPos(3040, 80, 9), 7));

    private final Minecraft minecraft = Minecraft.getInstance();
    private final Path output = Path.of(System.getProperty("enderio.clientqa.output"));
    private final String address = System.getProperty("enderio.clientqa.server");
    private final long started = System.nanoTime();
    private final Map<String, Boolean> checks = new LinkedHashMap<>();
    private final Map<String, Object> captures = new LinkedHashMap<>();
    private Stage stage = Stage.TITLE;
    private long titleAt;
    private int recipeIndex;
    private int placementIndex;
    private int amountBefore;
    private int requestedAt;
    private int selectedSlot;
    private int tableReadyAt = -1;

    ClientStarterProgressionSmoke() {
        MinecraftForge.EVENT_BUS.addListener(this::render);
    }

    private void render(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage == Stage.DONE) return;
        try {
            if (minecraft.screen instanceof DisconnectedScreen || System.nanoTime() - started > 235_000_000_000L) {
                throw new AssertionError("Starter progression stopped at " + stage + ", recipe " + recipeIndex);
            }
            if (stage == Stage.TITLE) {
                if (!(minecraft.screen instanceof TitleScreen) || minecraft.getOverlay() != null) return;
                if (titleAt == 0) titleAt = System.nanoTime();
                if (System.nanoTime() - titleAt < 2_000_000_000L) return;
                ConnectScreen.startConnecting(new TitleScreen(), minecraft, ServerAddress.parseString(address),
                    new ServerData("Starter progression QA", address, true), false);
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
                    stage = Stage.TABLE;
                }
                case TABLE -> {
                    if (!ready(recipeIndex == 0 ? "starter-table" : "conduit-table") || minecraft.screen != null
                        || player.distanceToSqr(3031.5, 80, 10.5) > 0.15) return;
                    if (tableReadyAt == -1) tableReadyAt = player.tickCount;
                    // Give the server time to acknowledge the fixture teleport before sending the block use.
                    if (player.tickCount - tableReadyAt < 8) return;
                    if (recipeIndex == 0) {
                        check("the_player_picked_up_at_least_16_native_grains",
                            count(BuiltInRegistries.ITEM.get(id("grains_of_infinity"))) >= 16);
                    }
                    check(recipeIndex == 0 ? "native_starter_table_use" : "native_conduit_table_use",
                        minecraft.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, new BlockHitResult(
                            Vec3.atCenterOf(TABLE).relative(Direction.SOUTH, 0.5), Direction.SOUTH, TABLE, false)).consumesAction());
                    requestedAt = player.tickCount;
                    tableReadyAt = -1;
                    stage = Stage.RECIPE;
                }
                case RECIPE, PREVIEW, TAKEN -> craft(RECIPES.get(recipeIndex));
                case PLACE, PLACED -> place(PLACEMENTS.get(placementIndex));
                case VERIFY -> {
                    if (!ready("complete")) return;
                    check("six_crafted_energy_conduits_remain_after_placement", count(RECIPES.get(recipeIndex - 1).result()) == 6);
                    minecraft.options.hideGui = true;
                    capture("starter-energy-route");
                    finish(null);
                }
                default -> { }
            }
        } catch (Throwable failure) {
            failure.printStackTrace();
            finish(failure);
        }
    }

    private void craft(Craft step) throws IOException {
        var player = minecraft.player;
        if (player.tickCount - requestedAt > 120) {
            throw new AssertionError("Crafting stopped at " + stage + " for " + step.recipe()
                + " with " + minecraft.screen + " and output count " + count(step.result()));
        }
        if (!(minecraft.screen instanceof CraftingScreen screen)) return;
        var menu = screen.getMenu();
        var item = step.result();
        String label = step.recipe() + "_" + recipeIndex;
        if (stage == Stage.RECIPE) {
            var recipe = minecraft.level.getRecipeManager().byKey(id(step.recipe())).orElseThrow();
            if (!player.getRecipeBook().contains(recipe)) return;
            amountBefore = count(item);
            minecraft.gameMode.handlePlaceRecipe(menu.containerId, recipe, false);
            requestedAt = player.tickCount;
            stage = Stage.PREVIEW;
        } else if (stage == Stage.PREVIEW) {
            var result = menu.getSlot(0).getItem();
            if (!result.is(item)) {
                if (player.tickCount - requestedAt > 100) throw new AssertionError("No result for " + label + ": " + result);
                return;
            }
            check(label + "_crafting_preview_count", result.getCount() == step.count());
            if (step.recipe().equals("energy_conduit")) {
                check("native_conduit_recipe_preview_has_the_energy_variant", isEnergyConduit(result));
                capture("energy-conduit-crafting");
            }
            minecraft.gameMode.handleInventoryMouseClick(menu.containerId, 0, 0, ClickType.QUICK_MOVE, player);
            requestedAt = player.tickCount;
            stage = Stage.TAKEN;
        } else if (!menu.getSlot(0).hasItem() && menu.slots.subList(1, 10).stream().noneMatch(Slot::hasItem)
            && count(item) == amountBefore + step.count()) {
            check(label + "_native_result_transfers_to_inventory", menu.getCarried().isEmpty());
            recipeIndex++;
            if (step.recipe().equals("conduit_binder_composite") || recipeIndex == RECIPES.size()) {
                player.closeContainer();
                signal(recipeIndex == RECIPES.size() ? "conduits-crafted" : "machines-crafted");
                stage = Stage.PLACE;
            } else {
                requestedAt = player.tickCount;
                stage = Stage.RECIPE;
            }
        }
    }

    private void place(Placement placement) throws IOException {
        var player = minecraft.player;
        var inventory = player.getInventory();
        var block = placement.stateBlock();
        if (stage == Stage.PLACE) {
            if (!ready(placementIndex < 2 ? "place-machines" : "place-conduits") || minecraft.screen != null
                || player.distanceToSqr(3039.5, 80, 11.5) > 0.15) return;
            selectedSlot = -1;
            for (int slot = 0; slot < 9; slot++) {
                if (inventory.getItem(slot).is(block.asItem())) {
                    selectedSlot = slot;
                    break;
                }
            }
            if (selectedSlot < 0) throw new AssertionError("No crafted item in the hotbar for " + placement.block());
            inventory.selected = selectedSlot;
            var stack = inventory.getSelected();
            check("placement_" + placementIndex + "_uses_the_crafted_stack",
                stack.getCount() == placement.inputCount() && (placementIndex < 2 || isEnergyConduit(stack)));
            var pos = placement.pos();
            var hit = new BlockHitResult(Vec3.atBottomCenterOf(pos), Direction.UP, pos.below(), false);
            check("placement_" + placementIndex + "_is_a_native_use", minecraft.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit).consumesAction());
            stage = Stage.PLACED;
        } else if (minecraft.level.getBlockState(placement.pos()).is(block)
            && inventory.getItem(selectedSlot).getCount() == placement.inputCount() - 1) {
            placementIndex++;
            if (placementIndex == 2 || placementIndex == PLACEMENTS.size()) {
                signal(placementIndex == 2 ? "machines-placed" : "conduits-placed");
                stage = placementIndex == 2 ? Stage.TABLE : Stage.VERIFY;
            } else {
                stage = Stage.PLACE;
            }
        }
    }

    private boolean isEnergyConduit(ItemStack stack) {
        return id("energy").equals(EnderIODataComponents.CONDUIT.getId(stack));
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("enderio", path);
    }

    private int count(Item item) {
        return minecraft.player.getInventory().items.stream().filter(stack -> stack.is(item)).mapToInt(ItemStack::getCount).sum();
    }

    private boolean ready(String label) {
        return Files.exists(output.resolve("server-" + label));
    }

    private void signal(String label) throws IOException {
        var dir = output.resolve("signals");
        Files.createDirectories(dir);
        var tmp = dir.resolve(label + ".tmp");
        Files.writeString(tmp, "{}");
        Files.move(tmp, dir.resolve(label + ".json"));
    }

    private void check(String name, boolean passed) {
        checks.put(name, passed);
        if (!passed) throw new AssertionError(name);
    }

    private void capture(String label) throws IOException {
        var frame = ClientSmoke.capture(label + ".png");
        captures.put(label, frame);
        check(label + "_quiet_hidden_frame", Boolean.TRUE.equals(frame.get("passed")));
    }

    private void finish(Throwable failure) {
        stage = Stage.DONE;
        var result = new LinkedHashMap<String, Object>();
        result.put("checks", checks);
        result.put("captures", captures);
        result.put("passed", failure == null);
        if (failure != null) result.put("error", failure.toString());
        ClientSmoke.complete("client_starter_progression", result);
    }
}
