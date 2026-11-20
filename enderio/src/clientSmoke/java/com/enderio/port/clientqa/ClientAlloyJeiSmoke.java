package com.enderio.port.clientqa;

import com.enderio.enderio.client.content.machines.gui.screen.AlloySmelterScreen;
import com.enderio.enderio.compat.jei_machines_to_merge.category.AlloySmeltingCategory;
import com.enderio.enderio.content.machines.alloy.AlloySmelterMenu;
import com.enderio.enderio.content.machines.alloy.AlloySmeltingRecipe;
import com.enderio.enderio.init.EIOBlocks;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.runtime.IJeiRuntime;
import mezz.jei.gui.recipes.RecipeLayoutWithButtons;
import mezz.jei.gui.recipes.RecipesGui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
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
import java.util.List;
import java.util.Map;

final class ClientAlloyJeiSmoke {
    private enum Stage { TITLE, JOIN, OPEN, SELECT, TRANSFER, INSERTED, FINISH, DONE }

    private record Input(Item item, int count) {
        boolean matches(ItemStack stack) {
            return stack.is(item) && stack.getCount() == count;
        }
    }

    private static final BlockPos SMELTER = new BlockPos(3257, 80, 8);
    private static final ResourceLocation STOCK = ResourceLocation.parse("enderio:alloy_smelting/red_dye");
    private static final ResourceLocation CUSTOM = ResourceLocation.parse("port_alloy:alloy_smelting/sized");
    private static final List<Input> STOCK_INPUTS = List.of(new Input(Items.BEETROOT, 1), new Input(Items.CLAY_BALL, 3), new Input(Items.EGG, 6));
    private static final List<Input> RELOADED_INPUTS = List.of(new Input(Items.PAPER, 4), new Input(Items.KELP, 2), new Input(Items.AMETHYST_SHARD, 3));

    private final Minecraft minecraft = Minecraft.getInstance();
    private final String address = System.getProperty("enderio.clientqa.server");
    private final Path output = Path.of(System.getProperty("enderio.clientqa.output"));
    private final Map<String, Boolean> checks = new LinkedHashMap<>();
    private final Map<String, Object> captures = new LinkedHashMap<>();
    private final long started = System.nanoTime();
    private Stage stage = Stage.TITLE;
    private long titleAt;
    private int finishedAt = -1;
    private boolean reloaded;

    ClientAlloyJeiSmoke() {
        MinecraftForge.EVENT_BUS.addListener(this::render);
    }

    private void render(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage == Stage.DONE) return;
        try {
            if (minecraft.screen instanceof DisconnectedScreen || System.nanoTime() - started > 160_000_000_000L) {
                throw new AssertionError("Alloy JEI stopped during " + stage + " for " + label());
            }
            if (stage == Stage.TITLE) {
                if (!(minecraft.screen instanceof TitleScreen) || minecraft.getOverlay() != null) return;
                if (titleAt == 0) titleAt = System.nanoTime();
                if (System.nanoTime() - titleAt < 2_000_000_000L) return;
                ConnectScreen.startConnecting(new TitleScreen(), minecraft, ServerAddress.parseString(address),
                    new ServerData("Alloy Smelter JEI QA", address, true), false);
                stage = Stage.JOIN;
                return;
            }
            var player = minecraft.player;
            var runtime = ClientSoulEngineFuelsSmoke.JeiProbe.runtime();
            if (player == null || minecraft.level == null || runtime == null) return;
            switch (stage) {
                case JOIN -> {
                    if (minecraft.screen != null || minecraft.getToasts().getToast(SystemToast.class,
                        SystemToast.SystemToastIds.UNSECURE_SERVER_WARNING) != null) return;
                    check("dedicated_survival_tcp", !player.isCreative() && !player.isSpectator()
                        && minecraft.getSingleplayerServer() == null && !minecraft.getConnection().getConnection().isMemoryConnection());
                    signal("connected");
                    stage = Stage.OPEN;
                }
                case OPEN -> {
                    if (!ready(label()) || minecraft.screen != null || player.distanceToSqr(3257.5, 80, 11.5) > 0.15) return;
                    var hit = new BlockHitResult(Vec3.atCenterOf(SMELTER).relative(Direction.SOUTH, 0.5), Direction.SOUTH, SMELTER, false);
                    check(label() + "_uses_the_actual_smelter_menu", minecraft.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit).consumesAction());
                    stage = Stage.SELECT;
                }
                case SELECT -> select(runtime);
                case TRANSFER -> transfer();
                case INSERTED -> {
                    if (!(minecraft.screen instanceof AlloySmelterScreen screen)) return;
                    var menu = screen.getMenu();
                    for (int i = 0; i < inputs().size(); i++) {
                        if (menu.getSlot(AlloySmelterMenu.INPUTS_INDEX + i).getItem().isEmpty()) return;
                    }
                    capture(label() + "-transferred");
                    for (int i = 0; i < inputs().size(); i++) {
                        var stack = menu.getSlot(AlloySmelterMenu.INPUTS_INDEX + i).getItem();
                        check(label() + "_slot_" + i + "_receives_the_displayed_count_and_keeps_nbt",
                            inputs().get(i).matches(stack) && stack.hasTag() && stack.getTag().getInt("PortAlloy") == 223);
                    }
                    signal(label() + "-transferred");
                    if (reloaded) {
                        stage = Stage.FINISH;
                    } else {
                        player.closeContainer();
                        reloaded = true;
                        stage = Stage.OPEN;
                    }
                }
                case FINISH -> {
                    if (!ready("finished") || !(minecraft.screen instanceof AlloySmelterScreen screen)) return;
                    var menu = screen.getMenu();
                    if (finishedAt < 0) finishedAt = player.tickCount;
                    boolean synced = menu.getEnergyStorage().getEnergyStored() == 0 && menu.getCraftingProgress() == 0
                        && menu.slots.subList(AlloySmelterMenu.INPUTS_INDEX, AlloySmelterMenu.LAST_INDEX + 1).stream()
                            .noneMatch(slot -> slot.hasItem());
                    if (!synced && player.tickCount - finishedAt < 100) return;
                    capture("alloy-jei-finished");
                    check("completed_job_synchronizes_empty_inputs_and_output", synced);
                    player.closeContainer();
                    finish(null);
                }
                default -> { }
            }
        } catch (Throwable failure) {
            failure.printStackTrace();
            finish(failure);
        }
    }

    private void select(IJeiRuntime runtime) {
        if (!(minecraft.screen instanceof AlloySmelterScreen screen)) return;
        var manager = runtime.getRecipeManager();
        var recipes = manager.createRecipeLookup(AlloySmeltingCategory.TYPE).get().toList();
        var recipe = recipes.stream().filter(candidate -> recipeId().equals(candidate.getId())).findFirst().orElse(null);
        if (recipe == null || (reloaded && recipe.energy() != 1800)) return;
        if (!reloaded) {
            check("smelter_is_an_alloy_recipe_catalyst", manager.createRecipeCatalystLookup(AlloySmeltingCategory.TYPE).get()
                .flatMap(ingredient -> ingredient.getItemStack().stream()).anyMatch(stack -> stack.is(EIOBlocks.ALLOY_SMELTER.asItem())));
            check("original_custom_recipe_is_indexed", recipes.stream().anyMatch(candidate -> CUSTOM.equals(candidate.getId())
                && candidate.energy() == 2400 && candidate.output().is(Items.PRISMARINE_CRYSTALS)));
        } else {
            check("reload_updates_output_and_ingredient_lookup", matches(runtime, RecipeIngredientRole.OUTPUT, Items.QUARTZ)
                && !matches(runtime, RecipeIngredientRole.OUTPUT, Items.PRISMARINE_CRYSTALS)
                && matches(runtime, RecipeIngredientRole.INPUT, Items.PAPER)
                && !matches(runtime, RecipeIngredientRole.INPUT, Items.STICK));
        }
        var category = manager.getRecipeCategory(AlloySmeltingCategory.TYPE);
        check(label() + "_has_a_transfer_handler", runtime.getRecipeTransferManager().getRecipeTransferHandler(screen.getMenu(), category).isPresent());
        runtime.getRecipesGui().showRecipes(category, List.of(recipe), List.of());
        stage = Stage.TRANSFER;
    }

    private boolean matches(IJeiRuntime runtime, RecipeIngredientRole role, Item item) {
        var focus = runtime.getJeiHelpers().getFocusFactory().createFocus(role, VanillaTypes.ITEM_STACK, new ItemStack(item));
        return runtime.getRecipeManager().createRecipeLookup(AlloySmeltingCategory.TYPE).limitFocus(List.of(focus)).get()
            .anyMatch(recipe -> CUSTOM.equals(recipe.getId()));
    }

    private void transfer() throws IOException {
        if (!(minecraft.screen instanceof RecipesGui gui)) return;
        var frame = visibleRecipe(gui);
        if (frame == null || !frame.transferButton().isInitialized()) return;
        var view = frame.getRecipeLayout().getRecipeSlotsView();
        var slots = view.getSlotViews(RecipeIngredientRole.INPUT);
        check(label() + "_has_three_visible_inputs", slots.size() == inputs().size());
        for (int i = 0; i < inputs().size(); i++) {
            check(label() + "_displays_the_required_count_for_input_" + i, slots.get(i).getItemStacks().anyMatch(inputs().get(i)::matches));
        }
        var result = view.getSlotViews(RecipeIngredientRole.OUTPUT).get(0).getDisplayedItemStack().orElseThrow();
        check(label() + "_displays_the_exact_output", reloaded
            ? result.is(Items.QUARTZ) && result.getCount() == 2 && result.hasTag() && result.getTag().getString("PortAlloyResult").equals("sized")
            : result.is(Items.RED_DYE) && result.getCount() == 12);
        capture(label() + "-alloy-jei");
        var rect = frame.getRecipeLayout().getRect();
        var button = frame.getRecipeLayout().getSideButtonArea(0);
        double x = rect.getX() + button.getX() + button.getWidth() / 2.0;
        double y = rect.getY() + button.getY() + button.getHeight() / 2.0;
        check(label() + "_uses_the_actual_jei_transfer_button",
            frame.transferButton().isMouseOver(x, y) && gui.mouseClicked(x, y, 0) && gui.mouseReleased(x, y, 0));
        stage = Stage.INSERTED;
    }

    private RecipeLayoutWithButtons<?> visibleRecipe(RecipesGui gui) {
        var area = gui.getArea();
        for (int y = area.getY(); y < area.getY() + area.getHeight(); y += 8) {
            for (int x = area.getX(); x < area.getX() + area.getWidth(); x += 8) {
                if (gui.getRecipeLayoutUnderMouse(x, y).orElse(null) instanceof RecipeLayoutWithButtons<?> layout
                    && layout.getRecipeLayout().getRecipe() instanceof AlloySmeltingRecipe recipe && recipeId().equals(recipe.getId())) return layout;
            }
        }
        return null;
    }

    private String label() { return reloaded ? "reloaded" : "stock"; }

    private ResourceLocation recipeId() { return reloaded ? CUSTOM : STOCK; }

    private List<Input> inputs() { return reloaded ? RELOADED_INPUTS : STOCK_INPUTS; }

    private boolean ready(String name) { return Files.exists(output.resolve("server-" + name)); }

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
        result.put("passed", failure == null);
        result.put("checks", checks);
        result.put("captures", captures);
        if (failure != null) result.put("error", failure.toString());
        ClientSmoke.complete("client_alloy_jei", result);
    }
}
