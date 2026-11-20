package com.enderio.port.clientqa;

import com.enderio.enderio.api.soul.SoulBoundUtils;
import com.enderio.enderio.client.content.machines.gui.screen.SoulBinderScreen;
import com.enderio.enderio.compat.jei_machines_to_merge.category.SoulBindingCategory;
import com.enderio.enderio.content.machines.powered_spawner.PoweredSpawnerBlockEntity;
import com.enderio.enderio.content.machines.soul_binder.SoulBindingRecipe;
import com.enderio.enderio.init.EIOBlocks;
import com.enderio.enderio.init.EIOItems;
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
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
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

final class ClientSpawnerRebindingSmoke {
    private enum Stage { TITLE, JOIN, OPEN, MENU, RECIPE, QUEUED, COMPLETE, TAKE, PLACE, PLACED, DONE }

    private static final BlockPos BINDER = new BlockPos(4882, 80, 8);
    private static final BlockPos PLACED = new BlockPos(4887, 80, 8);
    private static final ResourceLocation RECIPE = ResourceLocation.parse("enderio:soulbinding/powered_spawner");
    private final Minecraft minecraft = Minecraft.getInstance();
    private final Path output = Path.of(System.getProperty("enderio.clientqa.output"));
    private final Map<String, Boolean> checks = new LinkedHashMap<>();
    private final Map<String, Object> captures = new LinkedHashMap<>();
    private final long started = System.nanoTime();
    private Stage stage = Stage.TITLE;
    private long titleAt;
    private ItemStack original = ItemStack.EMPTY;
    private ItemStack capturedVial = ItemStack.EMPTY;

    ClientSpawnerRebindingSmoke() {
        MinecraftForge.EVENT_BUS.addListener(this::render);
    }

    private void render(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage == Stage.DONE) return;
        try {
            if (minecraft.screen instanceof DisconnectedScreen || System.nanoTime() - started > 195_000_000_000L) {
                throw new AssertionError("Spawner rebinding stopped during " + stage);
            }
            if (stage == Stage.TITLE) {
                connect();
                return;
            }
            var player = minecraft.player;
            var runtime = ClientSoulEngineFuelsSmoke.JeiProbe.runtime();
            if (player == null || minecraft.level == null || runtime == null) return;
            switch (stage) {
                case JOIN -> {
                    if (minecraft.screen != null || minecraft.getToasts().getToast(SystemToast.class,
                        SystemToast.SystemToastIds.UNSECURE_SERVER_WARNING) != null) return;
                    check("dedicated_survival_connection", !player.isCreative() && !player.isSpectator()
                        && minecraft.getSingleplayerServer() == null && !minecraft.getConnection().getConnection().isMemoryConnection());
                    signal("connected");
                    stage = Stage.OPEN;
                }
                case OPEN -> {
                    if (!ready("initial") || minecraft.screen != null
                        || player.distanceToSqr(Vec3.atBottomCenterOf(BINDER.south(2))) > 0.15) return;
                    check("opens_native_soul_binder", use(BINDER, Direction.SOUTH));
                    stage = Stage.MENU;
                }
                case MENU -> {
                    if (!(minecraft.screen instanceof SoulBinderScreen screen)
                        || screen.getMenu().getFluidTank().contents().getAmount() != 2239) return;
                    var material = player.getInventory().getItem(5);
                    var vial = player.getInventory().getItem(3);
                    if (!spawner(material) || !pig(vial)) return;
                    original = material.copy();
                    capturedVial = vial.copy();
                    check("original_spawner_has_the_captured_cow_and_machine_data",
                        SoulBoundUtils.getBoundSoul(original).entityType() == EntityType.COW
                            && original.getTag().getCompound("enderio:data").getInt("enderio:energy") == 877);
                    showRecipe(runtime);
                    stage = Stage.RECIPE;
                }
                case RECIPE -> transfer();
                case QUEUED -> {
                    if (!(minecraft.screen instanceof SoulBinderScreen screen)) return;
                    var menu = screen.getMenu();
                    if (!menu.getSlot(1).hasItem() || !menu.getSlot(2).hasItem()) return;
                    check("jei_transfers_the_exact_unmodified_stacks",
                        ItemStack.matches(menu.getSlot(1).getItem(), capturedVial) && ItemStack.matches(menu.getSlot(2).getItem(), original)
                            && player.getInventory().getItem(3).isEmpty() && player.getInventory().getItem(5).isEmpty());
                    capture("queued");
                    signal("queued");
                    stage = Stage.COMPLETE;
                }
                case COMPLETE -> {
                    if (!ready("complete") || !(minecraft.screen instanceof SoulBinderScreen screen)) return;
                    var menu = screen.getMenu();
                    var result = menu.getSlot(3).getItem();
                    var returned = menu.getSlot(4).getItem();
                    if (result.isEmpty() || returned.isEmpty() || menu.getFluidTank().contents().getAmount() != 37) return;
                    check("rebound_result_and_empty_vial_arrive_with_exact_fluid_balance",
                        pig(result) && sameConfiguration(result) && emptyVial(returned)
                            && !menu.getSlot(1).hasItem() && !menu.getSlot(2).hasItem());
                    capture("rebound");
                    minecraft.gameMode.handleInventoryMouseClick(menu.containerId, 3, 0, ClickType.QUICK_MOVE, player);
                    minecraft.gameMode.handleInventoryMouseClick(menu.containerId, 4, 0, ClickType.QUICK_MOVE, player);
                    stage = Stage.TAKE;
                }
                case TAKE -> {
                    if (!(minecraft.screen instanceof SoulBinderScreen screen) || screen.getMenu().getSlot(3).hasItem()
                        || screen.getMenu().getSlot(4).hasItem() || hotbarSpawner() < 0) return;
                    check("native_quick_move_returns_both_original_outputs",
                        player.getInventory().items.stream().anyMatch(ClientSpawnerRebindingSmoke::emptyVial)
                            && sameConfiguration(player.getInventory().getItem(hotbarSpawner())));
                    signal("taken");
                    player.closeContainer();
                    stage = Stage.PLACE;
                }
                case PLACE -> {
                    if (!ready("place") || minecraft.screen != null
                        || player.distanceToSqr(Vec3.atBottomCenterOf(PLACED.south(2))) > 0.15) return;
                    player.getInventory().selected = hotbarSpawner();
                    check("places_rebound_spawner_in_survival", use(PLACED.below(), Direction.UP));
                    stage = Stage.PLACED;
                }
                case PLACED -> {
                    if (!ready("placed") || !(minecraft.level.getBlockEntity(PLACED) instanceof PoweredSpawnerBlockEntity spawner)
                        || spawner.getBoundSoul().entityType() != EntityType.PIG) return;
                    check("placed_spawner_syncs_pig_and_consumes_carried_item",
                        hotbarSpawner() < 0 && spawner.getBoundSoul().equals(SoulBoundUtils.getBoundSoul(capturedVial)));
                    capture("placed");
                    finish(null);
                }
                default -> { }
            }
        } catch (Throwable failure) {
            failure.printStackTrace();
            finish(failure);
        }
    }

    private void showRecipe(IJeiRuntime runtime) {
        var manager = runtime.getRecipeManager();
        var focuses = runtime.getJeiHelpers().getFocusFactory();
        var material = focuses.createFocus(RecipeIngredientRole.INPUT, VanillaTypes.ITEM_STACK, original);
        var vial = focuses.createFocus(RecipeIngredientRole.INPUT, VanillaTypes.ITEM_STACK, capturedVial);
        var category = manager.getRecipeCategory(SoulBindingCategory.TYPE);
        var recipe = manager.createRecipeLookup(SoulBindingCategory.TYPE).limitFocus(List.of(material)).get()
            .filter(i -> RECIPE.equals(i.getId())).findFirst().orElseThrow();
        check("stock_rebinding_recipe_has_the_original_cost_and_copy_flag",
            recipe.experience() == 8 && recipe.energy() == 288000 && recipe.copyInputComponents());
        var slots = manager.createRecipeLayoutDrawable(category, recipe, focuses.createFocusGroup(List.of(material, vial)))
            .orElseThrow().getRecipeSlotsView();
        var result = slots.getSlotViews(RecipeIngredientRole.OUTPUT).get(1).getDisplayedItemStack().orElseThrow();
        check("focused_preview_replaces_the_soul_and_preserves_exact_machine_data", pig(result) && sameConfiguration(result));
        check("focused_material_slot_displays_the_configured_spawner",
            slots.getSlotViews(RecipeIngredientRole.INPUT).get(1).getDisplayedItemStack()
                .filter(stack -> ItemStack.matches(stack, original)).isPresent());
        runtime.getRecipesGui().showRecipes(category, List.of(recipe), List.of());
    }

    private void transfer() throws IOException {
        if (!(minecraft.screen instanceof RecipesGui gui)) return;
        var area = gui.getArea();
        for (int y = area.getY(); y < area.getY() + area.getHeight(); y += 8) {
            for (int x = area.getX(); x < area.getX() + area.getWidth(); x += 8) {
                if (!(JeiLayoutAccess.underMouse(gui, x, y).orElse(null) instanceof RecipeLayoutWithButtons<?> layout)
                    || !(layout.recipeLayout().getRecipe() instanceof SoulBindingRecipe recipe) || !RECIPE.equals(recipe.getId())
                    || !layout.transferButton().isInitialized()) continue;
                check("unfocused_transfer_finds_configured_cow_and_named_pig", layout.transferButton().getMissingCountHint() == 0);
                capture("recipe");
                check("uses_native_jei_transfer", ClientSagJeiSmoke.clickTransfer(gui, layout));
                stage = Stage.QUEUED;
                return;
            }
        }
    }

    private void connect() {
        if (!(minecraft.screen instanceof TitleScreen) || minecraft.getOverlay() != null) return;
        if (titleAt == 0) titleAt = System.nanoTime();
        if (System.nanoTime() - titleAt < 2_000_000_000L) return;
        String address = System.getProperty("enderio.clientqa.server");
        ConnectScreen.startConnecting(new TitleScreen(), minecraft, ServerAddress.parseString(address),
            new ServerData("Ender IO Spawner Rebinding", address, true), false);
        stage = Stage.JOIN;
    }

    private boolean use(BlockPos pos, Direction side) {
        var hit = new BlockHitResult(Vec3.atCenterOf(pos).relative(side, 0.5), side, pos, false);
        return minecraft.gameMode.useItemOn(minecraft.player, InteractionHand.MAIN_HAND, hit).consumesAction();
    }

    private int hotbarSpawner() {
        for (int i = 0; i < 9; i++) {
            if (spawner(minecraft.player.getInventory().getItem(i))) return i;
        }
        return -1;
    }

    private static boolean spawner(ItemStack stack) {
        return stack.is(EIOBlocks.POWERED_SPAWNER.asItem());
    }

    private static boolean pig(ItemStack stack) {
        var soul = SoulBoundUtils.getBoundSoul(stack);
        return soul.entityType() == EntityType.PIG && soul.entityTag().getFloat("Health") == 16
            && soul.entityTag().getString("CustomName").contains("Hog") && !soul.entityTag().contains("NoAI");
    }

    private static boolean emptyVial(ItemStack stack) {
        return stack.is(EIOItems.SOUL_VIAL.get()) && SoulBoundUtils.getBoundSoul(stack).isEmpty();
    }

    private boolean sameConfiguration(ItemStack candidate) {
        return spawner(candidate) && candidate.getCount() == 1 && configuration(candidate).equals(configuration(original));
    }

    private static CompoundTag configuration(ItemStack stack) {
        var tag = stack.getTag() == null ? new CompoundTag() : stack.getTag().copy();
        tag.getCompound("enderio:data").remove("enderio:soul");
        return tag;
    }

    private boolean ready(String name) {
        return Files.exists(output.resolve("server-" + name));
    }

    private void signal(String name) throws IOException {
        var signals = output.resolve("signals");
        Files.createDirectories(signals);
        var tmp = signals.resolve(name + ".tmp");
        Files.writeString(tmp, "{}");
        Files.move(tmp, signals.resolve(name + ".json"));
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
        ClientSmoke.complete("client_spawner_rebinding", result);
    }
}
