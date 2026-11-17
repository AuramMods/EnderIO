package com.enderio.core.common.recipes;

import com.enderio.core.mixin.RecipeManagerAccessor;
import net.minecraft.world.Container;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.event.TagsUpdatedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.Supplier;

/** Cached recipe-derived data scoped to a native manager and its current recipe/tag snapshot. */
@EventBusSubscriber(modid = "endercore", bus = EventBusSubscriber.Bus.FORGE)
public final class RecipeCache<C extends Container, R extends Recipe<C>, V> {
    private static final AtomicLong TAG_GENERATION = new AtomicLong();
    private final Supplier<RecipeType<R>> type;
    private final Function<List<R>, V> build;
    private final Map<RecipeManager, Entry<V>> entries = new WeakHashMap<>();

    public RecipeCache(Supplier<RecipeType<R>> type, Function<List<R>, V> build) {
        this.type = type;
        this.build = build;
    }

    public synchronized V get(RecipeManager manager) {
        var snapshot = ((RecipeManagerAccessor) manager).endercore$getRecipes();
        long tags = TAG_GENERATION.get();
        var entry = entries.get(manager);
        if (entry == null || entry.snapshot() != snapshot || entry.tags() != tags) {
            var recipes = manager.getAllRecipesFor(type.get());
            for (var recipe : recipes) {
                for (var ingredient : recipe.getIngredients()) {
                    // getItems/test alone do not consume Forge's invalidation counter.
                    // Check first: getStackingIds can otherwise skip it when its cache is null.
                    ingredient.checkInvalidation();
                    ingredient.getStackingIds();
                }
            }
            entry = new Entry<>(snapshot, tags, build.apply(recipes));
            entries.put(manager, entry);
        }
        return entry.value();
    }

    @SubscribeEvent
    public static void onTagsUpdated(TagsUpdatedEvent event) {
        TAG_GENERATION.incrementAndGet();
    }

    private record Entry<V>(Object snapshot, long tags, V value) {}
}
