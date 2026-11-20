package com.enderio.port.clientqa;

import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static com.enderio.port.clientqa.ClientAlloyProgressionSmoke.id;
import static com.enderio.port.clientqa.ClientPatchouliGuideSmoke.BOOK_GUI;
import static com.enderio.port.clientqa.ClientPatchouliGuideSmoke.PATCHOULI;
import static com.enderio.port.clientqa.ClientPatchouliGuideSmoke.clickBook;
import static com.enderio.port.clientqa.ClientPatchouliGuideSmoke.screenIs;

/** Tours the book through the same buttons and text links available to a player. */
final class ClientEnderGuidePages {
    private static final String RESOURCES = "patchouli_books/guide/en_us/";
    private static final Map<LinkSource, ResourceLocation> LINKS = Map.of(
        new LinkSource(id("enderio:getting_started/guidebook"), 0), id("enderio:getting_started/grains_of_infinity"),
        new LinkSource(id("enderio:getting_started/first_energy_line"), 1), id("enderio:power/energy_transfer"),
        new LinkSource(id("enderio:power/energy_transfer"), 3), id("enderio:conduits/bundles"));

    private final ClientPatchouliGuideSmoke smoke;
    private final Minecraft minecraft = Minecraft.getInstance();
    private final Set<ResourceLocation> seenCategories = new HashSet<>();
    private final Set<ResourceLocation> seenEntries = new HashSet<>();
    private final Set<LinkSource> visitedLinks = new HashSet<>();
    private final Set<String> frames = new HashSet<>();
    private Map<?, ?> categories;
    private Map<?, ?> entries;
    private CompletableFuture<Void> reload;
    private boolean reopened;
    private LinkSource activeLink;

    ClientEnderGuidePages(ClientPatchouliGuideSmoke smoke) {
        this.smoke = smoke;
    }

    boolean step() throws Exception {
        if (reload != null && !reopened) {
            if (!reload.isDone() || minecraft.getOverlay() != null) return false;
            reload.join();
            minecraft.gameMode.useItem(minecraft.player, InteractionHand.MAIN_HAND);
            reopened = true;
            return false;
        }
        var screen = minecraft.screen;
        if (screenIs(screen, "GuiBookLanding")) {
            landing(screen);
            if (reopened) return true;
        } else if (screenIs(screen, "GuiBookCategory")) {
            if (reopened) {
                back(screen);
            } else {
                category(screen);
            }
        } else if (screenIs(screen, "GuiBookEntry")) {
            entry(screen);
        }
        return false;
    }

    private void landing(Screen screen) throws Exception {
        var book = screen.getClass().getField("book").get(screen);
        var contents = book.getClass().getMethod("getContents").invoke(book);
        String phase = reopened ? "reloaded" : "initial";
        smoke.check(phase + "_landing_has_the_correct_book_title_and_no_load_error",
            smoke.activeBook(screen) && screen.getTitle().getString().equals("Ender IO Guide")
                && !(Boolean) contents.getClass().getMethod("isErrored").invoke(contents));
        categories = (Map<?, ?>) contents.getClass().getField("categories").get(contents);
        entries = (Map<?, ?>) contents.getClass().getField("entries").get(contents);
        smoke.check(phase + "_client_loaded_every_packaged_category_and_entry",
            categories.keySet().equals(resourceIds("categories")) && entries.keySet().equals(resourceIds("entries")));
        checkText("landing", field(BOOK_GUI + "GuiBookLanding", "text").get(screen), true);
        capture(reopened ? "landing-reloaded" : "landing-initial-" + spread(screen));
        if (reopened || openUnvisited(screen, "GuiButtonCategory", "getCategory", seenCategories)) return;
        if (pageForward(screen)) return;
        smoke.check("every_category_and_entry_was_opened_through_its_button",
            seenCategories.equals(categories.keySet()) && seenEntries.equals(entries.keySet()));
        smoke.check("inline_links_opened_the_expected_entries", visitedLinks.equals(LINKS.keySet()));
        minecraft.setScreen(null);
        reload = minecraft.reloadResourcePacks();
    }

    private void category(Screen screen) throws Exception {
        var category = field(BOOK_GUI + "GuiBookCategory", "category").get(screen);
        var categoryId = identifier(category);
        seenCategories.add(categoryId);
        var frame = "category-" + categoryId.getPath() + "-" + spread(screen);
        checkText(frame, field(BOOK_GUI + "GuiBookEntryList", "text").get(screen), false);
        capture(frame);
        if (!openUnvisited(screen, "GuiButtonEntry", "getEntry", seenEntries) && !pageForward(screen)) back(screen);
    }

    private void entry(Screen screen) throws Exception {
        var entry = screen.getClass().getMethod("getEntry").invoke(screen);
        var entryId = identifier(entry);
        if (activeLink != null) {
            String linked = "linked-" + entryId.getPath().replace('/', '-');
            smoke.check(linked + "_arrived_at_the_expected_entry", entryId.equals(LINKS.get(activeLink)));
            capture(linked);
            visitedLinks.add(activeLink);
            activeLink = null;
            back(screen);
            return;
        }
        var pages = (List<?>) entry.getClass().getMethod("getPages").invoke(entry);
        int spread = spread(screen);
        String frame = "entry-" + entryId.getPath().replace('/', '-') + "-" + spread;
        var left = field(BOOK_GUI + "GuiBookEntry", "leftPage").get(screen);
        var right = field(BOOK_GUI + "GuiBookEntry", "rightPage").get(screen);
        smoke.check(frame + "_displays_the_expected_pages", pages.get(spread * 2) == left
            && (spread * 2 + 1 < pages.size() ? pages.get(spread * 2 + 1) == right : right == null));
        var text = verifyPage(frame + "-left", left);
        if (right != null) verifyPage(frame + "-right", right);
        capture(frame);
        var link = new LinkSource(entryId, spread);
        if (LINKS.containsKey(link) && !visitedLinks.contains(link)) {
            clickLink(frame, screen, left, text);
            activeLink = link;
        } else if (!pageForward(screen)) {
            seenEntries.add(entryId);
            back(screen);
        }
    }

    private Object verifyPage(String name, Object page) throws Exception {
        var source = (JsonObject) page.getClass().getField("sourceObject").get(page);
        if (source.has("recipe")) {
            checkRecipe(name, page, source, "recipe", "recipe1");
            checkRecipe(name, page, source, "recipe2", "recipe2");
        }
        if (source.has("item")) {
            smoke.check(name + "_resolved_spotlight_item",
                field(PATCHOULI + "page.PageSpotlight", "ingredient").get(page) instanceof Ingredient ingredient
                    && !ingredient.isEmpty() && ingredient.getItems().length > 0);
        }
        var text = field(PATCHOULI + "page.abstr.PageWithText", "textRender").get(page);
        checkText(name, text, source.has("text") && !source.get("text").getAsString().isEmpty());
        return text;
    }

    private void checkRecipe(String name, Object page, JsonObject source, String key, String fieldName) throws Exception {
        if (!source.has(key)) return;
        var recipeId = id(source.get(key).getAsString());
        String recipePage = PATCHOULI + "page.abstr.PageDoubleRecipe";
        var recipe = field(recipePage, fieldName).get(page);
        smoke.check(name + "_" + key + "_uses_a_live_recipe", recipe instanceof Recipe<?> live && live.getId().equals(recipeId)
            && !live.getResultItem(minecraft.level.registryAccess()).isEmpty());
        var title = (Component) field(recipePage, key.equals("recipe") ? "title1" : "title2").get(page);
        var book = page.getClass().getField("book").get(page);
        var bookFont = (Style) book.getClass().getMethod("getFontStyle").invoke(book);
        smoke.check(name + "_" + key + "_title_fits_the_page",
            minecraft.font.width(title.copy().withStyle(bookFont)) <= 116);
    }

    private void checkText(String name, Object renderer, boolean required) throws Exception {
        var words = words(renderer);
        var text = new StringBuilder();
        int bottom = 0;
        for (var word : words) {
            var type = word.getClass();
            bottom = Math.max(bottom, type.getField("y").getInt(word) + type.getField("height").getInt(word));
            text.append(((Component) field(type.getName(), "text").get(word)).getString()).append(' ');
        }
        smoke.check(name + "_text_fits_at_full_size", bottom <= 156
            && field(BOOK_GUI + "BookTextRenderer", "scale").getFloat(renderer) == 1.0F
            && (!required || !words.isEmpty()) && !text.toString().contains("guidebook.enderio."));
    }

    private static List<?> words(Object renderer) throws Exception {
        return (List<?>) field(BOOK_GUI + "BookTextRenderer", "words").get(renderer);
    }

    private void clickLink(String name, Screen screen, Object page, Object renderer) throws Exception {
        Class<?> bookGui = Class.forName(BOOK_GUI + "GuiBook");
        int left = bookGui.getField("bookLeft").getInt(screen) + page.getClass().getField("left").getInt(page);
        int top = bookGui.getField("bookTop").getInt(screen) + page.getClass().getField("top").getInt(page);
        for (var word : words(renderer)) {
            Class<?> type = word.getClass();
            if (field(type.getName(), "onClick").get(word) == null) continue;
            double x = left + type.getField("x").getInt(word) + 1;
            double y = top + type.getField("y").getInt(word) + 1;
            smoke.check(name + "_inline_link_accepts_a_native_click", clickBook(screen, x, y));
            return;
        }
        throw new AssertionError("No link found on " + name);
    }

    private static boolean openUnvisited(Screen screen, String kind, String accessor, Set<ResourceLocation> visited) throws Exception {
        for (var child : screen.children()) {
            if (!(child instanceof AbstractWidget widget) || !child.getClass().getName().equals(BOOK_GUI + "button." + kind)) continue;
            var item = child.getClass().getMethod(accessor).invoke(child);
            if (item == null) continue; // The landing page's general index has no category.
            var target = identifier(item);
            if (!visited.contains(target)) {
                if (!clickBook(screen, widget.getX() + widget.getWidth() / 2.0, widget.getY() + widget.getHeight() / 2.0)) {
                    throw new AssertionError("Could not click " + target);
                }
                return true;
            }
        }
        return false;
    }

    private static boolean pageForward(Screen screen) throws Exception {
        int current = spread(screen);
        if (!(Boolean) screen.getClass().getMethod("canSeePageButton", boolean.class).invoke(screen, false)) return false;
        screen.mouseScrolled(0, 0, -1);
        if (spread(screen) != current + 1) throw new AssertionError("Next spread did not open");
        return true;
    }

    private static void back(Screen screen) {
        screen.mouseClicked(0, 0, 1);
        if (Minecraft.getInstance().screen == screen) throw new AssertionError("Back did not leave " + screen);
    }

    private Set<ResourceLocation> resourceIds(String kind) {
        String path = RESOURCES + kind;
        var ids = new HashSet<ResourceLocation>();
        for (var resource : minecraft.getResourceManager().listResources(path, key ->
            key.getNamespace().equals("enderio") && key.getPath().endsWith(".json")).keySet()) {
            ids.add(new ResourceLocation(resource.getNamespace(), resource.getPath().substring(path.length() + 1, resource.getPath().length() - 5)));
        }
        return ids;
    }

    private void capture(String frame) throws Exception {
        if (frames.add(frame)) smoke.capture(frame);
    }

    private static int spread(Screen screen) throws Exception {
        return (Integer) screen.getClass().getMethod("getSpread").invoke(screen);
    }

    private static ResourceLocation identifier(Object value) throws Exception {
        return (ResourceLocation) value.getClass().getMethod("getId").invoke(value);
    }

    private static Field field(String type, String name) throws ReflectiveOperationException {
        var field = Class.forName(type).getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private record LinkSource(ResourceLocation entry, int spread) {}
}
