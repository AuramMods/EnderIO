package com.enderio.port.clientqa;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.enderio.port.clientqa.ClientAlloyProgressionSmoke.id;
import static com.enderio.port.clientqa.ClientAlloyProgressionSmoke.item;

final class ClientPatchouliGuideSmoke {
    enum Guide {
        LASERIO("laserio-guide", "laserio:laseriobook", "laserio:my_book_recipe_shapeless", "laserio:logic_chip", "LaserIO 101"),
        ENDER_IO("enderio-guide", "enderio:guide", "enderio:guide", "minecraft:redstone", "Ender IO Guide");

        final String name;
        final ResourceLocation book;
        final ResourceLocation recipe;
        final ResourceLocation ingredient;
        final String title;

        Guide(String name, String book, String recipe, String ingredient, String title) {
            this.name = name;
            this.book = id(book);
            this.recipe = id(recipe);
            this.ingredient = id(ingredient);
            this.title = title;
        }
    }

    private enum Stage { TITLE, JOIN, TABLE, INGREDIENT, INCOMPLETE, CRAFT, TAKE, OPEN, GUIDE, CATEGORY, ENTRY, TOUR, AFTER_READ, DONE }

    private static final BlockPos TABLE = new BlockPos(4720, 80, 8);
    private static final ResourceLocation FILTERS = id("laserio:filters");
    private static final ResourceLocation BASIC_FILTER = id("laserio:filter_basic");
    static final String PATCHOULI = "vazkii.patchouli.client.book.";
    static final String BOOK_GUI = PATCHOULI + "gui.";

    private final Minecraft minecraft = Minecraft.getInstance();
    private final Path output = Path.of(System.getProperty("enderio.clientqa.output"));
    private final long started = System.nanoTime();
    private final Guide guide;
    private final boolean reopened;
    private final ClientEnderGuidePages pages;
    private final Map<String, Boolean> checks = new LinkedHashMap<>();
    private final Map<String, Object> captures = new LinkedHashMap<>();
    private Stage stage = Stage.TITLE;
    private long titleAt;
    private int waitUntil = -1;

    ClientPatchouliGuideSmoke(Guide guide, boolean reopened) {
        this.guide = guide;
        this.reopened = reopened;
        pages = guide == Guide.ENDER_IO ? new ClientEnderGuidePages(this) : null;
        MinecraftForge.EVENT_BUS.addListener(this::render);
    }

    private void render(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage == Stage.DONE) return;
        try {
            if (minecraft.screen instanceof DisconnectedScreen || System.nanoTime() - started > 145_000_000_000L) {
                throw new AssertionError(guide.name + " stopped at " + stage);
            }
            if (stage == Stage.TITLE) {
                connect();
                return;
            }
            var player = minecraft.player;
            if (player == null || minecraft.level == null) return;
            switch (stage) {
                case JOIN -> {
                    if (minecraft.screen != null || minecraft.getToasts().getToast(SystemToast.class,
                        SystemToast.SystemToastIds.UNSECURE_SERVER_WARNING) != null) return;
                    check("native_dedicated_survival", !player.isCreative() && !player.isSpectator()
                        && !minecraft.getConnection().getConnection().isMemoryConnection());
                    signal("connected");
                    stage = reopened ? Stage.OPEN : Stage.TABLE;
                }
                case TABLE -> openTable();
                case INGREDIENT, INCOMPLETE, CRAFT, TAKE -> craft();
                case OPEN -> {
                    int slot = guideSlot();
                    if (!ready("guide") || slot < 0 || minecraft.screen != null) return;
                    var guide = player.getInventory().getItem(slot);
                    check("book_in_survival_inventory_has_the_stock_identity", guide.getCount() == 1
                        && guide.getTooltipLines(player, TooltipFlag.NORMAL).get(0).getString().equals(this.guide.title));
                    player.getInventory().selected = slot;
                    minecraft.gameMode.useItem(player, InteractionHand.MAIN_HAND);
                    stage = pages == null ? Stage.GUIDE : Stage.TOUR;
                }
                case GUIDE -> guide();
                case CATEGORY -> {
                    var screen = minecraft.screen;
                    if (!screenIs(screen, "GuiBookCategory")) return;
                    check("the_native_category_uses_the_laserio_book", activeBook(screen));
                    capture("laserio-guide-filters");
                    check("the_basic_filter_entry_button_opens_natively",
                        clickEntry(screen, "GuiButtonEntry", "getEntry", BASIC_FILTER));
                    stage = Stage.ENTRY;
                }
                case ENTRY -> {
                    var screen = minecraft.screen;
                    if (!screenIs(screen, "GuiBookEntry")) return;
                    verifyEntry(screen);
                    capture("laserio-guide-basic-filter");
                    check("viewing_the_entry_keeps_the_original_guide", guideSlot() >= 0);
                    signal("read");
                    stage = Stage.AFTER_READ;
                }
                case TOUR -> {
                    if (!pages.step()) return;
                    check("viewing_and_reloading_the_guide_keeps_the_original_stack", guideSlot() >= 0);
                    signal("read");
                    stage = Stage.AFTER_READ;
                }
                case AFTER_READ -> {
                    if (ready("finish")) finish(null);
                }
                default -> {}
            }
        } catch (Throwable failure) {
            failure.printStackTrace();
            finish(failure);
        }
    }

    private void openTable() throws Exception {
        var player = minecraft.player;
        if (!ready("table") || minecraft.screen != null || player.distanceToSqr(Vec3.atBottomCenterOf(TABLE.south(2))) > 0.12) return;
        if (waitUntil < 0) waitUntil = player.tickCount + 6;
        if (player.tickCount < waitUntil) return;
        player.getInventory().selected = 8;
        var hit = new BlockHitResult(Vec3.atCenterOf(TABLE).relative(Direction.SOUTH, 0.5), Direction.SOUTH, TABLE, false);
        check("normal_survival_crafting_table_opens",
            minecraft.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit).consumesAction());
        var recipe = minecraft.level.getRecipeManager().byKey(guide.recipe).orElseThrow();
        check("the_stock_book_recipe_arrives_over_the_native_recipe_packet",
            recipe.getIngredients().size() == 2 && guide(recipe.getResultItem(minecraft.level.registryAccess())));
        waitUntil = -1;
        stage = Stage.INGREDIENT;
    }

    private void craft() throws Exception {
        if (!(minecraft.screen instanceof CraftingScreen screen)) return;
        var player = minecraft.player;
        var menu = screen.getMenu();
        var grid = menu.slots.subList(1, 10);
        switch (stage) {
            case INGREDIENT -> {
                if (!player.getInventory().getItem(0).is(item(guide.ingredient.toString()))) return;
                minecraft.gameMode.handleInventoryMouseClick(menu.containerId, 1, 0, ClickType.SWAP, player);
                stage = Stage.INCOMPLETE;
            }
            case INCOMPLETE -> {
                if (!menu.getSlot(1).getItem().is(item(guide.ingredient.toString()))) return;
                if (waitUntil < 0) waitUntil = player.tickCount + 6;
                if (player.tickCount < waitUntil) return;
                check("one_ingredient_leaves_the_result_empty", !menu.getSlot(0).hasItem());
                minecraft.gameMode.handleInventoryMouseClick(menu.containerId, 2, 1, ClickType.SWAP, player);
                stage = Stage.CRAFT;
            }
            case CRAFT -> {
                var result = menu.getSlot(0).getItem();
                if (!menu.getSlot(2).getItem().is(Items.BOOK) || result.isEmpty()) return;
                check("both_ingredients_preview_one_tagged_guide",
                    guide(result) && result.getCount() == 1 && grid.stream().filter(Slot::hasItem).count() == 2);
                capture(guide.name + "-recipe");
                minecraft.gameMode.handleInventoryMouseClick(menu.containerId, 0, 0, ClickType.QUICK_MOVE, player);
                stage = Stage.TAKE;
            }
            case TAKE -> {
                if (guideSlot() < 0 || grid.stream().anyMatch(Slot::hasItem)) return;
                check("result_take_spends_both_ingredients",
                    player.getInventory().countItem(Items.BOOK) == 0 && player.getInventory().countItem(item(guide.ingredient.toString())) == 0);
                player.closeContainer();
                signal("crafted");
                stage = Stage.OPEN;
            }
            default -> {}
        }
    }

    private void guide() throws Exception {
        var screen = minecraft.screen;
        if (screenIs(screen, "GuiBookLanding")) {
            check("using_the_original_stack_opens_the_laserio_landing_page", activeBook(screen));
            capture("laserio-guide-landing");
            check("the_stock_filters_category_button_opens_natively",
                clickEntry(screen, "GuiButtonCategory", "getCategory", FILTERS));
            stage = Stage.CATEGORY;
        } else if (reopened && screenIs(screen, "GuiBookCategory")) {
            stage = Stage.CATEGORY;
        } else if (reopened && screenIs(screen, "GuiBookEntry")) {
            stage = Stage.ENTRY;
        }
    }

    private void verifyEntry(Screen screen) throws Exception {
        var entry = screen.getClass().getMethod("getEntry").invoke(screen);
        check("the_open_entry_is_the_shipped_basic_filter_entry", activeBook(screen)
            && BASIC_FILTER.equals(entry.getClass().getMethod("getId").invoke(entry)));
        var pages = (List<?>) entry.getClass().getMethod("getPages").invoke(entry);
        var guiEntry = Class.forName(BOOK_GUI + "GuiBookEntry");
        var left = guiEntry.getDeclaredField("leftPage");
        var right = guiEntry.getDeclaredField("rightPage");
        left.setAccessible(true);
        right.setAccessible(true);
        check("both_shipped_basic_filter_pages_are_displayed", pages.size() == 2
            && left.get(screen) == pages.get(0) && right.get(screen) == pages.get(1));
        var text = Class.forName(PATCHOULI + "page.abstr.PageWithText").getDeclaredField("text");
        text.setAccessible(true);
        var asString = Class.forName("vazkii.patchouli.api.IVariable").getMethod("asString");
        check("the_two_live_pages_explain_ghost_slots_and_jei",
            ((String) asString.invoke(text.get(pages.get(0)))).contains("ghost slots")
                && ((String) asString.invoke(text.get(pages.get(1)))).contains("JEI"));
    }

    static boolean clickEntry(Screen screen, String kind, String accessor, ResourceLocation id) throws Exception {
        String buttonClass = BOOK_GUI + "button." + kind;
        for (var child : screen.children()) {
            if (!(child instanceof AbstractWidget button) || !child.getClass().getName().equals(buttonClass)) continue;
            var entry = child.getClass().getMethod(accessor).invoke(child);
            if (id.equals(entry.getClass().getMethod("getId").invoke(entry))) {
                return clickBook(screen, button.getX() + button.getWidth() / 2.0, button.getY() + button.getHeight() / 2.0);
            }
        }
        return false;
    }

    static boolean clickBook(Screen screen, double x, double y) throws Exception {
        var scale = Class.forName(BOOK_GUI + "GuiBook").getDeclaredField("scaleFactor");
        scale.setAccessible(true);
        float factor = scale.getFloat(screen);
        return screen.mouseClicked(x * factor, y * factor, 0);
    }

    boolean activeBook(Screen screen) throws Exception {
        Object book = screen.getClass().getField("book").get(screen);
        return guide.book.equals(book.getClass().getField("id").get(book));
    }

    static boolean screenIs(Screen screen, String kind) {
        return screen != null && screen.getClass().getName().equals(BOOK_GUI + kind);
    }

    private int guideSlot() {
        for (int slot = 0; slot < 9; slot++) {
            if (guide(minecraft.player.getInventory().getItem(slot))) return slot;
        }
        return -1;
    }

    private boolean guide(ItemStack stack) {
        return stack.is(item("patchouli:guide_book")) && stack.hasTag()
            && stack.getTag().getString("patchouli:book").equals(guide.book.toString());
    }

    private void connect() {
        if (!(minecraft.screen instanceof TitleScreen) || minecraft.getOverlay() != null) return;
        if (titleAt == 0) titleAt = System.nanoTime();
        if (System.nanoTime() - titleAt < 2_000_000_000L) return;
        String address = System.getProperty("enderio.clientqa.server");
        ConnectScreen.startConnecting(new TitleScreen(), minecraft, ServerAddress.parseString(address),
            new ServerData(guide.title, address, true), false);
        stage = Stage.JOIN;
    }

    private boolean ready(String name) {
        return Files.exists(output.resolve("server-" + name));
    }

    private void signal(String name) throws Exception {
        var dir = output.resolve("signals");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve(name + ".json"), "{}");
    }

    void capture(String name) throws Exception {
        var frame = ClientSmoke.capture(name + ".png");
        captures.put(name, frame);
        check(name + "_renders_hidden_and_muted", Boolean.TRUE.equals(frame.get("passed")));
    }

    void check(String name, boolean passed) {
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
        ClientSmoke.complete("client_" + guide.name.replace('-', '_'), result);
    }
}
