package com.enderio.port.clientqa;

import com.enderio.enderio.compat.jei_machines_to_merge.category.SagMillCategory;
import com.enderio.enderio.content.machines.sag_mill.SagMillMenu;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.gui.recipes.RecipesGui;
import net.irisshaders.iris.api.v0.IrisApi;
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
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.ModList;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Compares JEI with the server's SAG Mill while Almost Unified and the renderer stack are active. */
final class ClientUnifiedSagSmoke {
    private enum Stage { TITLE, JOIN, OPEN, MENU, VIEWER, TRANSFERRED, FINISH, DONE }

    private static final BlockPos MILL = new BlockPos(3131, 80, 8);
    private static final Vec3 STAND = new Vec3(3131.5, 80, 11.5);
    private static final ResourceLocation RECIPE = ResourceLocation.parse("enderio:sag_milling/tin");
    private static final TagKey<Item> TIN_DUST = TagKey.create(Registries.ITEM, ResourceLocation.parse("c:dusts/tin"));
    private static final List<String> OPTIONAL_MODS = List.of("ae2", "almostunified", "architectury", "athena",
        "computercraft", "curios", "embeddium", "ftblibrary", "ftbultimine", "guideme", "inventorysorter", "jade",
        "jei", "laserio", "mekanism", "oculus", "patchouli", "refinedstorage");

    private final Minecraft minecraft = Minecraft.getInstance();
    private final Path output = Path.of(System.getProperty("enderio.clientqa.output"));
    private final long started = System.nanoTime();
    private final Map<String, Boolean> checks = new LinkedHashMap<>();
    private final Map<String, Object> captures = new LinkedHashMap<>();
    private Stage stage = Stage.TITLE;
    private long titleAt;
    private int readyAt = -1;
    private int batch;

    ClientUnifiedSagSmoke() {
        MinecraftForge.EVENT_BUS.addListener(this::render);
    }

    private void render(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage == Stage.DONE) return;
        try {
            if (minecraft.screen instanceof DisconnectedScreen || System.nanoTime() - started > 192_000_000_000L) {
                throw new AssertionError("Unified SAG Mill stopped during " + stage + ", batch " + batch);
            }
            if (stage == Stage.TITLE) {
                if (!(minecraft.screen instanceof TitleScreen) || minecraft.getOverlay() != null) return;
                if (titleAt == 0) titleAt = System.nanoTime();
                if (System.nanoTime() - titleAt < 2_000_000_000L) return;
                var address = System.getProperty("enderio.clientqa.server");
                ConnectScreen.startConnecting(new TitleScreen(), minecraft, ServerAddress.parseString(address),
                    new ServerData("Ender IO optional integrations", address, true), false);
                stage = Stage.JOIN;
                return;
            }
            var player = minecraft.player;
            if (player == null || minecraft.level == null) return;
            var runtime = ClientSoulEngineFuelsSmoke.JeiProbe.runtime();
            var mekDust = item("mekanism:dust_tin");
            var nativeDust = item("enderio:powdered_tin");
            var ingot = item("mekanism:ingot_tin");
            switch (stage) {
                case JOIN -> {
                    if (runtime == null || minecraft.screen != null || minecraft.getToasts().getToast(SystemToast.class,
                        SystemToast.SystemToastIds.UNSECURE_SERVER_WARNING) != null) return;
                    check("all_eighteen_optional_mods_loaded_in_the_client", OPTIONAL_MODS.stream().allMatch(ModList.get()::isLoaded));
                    check("expected_oculus_installation", ModList.get().isLoaded("oculus") && ModList.get().isLoaded("embeddium"));
                    check("dedicated_survival_tcp", !player.isCreative() && !player.isSpectator()
                        && minecraft.getSingleplayerServer() == null && !minecraft.getConnection().getConnection().isMemoryConnection());
                    signal("connected");
                    stage = Stage.OPEN;
                }
                case OPEN -> {
                    if (runtime == null || minecraft.screen != null || !Files.exists(output.resolve("server-batch-" + batch))
                        || player.distanceToSqr(STAND) > 0.12 || mekDust.builtInRegistryHolder().is(TIN_DUST) != (batch == 0)) return;
                    if (readyAt == -1) readyAt = player.tickCount + 24;
                    if (player.tickCount < readyAt) return;
                    if (batch == 0) {
                        capture("integrated-world");
                        var frame = ImageIO.read(output.resolve("integrated-world.png").toFile());
                        check("authored_shader_draws_the_integrated_world", IrisApi.getInstance().isShaderPackInUse()
                            && (frame.getRGB(16, frame.getHeight() - 16) & 0xFFFFFF) == 0xFF00FF);
                    }
                    var hit = new BlockHitResult(Vec3.atCenterOf(MILL).relative(Direction.SOUTH, 0.5), Direction.SOUTH, MILL, false);
                    check("native_sag_mill_interaction_" + batch,
                        minecraft.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit).consumesAction());
                    stage = Stage.MENU;
                }
                case MENU -> {
                    if (runtime == null || !(player.containerMenu instanceof SagMillMenu)) return;
                    var recipe = ClientSagJeiSmoke.recipe(runtime, RECIPE);
                    var expected = batch == 0 ? mekDust : nativeDust;
                    var alternative = batch == 0 ? nativeDust : mekDust;
                    check("unified_sag_output_index_follows_the_server_" + batch,
                        ClientSagJeiSmoke.matches(runtime, RecipeIngredientRole.OUTPUT, expected, RECIPE)
                            && !ClientSagJeiSmoke.matches(runtime, RecipeIngredientRole.OUTPUT, alternative, RECIPE));
                    runtime.getRecipesGui().showRecipes(runtime.getRecipeManager().getRecipeCategory(SagMillCategory.TYPE),
                        List.of(recipe), List.of(ClientSagJeiSmoke.focus(runtime, RecipeIngredientRole.INPUT, ingot)));
                    stage = Stage.VIEWER;
                }
                case VIEWER -> {
                    if (!(minecraft.screen instanceof RecipesGui gui)) return;
                    var layout = ClientSagJeiSmoke.visibleRecipe(gui, RECIPE);
                    if (layout == null || !layout.transferButton().isInitialized()) return;
                    var shown = layout.recipeLayout().getRecipeSlotsView().getSlotViews(RecipeIngredientRole.OUTPUT)
                        .get(0).getDisplayedItemStack().orElseThrow();
                    var frame = capture("tin-recipe-" + batch);
                    frame.put("displayedTinDust", BuiltInRegistries.ITEM.getKey(shown.getItem()).toString());
                    check("viewer_shows_the_actual_server_product_" + batch, shown.is(batch == 0 ? mekDust : nativeDust) && shown.getCount() == 1);
                    check("native_jei_transfer_button_" + batch, ClientSagJeiSmoke.clickTransfer(gui, layout));
                    stage = Stage.TRANSFERRED;
                }
                case TRANSFERRED -> {
                    if (!(player.containerMenu instanceof SagMillMenu menu)) return;
                    var input = menu.getSlot(SagMillMenu.INPUTS_INDEX).getItem();
                    if (input.isEmpty()) return;
                    check("one_tagged_tin_moves_through_the_native_menu_" + batch, input.is(ingot)
                        && input.getCount() == 1 && input.getTag() != null && input.getTag().getInt("PortStack") == 177);
                    player.closeContainer();
                    signal("transferred-" + batch);
                    readyAt = -1;
                    stage = ++batch == 2 ? Stage.FINISH : Stage.OPEN;
                }
                case FINISH -> {
                    if (Files.exists(output.resolve("server-done"))) finish(null);
                }
                default -> {}
            }
        } catch (Throwable failure) {
            failure.printStackTrace();
            finish(failure);
        }
    }

    private static Item item(String id) {
        return BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(id)).orElseThrow();
    }

    private Map<String, Object> capture(String name) throws IOException {
        var data = ClientSmoke.capture(name + ".png");
        captures.put(name, data);
        check(name + "_hidden_muted", Boolean.TRUE.equals(data.get("passed")));
        return data;
    }

    private void signal(String name) throws IOException {
        var signals = output.resolve("signals");
        Files.createDirectories(signals);
        Files.writeString(signals.resolve(name + ".json"), "{}");
    }

    private void check(String name, boolean passed) {
        checks.put(name, passed);
    }

    private void finish(Throwable error) {
        stage = Stage.DONE;
        var result = new LinkedHashMap<String, Object>();
        result.put("passed", error == null && checks.values().stream().allMatch(Boolean::booleanValue));
        result.put("checks", checks);
        result.put("captures", captures);
        if (error != null) result.put("error", error.toString());
        ClientSmoke.complete("client_unified_sag", result);
    }
}
