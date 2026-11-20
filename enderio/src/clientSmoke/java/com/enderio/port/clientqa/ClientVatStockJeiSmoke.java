package com.enderio.port.clientqa;

import com.enderio.enderio.client.content.machines.gui.screen.VatScreen;
import com.enderio.enderio.compat.jei_machines_to_merge.category.VATCategory;
import com.enderio.enderio.content.machines.vat.FermentingRecipe;
import com.enderio.enderio.init.EIOBlocks;
import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.forge.ForgeTypes;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

final class ClientVatStockJeiSmoke {
    private enum Stage { TITLE, JOIN, OPEN, MENU, CARD, TRANSFERRED, PAUSED, DONE }

    private record Card(String name, String recipe, int x, int z, List<String> reagents, List<Double> modifiers,
                        String source, @SerializedName("input_amount") int inputAmount, String result,
                        @SerializedName("base_amount") int baseAmount, @SerializedName("output_amount") int outputAmount, int ticks,
                        boolean reversed, List<Map<String, Double>> groups) {
        BlockPos pos() { return new BlockPos(x, 80, z); }
        int slotInCard(int slot) { return reversed ? 1 - slot : slot; }
    }

    private final Minecraft minecraft = Minecraft.getInstance();
    private final Path output = Path.of(System.getProperty("enderio.clientqa.output"));
    private final Map<String, Boolean> checks = new LinkedHashMap<>();
    private final Map<String, Object> captures = new LinkedHashMap<>();
    private final Map<String, Object> observedCards = new LinkedHashMap<>();
    private final long started = System.nanoTime();
    private Stage stage = Stage.TITLE;
    private long titleAt;
    private Card[] cards;
    private int index;

    ClientVatStockJeiSmoke() {
        MinecraftForge.EVENT_BUS.addListener(this::render);
    }

    private void render(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage == Stage.DONE) return;
        try {
            if (minecraft.screen instanceof DisconnectedScreen || System.nanoTime() - started > 200_000_000_000L) {
                throw new AssertionError("Stock VAT JEI stopped during " + stage + " on recipe " + index);
            }
            if (stage == Stage.TITLE) {
                connect();
                return;
            }
            var player = minecraft.player;
            var runtime = ClientSoulEngineFuelsSmoke.JeiProbe.runtime();
            if (player == null || minecraft.level == null || runtime == null) return;
            if (stage == Stage.JOIN) {
                if (minecraft.screen != null || minecraft.getToasts().getToast(SystemToast.class,
                    SystemToast.SystemToastIds.UNSECURE_SERVER_WARNING) != null) return;
                check("dedicated_survival_tcp", !player.isCreative() && !player.isSpectator()
                    && minecraft.getSingleplayerServer() == null && !minecraft.getConnection().getConnection().isMemoryConnection());
                signal("connected");
                stage = Stage.OPEN;
                return;
            }
            if (cards == null) {
                var fixture = output.resolve("stock-vat-cards.json");
                if (!Files.exists(fixture)) return;
                cards = new Gson().fromJson(Files.readString(fixture), Card[].class);
                var recipes = runtime.getRecipeManager().createRecipeLookup(VATCategory.TYPE).get().toList();
                var expected = List.of(cards).stream().map(Card::recipe).collect(Collectors.toSet());
                check("jei_indexes_all_ten_stock_recipes", expected.size() == 10
                    && recipes.stream().map(recipe -> recipe.getId().toString()).collect(Collectors.toSet()).equals(expected));
                check("vat_is_the_recipe_catalyst", runtime.getRecipeManager().createRecipeCatalystLookup(VATCategory.TYPE).get()
                    .flatMap(i -> i.getItemStack().stream()).anyMatch(s -> s.is(EIOBlocks.VAT.asItem())));
            }
            var card = cards[index];
            if (stage == Stage.OPEN) {
                var pos = card.pos();
                if (minecraft.screen != null || !Files.exists(serverFile(card, "ready"))
                    || player.distanceToSqr(Vec3.atBottomCenterOf(pos.south(2))) > 0.15) return;
                var hit = new BlockHitResult(Vec3.atCenterOf(pos).relative(Direction.SOUTH, 0.5), Direction.SOUTH, pos, false);
                check(card.name() + "_opens_the_real_vat_menu",
                    minecraft.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit).consumesAction());
                stage = Stage.MENU;
            } else if (stage == Stage.CARD) {
                if (minecraft.screen instanceof RecipesGui gui) verifyAndTransfer(gui, runtime, card);
            } else if (minecraft.screen instanceof VatScreen screen) {
                checkMenu(screen, runtime, card);
            }
        } catch (Throwable failure) {
            failure.printStackTrace();
            finish(failure);
        }
    }

    private void checkMenu(VatScreen screen, IJeiRuntime runtime, Card card) throws IOException {
        var menu = screen.getMenu();
        if (menu.getInputTank() == null || menu.getOutputTank() == null) return;
        var left = menu.getSlot(0).getItem();
        var right = menu.getSlot(1).getItem();
        if (stage == Stage.MENU) {
            if (!fluidMatches(menu.getInputTank().contents(), card.source(), card.inputAmount() + 137)
                || !right.isEmpty() || (card.reversed() ? !matchesReagent(left, card, 0) : !left.isEmpty())) return;
            openRecipe(runtime, card);
            stage = Stage.CARD;
        } else if (stage == Stage.TRANSFERRED) {
            if (left.isEmpty() || right.isEmpty()) return;
            check(card.name() + "_transfers_both_marked_reagents_into_the_expected_slots",
                matchesReagent(left, card, 0) && matchesReagent(right, card, 1) && menu.getCraftingProgress() == 0);
            signal(card.name() + "-transferred");
            stage = Stage.PAUSED;
        } else if (stage == Stage.PAUSED && Files.exists(serverFile(card, "paused"))) {
            int elapsed = Integer.parseInt(Files.readString(serverFile(card, "paused")));
            var recipe = menu.getRecipe();
            if (recipe == null || Math.abs(menu.getCraftingProgress() - elapsed / (float) card.ticks()) > 0.00001f) return;
            check(card.name() + "_syncs_exact_recipe_duration_input_and_reagent_modifiers",
                card.recipe().equals(recipe.getId().toString()) && recipe.ticks() == card.ticks()
                    && fluidMatches(menu.getInputTank().contents(), card.source(), card.inputAmount() + 137)
                    && recipe.getSlotModifier(minecraft.level, left, right, 0) == card.modifiers().get(0)
                    && recipe.getSlotModifier(minecraft.level, left, right, 1) == card.modifiers().get(1)
                    && menu.getOutputTank().contents().isEmpty() && menu.getPendingOutput().isEmpty());
            capture(card.name() + "-paused");
            minecraft.player.closeContainer();
            signal(card.name() + "-checked");
            index++;
            if (index == cards.length) finish(null);
            else stage = Stage.OPEN;
        }
    }

    private void openRecipe(IJeiRuntime runtime, Card card) {
        var manager = runtime.getRecipeManager();
        var category = manager.getRecipeCategory(VATCategory.TYPE);
        var recipe = manager.createRecipeLookup(VATCategory.TYPE).get()
            .filter(r -> card.recipe().equals(r.getId().toString())).findFirst().orElseThrow();
        var factory = runtime.getJeiHelpers().getFocusFactory();
        var resultFocus = factory.createFocus(RecipeIngredientRole.OUTPUT, ForgeTypes.FLUID_STACK, recipe.output());
        check(card.name() + "_output_fluid_finds_the_stock_recipe",
            manager.createRecipeLookup(VATCategory.TYPE).limitFocus(List.of(resultFocus)).get()
                .anyMatch(r -> card.recipe().equals(r.getId().toString())));
        var unfocused = manager.createRecipeLayoutDrawable(category, recipe, factory.getEmptyFocusGroup()).orElseThrow();
        var inputs = unfocused.getRecipeSlotsView().getSlotViews(RecipeIngredientRole.INPUT);
        check(card.name() + "_unfocused_slots_match_all_server_tag_members",
            inputs.size() == 3 && itemIds(inputs.get(0).getItemStacks().toList()).equals(card.groups().get(0).keySet())
                && itemIds(inputs.get(1).getItemStacks().toList()).equals(card.groups().get(1).keySet()));
        check(card.name() + "_all_reagent_tooltip_modifiers_match_the_server_data_map",
            inputs.get(0).getItemStacks().allMatch(stack -> VATCategory.getModifier(stack, recipe.firstReagent())
                == card.groups().get(0).get(itemId(stack)))
                && inputs.get(1).getItemStacks().allMatch(stack -> VATCategory.getModifier(stack, recipe.secondReagent())
                    == card.groups().get(1).get(itemId(stack))));
        var focuses = card.reagents().stream().<IFocus<?>>map(item -> factory.createFocus(RecipeIngredientRole.INPUT, VanillaTypes.ITEM_STACK,
            new ItemStack(ForgeRegistries.ITEMS.getValue(ResourceLocation.parse(item))))).toList();
        runtime.getRecipesGui().showRecipes(category, List.of(recipe), focuses);
    }

    private void verifyAndTransfer(RecipesGui gui, IJeiRuntime runtime, Card card) throws IOException {
        var area = gui.getArea();
        for (int y = area.getY(); y < area.getY() + area.getHeight(); y += 8) {
            for (int x = area.getX(); x < area.getX() + area.getWidth(); x += 8) {
                if (!(JeiLayoutAccess.underMouse(gui, x, y).orElse(null) instanceof RecipeLayoutWithButtons<?> frame)
                    || !(frame.recipeLayout().getRecipe() instanceof FermentingRecipe recipe)
                    || !card.recipe().equals(recipe.getId().toString()) || !frame.transferButton().isInitialized()) continue;
                var view = frame.recipeLayout().getRecipeSlotsView();
                var inputs = view.getSlotViews(RecipeIngredientRole.INPUT);
                var fluids = inputs.get(2).getIngredients(ForgeTypes.FLUID_STACK).toList();
                var result = view.getSlotViews(RecipeIngredientRole.OUTPUT).get(0)
                    .getDisplayedIngredient(ForgeTypes.FLUID_STACK).orElseThrow();
                check(card.name() + "_stock_card_shows_exact_sized_input_and_base_output",
                    fluids.size() == 1 && fluidMatches(fluids.get(0), card.source(), card.inputAmount())
                        && fluidMatches(result, card.result(), card.baseAmount()) && recipe.ticks() == card.ticks());
                var labels = new ModifierLabels(minecraft);
                runtime.getRecipeManager().getRecipeCategory(VATCategory.TYPE).draw(recipe, view, labels, 0, 0);
                var expectedLabels = List.of("x" + card.modifiers().get(card.slotInCard(0)),
                    "x" + card.modifiers().get(card.slotInCard(1)));
                check(card.name() + "_focused_card_uses_the_live_server_modifiers",
                    itemId(inputs.get(0).getDisplayedItemStack().orElseThrow()).equals(card.reagents().get(card.slotInCard(0)))
                        && itemId(inputs.get(1).getDisplayedItemStack().orElseThrow()).equals(card.reagents().get(card.slotInCard(1)))
                        && labels.values.equals(expectedLabels));
                var rect = frame.recipeLayout().getRect();
                var tooltip = new JeiTooltip();
                frame.recipeLayout().getRecipeSlotUnderMouse(rect.getX() + 85, rect.getY() + 10).orElseThrow().getTooltip(tooltip);
                check(card.name() + "_right_reagent_tooltip_agrees_with_the_drawn_multiplier",
                    tooltip.toLegacyToComponents().stream().map(Component::getString).anyMatch(expectedLabels.get(1)::equals));
                check(card.name() + "_bases_the_expected_yield_on_the_displayed_multipliers",
                    (int) (result.getAmount() * (card.modifiers().get(0) * card.modifiers().get(1))) == card.outputAmount());
                observedCards.put(card.name(), Map.of("labels", labels.values, "baseAmount", result.getAmount(), "batch", card.outputAmount()));
                capture(card.name() + "-recipe");
                check(card.name() + "_uses_the_native_jei_transfer_button", frame.transferButton().getMissingCountHint() == 0
                    && ClientSagJeiSmoke.clickTransfer(gui, frame));
                stage = Stage.TRANSFERRED;
                return;
            }
        }
    }

    private static boolean matchesReagent(ItemStack stack, Card card, int slot) {
        return itemId(stack).equals(card.reagents().get(slot)) && stack.getCount() == 1
            && stack.hasTag() && stack.getTag().contains("VatStockSlot") && stack.getTag().getInt("VatStockSlot") == slot;
    }

    private static Set<String> itemIds(List<ItemStack> stacks) {
        return stacks.stream().map(ClientVatStockJeiSmoke::itemId).collect(Collectors.toSet());
    }

    private static String itemId(ItemStack stack) {
        return String.valueOf(ForgeRegistries.ITEMS.getKey(stack.getItem()));
    }

    private static boolean fluidMatches(FluidStack stack, String fluid, int amount) {
        return fluid.equals(String.valueOf(ForgeRegistries.FLUIDS.getKey(stack.getFluid()))) && stack.getAmount() == amount;
    }

    private static final class ModifierLabels extends GuiGraphics {
        private final List<String> values = new ArrayList<>();

        private ModifierLabels(Minecraft minecraft) {
            super(minecraft, minecraft.renderBuffers().bufferSource());
        }

        @Override
        public int drawString(Font font, String text, int x, int y, int color, boolean shadow) {
            values.add(text);
            return font.width(text);
        }
    }

    private void connect() {
        if (!(minecraft.screen instanceof TitleScreen) || minecraft.getOverlay() != null) return;
        if (titleAt == 0) titleAt = System.nanoTime();
        if (System.nanoTime() - titleAt < 2_000_000_000L) return;
        var address = System.getProperty("enderio.clientqa.server");
        ConnectScreen.startConnecting(new TitleScreen(), minecraft, ServerAddress.parseString(address),
            new ServerData("Ender IO stock VAT JEI", address, true), false);
        stage = Stage.JOIN;
    }

    private Path serverFile(Card card, String phase) {
        return output.resolve("server-" + card.name() + "-" + phase);
    }

    private void capture(String name) throws IOException {
        var frame = ClientSmoke.capture(name + ".png");
        captures.put(name, frame);
        check(name + "_quiet_hidden_frame", Boolean.TRUE.equals(frame.get("passed")));
    }

    private void signal(String name) throws IOException {
        var directory = output.resolve("signals");
        Files.createDirectories(directory);
        var tmp = directory.resolve(name + ".tmp");
        Files.writeString(tmp, "{}");
        Files.move(tmp, directory.resolve(name + ".json"));
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
        result.put("cards", observedCards);
        if (failure != null) result.put("error", failure.toString());
        ClientSmoke.complete("client_vat_jei_stock", result);
    }
}
