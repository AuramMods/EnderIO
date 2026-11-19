package com.enderio.port.clientqa;

import com.enderio.enderio.client.content.machines.gui.screen.SlicerScreen;
import com.enderio.enderio.compat.jei_machines_to_merge.category.SlicingRecipeCategory;
import com.enderio.enderio.content.machines.slicer.SlicerMenu;
import com.enderio.enderio.content.machines.slicer.SlicingRecipe;
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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.Slot;
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
import java.util.Set;
import java.util.stream.Collectors;

final class ClientSlicerJeiSmoke {
    private enum Stage { TITLE, JOIN, OPEN, SELECT, TRANSFER, INSERTED, RELOAD, FINISH, DONE }

    private static final BlockPos SLICER = new BlockPos(3257, 80, 8);
    private static final ResourceLocation STOCK = ResourceLocation.parse("enderio:slicing/z_logic_controller");
    private static final ResourceLocation CUSTOM = ResourceLocation.parse("port_slicer:slicing/ordered");
    private static final Set<String> STOCK_NAMES = Set.of("zombie_electrode", "z_logic_controller", "skeletal_contractor",
        "guardian_diode", "ender_resonator");
    private static final List<String> STOCK_INPUTS = List.of("enderio:soularium_ingot", "minecraft:zombie_head",
        "enderio:soularium_ingot", "enderio:silicon", "minecraft:redstone", "enderio:silicon");
    private static final List<String> NEW_INPUTS = List.of("minecraft:amethyst_shard", "minecraft:kelp", "minecraft:paper",
        "minecraft:coal", "minecraft:stick", "minecraft:copper_ingot");

    private final Minecraft minecraft = Minecraft.getInstance();
    private final String address = System.getProperty("enderio.clientqa.server");
    private final Path output = Path.of(System.getProperty("enderio.clientqa.output"));
    private final Map<String, Boolean> checks = new LinkedHashMap<>();
    private final Map<String, Object> captures = new LinkedHashMap<>();
    private final long started = System.nanoTime();
    private Stage stage = Stage.TITLE;
    private long titleAt;
    private int useAt = -1;
    private boolean custom;

    ClientSlicerJeiSmoke() {
        MinecraftForge.EVENT_BUS.addListener(this::render);
    }

    private void render(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage == Stage.DONE) return;
        try {
            if (minecraft.screen instanceof DisconnectedScreen || System.nanoTime() - started > 145_000_000_000L) {
                throw new AssertionError("Slice'N'Splice JEI stopped during " + stage + " for " + label());
            }
            if (stage == Stage.TITLE) {
                if (!(minecraft.screen instanceof TitleScreen) || minecraft.getOverlay() != null) return;
                if (titleAt == 0) titleAt = System.nanoTime();
                if (System.nanoTime() - titleAt < 2_000_000_000L) return;
                ConnectScreen.startConnecting(new TitleScreen(), minecraft, ServerAddress.parseString(address),
                    new ServerData("Slice'N'Splice JEI QA", address, true), false);
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
                case OPEN -> open();
                case SELECT -> {
                    if (!(minecraft.screen instanceof SlicerScreen screen)) return;
                    SlicingRecipe recipe = find(runtime, recipeId());
                    if (recipe == null || (custom && recipe.energy() != 1800)) return;
                    if (custom) verifyReload(runtime, screen.getMenu(), recipe);
                    else verifyStock(runtime, screen.getMenu());
                    var category = runtime.getRecipeManager().getRecipeCategory(SlicingRecipeCategory.TYPE);
                    check(label() + "_has_the_native_transfer_handler",
                        runtime.getRecipeTransferManager().getRecipeTransferHandler(screen.getMenu(), category).isPresent());
                    runtime.getRecipesGui().showRecipes(category, List.of(recipe), List.of());
                    stage = Stage.TRANSFER;
                }
                case TRANSFER -> transfer();
                case INSERTED -> {
                    if (!(minecraft.screen instanceof SlicerScreen screen)) return;
                    var menu = screen.getMenu();
                    if (menu.slots.subList(SlicerMenu.INPUTS_INDEX, SlicerMenu.INPUTS_INDEX + SlicerMenu.INPUT_COUNT)
                        .stream().anyMatch(slot -> !slot.hasItem())) return;
                    check(label() + "_fills_all_six_slots_in_exact_order_and_keeps_tags", orderedInputs(menu));
                    check(label() + "_leaves_tools_and_capacitor_in_place", toolsMatch(menu, custom ? 1 : 0));
                    capture(label() + "-transferred");
                    if (!custom) player.closeContainer();
                    signal(label() + "-transferred");
                    stage = custom ? Stage.FINISH : Stage.RELOAD;
                }
                case RELOAD -> {
                    var recipe = find(runtime, CUSTOM);
                    if (!ready("custom") || recipe == null || recipe.energy() != 1800) return;
                    custom = true;
                    stage = Stage.OPEN;
                }
                case FINISH -> {
                    if (!ready("finished") || !(minecraft.screen instanceof SlicerScreen screen)) return;
                    var menu = screen.getMenu();
                    if (!toolsMatch(menu, 2) || menu.getEnergyStorage().getEnergyStored() != 0 || menu.getCraftingProgress() != 0) return;
                    check("both_crafts_sync_exact_tool_wear_and_clear_the_inputs", menu.slots
                        .subList(SlicerMenu.INPUTS_INDEX, SlicerMenu.LAST_INDEX + 1).stream().noneMatch(Slot::hasItem));
                    capture("slicer-jei-finished");
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

    private void open() {
        var player = minecraft.player;
        if (!ready(label()) || minecraft.screen != null || player.distanceToSqr(3257.5, 80, 11.5) > 0.15) return;
        if (useAt < 0) useAt = player.tickCount + 8;
        if (player.tickCount < useAt) return;
        useAt = -1;
        var hit = new BlockHitResult(Vec3.atCenterOf(SLICER).relative(Direction.SOUTH, 0.5), Direction.SOUTH, SLICER, false);
        check(label() + "_uses_the_actual_slicer_menu", minecraft.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit).consumesAction());
        stage = Stage.SELECT;
    }

    private void verifyStock(IJeiRuntime runtime, SlicerMenu menu) {
        var manager = runtime.getRecipeManager();
        check("machine_is_registered_as_a_category_catalyst", manager.createRecipeCatalystLookup(SlicingRecipeCategory.TYPE).get()
            .flatMap(ingredient -> ingredient.getItemStack().stream()).anyMatch(stack -> stack.is(EIOBlocks.SLICE_AND_SPLICE.asItem())));
        var stock = recipes(runtime).stream().filter(recipe -> recipe.getId().getNamespace().equals("enderio")).toList();
        check("all_five_shipped_recipes_are_in_jei", stock.stream().map(recipe -> recipe.getId().getPath().replaceFirst("^slicing/", ""))
            .collect(Collectors.toSet()).equals(STOCK_NAMES));
        var before = find(runtime, CUSTOM);
        check("fixture_registers_its_original_custom_order_and_output", before != null && before.energy() == 2400
            && before.output().is(Items.PRISMARINE_CRYSTALS) && before.inputs().get(0).test(new ItemStack(Items.KELP))
            && menu.getSlot(SlicerMenu.INPUTS_INDEX).mayPlace(new ItemStack(Items.KELP))
            && !menu.getSlot(SlicerMenu.INPUTS_INDEX).mayPlace(new ItemStack(Items.AMETHYST_SHARD)));
        var recipe = find(runtime, STOCK);
        var group = runtime.getJeiHelpers().getFocusFactory().getEmptyFocusGroup();
        var category = manager.getRecipeCategory(SlicingRecipeCategory.TYPE);
        var view = manager.createRecipeLayoutDrawable(category, recipe, group).orElseThrow().getRecipeSlotsView();
        var tools = view.getSlotViews(RecipeIngredientRole.CATALYST);
        check("viewer_tool_candidates_match_the_real_machine_slots", tools.size() == 2
            && itemsIn(tools.get(0).getItemStacks().map(ItemStack::getItem)).equals(acceptedBy(menu.getSlot(1)))
            && itemsIn(tools.get(1).getItemStacks().map(ItemStack::getItem)).equals(acceptedBy(menu.getSlot(2))));
        check("wood_axe_is_rejected_and_iron_axe_and_shears_are_indexed", !menu.getSlot(1).mayPlace(new ItemStack(Items.WOODEN_AXE))
            && !matches(runtime, RecipeIngredientRole.CATALYST, Items.WOODEN_AXE, STOCK)
            && matches(runtime, RecipeIngredientRole.CATALYST, Items.IRON_AXE, STOCK)
            && matches(runtime, RecipeIngredientRole.CATALYST, Items.SHEARS, STOCK));
    }

    private void verifyReload(IJeiRuntime runtime, SlicerMenu menu, SlicingRecipe recipe) {
        check("reload_replaces_the_custom_result_and_jei_output_lookup", recipe.output().is(Items.QUARTZ)
            && recipe.output().getCount() == 2 && recipe.output().hasTag() && recipe.output().getTag().getString("PortSlicerResult").equals("ordered")
            && matches(runtime, RecipeIngredientRole.OUTPUT, Items.QUARTZ, CUSTOM)
            && !matches(runtime, RecipeIngredientRole.OUTPUT, Items.PRISMARINE_CRYSTALS, CUSTOM));
        check("reloaded_machine_slot_accepts_new_ingredient_and_rejects_old_position",
            menu.getSlot(SlicerMenu.INPUTS_INDEX).mayPlace(new ItemStack(Items.AMETHYST_SHARD))
                && !menu.getSlot(SlicerMenu.INPUTS_INDEX).mayPlace(new ItemStack(Items.KELP)));
    }

    private void transfer() throws IOException {
        if (!(minecraft.screen instanceof RecipesGui gui)) return;
        var frame = visibleRecipe(gui);
        if (frame == null || !frame.transferButton().isInitialized()) return;
        var slots = frame.getRecipeLayout().getRecipeSlotsView().getSlotViews(RecipeIngredientRole.INPUT);
        var inputs = custom ? NEW_INPUTS : STOCK_INPUTS;
        check(label() + "_viewer_displays_the_six_expected_inputs", slots.size() == inputs.size()
            && java.util.stream.IntStream.range(0, inputs.size()).allMatch(i -> slots.get(i).getItemStacks()
                .anyMatch(stack -> itemId(stack).equals(inputs.get(i)))));
        var results = frame.getRecipeLayout().getRecipeSlotsView().getSlotViews(RecipeIngredientRole.OUTPUT);
        check(label() + "_viewer_displays_the_expected_result", results.size() == 1 && results.get(0).getDisplayedItemStack()
            .filter(stack -> custom ? stack.is(Items.QUARTZ) && stack.getCount() == 2
                : itemId(stack).equals("enderio:z_logic_controller") && stack.getCount() == 1).isPresent());
        capture(label() + "-slicer-jei");
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
                    && layout.getRecipeLayout().getRecipe() instanceof SlicingRecipe recipe && recipeId().equals(recipe.getId())) return layout;
            }
        }
        return null;
    }

    private boolean orderedInputs(SlicerMenu menu) {
        var expected = custom ? NEW_INPUTS : STOCK_INPUTS;
        for (int i = 0; i < expected.size(); i++) {
            var stack = menu.getSlot(SlicerMenu.INPUTS_INDEX + i).getItem();
            if (stack.getCount() != 1 || !itemId(stack).equals(expected.get(i)) || !stack.hasTag()
                || stack.getTag().getInt("PortSlicer") != 161) return false;
        }
        return true;
    }

    private static boolean toolsMatch(SlicerMenu menu, int wear) {
        return itemId(menu.getSlot(0).getItem()).equals("enderio:octadic_capacitor")
            && matchesTool(menu.getSlot(1).getItem(), Items.IRON_AXE, 7 + wear)
            && matchesTool(menu.getSlot(2).getItem(), Items.SHEARS, 11 + wear);
    }

    private static boolean matchesTool(ItemStack stack, Item item, int damage) {
        return stack.is(item) && stack.getDamageValue() == damage && stack.hasTag() && stack.getTag().getInt("PortSlicer") == 161;
    }

    private static Set<Item> acceptedBy(Slot slot) {
        return itemsIn(BuiltInRegistries.ITEM.stream().filter(item -> slot.mayPlace(new ItemStack(item))));
    }

    private static Set<Item> itemsIn(java.util.stream.Stream<Item> items) {
        return items.collect(Collectors.toSet());
    }

    private static String itemId(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    private static boolean matches(IJeiRuntime runtime, RecipeIngredientRole role, Item item, ResourceLocation id) {
        var focus = runtime.getJeiHelpers().getFocusFactory().createFocus(role, VanillaTypes.ITEM_STACK, new ItemStack(item));
        return runtime.getRecipeManager().createRecipeLookup(SlicingRecipeCategory.TYPE).limitFocus(List.of(focus)).get()
            .anyMatch(recipe -> recipe.getId().equals(id));
    }

    private static List<SlicingRecipe> recipes(IJeiRuntime runtime) {
        return runtime.getRecipeManager().createRecipeLookup(SlicingRecipeCategory.TYPE).get().toList();
    }

    private static SlicingRecipe find(IJeiRuntime runtime, ResourceLocation id) {
        return recipes(runtime).stream().filter(recipe -> recipe.getId().equals(id)).findFirst().orElse(null);
    }

    private ResourceLocation recipeId() { return custom ? CUSTOM : STOCK; }

    private String label() { return custom ? "custom" : "stock"; }

    private boolean ready(String name) {
        return Files.exists(output.resolve("server-" + name));
    }

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
        result.put("checks", checks);
        result.put("captures", captures);
        result.put("passed", failure == null);
        if (failure != null) result.put("error", failure.toString());
        ClientSmoke.complete("client_slicer_jei", result);
    }
}
