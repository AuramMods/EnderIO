package com.enderio.port.clientqa;

import com.enderio.enderio.client.content.machines.gui.screen.CrafterScreen;
import com.enderio.enderio.content.storage.crafter.CrafterMenu;
import com.mojang.blaze3d.platform.InputConstants;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.gui.recipes.RecipeLayoutWithButtons;
import mezz.jei.gui.recipes.RecipesGui;
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
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.ModList;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

final class ClientCrafterSmoke {
    private enum Stage { TITLE, JOIN, OPEN, MENU, JEI_BUTTON, SHAPED, MANUAL_TAG, TEMPLATE, INPUT, OUTPUT, PICKUP, SAVED, DONE }

    private static final BlockPos CRAFTER = new BlockPos(3104, 80, 8);
    private static final ResourceLocation STICKS = ResourceLocation.fromNamespaceAndPath("minecraft", "stick");
    private static final ResourceLocation TAGGED = ResourceLocation.fromNamespaceAndPath("port_crafter", "tagged_paper");
    private static final int GHOST = CrafterMenu.INPUTS_INDEX;
    private static final int HOTBAR = CrafterMenu.LAST_INDEX + 1 + 27;

    private final Minecraft minecraft = Minecraft.getInstance();
    private final Path output = Path.of(System.getProperty("enderio.clientqa.output"));
    private final String address = System.getProperty("enderio.clientqa.server");
    private final Map<String, Boolean> checks = new LinkedHashMap<>();
    private final Map<String, Object> captures = new LinkedHashMap<>();
    private final boolean withJei;
    private Stage stage = Stage.TITLE;
    private long titleReady;
    private long stageStarted;
    private int steps;
    private ResourceLocation selectedRecipe;

    ClientCrafterSmoke(boolean withJei) {
        this.withJei = withJei;
        MinecraftForge.EVENT_BUS.addListener(this::tick);
    }

    private void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage == Stage.DONE) return;
        try {
            if (minecraft.screen instanceof DisconnectedScreen) throw new AssertionError("Disconnected during " + stage);
            if (stage != Stage.TITLE && System.nanoTime() - stageStarted > 14_000_000_000L) {
                throw new AssertionError("Timed out in " + stage + ", screen=" + minecraft.screen + ", template=" + templateState());
            }
            switch (stage) {
                case TITLE -> connect();
                case JOIN -> joined();
                case OPEN -> openCrafter();
                case MENU -> {
                    if (menu() == null) return;
                    check("empty_initial_ghost_grid", templateCount(menu()) == 0);
                    if (withJei) {
                        showRecipe(STICKS);
                    } else {
                        enter(Stage.MANUAL_TAG);
                    }
                }
                case JEI_BUTTON -> activateJeiButton();
                case SHAPED -> checkShapedTemplate();
                case MANUAL_TAG -> setManualTemplate();
                case TEMPLATE -> checkTaggedTemplate();
                case INPUT -> {
                    var menu = menu();
                    if (menu == null || !taggedPaper(menu.getSlot(1).getItem())
                        || !menu.getSlot(2).getItem().is(Items.REDSTONE)) return;
                    check("shift_click_fills_the_two_physical_input_slots", menu.getSlot(1).getItem().getCount() == 1
                        && menu.getSlot(2).getItem().getCount() == 1);
                    signal("inputs-ready");
                    enter(Stage.OUTPUT);
                }
                case OUTPUT -> {
                    var menu = menu();
                    if (menu == null || !menu.getSlot(10).getItem().is(Items.AMETHYST_SHARD)) return;
                    check("craft_consumes_exactly_one_of_each_input", menu.getSlot(10).getItem().getCount() == 1
                        && !menu.getSlot(1).hasItem() && !menu.getSlot(2).hasItem());
                    capture("crafted-output");
                    click(10, ClickType.QUICK_MOVE);
                    enter(Stage.PICKUP);
                }
                case PICKUP -> {
                    if (menu() == null || menu().getSlot(10).hasItem()) return;
                    check("output_shift_click_delivers_one_amethyst_shard", minecraft.player.getInventory().countItem(Items.AMETHYST_SHARD) == 1);
                    check("ghost_template_is_retained_after_crafting", taggedPaper(menu().getSlot(GHOST).getItem())
                        && templateCount(menu()) == 2);
                    signal("output-collected");
                    enter(Stage.SAVED);
                }
                case SAVED -> {
                    if (Files.exists(output.resolve("server-confirmed"))) complete(null);
                }
                case DONE -> { }
            }
        } catch (Throwable failure) {
            failure.printStackTrace();
            complete(failure);
        }
    }

    private void connect() {
        if (!(minecraft.screen instanceof TitleScreen) || minecraft.getOverlay() != null) return;
        if (titleReady == 0) titleReady = System.nanoTime();
        if (System.nanoTime() - titleReady < 2_000_000_000L) return;
        ConnectScreen.startConnecting(new TitleScreen(), minecraft, ServerAddress.parseString(address),
            new ServerData("Crafter recipe test", address, true), false);
        enter(Stage.JOIN);
    }

    private void joined() throws IOException {
        if (minecraft.player == null || minecraft.screen != null) return;
        var connection = minecraft.getConnection().getConnection();
        check("real_dedicated_survival_connection", minecraft.getSingleplayerServer() == null
            && !connection.isMemoryConnection() && connection.getRemoteAddress() instanceof InetSocketAddress
            && !minecraft.player.isCreative() && !minecraft.player.isSpectator());
        check("expected_jei_installation", ModList.get().isLoaded("jei") == withJei);
        signal("connected");
        enter(Stage.OPEN);
    }

    private void openCrafter() {
        if (!Files.exists(output.resolve("server-ready")) || minecraft.screen != null
            || !taggedPaper(minecraft.player.getInventory().getItem(2))
            || minecraft.player.distanceToSqr(3104.5, 80, 11.5) > 0.1) return;
        var hit = new BlockHitResult(new Vec3(3104.5, 80.6, 9), Direction.SOUTH, CRAFTER, false);
        minecraft.gameMode.useItemOn(minecraft.player, InteractionHand.MAIN_HAND, hit);
        enter(Stage.MENU);
    }

    private void showRecipe(ResourceLocation id) {
        if (JeiActions.show(id, menu())) {
            selectedRecipe = id;
            enter(Stage.JEI_BUTTON);
        }
    }

    private void activateJeiButton() throws IOException {
        if (JeiActions.pressTransferButton(minecraft, selectedRecipe)) {
            enter(selectedRecipe.equals(STICKS) ? Stage.SHAPED : Stage.TEMPLATE);
        }
    }

    private void checkShapedTemplate() throws IOException {
        var menu = menu();
        if (menu == null || !menu.getSlot(20).getItem().is(Items.STICK)) return;
        check("jei_shaped_recipe_preserves_shape_and_selects_owned_birch_variant", templateCount(menu) == 2
            && menu.getSlot(GHOST).getItem().is(Items.BIRCH_PLANKS) && menu.getSlot(GHOST + 3).getItem().is(Items.BIRCH_PLANKS)
            && menu.getSlot(20).getItem().getCount() == 4);
        signal("shaped-template");
        if (Files.exists(output.resolve("server-shaped"))) showRecipe(TAGGED);
    }

    private void setManualTemplate() {
        if (menu() == null) return;
        // Pick up each real ingredient, copy its ghost slot, then return it to the hotbar.
        int[] slots = {HOTBAR + 2, GHOST, HOTBAR + 2, HOTBAR + 3, GHOST + 1, HOTBAR + 3};
        click(slots[steps++], ClickType.PICKUP);
        if (steps == slots.length) enter(Stage.TEMPLATE);
    }

    private void checkTaggedTemplate() throws IOException {
        var menu = menu();
        if (menu == null || !menu.getSlot(GHOST + 1).getItem().is(Items.REDSTONE)) return;
        check("template_keeps_the_forge_required_nbt", taggedPaper(menu.getSlot(GHOST).getItem()));
        check("shapeless_template_replaces_the_previous_pattern", templateCount(menu) == 2
            && menu.getSlot(GHOST).getItem().getCount() == 1 && menu.getSlot(GHOST + 1).getItem().getCount() == 1);
        if (!menu.getSlot(20).getItem().is(Items.AMETHYST_SHARD)) return;
        check("strict_recipe_preview_is_the_amethyst_shard", menu.getSlot(20).getItem().getCount() == 1);
        check("template_does_not_spend_inventory_items", minecraft.player.getInventory().getItem(2).getCount() == 2
            && minecraft.player.getInventory().getItem(3).getCount() == 2 && menu.getCarried().isEmpty());
        capture("tagged-template");
        click(HOTBAR + 2, ClickType.QUICK_MOVE);
        click(HOTBAR + 3, ClickType.QUICK_MOVE);
        enter(Stage.INPUT);
    }

    private void click(int slot, ClickType type) {
        minecraft.gameMode.handleInventoryMouseClick(menu().containerId, slot, 0, type, minecraft.player);
    }

    private CrafterMenu menu() {
        return minecraft.screen instanceof CrafterScreen screen ? screen.getMenu() : null;
    }

    private static boolean taggedPaper(ItemStack stack) {
        return stack.is(Items.PAPER) && stack.hasTag() && stack.getTag().getInt("PortCrafterToken") == 7;
    }

    private static int templateCount(CrafterMenu menu) {
        return (int) menu.slots.subList(GHOST, GHOST + CrafterMenu.INPUT_COUNT).stream().filter(slot -> slot.hasItem()).count();
    }

    private String templateState() {
        return menu() == null ? "unavailable" : menu().slots.subList(GHOST, 21).stream().map(slot -> slot.getItem().save(new net.minecraft.nbt.CompoundTag()).toString()).toList().toString();
    }

    private void enter(Stage next) {
        stage = next;
        stageStarted = System.nanoTime();
    }

    private void capture(String name) throws IOException {
        var frame = ClientSmoke.capture(name + ".png");
        captures.put(name, frame);
        check(name + "_silent_hidden_render", Boolean.TRUE.equals(frame.get("passed")));
    }

    private void signal(String name) throws IOException {
        Path directory = output.resolve("signals");
        Files.createDirectories(directory);
        Path signal = directory.resolve(name + ".json");
        if (Files.exists(signal)) return;
        Path temporary = directory.resolve(name + ".tmp");
        Files.writeString(temporary, "{}");
        Files.move(temporary, signal);
    }

    private void check(String name, boolean passed) {
        checks.put(name, passed);
        if (!passed) throw new AssertionError(name);
    }

    private void complete(Throwable failure) {
        stage = Stage.DONE;
        var result = new LinkedHashMap<String, Object>();
        result.put("passed", failure == null);
        result.put("checks", checks);
        result.put("captures", captures);
        if (failure != null) result.put("error", failure.toString());
        ClientSmoke.complete("client_crafter", result);
    }

    // Keep references to JEI's optional GUI classes off the core client test path.
    private static final class JeiActions {
        private static boolean show(ResourceLocation id, CrafterMenu menu) {
            var runtime = ClientSoulEngineFuelsSmoke.JeiProbe.runtime();
            if (runtime == null) return false;
            var manager = runtime.getRecipeManager();
            var category = manager.getRecipeCategory(RecipeTypes.CRAFTING);
            var recipes = manager.createRecipeLookup(RecipeTypes.CRAFTING).get().filter(recipe -> recipe.getId().equals(id)).toList();
            if (recipes.isEmpty()) return false;
            if (runtime.getRecipeTransferManager().getRecipeTransferHandler(menu, category).isEmpty()) {
                throw new AssertionError("JEI did not register a Crafter transfer handler");
            }
            runtime.getRecipesGui().showRecipes(category, recipes, java.util.List.of());
            return true;
        }

        private static boolean pressTransferButton(Minecraft minecraft, ResourceLocation id) {
            if (!(minecraft.screen instanceof RecipesGui gui)) return false;
            var area = gui.getArea();
            for (int y = area.getY(); y < area.getY() + area.getHeight(); y += 8) {
                for (int x = area.getX(); x < area.getX() + area.getWidth(); x += 8) {
                    var found = gui.getRecipeLayoutUnderMouse(x, y).orElse(null);
                    if (!(found instanceof RecipeLayoutWithButtons<?> layout)
                        || !(layout.getRecipeLayout().getRecipe() instanceof CraftingRecipe recipe) || !recipe.getId().equals(id)) continue;
                    var button = layout.transferButton();
                    if (!button.isInitialized()) return false;
                    var recipeArea = layout.getRecipeLayout().getRect();
                    var relative = layout.getRecipeLayout().getSideButtonArea(0);
                    double buttonX = recipeArea.getX() + relative.getX() + relative.getWidth() / 2.0;
                    double buttonY = recipeArea.getY() + relative.getY() + relative.getHeight() / 2.0;
                    if (!button.isMouseOver(buttonX, buttonY)) throw new AssertionError("Could not locate the actual JEI transfer button");
                    return gui.mouseClicked(buttonX, buttonY, InputConstants.MOUSE_BUTTON_LEFT)
                        && gui.mouseReleased(buttonX, buttonY, InputConstants.MOUSE_BUTTON_LEFT);
                }
            }
            return false;
        }
    }
}
