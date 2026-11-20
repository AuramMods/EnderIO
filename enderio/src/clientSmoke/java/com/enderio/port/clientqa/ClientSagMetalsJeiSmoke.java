package com.enderio.port.clientqa;

import com.enderio.enderio.compat.jei_machines_to_merge.category.SagMillCategory;
import com.enderio.enderio.content.machines.sag_mill.SagMillMenu;
import com.enderio.enderio.content.machines.sag_mill.SagMillingRecipe;
import com.enderio.enderio.init.EIOBlocks;
import com.google.gson.Gson;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.runtime.IJeiRuntime;
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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.ModList;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

final class ClientSagMetalsJeiSmoke {
    private enum Stage { TITLE, JOIN, OPEN, MENU, VIEWER, TRANSFERRED, PAUSED, DONE }
    private record Mill(String item, int x, List<String> candidates, int cost) {
        BlockPos pos() { return new BlockPos(x, 80, 8); }
        ResourceLocation recipe() { return ResourceLocation.parse(candidates.get(0)); }
    }
    private record Product(String item, int count, float chance, boolean optional) { }
    private record Card(Set<String> inputs, List<Product> outputs, int energy, String bonus) { }
    private record Fixtures(List<Mill> mills, Map<String, Card> recipes) { }

    private static final Set<String> CAPTURE_ITEMS = Set.of("immersiveengineering:ore_aluminum",
        "mekanism:deepslate_osmium_ore", "mekanism:ingot_tin");

    private final Minecraft minecraft = Minecraft.getInstance();
    private final Path output = Path.of(System.getProperty("enderio.clientqa.output"));
    private final Map<String, Boolean> checks = new LinkedHashMap<>();
    private final Map<String, Object> captures = new LinkedHashMap<>();
    private final long started = System.nanoTime();
    private Stage stage = Stage.TITLE;
    private long titleAt;
    private int index;
    private Fixtures fixtures;

    ClientSagMetalsJeiSmoke() {
        MinecraftForge.EVENT_BUS.addListener(this::render);
    }

    private void render(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage == Stage.DONE) return;
        try {
            if (minecraft.screen instanceof DisconnectedScreen || System.nanoTime() - started > 220_000_000_000L) {
                throw new AssertionError("SAG metals JEI stopped during " + stage + " on input " + index);
            }
            if (stage == Stage.TITLE) {
                connect();
                return;
            }
            var player = minecraft.player;
            var runtime = ClientSoulEngineFuelsSmoke.JeiProbe.runtime();
            if (player == null || minecraft.level == null || runtime == null) return;
            if (stage == Stage.JOIN) {
                if (minecraft.screen != null || minecraft.getToasts().getToast(SystemToast.class,
                    SystemToast.SystemToastIds.UNSECURE_SERVER_WARNING) != null) return;
                check("real_dedicated_survival_client", !player.isCreative() && !player.isSpectator()
                    && minecraft.getSingleplayerServer() == null && !minecraft.getConnection().getConnection().isMemoryConnection());
                check("client_loads_jei_mekanism_and_immersive_engineering",
                    List.of("jei", "mekanism", "immersiveengineering").stream().allMatch(ModList.get()::isLoaded));
                signal("connected");
                stage = Stage.OPEN;
                return;
            }
            if (fixtures == null) {
                var file = output.resolve("sag-metals.json");
                if (!Files.exists(file)) return;
                fixtures = new Gson().fromJson(Files.readString(file), Fixtures.class);
                verifyCatalog(runtime);
            }
            var mill = fixtures.mills().get(index);
            switch (stage) {
                case OPEN -> {
                    var pos = mill.pos();
                    if (minecraft.screen != null || !ready("ready")
                        || player.distanceToSqr(Vec3.atBottomCenterOf(pos.south(2))) > 0.15) return;
                    var hit = new BlockHitResult(Vec3.atCenterOf(pos).relative(Direction.SOUTH, 0.5), Direction.SOUTH, pos, false);
                    check(mill.item() + "_opens_the_real_sag_mill",
                        minecraft.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit).consumesAction());
                    stage = Stage.MENU;
                }
                case MENU -> {
                    if (!(player.containerMenu instanceof SagMillMenu menu) || !marked(player.getInventory().getItem(1), mill, 2)
                        || !menu.getSlot(SagMillMenu.INPUTS_INDEX).getItem().isEmpty()) return;
                    openRecipe(runtime, mill);
                    stage = Stage.VIEWER;
                }
                case VIEWER -> {
                    if (!(minecraft.screen instanceof RecipesGui gui)) return;
                    var layout = ClientSagJeiSmoke.visibleRecipe(gui, mill.recipe());
                    if (layout == null || !layout.transferButton().isInitialized()) return;
                    var view = layout.recipeLayout().getRecipeSlotsView();
                    var input = view.getSlotViews(RecipeIngredientRole.INPUT).get(0).getDisplayedItemStack().orElseThrow();
                    check(mill.item() + "_focused_card_keeps_its_material_and_exact_server_products",
                        itemId(input).equals(mill.item()) && matchesOutputs(view, fixtures.recipes().get(mill.recipe().toString())));
                    if (CAPTURE_ITEMS.contains(mill.item())) capture(mill.item(), "card");
                    check(mill.item() + "_passes_the_native_jei_transfer_button",
                        layout.transferButton().getMissingCountHint() == 0 && ClientSagJeiSmoke.clickTransfer(gui, layout));
                    stage = Stage.TRANSFERRED;
                }
                case TRANSFERRED -> {
                    if (!(player.containerMenu instanceof SagMillMenu menu)) return;
                    var input = menu.getSlot(SagMillMenu.INPUTS_INDEX).getItem();
                    if (input.isEmpty()) return;
                    check(mill.item() + "_moves_one_marked_ingot_ore_or_raw_input",
                        marked(input, mill, 1) && marked(player.getInventory().getItem(1), mill, 1));
                    signal("transferred-" + index);
                    stage = Stage.PAUSED;
                }
                case PAUSED -> {
                    if (!(player.containerMenu instanceof SagMillMenu menu) || !ready("paused") || menu.getEnergyStorage() == null
                        || Math.abs(menu.getCraftingProgress() - 600f / mill.cost()) > 0.00001f) return;
                    check(mill.item() + "_native_menu_syncs_the_exact_partial_payment",
                        menu.getEnergyStorage().getEnergyStored() == 0 && marked(menu.getSlot(SagMillMenu.INPUTS_INDEX).getItem(), mill, 1));
                    if (CAPTURE_ITEMS.contains(mill.item())) capture(mill.item(), "paused");
                    player.closeContainer();
                    signal("observed-" + index);
                    if (++index == fixtures.mills().size()) finish(null);
                    else stage = Stage.OPEN;
                }
                default -> { }
            }
        } catch (Throwable failure) {
            failure.printStackTrace();
            finish(failure);
        }
    }

    private void verifyCatalog(IJeiRuntime runtime) {
        var manager = runtime.getRecipeManager();
        var category = manager.getRecipeCategory(SagMillCategory.TYPE);
        var client = manager.createRecipeLookup(SagMillCategory.TYPE).get()
            .collect(Collectors.toMap(recipe -> recipe.getId().toString(), Function.identity()));
        check("sag_mill_is_the_jei_catalyst", manager.createRecipeCatalystLookup(SagMillCategory.TYPE).get()
            .flatMap(i -> i.getItemStack().stream()).anyMatch(stack -> stack.is(EIOBlocks.SAG_MILL.asItem())));
        check("jei_indexes_every_live_stock_sag_recipe", client.keySet().equals(fixtures.recipes().keySet()) && client.size() == 86);
        fixtures.recipes().forEach((id, expected) -> {
            var recipe = client.get(id);
            var layout = manager.createRecipeLayoutDrawable(category, recipe, runtime.getJeiHelpers().getFocusFactory().getEmptyFocusGroup())
                .orElseThrow();
            var view = layout.getRecipeSlotsView();
            var ingredients = view.getSlotViews(RecipeIngredientRole.INPUT).get(0).getItemStacks()
                .map(ClientSagMetalsJeiSmoke::itemId).collect(Collectors.toSet());
            check(id + "_jei_lists_the_same_inputs_as_the_server", ingredients.equals(expected.inputs()));
            check(id + "_jei_keeps_server_outputs_counts_and_chances",
                matchesOutputs(view, expected) && recipe.outputs().stream().map(ClientSagMetalsJeiSmoke::product).toList().equals(expected.outputs()));
            check(id + "_keeps_the_server_base_cost_and_bonus_mode", recipe.getBaseEnergyCost() == expected.energy()
                && recipe.bonusType().getSerializedName().equals(expected.bonus()));
        });
    }

    private void openRecipe(IJeiRuntime runtime, Mill mill) {
        var ingredient = BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(mill.item())).orElseThrow();
        check(mill.item() + "_ingredient_lookup_finds_the_stock_recipe",
            ClientSagJeiSmoke.matches(runtime, RecipeIngredientRole.INPUT, ingredient, mill.recipe()));
        var manager = runtime.getRecipeManager();
        runtime.getRecipesGui().showRecipes(manager.getRecipeCategory(SagMillCategory.TYPE),
            List.of(ClientSagJeiSmoke.recipe(runtime, mill.recipe())),
            List.of(ClientSagJeiSmoke.focus(runtime, RecipeIngredientRole.INPUT, ingredient)));
    }

    private static boolean matchesOutputs(IRecipeSlotsView view, Card expected) {
        var slots = view.getSlotViews(RecipeIngredientRole.OUTPUT);
        if (slots.size() != expected.outputs().size()) return false;
        for (int slot = 0; slot < slots.size(); slot++) {
            var actual = slots.get(slot).getDisplayedItemStack().orElse(ItemStack.EMPTY);
            var product = expected.outputs().get(slot);
            if (!itemId(actual).equals(product.item()) || actual.getCount() != product.count()) return false;
        }
        return true;
    }

    private static Product product(SagMillingRecipe.OutputItem output) {
        var stack = output.getItemStack();
        return new Product(itemId(stack), stack.getCount(), output.chance(), output.isOptional());
    }

    private static String itemId(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    private static boolean marked(ItemStack stack, Mill mill, int count) {
        return itemId(stack).equals(mill.item()) && stack.getCount() == count && stack.hasTag()
            && stack.getTag().getInt("StockSagInput") == mill.x();
    }

    private void connect() {
        if (!(minecraft.screen instanceof TitleScreen) || minecraft.getOverlay() != null) return;
        if (titleAt == 0) titleAt = System.nanoTime();
        if (System.nanoTime() - titleAt < 2_000_000_000L) return;
        var address = System.getProperty("enderio.clientqa.server");
        ConnectScreen.startConnecting(new TitleScreen(), minecraft, ServerAddress.parseString(address),
            new ServerData("Ender IO stock metals JEI", address, true), false);
        stage = Stage.JOIN;
    }

    private boolean ready(String phase) {
        return Files.exists(output.resolve("server-" + index + "-" + phase));
    }

    private void capture(String item, String phase) throws IOException {
        String name = item.replace(':', '-') + "-" + phase;
        var frame = ClientSmoke.capture(name + ".png");
        captures.put(name, frame);
        check(name + "_quiet_hidden_frame", Boolean.TRUE.equals(frame.get("passed")));
    }

    private void signal(String name) throws IOException {
        var directory = output.resolve("signals");
        Files.createDirectories(directory);
        var temp = directory.resolve(name + ".tmp");
        Files.writeString(temp, "{}");
        Files.move(temp, directory.resolve(name + ".json"));
    }

    private void check(String label, boolean passed) {
        checks.put(label, passed);
        if (!passed) throw new AssertionError(label);
    }

    private void finish(Throwable failure) {
        stage = Stage.DONE;
        var result = new LinkedHashMap<String, Object>();
        result.put("passed", failure == null);
        result.put("checks", checks);
        result.put("captures", captures);
        if (failure != null) result.put("error", failure.toString());
        ClientSmoke.complete("client_sag_jei_metals", result);
    }
}
