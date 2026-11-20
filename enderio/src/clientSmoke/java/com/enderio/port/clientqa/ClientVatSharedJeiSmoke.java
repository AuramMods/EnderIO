package com.enderio.port.clientqa;

import com.enderio.enderio.client.content.machines.gui.screen.VatScreen;
import com.enderio.enderio.compat.jei_machines_to_merge.category.VATCategory;
import com.enderio.enderio.content.machines.vat.VatMenu;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.runtime.IJeiRuntime;
import mezz.jei.common.transfer.RecipeTransferUtil;
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
import net.minecraft.world.level.material.Fluids;
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

final class ClientVatSharedJeiSmoke {
    private enum Stage { TITLE, JOIN, OPEN, SHORT, SUPPLIED, TRANSFER, QUEUED, VERIFIED, DONE }

    private record Fixture(String name, int x, int supply, boolean bulk, Item left, int quantity,
                           int leftVariant, int rightVariant, int remaining) {
        BlockPos pos() { return new BlockPos(x, 80, 8); }
    }

    private static final ResourceLocation RECIPE = ResourceLocation.parse("port_vat:shared_reagents");
    private static final List<Fixture> FIXTURES = List.of(
        new Fixture("choice", 4794, 1, false, Items.GOLD_INGOT, 1, 3, 1, 0),
        new Fixture("shared", 4798, 5, true, Items.COPPER_INGOT, 2, 1, 1, 1),
        new Fixture("variants", 4802, 6, true, Items.COPPER_INGOT, 3, 1, 2, 0),
        new Fixture("loaded", 4806, 7, true, Items.COPPER_INGOT, 3, 2, 1, 4),
        new Fixture("capacity", 4810, 129, true, Items.COPPER_INGOT, 64, 1, 1, 1));

    private final Minecraft minecraft = Minecraft.getInstance();
    private final Path output = Path.of(System.getProperty("enderio.clientqa.output"));
    private final Map<String, Boolean> checks = new LinkedHashMap<>();
    private final Map<String, Object> captures = new LinkedHashMap<>();
    private final long started = System.nanoTime();
    private Stage stage = Stage.TITLE;
    private long titleAt;
    private int index;
    private VatMenu menu;

    ClientVatSharedJeiSmoke() {
        MinecraftForge.EVENT_BUS.addListener(this::render);
    }

    private void render(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage == Stage.DONE) return;
        try {
            if (minecraft.screen instanceof DisconnectedScreen || System.nanoTime() - started > 190_000_000_000L) {
                throw new AssertionError("Shared VAT JEI stopped during " + stage + " for fixture " + index);
            }
            if (stage == Stage.TITLE) {
                connect();
                return;
            }
            var player = minecraft.player;
            var runtime = ClientSoulEngineFuelsSmoke.JeiProbe.runtime();
            if (player == null || minecraft.level == null || runtime == null) return;
            var fixture = FIXTURES.get(index);
            switch (stage) {
                case JOIN -> {
                    if (minecraft.screen != null || minecraft.getToasts().getToast(SystemToast.class,
                        SystemToast.SystemToastIds.UNSECURE_SERVER_WARNING) != null) return;
                    if (runtime.getRecipeManager().createRecipeLookup(VATCategory.TYPE).get().noneMatch(r -> RECIPE.equals(r.getId()))) return;
                    check("actual_dedicated_survival_menu", !player.isCreative() && !player.isSpectator()
                        && minecraft.getSingleplayerServer() == null && !minecraft.getConnection().getConnection().isMemoryConnection());
                    signal("connected");
                    stage = Stage.OPEN;
                }
                case OPEN -> {
                    var pos = fixture.pos();
                    if (!ready(fixture, "initial") || minecraft.screen != null || player.distanceToSqr(Vec3.atBottomCenterOf(pos.south(2))) > 0.15) return;
                    var hit = new BlockHitResult(Vec3.atCenterOf(pos).relative(Direction.SOUTH, 0.5), Direction.SOUTH, pos, false);
                    check(fixture.name() + "_opens_the_native_menu", minecraft.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit).consumesAction());
                    stage = index == 0 ? Stage.SHORT : Stage.SUPPLIED;
                }
                case SHORT -> {
                    if (!(minecraft.screen instanceof VatScreen screen) || !hasFixtureFluid(screen.getMenu())
                        || copper() != 1 || player.getInventory().countItem(Items.GOLD_INGOT) != 0) return;
                    rejectSingle(runtime, screen.getMenu());
                    signal("choice-rejected");
                    stage = Stage.SUPPLIED;
                }
                case SUPPLIED -> {
                    if (!ready(fixture, "supplied") || !(minecraft.screen instanceof VatScreen screen)
                        || !hasFixtureFluid(screen.getMenu()) || copper() != fixture.supply()
                        || (index == 0 && player.getInventory().countItem(Items.GOLD_INGOT) != 1)) return;
                    menu = screen.getMenu();
                    var manager = runtime.getRecipeManager();
                    var recipe = manager.createRecipeLookup(VATCategory.TYPE).get().filter(r -> RECIPE.equals(r.getId())).findFirst().orElseThrow();
                    runtime.getRecipesGui().showRecipes(manager.getRecipeCategory(VATCategory.TYPE), List.of(recipe), List.of());
                    stage = Stage.TRANSFER;
                }
                case TRANSFER -> transfer(runtime, fixture);
                case QUEUED -> {
                    if (copper() != fixture.remaining() || menu.getSlot(1).getItem().isEmpty()) return;
                    check(fixture.name() + "_keeps_exact_counts_and_nbt_in_each_slot",
                        matches(menu.getSlot(0).getItem(), fixture.left(), fixture.quantity(), fixture.leftVariant())
                            && matches(menu.getSlot(1).getItem(), Items.COPPER_INGOT, fixture.quantity(), fixture.rightVariant())
                            && player.getInventory().countItem(Items.GOLD_INGOT) == 0);
                    check(fixture.name() + "_moves_only_items_while_the_vat_is_paused",
                        hasFixtureFluid(menu) && menu.getOutputTank().contents().isEmpty() && menu.getCraftingProgress() == 0);
                    capture(fixture.name() + "-queued");
                    signal(fixture.name() + "-queued");
                    stage = Stage.VERIFIED;
                }
                case VERIFIED -> {
                    if (!ready(fixture, "verified")) return;
                    player.closeContainer();
                    index++;
                    if (index == FIXTURES.size()) finish(null);
                    else stage = Stage.OPEN;
                }
                default -> { }
            }
        } catch (Throwable failure) {
            failure.printStackTrace();
            finish(failure);
        }
    }

    private void rejectSingle(IJeiRuntime runtime, VatMenu menu) throws ReflectiveOperationException {
        var manager = runtime.getRecipeManager();
        var recipe = manager.createRecipeLookup(VATCategory.TYPE).get().filter(r -> RECIPE.equals(r.getId())).findFirst().orElseThrow();
        var category = manager.getRecipeCategory(VATCategory.TYPE);
        var layout = manager.createRecipeLayoutDrawable(category, recipe, runtime.getJeiHelpers().getFocusFactory().getEmptyFocusGroup()).orElseThrow();
        var transfer = runtime.getRecipeTransferManager();
        var error = RecipeTransferUtil.getTransferRecipeError(transfer, menu, layout, minecraft.player).orElse(null);
        var inputs = layout.getRecipeSlotsView().getSlotViews(RecipeIngredientRole.INPUT);
        var highlighted = error instanceof RecipeTransferErrorMissingSlots ? ClientVatJeiSmoke.highlightedSlots(error) : Set.of();
        boolean correct = highlighted.equals(Set.of(inputs.get(1)));
        if (!correct) {
            System.out.println("VAT shared test highlighted input indices " + highlighted.stream().map(inputs::indexOf).toList());
        }
        check("one_shared_item_highlights_the_other_reagent", correct);
        check("one_shared_item_cannot_trigger_a_transfer", !RecipeTransferUtil.transferRecipe(transfer, menu, layout, minecraft.player, false));
    }

    private void transfer(IJeiRuntime runtime, Fixture fixture) throws IOException {
        if (!(minecraft.screen instanceof RecipesGui gui)) return;
        var area = gui.getArea();
        for (int y = area.getY(); y < area.getY() + area.getHeight(); y += 8) {
            for (int x = area.getX(); x < area.getX() + area.getWidth(); x += 8) {
                if (!(gui.getRecipeLayoutUnderMouse(x, y).orElse(null) instanceof RecipeLayoutWithButtons<?> layout)
                    || !layout.transferButton().isInitialized()) continue;
                check(fixture.name() + "_enables_transfer_for_sufficient_stock", layout.transferButton().getMissingCountHint() == 0);
                capture(fixture.name() + "-recipe");
                // The button dispatches this same JEI operation with maximum=true when Shift is held.
                boolean sent = fixture.bulk()
                    ? RecipeTransferUtil.transferRecipe(runtime.getRecipeTransferManager(), menu, layout.getRecipeLayout(), minecraft.player, true)
                    : ClientSagJeiSmoke.clickTransfer(gui, layout);
                check(fixture.name() + "_dispatches_the_jei_action", sent);
                if (fixture.bulk()) gui.onClose();
                stage = Stage.QUEUED;
                return;
            }
        }
    }

    private int copper() { return minecraft.player.getInventory().countItem(Items.COPPER_INGOT); }

    private static boolean hasFixtureFluid(VatMenu menu) {
        var contents = menu.getInputTank().contents();
        return contents.getFluid() == Fluids.WATER && contents.getAmount() == 937;
    }

    private static boolean matches(ItemStack stack, Item item, int amount, int variant) {
        return stack.is(item) && stack.getCount() == amount && stack.hasTag() && stack.getTag().getInt("PortVariant") == variant;
    }

    private void connect() {
        if (!(minecraft.screen instanceof TitleScreen) || minecraft.getOverlay() != null) return;
        if (titleAt == 0) titleAt = System.nanoTime();
        if (System.nanoTime() - titleAt < 2_000_000_000L) return;
        String address = System.getProperty("enderio.clientqa.server");
        ConnectScreen.startConnecting(new TitleScreen(), minecraft, ServerAddress.parseString(address),
            new ServerData("Ender IO shared VAT JEI", address, true), false);
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
        ClientSmoke.complete("client_vat_jei_shared", result);
    }
}
