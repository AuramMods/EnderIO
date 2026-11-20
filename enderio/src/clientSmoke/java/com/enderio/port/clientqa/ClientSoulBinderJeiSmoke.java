package com.enderio.port.clientqa;

import com.enderio.enderio.api.soul.Soul;
import com.enderio.enderio.api.soul.SoulBoundUtils;
import com.enderio.enderio.client.content.machines.gui.screen.SoulBinderScreen;
import com.enderio.enderio.compat.jei_machines_to_merge.category.SoulBindingCategory;
import com.enderio.enderio.content.machines.solar_panel.SolarPanelTier;
import com.enderio.enderio.content.machines.soul_binder.SoulBinderMenu;
import com.enderio.enderio.content.machines.soul_binder.SoulBindingRecipe;
import com.enderio.enderio.content.tools.vials.SoulVialItem;
import com.enderio.enderio.foundation.souldata.EngineSoul;
import com.enderio.enderio.init.EIOBlocks;
import com.enderio.enderio.init.EIOItems;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.runtime.IJeiRuntime;
import mezz.jei.common.gui.JeiTooltip;
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
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
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
import java.util.function.Supplier;

final class ClientSoulBinderJeiSmoke {
    private enum Stage { TITLE, JOIN, OPEN, MENU, RECIPE, MANUAL, QUEUED, VERIFIED, DONE }

    private record Fixture(String name, int x, ResourceLocation id, Supplier<? extends ItemLike> input, Supplier<? extends ItemLike> result,
                           EntityType<?> soul, String soulName, EntityType<?> decoy,
                           int experience, int energy, int initialFluid) {
        BlockPos pos() { return new BlockPos(x, 80, 8); }
    }

    private static final List<Fixture> EXACT = List.of(
        new Fixture("crystal", 4834, ResourceLocation.parse("enderio:soulbinding/ender_crystal"),
            EIOItems.VIBRANT_CRYSTAL::get, EIOItems.ENDER_CRYSTAL::get, EntityType.ENDERMAN, "West", EntityType.ZOMBIE, 6, 76800, 1439),
        new Fixture("panel", 4842, ResourceLocation.parse("enderio:soulbinding/energetic_photovoltaic_module"),
            () -> EIOBlocks.SOLAR_PANELS.get(SolarPanelTier.ENERGETIC).get(), () -> EIOBlocks.SOLAR_PANELS.get(SolarPanelTier.ENERGETIC).get(),
            EntityType.PHANTOM, "Sky", EntityType.ZOMBIE, 8, 12800, 2239));
    private static final ResourceLocation ENGINE_RULE = ResourceLocation.parse("enderio:soulbinding/soul_engine");
    private static final List<Fixture> RULES = List.of(
        new Fixture("animal", 4850, ResourceLocation.parse("enderio:soulbinding/animal_token"),
            EIOItems.SOULARIUM_BALL::get, EIOItems.ANIMAL_TOKEN::get, EntityType.COW, "Meadow", EntityType.ZOMBIE, 1, 12800, 139),
        new Fixture("stock", 4858, ENGINE_RULE,
            EIOBlocks.SOUL_ENGINE::get, EIOBlocks.SOUL_ENGINE::get, EntityType.BLAZE, "Ember", EntityType.COW, 5, 188000, 1099),
        new Fixture("reloaded", 4866, ENGINE_RULE,
            EIOBlocks.SOUL_ENGINE::get, EIOBlocks.SOUL_ENGINE::get, EntityType.COW, "Clover", EntityType.BLAZE, 5, 188000, 1099));

    private final Minecraft minecraft = Minecraft.getInstance();
    private final Path output = Path.of(System.getProperty("enderio.clientqa.output"));
    private final Map<String, Boolean> checks = new LinkedHashMap<>();
    private final Map<String, Object> captures = new LinkedHashMap<>();
    private final long started = System.nanoTime();
    private final List<Fixture> fixtures;
    private Stage stage = Stage.TITLE;
    private long titleAt;
    private int index;

    ClientSoulBinderJeiSmoke(boolean rules) {
        fixtures = rules ? RULES : EXACT;
        MinecraftForge.EVENT_BUS.addListener(this::render);
    }

    private void render(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage == Stage.DONE) return;
        try {
            if (minecraft.screen instanceof DisconnectedScreen || System.nanoTime() - started > 190_000_000_000L) {
                throw new AssertionError("Soul Binder JEI stopped during " + stage + " for fixture " + index);
            }
            if (stage == Stage.TITLE) {
                connect();
                return;
            }
            var player = minecraft.player;
            var runtime = ClientSoulEngineFuelsSmoke.JeiProbe.runtime();
            if (player == null || minecraft.level == null || runtime == null) return;
            var fixture = fixtures.get(index);
            switch (stage) {
                case JOIN -> {
                    if (minecraft.screen != null || minecraft.getToasts().getToast(SystemToast.class,
                        SystemToast.SystemToastIds.UNSECURE_SERVER_WARNING) != null) return;
                    if (runtime.getRecipeManager().createRecipeLookup(SoulBindingCategory.TYPE).get().noneMatch(r -> fixture.id().equals(r.getId()))) return;
                    check("real_dedicated_survival_menu", !player.isCreative() && !player.isSpectator()
                        && minecraft.getSingleplayerServer() == null && !minecraft.getConnection().getConnection().isMemoryConnection());
                    check("binder_is_registered_as_catalyst", runtime.getRecipeManager().createRecipeCatalystLookup(SoulBindingCategory.TYPE).get()
                        .flatMap(i -> i.getItemStack().stream()).anyMatch(s -> s.is(EIOBlocks.SOUL_BINDER.asItem())));
                    signal("connected");
                    stage = Stage.OPEN;
                }
                case OPEN -> {
                    var pos = fixture.pos();
                    if (!ready(fixture, "initial") || minecraft.screen != null || player.distanceToSqr(Vec3.atBottomCenterOf(pos.south(2))) > 0.15) return;
                    var hit = new BlockHitResult(Vec3.atCenterOf(pos).relative(Direction.SOUTH, 0.5), Direction.SOUTH, pos, false);
                    check(fixture.name() + "_opens_native_binder", minecraft.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit).consumesAction());
                    stage = Stage.MENU;
                }
                case MENU -> {
                    if (!(minecraft.screen instanceof SoulBinderScreen screen)) return;
                    var menu = screen.getMenu();
                    if (menu.getFluidTank().contents().getAmount() != fixture.initialFluid()
                        || !namedSoul(player.getInventory().getItem(3), fixture)
                        || !player.getInventory().getItem(5).is(fixture.input().get().asItem())) return;
                    if (fixture.id().equals(ENGINE_RULE)
                        && (EngineSoul.ENGINE.matches(fixture.soul()).isEmpty() || EngineSoul.ENGINE.matches(fixture.decoy()).isPresent())) return;
                    showRecipe(runtime, fixture, menu);
                    stage = Stage.RECIPE;
                }
                case RECIPE -> transfer(runtime, fixture);
                case MANUAL -> {
                    if (!(minecraft.screen instanceof SoulBinderScreen screen)) return;
                    var menu = screen.getMenu();
                    var vial = menu.slots.stream().filter(slot -> slot.container == player.getInventory()
                        && namedSoul(slot.getItem(), fixture)).findFirst().orElseThrow();
                    var material = menu.slots.stream().filter(slot -> slot.container == player.getInventory()
                        && slot.getItem().is(fixture.input().get().asItem())).findFirst().orElseThrow();
                    clickSlot(menu, vial.index, 0);
                    clickSlot(menu, SoulBinderMenu.INPUTS_INDEX, 0);
                    clickSlot(menu, material.index, 1);
                    clickSlot(menu, SoulBinderMenu.INPUTS_INDEX + 1, 0);
                    stage = Stage.QUEUED;
                }
                case QUEUED -> {
                    if (!(minecraft.screen instanceof SoulBinderScreen screen)) return;
                    var menu = screen.getMenu();
                    if (menu.getSlot(1).getItem().isEmpty() || menu.getSlot(2).getItem().isEmpty()) return;
                    check(fixture.name() + "_moves_the_original_named_vial_and_one_tagged_ingredient",
                        namedSoul(menu.getSlot(1).getItem(), fixture) && menu.getSlot(1).getItem().getCount() == 1
                            && menu.getSlot(2).getItem().is(fixture.input().get().asItem()) && menu.getSlot(2).getItem().getCount() == 1
                            && menu.getSlot(2).getItem().getTag() != null && menu.getSlot(2).getItem().getTag().getInt("PortBinder") == 227);
                    check(fixture.name() + "_keeps_empty_and_wrong_souls_and_preserves_fluid",
                        SoulBoundUtils.getBoundSoul(player.getInventory().getItem(1)).entityType() == fixture.decoy()
                            && player.getInventory().getItem(2).is(EIOItems.SOUL_VIAL.get())
                            && SoulBoundUtils.getBoundSoul(player.getInventory().getItem(2)).isEmpty()
                            && menu.getFluidTank().contents().getAmount() == fixture.initialFluid());
                    capture(fixture.name() + "-queued");
                    signal(fixture.name() + "-queued");
                    stage = Stage.VERIFIED;
                }
                case VERIFIED -> {
                    if (!ready(fixture, "verified")) return;
                    player.closeContainer();
                    index++;
                    if (index == fixtures.size()) finish(null);
                    else stage = Stage.OPEN;
                }
                default -> { }
            }
        } catch (Throwable failure) {
            failure.printStackTrace();
            finish(failure);
        }
    }

    private void showRecipe(IJeiRuntime runtime, Fixture fixture, SoulBinderMenu menu) {
        var mgr = runtime.getRecipeManager();
        var recipe = mgr.createRecipeLookup(SoulBindingCategory.TYPE).get().filter(r -> fixture.id().equals(r.getId())).findFirst().orElseThrow();
        var category = mgr.getRecipeCategory(SoulBindingCategory.TYPE);
        var focuses = runtime.getJeiHelpers().getFocusFactory();
        var named = minecraft.player.getInventory().getItem(3);
        var focus = focuses.createFocus(RecipeIngredientRole.INPUT, VanillaTypes.ITEM_STACK, named);
        check(fixture.name() + "_base_item_finds_the_recipe",
            finds(runtime, fixture, RecipeIngredientRole.INPUT, minecraft.player.getInventory().getItem(5)));
        check(fixture.name() + "_canonical_vial_finds_the_exact_recipe", finds(runtime, fixture, RecipeIngredientRole.INPUT, SoulVialItem.forSoul(Soul.of(fixture.soul()))));
        check(fixture.name() + "_named_vial_finds_the_same_exact_recipe", finds(runtime, fixture, RecipeIngredientRole.INPUT, named));
        check(fixture.name() + "_wrong_and_empty_vials_do_not_find_the_exact_recipe",
            !finds(runtime, fixture, RecipeIngredientRole.INPUT, minecraft.player.getInventory().getItem(1))
                && !finds(runtime, fixture, RecipeIngredientRole.INPUT, minecraft.player.getInventory().getItem(2)));
        check(fixture.name() + "_recipe_has_the_stock_xp_and_fe_cost", recipe.experience() == fixture.experience() && recipe.energy() == fixture.energy());
        if (fixture.name().equals("panel") || fixture.id().equals(ENGINE_RULE)) {
            var view = mgr.createRecipeLayoutDrawable(category, recipe, focuses.createFocusGroup(List.of(focus))).orElseThrow().getRecipeSlotsView();
            var bound = view.getSlotViews(RecipeIngredientRole.OUTPUT).get(1).getDisplayedItemStack().orElseThrow();
            check(fixture.name() + "_focused_bound_output_preview_retains_the_named_soul", namedSoul(bound, fixture));
            check(fixture.name() + "_bound_output_finds_the_recipe", finds(runtime, fixture, RecipeIngredientRole.OUTPUT, bound));
            var outputFocus = focuses.createFocus(RecipeIngredientRole.OUTPUT, VanillaTypes.ITEM_STACK, bound);
            var outputView = mgr.createRecipeLayoutDrawable(category, recipe, focuses.createFocusGroup(List.of(outputFocus)))
                .orElseThrow().getRecipeSlotsView();
            check(fixture.name() + "_bound_output_focus_displays_the_named_source_vial",
                outputView.getSlotViews(RecipeIngredientRole.INPUT).get(0).getDisplayedItemStack()
                    .filter(stack -> stack.is(EIOItems.SOUL_VIAL.get()) && namedSoul(stack, fixture)).isPresent());
        }
        check(fixture.name() + "_registered_native_transfer_handler_exists", runtime.getRecipeTransferManager().getRecipeTransferHandler(menu, category).isPresent());
        runtime.getRecipesGui().showRecipes(category, List.of(recipe), List.of());
    }

    private static boolean finds(IJeiRuntime runtime, Fixture fixture, RecipeIngredientRole role, ItemStack value) {
        var focus = runtime.getJeiHelpers().getFocusFactory().createFocus(role, VanillaTypes.ITEM_STACK, value);
        return runtime.getRecipeManager().createRecipeLookup(SoulBindingCategory.TYPE).limitFocus(List.of(focus)).get()
            .anyMatch(recipe -> fixture.id().equals(recipe.getId()));
    }

    private void transfer(IJeiRuntime runtime, Fixture fixture) throws IOException {
        if (!(minecraft.screen instanceof RecipesGui gui)) return;
        var area = gui.getArea();
        for (int y = area.getY(); y < area.getY() + area.getHeight(); y += 8) {
            for (int x = area.getX(); x < area.getX() + area.getWidth(); x += 8) {
                if (!(gui.getRecipeLayoutUnderMouse(x, y).orElse(null) instanceof RecipeLayoutWithButtons<?> layout)
                    || !(layout.getRecipeLayout().getRecipe() instanceof SoulBindingRecipe recipe) || !fixture.id().equals(recipe.getId())
                    || !layout.transferButton().isInitialized()) continue;
                var view = layout.getRecipeLayout().getRecipeSlotsView();
                var inputs = view.getSlotViews(RecipeIngredientRole.INPUT);
                var outputs = view.getSlotViews(RecipeIngredientRole.OUTPUT);
                check(fixture.name() + "_viewer_uses_the_required_soul_and_shows_a_returned_empty_vial", inputs.size() == 2 && outputs.size() == 2
                    && inputs.get(0).getDisplayedItemStack().isPresent()
                    && inputs.get(0).getItemStacks().anyMatch(stack -> SoulBoundUtils.getBoundSoul(stack).entityType() == fixture.soul())
                    && inputs.get(0).getItemStacks().noneMatch(stack -> SoulBoundUtils.getBoundSoul(stack).entityType() == fixture.decoy())
                    && outputs.get(0).getDisplayedItemStack().filter(i -> i.is(EIOItems.SOUL_VIAL.get()) && SoulBoundUtils.getBoundSoul(i).isEmpty()).isPresent()
                    && outputs.get(1).getDisplayedItemStack().filter(i -> i.is(fixture.result().get().asItem())).isPresent());
                capture(fixture.name() + "-recipe");
                if (Files.exists(output.resolve("server-client-only"))) {
                    var menu = (SoulBinderMenu) minecraft.player.containerMenu;
                    var category = runtime.getRecipeManager().getRecipeCategory(SoulBindingCategory.TYPE);
                    var handler = runtime.getRecipeTransferManager().getRecipeTransferHandler(menu, category).orElseThrow();
                    var error = handler.transferRecipe(menu, recipe, view, minecraft.player, false, false);
                    var tooltip = new JeiTooltip();
                    if (error != null) error.getTooltip(tooltip);
                    var expected = Component.translatable("jei.tooltip.error.recipe.transfer.no.server").getString();
                    check(fixture.name() + "_jei_explains_the_server_requirement_for_the_transfer_button",
                        tooltip.toLegacyToComponents().stream().map(Component::getString).anyMatch(expected::equals));
                    gui.onClose();
                    stage = Stage.MANUAL;
                    return;
                }
                check(fixture.name() + "_unfocused_jei_button_accepts_the_named_vial", layout.transferButton().getMissingCountHint() == 0);
                check(fixture.name() + "_uses_the_actual_jei_transfer_button", ClientSagJeiSmoke.clickTransfer(gui, layout));
                stage = Stage.QUEUED;
                return;
            }
        }
    }

    private void clickSlot(SoulBinderMenu menu, int slot, int button) {
        minecraft.gameMode.handleInventoryMouseClick(menu.containerId, slot, button, ClickType.PICKUP, minecraft.player);
    }

    private static boolean namedSoul(ItemStack stack, Fixture fixture) {
        var soul = SoulBoundUtils.getBoundSoul(stack);
        return soul.entityType() == fixture.soul() && soul.entityTag().getString("CustomName").contains(fixture.soulName())
            && soul.entityTag().getFloat("Health") == 16;
    }

    private void connect() {
        if (!(minecraft.screen instanceof TitleScreen) || minecraft.getOverlay() != null) return;
        if (titleAt == 0) titleAt = System.nanoTime();
        if (System.nanoTime() - titleAt < 2_000_000_000L) return;
        String address = System.getProperty("enderio.clientqa.server");
        ConnectScreen.startConnecting(new TitleScreen(), minecraft, ServerAddress.parseString(address),
            new ServerData("Ender IO Soul Binder JEI", address, true), false);
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
        ClientSmoke.complete("client_soul_binder_jei", result);
    }
}
