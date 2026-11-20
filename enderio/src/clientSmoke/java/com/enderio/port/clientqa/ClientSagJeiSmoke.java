package com.enderio.port.clientqa;

import com.enderio.core.common.datamap.ClientDataMaps;
import com.enderio.enderio.api.components.GrindingBallData;
import com.enderio.enderio.client.content.machines.gui.screen.SagMillScreen;
import com.enderio.enderio.compat.jei_machines_to_merge.category.SagMillCategory;
import com.enderio.enderio.content.machines.sag_mill.SagMillMenu;
import com.enderio.enderio.content.machines.sag_mill.SagMillingRecipe;
import com.enderio.enderio.init.EIOBlocks;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.runtime.IJeiRuntime;
import mezz.jei.common.gui.JeiTooltip;
import mezz.jei.gui.recipes.RecipeLayoutWithButtons;
import mezz.jei.gui.recipes.RecipesGui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
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
import net.minecraft.world.inventory.ClickType;
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

final class ClientSagJeiSmoke {
    private enum Stage { TITLE, JOIN, OPEN, STOCK, TRANSFER, MANUAL, TRANSFERRED, RELOAD, RELOADED, DONE }

    private static final BlockPos MILL = new BlockPos(3131, 80, 8);
    private static final ResourceLocation ORE = ResourceLocation.parse("enderio:sag_milling/iron_ore");
    private static final ResourceLocation INGOT = ResourceLocation.parse("enderio:sag_milling/iron");

    private final Minecraft minecraft = Minecraft.getInstance();
    private final String address = System.getProperty("enderio.clientqa.server");
    private final Path output = Path.of(System.getProperty("enderio.clientqa.output"));
    private final Map<String, Boolean> checks = new LinkedHashMap<>();
    private final Map<String, Object> captures = new LinkedHashMap<>();
    private final Map<String, String> energy = new LinkedHashMap<>();
    private final long started = System.nanoTime();
    private Stage stage = Stage.TITLE;
    private long titleAt;
    private int useAt = -1;

    ClientSagJeiSmoke() {
        MinecraftForge.EVENT_BUS.addListener(this::render);
    }

    private void render(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage == Stage.DONE) return;
        try {
            if (minecraft.screen instanceof DisconnectedScreen || System.nanoTime() - started > 130_000_000_000L) {
                throw new AssertionError("SAG Mill JEI probe stopped during " + stage);
            }
            if (stage == Stage.TITLE) {
                if (!(minecraft.screen instanceof TitleScreen) || minecraft.getOverlay() != null) return;
                if (titleAt == 0) titleAt = System.nanoTime();
                if (System.nanoTime() - titleAt < 2_000_000_000L) return;
                ConnectScreen.startConnecting(new TitleScreen(), minecraft, ServerAddress.parseString(address),
                    new ServerData("SAG Mill recipe QA", address, true), false);
                stage = Stage.JOIN;
                return;
            }
            var player = minecraft.player;
            if (player == null || minecraft.level == null) return;
            var runtime = ClientSoulEngineFuelsSmoke.JeiProbe.runtime();
            switch (stage) {
                case JOIN -> {
                    if (runtime == null || minecraft.screen != null || minecraft.getToasts().getToast(SystemToast.class,
                        SystemToast.SystemToastIds.UNSECURE_SERVER_WARNING) != null) return;
                    check("dedicated_survival_tcp", !player.isCreative() && !player.isSpectator()
                        && minecraft.getSingleplayerServer() == null && !minecraft.getConnection().getConnection().isMemoryConnection());
                    signal("connected");
                    stage = Stage.OPEN;
                }
                case OPEN -> {
                    if (!Files.exists(output.resolve("server-ready")) || player.distanceToSqr(3131.5, 80, 11.5) > 0.15) return;
                    if (useAt < 0) useAt = player.tickCount + 8;
                    if (player.tickCount < useAt) return;
                    var hit = new BlockHitResult(Vec3.atCenterOf(MILL).relative(Direction.SOUTH, 0.5), Direction.SOUTH, MILL, false);
                    check("native_sag_mill_interaction", minecraft.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit).consumesAction());
                    stage = Stage.STOCK;
                }
                case STOCK -> {
                    if (runtime == null || !(minecraft.screen instanceof SagMillScreen screen)
                        || ClientDataMaps.get(GrindingBallData.DATA_MAP_TYPE, Items.FLINT) == null) return;
                    verifyStock(runtime);
                    check("jei_registers_sag_mill_transfer", runtime.getRecipeTransferManager()
                        .getRecipeTransferHandler(screen.getMenu(), runtime.getRecipeManager().getRecipeCategory(SagMillCategory.TYPE)).isPresent());
                    showOre(runtime, Items.FLINT);
                    stage = Stage.TRANSFER;
                }
                case TRANSFER -> {
                    if (!(minecraft.screen instanceof RecipesGui gui)) return;
                    var layout = visibleRecipe(gui, ORE);
                    if (layout == null || !layout.transferButton().isInitialized()) return;
                    check("real_viewer_displays_stock_flint", layout.getRecipeLayout().getRecipeSlotsView()
                        .getSlotViews(RecipeIngredientRole.CATALYST).get(0).getDisplayedItemStack().orElseThrow().is(Items.FLINT));
                    capture("iron-ore-flint");
                    if (Files.exists(output.resolve("server-client-only"))) {
                        verifyClientOnlyTransfer(runtime);
                        gui.onClose();
                        stage = Stage.MANUAL;
                        return;
                    }
                    check("clicks_actual_jei_transfer_button", clickTransfer(gui, layout));
                    stage = Stage.TRANSFERRED;
                }
                case MANUAL -> {
                    if (!(minecraft.screen instanceof SagMillScreen screen)) return;
                    var menu = screen.getMenu();
                    var source = menu.slots.stream().filter(slot -> slot.container == player.getInventory()
                        && slot.getItem().is(Items.DEEPSLATE_IRON_ORE)).findFirst().orElseThrow();
                    minecraft.gameMode.handleInventoryMouseClick(menu.containerId, source.index, 1, ClickType.PICKUP, player);
                    minecraft.gameMode.handleInventoryMouseClick(menu.containerId, SagMillMenu.INPUTS_INDEX, 0, ClickType.PICKUP, player);
                    stage = Stage.TRANSFERRED;
                }
                case TRANSFERRED -> {
                    if (!(minecraft.screen instanceof SagMillScreen screen)) return;
                    var input = screen.getMenu().getSlot(1).getItem();
                    if (input.isEmpty()) return;
                    check("native_menu_receives_one_tagged_deepslate_ore", input.is(Items.DEEPSLATE_IRON_ORE)
                        && input.getCount() == 1 && input.hasTag() && input.getTag().getInt("PortSAG") == 158);
                    check("transfer_keeps_tagged_grinding_ball_in_player_inventory_and_machine_slot_empty",
                        !screen.getMenu().getSlot(6).hasItem() && player.getInventory().items.stream()
                            .anyMatch(stack -> stack.is(Items.FLINT) && stack.getCount() == 2 && stack.hasTag()
                                && stack.getTag().getInt("PortSAG") == 158));
                    capture("ore-transferred");
                    player.closeContainer();
                    signal("transferred");
                    stage = Stage.RELOAD;
                }
                case RELOAD -> {
                    if (runtime == null || ClientDataMaps.get(GrindingBallData.DATA_MAP_TYPE, Items.FEATHER) == null) return;
                    verifyReload(runtime);
                    showOre(runtime, Items.FEATHER);
                    stage = Stage.RELOADED;
                }
                case RELOADED -> {
                    if (!(minecraft.screen instanceof RecipesGui gui)) return;
                    var layout = visibleRecipe(gui, ORE);
                    if (layout == null) return;
                    check("real_viewer_displays_authored_feather", layout.getRecipeLayout().getRecipeSlotsView()
                        .getSlotViews(RecipeIngredientRole.CATALYST).get(0).getDisplayedItemStack().orElseThrow().is(Items.FEATHER));
                    capture("iron-ore-feather");
                    finish(null);
                }
                default -> { }
            }
        } catch (Throwable failure) {
            failure.printStackTrace();
            finish(failure);
        }
    }

    private void verifyStock(IJeiRuntime runtime) {
        var manager = runtime.getRecipeManager();
        var ore = recipe(runtime, ORE);
        check("forge_ore_tag_lookup_includes_both_iron_ores", matches(runtime, RecipeIngredientRole.INPUT, Items.IRON_ORE, ORE)
            && matches(runtime, RecipeIngredientRole.INPUT, Items.DEEPSLATE_IRON_ORE, ORE));
        check("jei_indexes_both_raw_iron_and_cobblestone_results", matches(runtime, RecipeIngredientRole.OUTPUT, Items.RAW_IRON, ORE)
            && matches(runtime, RecipeIngredientRole.OUTPUT, Items.COBBLESTONE, ORE));
        check("machine_is_registered_as_a_recipe_catalyst", manager.createRecipeCatalystLookup(SagMillCategory.TYPE).get()
            .flatMap(ingredient -> ingredient.getItemStack().stream()).anyMatch(stack -> stack.is(EIOBlocks.SAG_MILL.get().asItem())));
        var layout = layout(runtime, ore, List.of(focus(runtime, RecipeIngredientRole.CATALYST, Items.FLINT)));
        check("raw_iron_main_and_two_stock_bonus_slots_have_exact_tooltips",
            slotMatches(layout, 1, Items.RAW_IRON, null)
                && slotMatches(layout, 22, Items.RAW_IRON, "Chance: 33% (modified by grinding ball)")
                && slotMatches(layout, 43, Items.COBBLESTONE, "Chance: 15% (modified by grinding ball)"));
        verifyBall(layout, Items.FLINT, "2,040 µI", "Main Output 120%", "Bonus Output 125%", "Power Use 85%");
        var ingot = layout(runtime, recipe(runtime, INGOT), List.of());
        check("no_bonus_recipe_cannot_use_grinding_balls", ingot.getRecipeSlotsView().getSlotViews(RecipeIngredientRole.CATALYST)
            .stream().allMatch(slot -> slot.getItemStacks().findAny().isEmpty()));
        check("no_bonus_recipe_displays_the_full_2400_ui", "2,400 µI".equals(readEnergy(ingot, "iron-ingot")));
        check("initial_flint_usage_indexes_only_recipes_that_allow_it", matches(runtime, RecipeIngredientRole.CATALYST, Items.FLINT, ORE)
            && !matches(runtime, RecipeIngredientRole.CATALYST, Items.FLINT, INGOT));
    }

    private void verifyReload(IJeiRuntime runtime) {
        var ore = recipe(runtime, ORE);
        var layout = layout(runtime, ore, List.of(focus(runtime, RecipeIngredientRole.CATALYST, Items.FEATHER)));
        verifyBall(layout, Items.FEATHER, "1,200 µI", "Main Output 200%", "Bonus Output 300%", "Power Use 50%");
        check("live_recipe_layout_excludes_removed_flint", layout.getRecipeSlotsView().getSlotViews(RecipeIngredientRole.CATALYST)
            .get(0).getItemStacks().noneMatch(stack -> stack.is(Items.FLINT)));
        check("live_lookup_indexes_new_ball_and_removes_old_ball", matches(runtime, RecipeIngredientRole.CATALYST, Items.FEATHER, ORE)
            && !matches(runtime, RecipeIngredientRole.CATALYST, Items.FLINT, ORE));
    }

    private void verifyClientOnlyTransfer(IJeiRuntime runtime) {
        var menu = (SagMillMenu) minecraft.player.containerMenu;
        var ore = recipe(runtime, ORE);
        var layout = layout(runtime, ore, List.of());
        var handler = runtime.getRecipeTransferManager().getRecipeTransferHandler(menu, layout.getRecipeCategory()).orElseThrow();
        var error = handler.transferRecipe(menu, ore, layout.getRecipeSlotsView(), minecraft.player, false, false);
        var tooltip = new JeiTooltip();
        if (error != null) error.getTooltip(tooltip);
        String expected = Component.translatable("jei.tooltip.error.recipe.transfer.no.server").getString();
        check("client_only_jei_explains_the_server_dependency_for_transfer",
            tooltipText(tooltip).contains(expected) && !menu.getSlot(SagMillMenu.INPUTS_INDEX).hasItem());
    }

    private void verifyBall(IRecipeLayoutDrawable<SagMillingRecipe> layout, Item item, String cost, String... lines) {
        var slot = layout.getRecipeSlotUnderMouse(82, 20).orElseThrow();
        var itemTooltip = new JeiTooltip();
        slot.getTooltip(itemTooltip);
        var energyTooltip = new JeiTooltip();
        layout.getRecipeCategory().getTooltip(energyTooltip, layout.getRecipe(), layout.getRecipeSlotsView(), 84, 48);
        check("ball_and_exact_rendered_energy_" + cost, slot.getDisplayedItemStack().orElseThrow().is(item)
            && tooltipText(itemTooltip).containsAll(List.of(lines)) && cost.equals(readEnergy(layout, cost))
            && tooltipText(energyTooltip).contains("A unit of energy, equivalent to FE."));
    }

    private String readEnergy(IRecipeLayoutDrawable<SagMillingRecipe> layout, String key) {
        String value = renderedEnergy(minecraft, layout);
        energy.put(key, value);
        return value;
    }

    static String renderedEnergy(Minecraft minecraft, IRecipeLayoutDrawable<SagMillingRecipe> layout) {
        var text = new EnergyText(minecraft);
        layout.getRecipeCategory().draw(layout.getRecipe(), layout.getRecipeSlotsView(), text, 84, 48);
        return text.value;
    }

    private static boolean slotMatches(IRecipeLayoutDrawable<SagMillingRecipe> layout, int x, Item item, String chance) {
        var slot = layout.getRecipeSlotUnderMouse(x + 8, 56).orElseThrow();
        var tooltip = new JeiTooltip();
        slot.getTooltip(tooltip);
        var lines = tooltipText(tooltip);
        return slot.getRole() == RecipeIngredientRole.OUTPUT
            && slot.getDisplayedItemStack().filter(stack -> stack.is(item) && stack.getCount() == 1).isPresent()
            && (chance == null ? lines.stream().noneMatch(line -> line.startsWith("Chance:")) : lines.contains(chance));
    }

    private static List<String> tooltipText(JeiTooltip tooltip) {
        return tooltip.toLegacyToComponents().stream().map(Component::getString).toList();
    }

    static boolean matches(IJeiRuntime runtime, RecipeIngredientRole role, Item item, ResourceLocation id) {
        return runtime.getRecipeManager().createRecipeLookup(SagMillCategory.TYPE)
            .limitFocus(List.of(focus(runtime, role, item))).get().anyMatch(recipe -> recipe.getId().equals(id));
    }

    static IFocus<ItemStack> focus(IJeiRuntime runtime, RecipeIngredientRole role, Item item) {
        return runtime.getJeiHelpers().getFocusFactory().createFocus(role, VanillaTypes.ITEM_STACK, new ItemStack(item));
    }

    static SagMillingRecipe recipe(IJeiRuntime runtime, ResourceLocation id) {
        return runtime.getRecipeManager().createRecipeLookup(SagMillCategory.TYPE).get()
            .filter(recipe -> recipe.getId().equals(id)).findFirst().orElseThrow();
    }

    private static IRecipeLayoutDrawable<SagMillingRecipe> layout(IJeiRuntime runtime, SagMillingRecipe recipe, List<IFocus<?>> focus) {
        var manager = runtime.getRecipeManager();
        var layout = manager.createRecipeLayoutDrawable(manager.getRecipeCategory(SagMillCategory.TYPE), recipe,
            runtime.getJeiHelpers().getFocusFactory().createFocusGroup(focus)).orElseThrow();
        layout.setPosition(0, 0);
        return layout;
    }

    private static void showOre(IJeiRuntime runtime, Item ball) {
        runtime.getRecipesGui().showRecipes(runtime.getRecipeManager().getRecipeCategory(SagMillCategory.TYPE),
            List.of(recipe(runtime, ORE)), List.of(focus(runtime, RecipeIngredientRole.CATALYST, ball)));
    }

    static RecipeLayoutWithButtons<?> visibleRecipe(RecipesGui gui, ResourceLocation id) {
        var area = gui.getArea();
        for (int y = area.getY(); y < area.getY() + area.getHeight(); y += 8) {
            for (int x = area.getX(); x < area.getX() + area.getWidth(); x += 8) {
                if (gui.getRecipeLayoutUnderMouse(x, y).orElse(null) instanceof RecipeLayoutWithButtons<?> layout
                    && layout.getRecipeLayout().getRecipe() instanceof SagMillingRecipe recipe && id.equals(recipe.getId())) return layout;
            }
        }
        return null;
    }

    static boolean clickTransfer(RecipesGui gui, RecipeLayoutWithButtons<?> layout) {
        var rect = layout.getRecipeLayout().getRect();
        var button = layout.getRecipeLayout().getSideButtonArea(0);
        double x = rect.getX() + button.getX() + button.getWidth() / 2.0;
        double y = rect.getY() + button.getY() + button.getHeight() / 2.0;
        return layout.transferButton().isMouseOver(x, y) && gui.mouseClicked(x, y, 0) && gui.mouseReleased(x, y, 0);
    }

    private void signal(String name) throws IOException {
        var signals = output.resolve("signals");
        Files.createDirectories(signals);
        Files.writeString(signals.resolve(name + ".json"), "{}");
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
        result.put("checks", checks);
        result.put("captures", captures);
        result.put("energy", energy);
        result.put("passed", failure == null);
        if (failure != null) result.put("error", failure.toString());
        ClientSmoke.complete("client_sag_jei", result);
    }

    private static final class EnergyText extends GuiGraphics {
        private String value;

        private EnergyText(Minecraft minecraft) {
            super(minecraft, minecraft.renderBuffers().bufferSource());
        }

        @Override
        public int drawString(Font font, Component text, int x, int y, int color, boolean shadow) {
            value = text.getString();
            return font.width(text);
        }
    }
}
