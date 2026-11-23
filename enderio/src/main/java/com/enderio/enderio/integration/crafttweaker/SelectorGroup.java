package com.enderio.enderio.integration.crafttweaker;

import com.blamejared.crafttweaker.api.CraftTweakerAPI;
import com.blamejared.crafttweaker.api.annotation.ZenRegister;
import com.blamejared.crafttweaker.api.ingredient.IIngredient;
import com.blamejared.crafttweaker.api.ingredient.type.WrappingIIngredient;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraftforge.common.crafting.CompoundIngredient;
import org.openzen.zencode.java.ZenCodeType;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Consumer;

@ZenRegister
@ZenCodeType.Name("mods.enderio.SelectorGroup")
public final class SelectorGroup {
    private static final Map<RecipeManager, Map<Key, Set<ResourceLocation>>> GROUPS = new WeakHashMap<>();
    private final Key key;

    private SelectorGroup(String domain, ResourceLocation id) {
        this.key = new Key(domain, id);
    }

    @ZenCodeType.Method
    public static SelectorGroup conduits(ResourceLocation id) {
        return new SelectorGroup("conduits", id);
    }

    @ZenCodeType.Method
    public static SelectorGroup dimensions(ResourceLocation id) {
        return new SelectorGroup("dimensions", id);
    }

    @ZenCodeType.Getter("members")
    public ResourceLocation[] members() {
        synchronized (GROUPS) {
            return GROUPS.getOrDefault(manager(), Map.of()).getOrDefault(key, Set.of())
                    .toArray(ResourceLocation[]::new);
        }
    }

    @ZenCodeType.Method
    public void add(ResourceLocation... ids) {
        var values = validated(ids);
        edit("Add to", entries -> entries.addAll(values));
    }

    @ZenCodeType.Method
    public void remove(ResourceLocation... ids) {
        var values = List.of(ids);
        edit("Remove from", entries -> entries.removeAll(values));
    }

    @ZenCodeType.Method
    public void replace(ResourceLocation... ids) {
        var values = validated(ids);
        edit("Replace", entries -> {
            entries.clear();
            entries.addAll(values);
        });
    }

    @ZenCodeType.Method
    public IIngredient asIngredient() {
        if (!key.domain().equals("conduits")) {
            throw new IllegalStateException("Only conduit groups form item ingredients");
        }
        var ids = members();
        if (ids.length == 0) {
            throw new IllegalArgumentException("Conduit group " + key.id() + " is empty");
        }
        var ingredients = Arrays.stream(ids).map(EnderIOIngredients::conduitIngredient).toArray(Ingredient[]::new);
        return new WrappingIIngredient(CompoundIngredient.of(ingredients),
                "mods.enderio.SelectorGroup.conduits(<resource:" + key.id() + ">).asIngredient()");
    }

    private List<ResourceLocation> validated(ResourceLocation[] ids) {
        var values = List.of(ids);
        if (key.domain().equals("conduits")) {
            values.forEach(EnderIOIngredients::conduitIngredient);
        }
        return values;
    }

    private void edit(String description, Consumer<Set<ResourceLocation>> change) {
        CraftTweakerAPI.apply(new EnderIOAction(description + " Ender IO " + key.domain() + " group " + key.id(), () -> {
            synchronized (GROUPS) {
                var groups = GROUPS.computeIfAbsent(manager(), ignored -> new HashMap<>());
                var values = groups.computeIfAbsent(key, ignored -> new LinkedHashSet<>());
                change.accept(values);
            }
        }));
    }

    private static RecipeManager manager() {
        return CraftTweakerAPI.getAccessibleElementsProvider().recipeManager();
    }

    private record Key(String domain, ResourceLocation id) {}
}
