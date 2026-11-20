package com.enderio.port.clientqa;

import com.enderio.enderio.client.content.machines.gui.screen.WeatherObeliskScreen;
import com.enderio.enderio.compat.jei_machines_to_merge.category.WeatherChangeCategory;
import com.enderio.enderio.content.machines.obelisks.weather.WeatherChangeRecipe;
import com.enderio.enderio.content.machines.obelisks.weather.WeatherObeliskMenu;
import com.enderio.enderio.init.EIOBlocks;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.forge.ForgeTypes;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.enderio.port.clientqa.ClientAlloyProgressionSmoke.id;

final class ClientWeatherJeiSmoke {
    private enum Stage { TITLE, JOIN, OPEN, SHORT_EMPTY, WRONG_FLUID, SHORT_FULL, RECIPE, TRANSFER, INSERTED, FINISH, DONE }

    private record Fixture(WeatherChangeRecipe.WeatherMode mode, ResourceLocation fluid, BlockPos position, int supplied) {
        String name() { return mode.getSerializedName(); }
        ResourceLocation recipe() { return id(name()); }
    }

    private static final List<Fixture> FIXTURES = List.of(
        new Fixture(WeatherChangeRecipe.WeatherMode.RAIN, id("fluid_cloud_seed_still"), new BlockPos(4674, 80, 8), 1237),
        new Fixture(WeatherChangeRecipe.WeatherMode.LIGHTNING, id("fluid_cloud_seed_concentrated_still"), new BlockPos(4682, 80, 8), 1321),
        new Fixture(WeatherChangeRecipe.WeatherMode.CLEAR, id("fluid_liquid_sunshine_still"), new BlockPos(4690, 80, 8), 1459));

    private final Minecraft minecraft = Minecraft.getInstance();
    private final Path output = Path.of(System.getProperty("enderio.clientqa.output"));
    private final Map<String, Boolean> checks = new LinkedHashMap<>();
    private final Map<String, Object> captures = new LinkedHashMap<>();
    private final long started = System.nanoTime();
    private Stage stage = Stage.TITLE;
    private long titleAt;
    private int settleBy = -1;
    private int index;

    ClientWeatherJeiSmoke() {
        MinecraftForge.EVENT_BUS.addListener(this::render);
    }

    private void render(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage == Stage.DONE) return;
        try {
            if (minecraft.screen instanceof DisconnectedScreen || System.nanoTime() - started > 170_000_000_000L) {
                throw new AssertionError("Weather JEI stopped at " + stage + " for fixture " + index);
            }
            if (stage == Stage.TITLE) {
                connect();
                return;
            }
            var player = minecraft.player;
            var runtime = ClientSoulEngineFuelsSmoke.JeiProbe.runtime();
            if (player == null || minecraft.level == null || runtime == null) return;
            if (stage == Stage.FINISH) {
                if (ready("finish")) finish(null);
                return;
            }
            var fixture = FIXTURES.get(index);
            switch (stage) {
                case JOIN -> {
                    if (minecraft.screen != null || minecraft.getToasts().getToast(SystemToast.class,
                        SystemToast.SystemToastIds.UNSECURE_SERVER_WARNING) != null) return;
                    check("dedicated_survival_tcp", !player.isCreative() && !player.isSpectator()
                        && !minecraft.getConnection().getConnection().isMemoryConnection());
                    if (!verifyCatalog(runtime)) return;
                    signal("connected");
                    stage = Stage.OPEN;
                }
                case OPEN -> open(fixture);
                case SHORT_EMPTY, WRONG_FLUID, SHORT_FULL -> verifyRejection(runtime, fixture);
                case RECIPE -> {
                    if (!(minecraft.screen instanceof WeatherObeliskScreen screen)) return;
                    var menu = screen.getMenu();
                    if (menu.getFluidTank() == null || !tankMatches(menu, fixture, fixture.supplied())
                        || player.getInventory().countItem(Items.FIREWORK_ROCKET) != 4 - index || menu.getSlot(0).hasItem()) return;
                    var manager = runtime.getRecipeManager();
                    var category = manager.getRecipeCategory(WeatherChangeCategory.TYPE);
                    var recipe = recipes(runtime).stream().filter(r -> fixture.recipe().equals(r.getId())).findFirst().orElseThrow();
                    check(fixture.name() + "_has_the_stock_transfer_handler",
                        runtime.getRecipeTransferManager().getRecipeTransferHandler(menu, category).isPresent());
                    runtime.getRecipesGui().showRecipes(category, List.of(recipe), List.of());
                    stage = Stage.TRANSFER;
                }
                case TRANSFER -> transfer(fixture);
                case INSERTED -> {
                    if (!(minecraft.screen instanceof WeatherObeliskScreen screen)) return;
                    var menu = screen.getMenu();
                    var rocket = menu.getSlot(0).getItem();
                    if (!rocket.is(Items.FIREWORK_ROCKET)) return;
                    check(fixture.name() + "_one_tagged_rocket_moves_without_spending_fluid",
                        rocket.getCount() == 1 && rocket.hasTag() && rocket.getTag().getInt("PortWeather216") == 216
                            && tankMatches(menu, fixture, fixture.supplied()) && menu.getCraftingProgress() == 0
                            && player.getInventory().countItem(Items.FIREWORK_ROCKET) == 3 - index);
                    capture(fixture.name() + "-weather-queued");
                    player.closeContainer();
                    signal(fixture.name() + "-transferred");
                    stage = ++index == FIXTURES.size() ? Stage.FINISH : Stage.OPEN;
                }
                default -> {}
            }
        } catch (Throwable failure) {
            failure.printStackTrace();
            finish(failure);
        }
    }

    private boolean verifyCatalog(IJeiRuntime runtime) {
        var stock = recipes(runtime);
        if (stock.isEmpty()) return false;
        var manager = runtime.getRecipeManager();
        var focuses = runtime.getJeiHelpers().getFocusFactory();
        var ids = FIXTURES.stream().map(Fixture::recipe).collect(Collectors.toSet());
        check("jei_lists_exactly_the_three_stock_weather_recipes",
            stock.size() == 3 && stock.stream().map(WeatherChangeRecipe::getId).collect(Collectors.toSet()).equals(ids));
        check("weather_obelisk_is_the_jei_recipe_catalyst",
            manager.createRecipeCatalystLookup(WeatherChangeCategory.TYPE).get()
                .flatMap(ingredient -> ingredient.getItemStack().stream()).anyMatch(s -> s.is(EIOBlocks.WEATHER_OBELISK.asItem())));
        var rockets = focuses.createFocus(RecipeIngredientRole.INPUT, VanillaTypes.ITEM_STACK, new ItemStack(Items.FIREWORK_ROCKET));
        check("firework_input_lookup_finds_all_three_weather_recipes",
            manager.createRecipeLookup(WeatherChangeCategory.TYPE).limitFocus(List.of(rockets)).get()
                .map(WeatherChangeRecipe::getId).collect(Collectors.toSet()).equals(ids));
        for (var fixture : FIXTURES) {
            var recipe = stock.stream().filter(r -> fixture.recipe().equals(r.getId())).findFirst().orElseThrow();
            check(fixture.name() + "_stock_recipe_has_the_exact_fluid_amount_and_mode", recipe.mode() == fixture.mode()
                && recipe.fluid().getAmount() == 1_000 && fixture.fluid().equals(ForgeRegistries.FLUIDS.getKey(recipe.fluid().getFluid())));
            var fluid = focuses.createFocus(RecipeIngredientRole.INPUT, ForgeTypes.FLUID_STACK, recipe.fluid());
            check(fixture.name() + "_jei_fluid_input_lookup_finds_only_its_matching_weather",
                manager.createRecipeLookup(WeatherChangeCategory.TYPE).limitFocus(List.of(fluid)).get()
                    .map(WeatherChangeRecipe::getId).collect(Collectors.toSet()).equals(Set.of(fixture.recipe())));
        }
        return true;
    }

    private void verifyRejection(IJeiRuntime runtime, Fixture fixture) throws Exception {
        if (!(minecraft.screen instanceof WeatherObeliskScreen screen)) return;
        var menu = screen.getMenu();
        if (menu.getFluidTank() == null) return;
        var player = minecraft.player;
        int rockets = player.getInventory().countItem(Items.FIREWORK_ROCKET);
        String rejection;
        int missing;
        Stage next;
        switch (stage) {
            case SHORT_EMPTY -> {
                if (!tankMatches(menu, fixture, 999) || rockets != 0) return;
                rejection = "short-no-rocket";
                missing = 2;
                next = Stage.WRONG_FLUID;
            }
            case WRONG_FLUID -> {
                var fluid = menu.getFluidTank().contents();
                if (!ready("wrong-fluid") || fluid.getFluid() != Fluids.WATER || fluid.getAmount() != 1237 || rockets != 4) return;
                rejection = "wrong-fluid";
                missing = 1;
                next = Stage.SHORT_FULL;
            }
            case SHORT_FULL -> {
                if (!ready("short-fluid") || !tankMatches(menu, fixture, 999) || rockets != 4) return;
                rejection = "short-fluid";
                missing = 1;
                next = Stage.RECIPE;
            }
            default -> throw new IllegalStateException(stage.name());
        }
        var manager = runtime.getRecipeManager();
        var category = manager.getRecipeCategory(WeatherChangeCategory.TYPE);
        var recipe = recipes(runtime).stream().filter(r -> fixture.recipe().equals(r.getId())).findFirst().orElseThrow();
        var layout = manager.createRecipeLayoutDrawable(category, recipe, runtime.getJeiHelpers().getFocusFactory().getEmptyFocusGroup())
            .orElseThrow();
        var handler = runtime.getRecipeTransferManager().getRecipeTransferHandler(menu, category).orElseThrow();
        var error = handler.transferRecipe(menu, recipe, layout.getRecipeSlotsView(), player, false, false);
        check(rejection + "_returns_the_expected_missing_slot_count", error != null
            && error.getType() == IRecipeTransferError.Type.USER_FACING && error.getMissingCountHint() == missing);
        signal(rejection);
        stage = next;
    }

    private void transfer(Fixture fixture) throws Exception {
        if (!(minecraft.screen instanceof RecipesGui gui)) return;
        var frame = visibleRecipe(gui, fixture);
        if (frame == null || !frame.transferButton().isInitialized()) return;
        var slots = frame.recipeLayout().getRecipeSlotsView().getSlotViews(RecipeIngredientRole.INPUT);
        check(fixture.name() + "_actual_jei_layout_shows_one_rocket_and_the_exact_fluid", slots.size() == 2
            && slots.get(0).getDisplayedItemStack().filter(s -> s.is(Items.FIREWORK_ROCKET) && s.getCount() == 1).isPresent()
            && slots.get(1).getDisplayedIngredient(ForgeTypes.FLUID_STACK)
                .filter(f -> f.getAmount() == 1_000 && fixture.fluid().equals(ForgeRegistries.FLUIDS.getKey(f.getFluid()))).isPresent());
        check(fixture.name() + "_jei_transfer_reports_all_inputs_available", frame.transferButton().getMissingCountHint() == 0);
        capture(fixture.name() + "-weather-jei");
        var rect = frame.recipeLayout().getRect();
        var button = frame.recipeLayout().getRecipeTransferButtonArea();
        double x = rect.getX() + button.getX() + button.getWidth() / 2.0;
        double y = rect.getY() + button.getY() + button.getHeight() / 2.0;
        check(fixture.name() + "_uses_the_actual_jei_transfer_button",
            frame.transferButton().isMouseOver(x, y) && gui.mouseClicked(x, y, 0) && gui.mouseReleased(x, y, 0));
        stage = Stage.INSERTED;
    }

    private RecipeLayoutWithButtons<?> visibleRecipe(RecipesGui gui, Fixture fixture) {
        var area = gui.getArea();
        for (int y = area.getY(); y < area.getY() + area.getHeight(); y += 8) {
            for (int x = area.getX(); x < area.getX() + area.getWidth(); x += 8) {
                if (JeiLayoutAccess.underMouse(gui, x, y).orElse(null) instanceof RecipeLayoutWithButtons<?> layout
                    && layout.recipeLayout().getRecipe() instanceof WeatherChangeRecipe recipe
                    && fixture.recipe().equals(recipe.getId())) return layout;
            }
        }
        return null;
    }

    private static boolean tankMatches(WeatherObeliskMenu menu, Fixture fixture, int amount) {
        FluidStack fluid = menu.getFluidTank().contents();
        return fluid.getAmount() == amount && fixture.fluid().equals(ForgeRegistries.FLUIDS.getKey(fluid.getFluid()));
    }

    private static List<WeatherChangeRecipe> recipes(IJeiRuntime runtime) {
        return runtime.getRecipeManager().createRecipeLookup(WeatherChangeCategory.TYPE).get().toList();
    }

    private void open(Fixture fixture) {
        var player = minecraft.player;
        var pos = fixture.position();
        if (!ready(fixture.name()) || minecraft.screen != null
            || player.distanceToSqr(Vec3.atBottomCenterOf(pos.south(2))) > 0.14) return;
        if (settleBy < 0) settleBy = player.tickCount + 6;
        if (player.tickCount < settleBy) return;
        settleBy = -1;
        player.getInventory().selected = 8;
        var hit = new BlockHitResult(Vec3.atCenterOf(pos).relative(Direction.SOUTH, 0.5), Direction.SOUTH, pos, false);
        check(fixture.name() + "_opens_the_native_weather_obelisk",
            minecraft.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit).consumesAction());
        stage = index == 0 ? Stage.SHORT_EMPTY : Stage.RECIPE;
    }

    private void connect() {
        if (!(minecraft.screen instanceof TitleScreen) || minecraft.getOverlay() != null) return;
        if (titleAt == 0) titleAt = System.nanoTime();
        if (System.nanoTime() - titleAt < 2_000_000_000L) return;
        String address = System.getProperty("enderio.clientqa.server");
        ConnectScreen.startConnecting(new TitleScreen(), minecraft, ServerAddress.parseString(address),
            new ServerData("Ender IO Weather JEI", address, true), false);
        stage = Stage.JOIN;
    }

    private boolean ready(String name) {
        return Files.exists(output.resolve("server-" + name));
    }

    private void signal(String name) throws Exception {
        var directory = output.resolve("signals");
        Files.createDirectories(directory);
        Files.writeString(directory.resolve(name + ".json"), "{}");
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
        ClientSmoke.complete("client_weather_jei", result);
    }
}
