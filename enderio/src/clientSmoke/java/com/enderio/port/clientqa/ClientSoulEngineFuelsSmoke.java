package com.enderio.port.clientqa;

import com.enderio.enderio.api.io.RedstoneControl;
import com.enderio.enderio.api.soul.Soul;
import com.enderio.enderio.compat.jei_machines_to_merge.category.SoulEngineCategory;
import com.enderio.enderio.content.machines.soul_engine.SoulEngineBlockEntity;
import com.enderio.enderio.content.tools.vials.SoulVialItem;
import com.enderio.enderio.foundation.souldata.EngineSoul;
import com.enderio.enderio.init.EIOBlocks;
import com.enderio.enderio.init.EIODataComponents;
import com.mojang.blaze3d.platform.NativeImage;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.forge.ForgeTypes;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.runtime.IJeiRuntime;
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
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fluids.FluidStack;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class ClientSoulEngineFuelsSmoke {
    private enum Stage { TITLE, CONNECT, SETUP, GRID, RESULT, TAKE, PLACE, JEI, JEI_CAPTURE, RELOAD, RELOAD_CAPTURE, DONE }

    private record Fuel(String id, int energy, int ticks) { }

    private static final Map<String, Fuel> FUELS = Map.of(
        "blaze", new Fuel("minecraft:lava", 800, 18),
        "creeper", new Fuel("enderio:fluid_rocket_fuel_still", 800, 12),
        "enderman", new Fuel("enderio:fluid_dew_of_the_void_still", 1200, 12),
        "husk", new Fuel("enderio:fluid_nutrient_distillation_still", 1000, 18),
        "zombie", new Fuel("enderio:fluid_nutrient_distillation_still", 1000, 18),
        "zombie_villager", new Fuel("enderio:fluid_nutrient_distillation_still", 1000, 18));
    private static final Fuel RELOADED = new Fuel("minecraft:water", 900, 4);
    private static final BlockPos TABLE = new BlockPos(1507, 80, 11);
    private static final BlockPos ERASED = new BlockPos(1508, 80, 11);

    private final Minecraft minecraft = Minecraft.getInstance();
    private final String address = System.getProperty("enderio.clientqa.server");
    private final Path output = Path.of(System.getProperty("enderio.clientqa.output"));
    private final Map<String, Boolean> checks = new LinkedHashMap<>();
    private final Map<String, Object> captures = new LinkedHashMap<>();
    private final long started = System.nanoTime();
    private long titleReady;
    private long reloadSynced;
    private Stage stage = Stage.TITLE;
    private int productSlot;

    ClientSoulEngineFuelsSmoke() {
        MinecraftForge.EVENT_BUS.addListener(this::render);
    }

    private void render(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage == Stage.DONE) return;
        try {
            if (minecraft.screen instanceof DisconnectedScreen) throw new AssertionError("Disconnected during " + stage);
            if (System.nanoTime() - started > 200_000_000_000L) throw new AssertionError("Timed out in " + stage);
            switch (stage) {
                case TITLE -> {
                    if (!(minecraft.screen instanceof TitleScreen) || minecraft.getOverlay() != null) return;
                    if (titleReady == 0) titleReady = System.nanoTime();
                    if (System.nanoTime() - titleReady < 2_000_000_000L) return;
                    ConnectScreen.startConnecting(new TitleScreen(), minecraft, ServerAddress.parseString(address),
                        new ServerData("Ender IO local Soul Engine fuel test", address, true), false);
                    stage = Stage.CONNECT;
                }
                case CONNECT -> {
                    if (minecraft.level == null || minecraft.player == null || minecraft.screen != null) return;
                    if (minecraft.getToasts().getToast(SystemToast.class, SystemToast.SystemToastIds.UNSECURE_SERVER_WARNING) != null) return;
                    var connection = minecraft.getConnection().getConnection();
                    check("dedicated_survival_tcp", !minecraft.player.isCreative() && !minecraft.player.isSpectator()
                        && minecraft.getSingleplayerServer() == null && !connection.isMemoryConnection()
                        && connection.getRemoteAddress() instanceof InetSocketAddress);
                    signal("connected");
                    stage = Stage.SETUP;
                }
                case SETUP -> {
                    var input = minecraft.player.getInventory().getItem(0);
                    if (!isEngine(input) || !minecraft.level.getBlockState(TABLE).is(Blocks.CRAFTING_TABLE)) return;
                    check("native_drop_carries_blaze_charge_and_settings",
                        Soul.ITEM_DATA.getOrDefault(input, Soul.EMPTY).entityType() == EntityType.BLAZE
                            && EIODataComponents.ENERGY.getOrDefault(input, 0) == 1543
                            && RedstoneControl.ITEM_DATA.get(input) == RedstoneControl.NEVER_ACTIVE
                            && EIODataComponents.IO_CONFIG.get(input) != null);
                    minecraft.gameMode.useItemOn(minecraft.player, InteractionHand.MAIN_HAND,
                        new BlockHitResult(new Vec3(1507.5, 80.5, 12), Direction.SOUTH, TABLE, false));
                    stage = Stage.GRID;
                }
                case GRID -> {
                    if (!(minecraft.screen instanceof CraftingScreen)
                        || !(minecraft.player.containerMenu instanceof CraftingMenu menu)) return;
                    minecraft.gameMode.handleInventoryMouseClick(menu.containerId, 1, 0, ClickType.SWAP, minecraft.player);
                    stage = Stage.RESULT;
                }
                case RESULT -> {
                    var menu = (CraftingMenu) minecraft.player.containerMenu;
                    var stack = menu.getSlot(0).getItem();
                    if (stack.isEmpty()) return;
                    check("native_erase_recipe_returns_one_pristine_engine", isEngine(stack) && stack.getCount() == 1 && !stack.hasTag());
                    capture("erase-soul-engine");
                    minecraft.gameMode.handleInventoryMouseClick(menu.containerId, 0, 0, ClickType.QUICK_MOVE, minecraft.player);
                    stage = Stage.TAKE;
                }
                case TAKE -> {
                    var menu = (CraftingMenu) minecraft.player.containerMenu;
                    if (menu.getSlot(0).hasItem() || menu.getSlot(1).hasItem()) return;
                    var inventory = minecraft.player.getInventory();
                    productSlot = 0;
                    while (productSlot < 9 && !isEngine(inventory.getItem(productSlot))) productSlot++;
                    if (productSlot == 9) return;
                    check("quick_move_consumes_input_and_delivers_pristine_engine", !inventory.getItem(productSlot).hasTag());
                    minecraft.player.closeContainer();
                    stage = Stage.PLACE;
                }
                case PLACE -> {
                    if (minecraft.screen != null) return;
                    minecraft.player.getInventory().selected = productSlot;
                    var result = minecraft.gameMode.useItemOn(minecraft.player, InteractionHand.MAIN_HAND,
                        new BlockHitResult(Vec3.atBottomCenterOf(ERASED), Direction.UP, ERASED.below(), false));
                    check("native_placement_accepts_craft_result", result.consumesAction());
                    signal("placed");
                    stage = Stage.JEI;
                }
                case JEI -> {
                    if (!Files.exists(output.resolve("server-placed"))) return;
                    if (!(minecraft.level.getBlockEntity(ERASED) instanceof SoulEngineBlockEntity engine)) return;
                    var runtime = JeiProbe.runtime;
                    if (runtime == null) return;
                    check("placed_engine_syncs_without_bound_soul", engine.getBoundSoul().isEmpty());
                    verifyDefaultRecipes(runtime);
                    showBlaze(runtime);
                    stage = Stage.JEI_CAPTURE;
                }
                case JEI_CAPTURE -> {
                    check("real_jei_viewer_open", minecraft.screen == JeiProbe.runtime.getRecipesGui());
                    capture("jei-default");
                    checkTankColor("jei-default", true);
                    signal("jei-default");
                    stage = Stage.RELOAD;
                }
                case RELOAD -> {
                    if (EngineSoul.ENGINE.matches(EntityType.BLAZE).map(EngineSoul.SoulData::powerpermb).orElse(0) != RELOADED.energy()) return;
                    if (reloadSynced == 0) reloadSynced = System.nanoTime();
                    var runtime = JeiProbe.runtime;
                    if (runtime == null) return;
                    var recipes = recipes(runtime);
                    var blaze = recipes.get("blaze");
                    var waterRecipes = byFluid(runtime, "minecraft:water");
                    if ((blaze == null || blaze.powerpermb() != RELOADED.energy() || waterRecipes.isEmpty()) &&
                        System.nanoTime() - reloadSynced < 12_000_000_000L) return;
                    check("jei_replaces_old_rule_on_live_reload", recipes.size() == FUELS.size()
                        && blaze != null && blaze.powerpermb() == RELOADED.energy());
                    verifyFuel(runtime, "blaze", blaze, RELOADED);
                    check("jei_indexes_water_and_hides_old_lava_rule", waterRecipes.equals(List.of(blaze))
                        && byFluid(runtime, "minecraft:lava").isEmpty());
                    showBlaze(runtime);
                    stage = Stage.RELOAD_CAPTURE;
                }
                case RELOAD_CAPTURE -> {
                    check("real_jei_viewer_open_after_reload", minecraft.screen == JeiProbe.runtime.getRecipesGui());
                    capture("jei-reloaded");
                    checkTankColor("jei-reloaded", false);
                    finish(null);
                }
                default -> { }
            }
        } catch (Throwable failure) {
            failure.printStackTrace();
            finish(failure);
        }
    }

    private static boolean isEngine(ItemStack stack) {
        return stack.is(EIOBlocks.SOUL_ENGINE_ITEM.get());
    }

    private static Map<String, EngineSoul.SoulData> recipes(IJeiRuntime runtime) {
        return runtime.getRecipeManager().createRecipeLookup(SoulEngineCategory.TYPE).get()
            .collect(Collectors.toMap(rule -> rule.entitytype().getPath(), Function.identity()));
    }

    private void verifyDefaultRecipes(IJeiRuntime runtime) {
        var recipes = recipes(runtime);
        check("jei_registers_all_six_synced_rules", recipes.keySet().equals(FUELS.keySet()));
        check("jei_has_soul_engine_catalyst",
            runtime.getRecipeManager().createRecipeCatalystLookup(SoulEngineCategory.TYPE).getItemStack()
                .anyMatch(ClientSoulEngineFuelsSmoke::isEngine));
        FUELS.forEach((mob, fuel) -> verifyFuel(runtime, mob, recipes.get(mob), fuel));
        var manager = runtime.getRecipeManager();
        var focuses = runtime.getJeiHelpers().getFocusFactory();
        var blaze = recipes.get("blaze");
        var egg = new ItemStack(SpawnEggItem.byId(EntityType.BLAZE));
        var vial = SoulVialItem.forSoul(Soul.of(blaze.entitytype()));
        for (var stack : List.of(egg, vial)) {
            var focus = focuses.createFocus(RecipeIngredientRole.INPUT, VanillaTypes.ITEM_STACK, stack);
            check("jei_indexes_blaze_" + BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath(),
                manager.createRecipeLookup(SoulEngineCategory.TYPE).limitFocus(List.of(focus)).get().toList().equals(List.of(blaze)));
        }
        check("jei_indexes_the_original_lava_tag_rule", byFluid(runtime, "minecraft:lava").equals(List.of(blaze)));
        check("jei_exposes_the_soul_engine_category_for_lava",
            manager.createRecipeCategoryLookup().limitFocus(List.of(fluidFocus(runtime, "minecraft:lava"))).get()
                .anyMatch(category -> category.getRecipeType().equals(SoulEngineCategory.TYPE)));
        check("jei_indexes_three_nutrient_distillation_souls",
            byFluid(runtime, FUELS.get("husk").id()).stream().map(rule -> rule.entitytype().getPath()).sorted().toList()
                .equals(List.of("husk", "zombie", "zombie_villager")));
    }

    private void verifyFuel(IJeiRuntime runtime, String mob, EngineSoul.SoulData rule, Fuel expected) {
        var manager = runtime.getRecipeManager();
        var category = manager.getRecipeCategory(SoulEngineCategory.TYPE);
        var layout = manager.createRecipeLayoutDrawable(category, rule, runtime.getJeiHelpers().getFocusFactory().getEmptyFocusGroup())
            .orElseThrow();
        check("jei_" + mob + "_" + expected.ticks() + "_ticks_" + expected.energy() + "_fe",
            rule.tickpermb() == expected.ticks() && rule.powerpermb() == expected.energy()
                && layout.getRecipeSlotsView().getSlotViews(RecipeIngredientRole.INPUT).stream()
                    .flatMap(slot -> slot.getIngredients(ForgeTypes.FLUID_STACK))
                    .anyMatch(fluid -> BuiltInRegistries.FLUID.getKey(fluid.getFluid()).toString().equals(expected.id())
                        && fluid.getAmount() == SoulEngineBlockEntity.FLUID_CAPACITY));
    }

    private static List<EngineSoul.SoulData> byFluid(IJeiRuntime runtime, String fluidId) {
        return runtime.getRecipeManager().createRecipeLookup(SoulEngineCategory.TYPE)
            .limitFocus(List.of(fluidFocus(runtime, fluidId))).get().toList();
    }

    private static IFocus<FluidStack> fluidFocus(IJeiRuntime runtime, String fluidId) {
        var fluid = BuiltInRegistries.FLUID.get(ResourceLocation.parse(fluidId));
        return runtime.getJeiHelpers().getFocusFactory().createFocus(RecipeIngredientRole.INPUT,
            ForgeTypes.FLUID_STACK, new FluidStack(fluid, 1000));
    }

    private static void showBlaze(IJeiRuntime runtime) {
        var manager = runtime.getRecipeManager();
        runtime.getRecipesGui().showRecipes(manager.getRecipeCategory(SoulEngineCategory.TYPE),
            List.of(recipes(runtime).get("blaze")), List.of());
    }

    private void signal(String name) throws IOException {
        var directory = output.resolve("signals");
        Files.createDirectories(directory);
        Files.writeString(directory.resolve(name + ".json"), "{}");
    }

    private void capture(String name) throws IOException {
        var frame = ClientSmoke.capture(name + ".png");
        captures.put(name, frame);
        check("silent_hidden_render_" + name, Boolean.TRUE.equals(frame.get("passed")));
    }

    private void checkTankColor(String frame, boolean lava) throws IOException {
        try (var input = Files.newInputStream(output.resolve(frame + ".png")); var pixels = NativeImage.read(input)) {
            // The center of the fuel slot in the pinned JEI layout at this probe's window size.
            int color = pixels.getPixelRGBA(pixels.getWidth() * 45 / 100, pixels.getHeight() * 46 / 100);
            int red = color & 255;
            int green = (color >>> 8) & 255;
            int blue = (color >>> 16) & 255;
            check("visible_" + (lava ? "lava" : "water") + "_in_jei_tank",
                lava ? red > green + 25 && green > blue + 15 : blue > red + 40);
        }
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
        ClientSmoke.complete("client_soul_engine_fuels", result);
    }

    @JeiPlugin
    public static final class JeiProbe implements IModPlugin {
        private static IJeiRuntime runtime;

        static IJeiRuntime runtime() {
            return runtime;
        }

        @Override
        public ResourceLocation getPluginUid() {
            return ResourceLocation.fromNamespaceAndPath("enderio_client_qa", "soul_engine_fuels");
        }

        @Override
        public void onRuntimeAvailable(IJeiRuntime value) {
            runtime = value;
        }

        @Override
        public void onRuntimeUnavailable() {
            runtime = null;
        }
    }
}
