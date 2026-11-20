package com.enderio.port.clientqa;

import com.enderio.enderio.client.content.machines.gui.screen.VatScreen;
import com.enderio.enderio.compat.jei_machines_to_merge.category.VATCategory;
import com.enderio.enderio.content.machines.vat.FermentingRecipe;
import com.enderio.enderio.content.machines.vat.VatMenu;
import com.enderio.enderio.init.EIOBlocks;
import mezz.jei.api.forge.ForgeTypes;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.runtime.IJeiRuntime;
import mezz.jei.gui.recipes.RecipeLayoutWithButtons;
import mezz.jei.gui.recipes.RecipesGui;
import mezz.jei.library.transfer.RecipeTransferErrorMissingSlots;
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
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

final class ClientVatJeiSmoke {
    private enum Stage { TITLE, JOIN, OPEN, CHECK, TRANSFER, INSERTED, LOADED, DONE }

    private record Supply(String name, Fluid fluid, int amount, int first, int second, Set<Integer> missing) { }

    private record Fixture(String name, BlockPos pos, ResourceLocation id, Item first, Item second,
                           boolean reversed, int required, ResourceLocation output, int outputAmount, List<Supply> supplies) {
        Item left() { return reversed ? second : first; }
        Item right() { return reversed ? first : second; }
        Supply ready() { return supplies.get(supplies.size() - 1); }
    }

    private static final List<Fixture> FIXTURES = List.of(
        new Fixture("stock", new BlockPos(4770, 80, 8), ResourceLocation.parse("enderio:fermenting/fluid_nutrient_distillation_still"),
            Items.BEEF, Items.WHEAT, false, 1000, ResourceLocation.parse("enderio:fluid_nutrient_distillation_still"), 250, List.of(
                new Supply("short-and-empty", Fluids.WATER, 999, 0, 0, Set.of(0, 1, 2)),
                new Supply("missing-left", Fluids.WATER, 999, 0, 2, Set.of(0, 2)),
                new Supply("wrong-fluid", Fluids.LAVA, 1000, 2, 2, Set.of(2)),
                new Supply("short-fluid", Fluids.WATER, 999, 2, 2, Set.of(2)),
                new Supply("exact", Fluids.WATER, 1000, 2, 2, Set.of()))),
        new Fixture("reversed", new BlockPos(4778, 80, 8), ResourceLocation.parse("port_vat:jei_batch"),
            Items.COPPER_INGOT, Items.GOLD_INGOT, true, 500, ResourceLocation.parse("enderio:fluid_hootch_still"), 480, List.of(
                new Supply("missing-complement", Fluids.LAVA, 537, 0, 0, Set.of(0)),
                new Supply("complement", Fluids.LAVA, 537, 2, 0, Set.of()))));

    private final Minecraft minecraft = Minecraft.getInstance();
    private final Path output = Path.of(System.getProperty("enderio.clientqa.output"));
    private final Map<String, Boolean> checks = new LinkedHashMap<>();
    private final Map<String, Object> captures = new LinkedHashMap<>();
    private final long started = System.nanoTime();
    private Stage stage = Stage.TITLE;
    private long titleAt;
    private int index;
    private int step;

    ClientVatJeiSmoke() {
        MinecraftForge.EVENT_BUS.addListener(this::render);
    }

    private void render(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage == Stage.DONE) return;
        try {
            if (minecraft.screen instanceof DisconnectedScreen || System.nanoTime() - started > 180_000_000_000L) {
                throw new AssertionError("VAT JEI stopped during " + stage + " on fixture " + index + " step " + step);
            }
            if (stage == Stage.TITLE) {
                connect();
                return;
            }
            var player = minecraft.player;
            var runtime = ClientSoulEngineFuelsSmoke.JeiProbe.runtime();
            if (player == null || minecraft.level == null || runtime == null) return;
            var fixture = FIXTURES.get(index);
            if (stage == Stage.JOIN) {
                if (minecraft.screen != null || minecraft.getToasts().getToast(SystemToast.class,
                    SystemToast.SystemToastIds.UNSECURE_SERVER_WARNING) != null) return;
                if (!verifyCatalog(runtime)) return;
                check("dedicated_survival_tcp", !player.isCreative() && !player.isSpectator()
                    && minecraft.getSingleplayerServer() == null && !minecraft.getConnection().getConnection().isMemoryConnection());
                signal("connected");
                stage = Stage.OPEN;
            } else if (stage == Stage.OPEN) {
                if (!ready(fixture, "initial") || minecraft.screen != null
                    || player.distanceToSqr(Vec3.atBottomCenterOf(fixture.pos().south(2))) > 0.15) return;
                var hit = new BlockHitResult(Vec3.atCenterOf(fixture.pos()).relative(Direction.SOUTH, 0.5), Direction.SOUTH, fixture.pos(), false);
                check(fixture.name() + "_opens_the_real_vat_menu", minecraft.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit).consumesAction());
                stage = Stage.CHECK;
            } else if (stage == Stage.TRANSFER) {
                transfer(fixture);
            } else if (minecraft.screen instanceof VatScreen screen && screen.getMenu().getInputTank() != null) {
                checkMenu(runtime, screen.getMenu(), fixture);
            }
        } catch (Throwable failure) {
            failure.printStackTrace();
            finish(failure);
        }
    }

    private void checkMenu(IJeiRuntime runtime, VatMenu menu, Fixture fixture) throws Exception {
        var player = minecraft.player;
        int first = player.getInventory().countItem(fixture.first());
        int second = player.getInventory().countItem(fixture.second());
        var left = menu.getSlot(0).getItem();
        var right = menu.getSlot(1).getItem();
        switch (stage) {
            case CHECK -> {
                var supply = fixture.supplies().get(step);
                if (!ready(fixture, supply.name()) || !inputTankMatches(menu, supply) || first != supply.first() || second != supply.second()
                    || !right.isEmpty() || (fixture.reversed() ? !tagged(left, fixture.left()) : !left.isEmpty())) return;
                if (supply.missing().isEmpty()) {
                    var category = runtime.getRecipeManager().getRecipeCategory(VATCategory.TYPE);
                    runtime.getRecipesGui().showRecipes(category, List.of(find(runtime, fixture)), List.of());
                    stage = Stage.TRANSFER;
                } else {
                    probe(runtime, menu, fixture, supply.name(), supply.missing());
                    step++;
                }
            }
            case INSERTED -> {
                if (left.isEmpty() || right.isEmpty() || first == 2) return;
                check(fixture.name() + "_queues_exactly_the_original_tagged_reagents_and_keeps_the_surplus",
                    tagged(left, fixture.left()) && tagged(right, fixture.right()) && first == 1 && second == (fixture.reversed() ? 0 : 1)
                        && inputTankMatches(menu, fixture.ready()) && menu.getCraftingProgress() == 0);
                capture(fixture.name() + "-queued");
                signal(fixture.name() + "-inserted");
                stage = Stage.LOADED;
            }
            case LOADED -> {
                if (!ready(fixture, "loaded") || first != 0 || second != 0) return;
                probe(runtime, menu, fixture, "loaded", Set.of());
                player.closeContainer();
                step = 0;
                index++;
                if (index == FIXTURES.size()) finish(null);
                else stage = Stage.OPEN;
            }
            default -> { }
        }
    }

    private void probe(IJeiRuntime runtime, VatMenu menu, Fixture fixture, String phase, Set<Integer> missing) throws Exception {
        var manager = runtime.getRecipeManager();
        var category = manager.getRecipeCategory(VATCategory.TYPE);
        var recipe = find(runtime, fixture);
        var view = manager.createRecipeLayoutDrawable(category, recipe, runtime.getJeiHelpers().getFocusFactory().getEmptyFocusGroup())
            .orElseThrow().getRecipeSlotsView();
        var handler = runtime.getRecipeTransferManager().getRecipeTransferHandler(menu, category).orElseThrow();
        var error = handler.transferRecipe(menu, recipe, view, minecraft.player, false, false);
        if (missing.isEmpty()) {
            check(fixture.name() + "_already_loaded_reagents_satisfy_the_recipe", error == null);
        } else {
            var inputs = view.getSlotViews(RecipeIngredientRole.INPUT);
            var expected = missing.stream().map(inputs::get).collect(Collectors.toSet());
            check(fixture.name() + "_" + phase + "_highlights_exactly_the_missing_inputs",
                error instanceof RecipeTransferErrorMissingSlots && error.getType() == IRecipeTransferError.Type.USER_FACING
                    && error.getMissingCountHint() == expected.size() && highlightedSlots(error).equals(expected));
        }
        signal(fixture.name() + "-" + phase);
    }

    static Set<?> highlightedSlots(IRecipeTransferError error) throws ReflectiveOperationException {
        var field = RecipeTransferErrorMissingSlots.class.getDeclaredField("slots");
        field.setAccessible(true);
        return Set.copyOf((Collection<?>) field.get(error));
    }

    private void transfer(Fixture fixture) throws IOException {
        if (!(minecraft.screen instanceof RecipesGui gui)) return;
        var area = gui.getArea();
        RecipeLayoutWithButtons<?> frame = null;
        for (int y = area.getY(); y < area.getY() + area.getHeight() && frame == null; y += 8) {
            for (int x = area.getX(); x < area.getX() + area.getWidth(); x += 8) {
                if (gui.getRecipeLayoutUnderMouse(x, y).orElse(null) instanceof RecipeLayoutWithButtons<?> layout
                    && layout.getRecipeLayout().getRecipe() instanceof FermentingRecipe recipe && fixture.id().equals(recipe.getId())) {
                    frame = layout;
                    break;
                }
            }
        }
        if (frame == null || !frame.transferButton().isInitialized()) return;
        var view = frame.getRecipeLayout().getRecipeSlotsView();
        var inputs = view.getSlotViews(RecipeIngredientRole.INPUT);
        var fluids = inputs.get(2).getIngredients(ForgeTypes.FLUID_STACK).toList();
        check(fixture.name() + "_layout_has_two_reagent_slots_and_one_sized_fluid_slot", inputs.size() == 3
            && inputs.get(0).getItemStacks().anyMatch(stack -> stack.is(fixture.first()))
            && inputs.get(1).getItemStacks().anyMatch(stack -> stack.is(fixture.second()))
            && fluids.stream().allMatch(fluid -> fluid.getAmount() == fixture.required())
            && fluids.stream().map(fluid -> fluid.getFluid()).collect(Collectors.toSet()).equals(fixture.reversed()
                ? Set.of(Fluids.WATER, Fluids.LAVA) : Set.of(Fluids.WATER)));
        var result = view.getSlotViews(RecipeIngredientRole.OUTPUT).get(0).getDisplayedIngredient(ForgeTypes.FLUID_STACK).orElseThrow();
        check(fixture.name() + "_layout_preserves_the_base_output_amount_and_fluid_tag", result.getAmount() == fixture.outputAmount()
            && fixture.output().equals(ForgeRegistries.FLUIDS.getKey(result.getFluid()))
            && (!fixture.reversed() || (result.hasTag() && result.getTag().getString("PortVat").equals("jei")
                && java.util.Arrays.equals(result.getTag().getIntArray("Readings"), new int[] {4, 3, 1}))));
        check(fixture.name() + "_jei_recognizes_the_sufficient_supply", frame.transferButton().getMissingCountHint() == 0);
        capture(fixture.name() + "-recipe");
        var rect = frame.getRecipeLayout().getRect();
        var button = frame.getRecipeLayout().getSideButtonArea(0);
        double x = rect.getX() + button.getX() + button.getWidth() / 2.0;
        double y = rect.getY() + button.getY() + button.getHeight() / 2.0;
        check(fixture.name() + "_uses_the_actual_jei_transfer_button",
            frame.transferButton().isMouseOver(x, y) && gui.mouseClicked(x, y, 0) && gui.mouseReleased(x, y, 0));
        stage = Stage.INSERTED;
    }

    private static boolean tagged(ItemStack stack, Item item) {
        return stack.is(item) && stack.getCount() == 1 && stack.hasTag() && stack.getTag().getInt("PortVat") == 225;
    }

    private static boolean inputTankMatches(VatMenu menu, Supply supply) {
        var contents = menu.getInputTank().contents();
        return contents.getFluid() == supply.fluid() && contents.getAmount() == supply.amount();
    }

    private boolean verifyCatalog(IJeiRuntime runtime) {
        var manager = runtime.getRecipeManager();
        var recipes = manager.createRecipeLookup(VATCategory.TYPE).get().toList();
        if (recipes.isEmpty()) return false;
        check("vat_is_the_recipe_catalyst", manager.createRecipeCatalystLookup(VATCategory.TYPE).get()
            .flatMap(ingredient -> ingredient.getItemStack().stream()).anyMatch(stack -> stack.is(EIOBlocks.VAT.asItem())));
        check("catalog_includes_both_fixture_recipes_and_all_ten_stock_ids", FIXTURES.stream().allMatch(fixture ->
            recipes.stream().anyMatch(recipe -> fixture.id().equals(recipe.getId())))
            && recipes.stream().filter(recipe -> recipe.getId().getNamespace().equals("enderio")).count() == 10);
        return true;
    }

    private static FermentingRecipe find(IJeiRuntime runtime, Fixture fixture) {
        return runtime.getRecipeManager().createRecipeLookup(VATCategory.TYPE).get()
            .filter(recipe -> fixture.id().equals(recipe.getId())).findFirst().orElseThrow();
    }

    private void connect() {
        if (!(minecraft.screen instanceof TitleScreen) || minecraft.getOverlay() != null) return;
        if (titleAt == 0) titleAt = System.nanoTime();
        if (System.nanoTime() - titleAt < 2_000_000_000L) return;
        String address = System.getProperty("enderio.clientqa.server");
        ConnectScreen.startConnecting(new TitleScreen(), minecraft, ServerAddress.parseString(address),
            new ServerData("Ender IO VAT JEI", address, true), false);
        stage = Stage.JOIN;
    }

    private boolean ready(Fixture fixture, String phase) {
        return Files.exists(output.resolve("server-" + fixture.name() + "-" + phase));
    }

    private void signal(String name) throws IOException {
        var directory = output.resolve("signals");
        Files.createDirectories(directory);
        var tmp = directory.resolve(name + ".tmp");
        Files.writeString(tmp, "{}");
        Files.move(tmp, directory.resolve(name + ".json"));
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
        ClientSmoke.complete("client_vat_jei", result);
    }
}
