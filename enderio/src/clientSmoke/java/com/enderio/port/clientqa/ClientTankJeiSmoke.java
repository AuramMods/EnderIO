package com.enderio.port.clientqa;

import com.enderio.enderio.client.content.machines.gui.screen.FluidTankScreen;
import com.enderio.enderio.compat.jei_machines_to_merge.category.TankCategory;
import com.enderio.enderio.content.storage.fluid_tank.FluidTankMenu;
import com.enderio.enderio.content.storage.fluid_tank.TankRecipe;
import com.enderio.enderio.init.EIOBlocks;
import mezz.jei.api.forge.ForgeTypes;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
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
import net.minecraft.world.item.Items;
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

final class ClientTankJeiSmoke {
    private enum Stage { TITLE, JOIN, OPEN, MISSING, WRONG_FLUID, SHORT_FLUID, READY, TRANSFER, INSERTED, LOADED, OCCUPIED, DONE }

    private record Fixture(String name, int x, boolean fill, Item input, Item output, String recipe, ResourceLocation fluid,
                           int capacity, int amount, int cost) {
        BlockPos pos() { return new BlockPos(x, 80, 8); }
        int inputSlot() { return fill ? 2 : 0; }
        int initialAmount() { return name.equals("standard-fill") ? cost - 1 : amount; }
        ResourceLocation recipeId() { return ResourceLocation.parse("enderio:tank_" + (fill ? "fill/" : "empty/") + recipe); }
    }

    private static final ResourceLocation WATER = ResourceLocation.parse("minecraft:water");
    private static final ResourceLocation LAVA = ResourceLocation.parse("minecraft:lava");
    private static final ResourceLocation XP = ResourceLocation.parse("enderio:fluid_xp_juice_still");
    private static final List<Fixture> FIXTURES = List.of(
        new Fixture("standard-fill", 4738, true, Items.SPONGE, Items.WET_SPONGE, "wet_sponge", WATER, 16000, 1137, 1000),
        new Fixture("standard-empty", 4746, false, Items.WET_SPONGE, Items.SPONGE, "sponge", WATER, 16000, 137, 1000),
        new Fixture("pressurized-fill", 4754, true, Items.GLASS_BOTTLE, Items.EXPERIENCE_BOTTLE, "experience_bottle", XP, 32000, 18137, 250),
        new Fixture("pressurized-empty", 4762, false, Items.EXPERIENCE_BOTTLE, Items.GLASS_BOTTLE, "glass_bottle", XP, 32000, 18137, 250));

    private final Minecraft minecraft = Minecraft.getInstance();
    private final Path output = Path.of(System.getProperty("enderio.clientqa.output"));
    private final Map<String, Boolean> checks = new LinkedHashMap<>();
    private final Map<String, Object> captures = new LinkedHashMap<>();
    private final long started = System.nanoTime();
    private Stage stage = Stage.TITLE;
    private long titleAt;
    private int index;

    ClientTankJeiSmoke() {
        MinecraftForge.EVENT_BUS.addListener(this::render);
    }

    private void render(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage == Stage.DONE) return;
        try {
            if (minecraft.screen instanceof DisconnectedScreen || System.nanoTime() - started > 180_000_000_000L) {
                throw new AssertionError("Tank JEI stopped during " + stage + " on fixture " + index);
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
                if (!catalogReady(runtime)) return;
                check("dedicated_survival_tcp", !player.isCreative() && !player.isSpectator()
                    && minecraft.getSingleplayerServer() == null && !minecraft.getConnection().getConnection().isMemoryConnection());
                signal("connected");
                stage = Stage.OPEN;
            } else if (stage == Stage.OPEN) {
                var pos = fixture.pos();
                if (!ready(fixture, "initial") || minecraft.screen != null || player.distanceToSqr(Vec3.atBottomCenterOf(pos.south(2))) > 0.15) return;
                var hit = new BlockHitResult(Vec3.atCenterOf(pos).relative(Direction.SOUTH, 0.5), Direction.SOUTH, pos, false);
                check(fixture.name() + "_opens_the_native_tank_menu", minecraft.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit).consumesAction());
                stage = Stage.MISSING;
            } else if (stage == Stage.TRANSFER) {
                transfer(fixture);
            } else if (minecraft.screen instanceof FluidTankScreen screen && screen.getMenu().getFluidTank() != null) {
                checkMenu(runtime, screen.getMenu(), fixture);
            }
        } catch (Throwable failure) {
            failure.printStackTrace();
            finish(failure);
        }
    }

    private void checkMenu(IJeiRuntime runtime, FluidTankMenu menu, Fixture fixture) throws Exception {
        var player = minecraft.player;
        var input = menu.getSlot(fixture.inputSlot()).getItem();
        int count = player.getInventory().countItem(fixture.input());
        switch (stage) {
            case MISSING -> {
                if (count != 0 || !input.isEmpty() || !tankMatches(menu, fixture, fixture.fluid(), fixture.initialAmount())) return;
                probe(runtime, menu, fixture, "missing", true, index == 0);
                stage = index == 0 ? Stage.WRONG_FLUID : Stage.READY;
            }
            case WRONG_FLUID -> {
                if (!ready(fixture, "wrong-fluid") || count != 2 || !tankMatches(menu, fixture, LAVA, fixture.amount())) return;
                probe(runtime, menu, fixture, "wrong-fluid", false, true);
                stage = Stage.SHORT_FLUID;
            }
            case SHORT_FLUID -> {
                if (!ready(fixture, "short-fluid") || count != 2 || !tankMatches(menu, fixture, fixture.fluid(), fixture.cost() - 1)) return;
                probe(runtime, menu, fixture, "short-fluid", false, true);
                stage = Stage.READY;
            }
            case READY -> {
                if (!ready(fixture, "ready") || count != 2 || !input.isEmpty() || !tankMatches(menu, fixture, fixture.fluid(), fixture.amount())) return;
                var manager = runtime.getRecipeManager();
                var category = manager.getRecipeCategory(TankCategory.TYPE);
                runtime.getRecipesGui().showRecipes(category, List.of(find(runtime, fixture)), List.of());
                stage = Stage.TRANSFER;
            }
            case INSERTED -> {
                if (input.isEmpty() || count == 2) return;
                check(fixture.name() + "_moves_one_tagged_item_into_only_the_matching_input",
                    input.is(fixture.input()) && input.getCount() == 1 && input.hasTag() && input.getTag().getInt("PortTank") == 224
                        && count == 1 && menu.slots.subList(0, 4).stream().filter(slot -> slot.hasItem()).count() == 1
                        && tankMatches(menu, fixture, fixture.fluid(), fixture.amount()));
                capture(fixture.name() + "-queued");
                signal(fixture.name() + "-inserted");
                stage = Stage.LOADED;
            }
            case LOADED -> {
                if (!ready(fixture, "loaded") || count != 0 || !input.is(fixture.input())) return;
                probe(runtime, menu, fixture, "loaded", false, false);
                if (index == 0) stage = Stage.OCCUPIED;
                else nextFixture();
            }
            case OCCUPIED -> {
                if (!ready(fixture, "occupied") || count != 1 || !input.is(Items.WHITE_CONCRETE_POWDER)
                    || !tankMatches(menu, fixture, fixture.fluid(), fixture.amount())) return;
                probe(runtime, menu, fixture, "occupied", true, false);
                nextFixture();
            }
            default -> { }
        }
    }

    private void nextFixture() {
        minecraft.player.closeContainer();
        index++;
        if (index == FIXTURES.size()) finish(null);
        else stage = Stage.OPEN;
    }

    private void probe(IJeiRuntime runtime, FluidTankMenu menu, Fixture fixture, String phase,
            boolean missingItem, boolean missingFluid) throws Exception {
        var manager = runtime.getRecipeManager();
        var category = manager.getRecipeCategory(TankCategory.TYPE);
        var recipe = find(runtime, fixture);
        var layout = manager.createRecipeLayoutDrawable(category, recipe, runtime.getJeiHelpers().getFocusFactory().getEmptyFocusGroup()).orElseThrow();
        var slots = layout.getRecipeSlotsView();
        var handler = runtime.getRecipeTransferManager().getRecipeTransferHandler(menu, category).orElseThrow();
        var error = handler.transferRecipe(menu, recipe, slots, minecraft.player, false, false);
        var expected = slots.getSlotViews(RecipeIngredientRole.INPUT).stream()
            .filter(slot -> (missingItem && slot.getItemStacks().findAny().isPresent())
                         || (missingFluid && slot.getIngredients(ForgeTypes.FLUID_STACK).findAny().isPresent()))
            .collect(Collectors.toSet());
        if (!missingItem && !missingFluid) {
            check(fixture.name() + "_accepts_its_already_loaded_input_with_no_player_copy", error == null);
        } else {
            check(fixture.name() + "_" + phase + "_highlights_exactly_the_missing_input_slots",
                error instanceof RecipeTransferErrorMissingSlots && error.getType() == IRecipeTransferError.Type.USER_FACING
                    && expected.size() == (missingItem && missingFluid ? 2 : 1) && error.getMissingCountHint() == expected.size()
                    && highlightedSlots(error).equals(expected));
        }
        signal(fixture.name() + "-" + phase);
    }

    private static Set<?> highlightedSlots(IRecipeTransferError error) throws ReflectiveOperationException {
        var field = RecipeTransferErrorMissingSlots.class.getDeclaredField("slots");
        field.setAccessible(true);
        return Set.copyOf((Collection<?>) field.get(error));
    }

    private void transfer(Fixture fixture) throws IOException {
        if (!(minecraft.screen instanceof RecipesGui gui)) return;
        var frame = visibleRecipe(gui, fixture);
        if (frame == null || !frame.transferButton().isInitialized()) return;
        var view = frame.getRecipeLayout().getRecipeSlotsView();
        check(fixture.name() + "_displays_the_real_input_and_output_with_the_exact_fluid",
            itemDisplayed(view, RecipeIngredientRole.INPUT, fixture.input())
                && itemDisplayed(view, RecipeIngredientRole.OUTPUT, fixture.output())
                && view.getSlotViews(fixture.fill() ? RecipeIngredientRole.INPUT : RecipeIngredientRole.OUTPUT).stream()
                    .flatMap(slot -> slot.getIngredients(ForgeTypes.FLUID_STACK))
                    .anyMatch(fluid -> fluid.getAmount() == fixture.cost() && fixture.fluid().equals(ForgeRegistries.FLUIDS.getKey(fluid.getFluid()))));
        check(fixture.name() + "_has_no_missing_inputs_with_valid_supply", frame.transferButton().getMissingCountHint() == 0);
        capture(fixture.name() + "-recipe");
        var rect = frame.getRecipeLayout().getRect();
        var button = frame.getRecipeLayout().getSideButtonArea(0);
        double x = rect.getX() + button.getX() + button.getWidth() / 2.0;
        double y = rect.getY() + button.getY() + button.getHeight() / 2.0;
        check(fixture.name() + "_uses_the_actual_jei_transfer_button",
            frame.transferButton().isMouseOver(x, y) && gui.mouseClicked(x, y, 0) && gui.mouseReleased(x, y, 0));
        stage = Stage.INSERTED;
    }

    private static boolean itemDisplayed(IRecipeSlotsView view, RecipeIngredientRole role, Item expected) {
        return view.getSlotViews(role).stream().flatMap(slot -> slot.getItemStacks())
            .anyMatch(stack -> stack.is(expected) && stack.getCount() == 1);
    }

    private static boolean tankMatches(FluidTankMenu menu, Fixture fixture, ResourceLocation id, int amount) {
        var tank = menu.getFluidTank();
        return tank.capacity() == fixture.capacity() && tank.contents().getAmount() == amount
            && id.equals(ForgeRegistries.FLUIDS.getKey(tank.contents().getFluid()));
    }

    private static RecipeLayoutWithButtons<?> visibleRecipe(RecipesGui gui, Fixture fixture) {
        var area = gui.getArea();
        for (int y = area.getY(); y < area.getY() + area.getHeight(); y += 8) {
            for (int x = area.getX(); x < area.getX() + area.getWidth(); x += 8) {
                if (gui.getRecipeLayoutUnderMouse(x, y).orElse(null) instanceof RecipeLayoutWithButtons<?> layout
                    && layout.getRecipeLayout().getRecipe() instanceof TankRecipe recipe && fixture.recipeId().equals(recipe.getId())) return layout;
            }
        }
        return null;
    }

    private boolean catalogReady(IJeiRuntime runtime) {
        var manager = runtime.getRecipeManager();
        if (manager.createRecipeLookup(TankCategory.TYPE).get().findAny().isEmpty()) return false;
        var catalysts = manager.createRecipeCatalystLookup(TankCategory.TYPE).get().flatMap(ingredient -> ingredient.getItemStack().stream())
            .map(stack -> stack.getItem()).collect(Collectors.toSet());
        check("both_native_tanks_are_recipe_catalysts",
            catalysts.contains(EIOBlocks.FLUID_TANK.asItem()) && catalysts.contains(EIOBlocks.PRESSURIZED_FLUID_TANK.asItem()));
        return true;
    }

    private static TankRecipe find(IJeiRuntime runtime, Fixture fixture) {
        return runtime.getRecipeManager().createRecipeLookup(TankCategory.TYPE).get()
            .filter(recipe -> fixture.recipeId().equals(recipe.getId())).findFirst().orElseThrow();
    }

    private void connect() {
        if (!(minecraft.screen instanceof TitleScreen) || minecraft.getOverlay() != null) return;
        if (titleAt == 0) titleAt = System.nanoTime();
        if (System.nanoTime() - titleAt < 2_000_000_000L) return;
        String address = System.getProperty("enderio.clientqa.server");
        ConnectScreen.startConnecting(new TitleScreen(), minecraft, ServerAddress.parseString(address),
            new ServerData("Ender IO Tank JEI", address, true), false);
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
        ClientSmoke.complete("client_tank_jei", result);
    }
}
