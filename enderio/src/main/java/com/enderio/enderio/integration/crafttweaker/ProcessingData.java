package com.enderio.enderio.integration.crafttweaker;

import com.blamejared.crafttweaker.api.CraftTweakerAPI;
import com.blamejared.crafttweaker.api.annotation.ZenRegister;
import com.blamejared.crafttweaker.api.item.IItemStack;
import com.blamejared.crafttweaker.api.tag.type.KnownTag;
import com.enderio.core.common.datamap.DataMaps;
import com.enderio.core.common.datamap.RegistryDataMap;
import com.enderio.enderio.api.components.GrindingBallData;
import com.enderio.enderio.foundation.datamap.RangeExtender;
import com.enderio.enderio.foundation.datamap.VatReagent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.block.Block;
import org.openzen.zencode.java.ZenCodeType;

import java.util.HashMap;
import java.util.Map;
import java.util.function.UnaryOperator;

@ZenRegister
@ZenCodeType.Name("mods.enderio.ProcessingData")
public final class ProcessingData {
    private ProcessingData() {}

    @ZenCodeType.Method
    public static void setGrindingBall(IItemStack item, float grinding, float chance, float power, int durability) {
        if (!Float.isFinite(grinding) || grinding < 0 || !Float.isFinite(chance) || chance < 0
                || !Float.isFinite(power) || power <= 0 || durability <= 0) {
            throw new IllegalArgumentException("Grinding balls need nonnegative grinding and chance multipliers, positive power, and positive durability");
        }
        var data = new GrindingBallData(grinding, chance, power, durability);
        edit(GrindingBallData.DATA_MAP_TYPE, itemId(item), old -> data);
    }

    @ZenCodeType.Method
    public static void removeGrindingBall(IItemStack item) {
        edit(GrindingBallData.DATA_MAP_TYPE, itemId(item), old -> null);
    }

    @ZenCodeType.Method
    @ZenCodeType.Nullable
    public static GrindingBallModifiers getGrindingBall(IItemStack item) {
        var data = GrindingBallData.DATA_MAP_TYPE.values(manager()).get(itemId(item));
        return data == null ? null : new GrindingBallModifiers(data);
    }

    @ZenCodeType.Method
    public static void setVatYield(IItemStack item, KnownTag<Item> reagent, double multiplier) {
        if (!Double.isFinite(multiplier) || multiplier <= 0) {
            throw new IllegalArgumentException("VAT yield multiplier must be finite and positive");
        }
        editKey(VatReagent.DATA_MAP, itemId(item), reagent.getTagKey(), multiplier);
    }

    @ZenCodeType.Method
    public static void removeVatYield(IItemStack item, KnownTag<Item> reagent) {
        editKey(VatReagent.DATA_MAP, itemId(item), reagent.getTagKey(), null);
    }

    @ZenCodeType.Method
    public static double getVatYield(IItemStack item, KnownTag<Item> reagent) {
        return VatReagent.DATA_MAP.values(manager()).getOrDefault(itemId(item), Map.of())
                .getOrDefault(reagent.getTagKey(), 1d);
    }

    @ZenCodeType.Method
    public static void setRangeExtension(Block block, KnownTag<Block> target, int distance) {
        editKey(RangeExtender.DATA_MAP, BuiltInRegistries.BLOCK.getKey(block), target.getTagKey(), distance);
    }

    @ZenCodeType.Method
    public static void removeRangeExtension(Block block, KnownTag<Block> target) {
        editKey(RangeExtender.DATA_MAP, BuiltInRegistries.BLOCK.getKey(block), target.getTagKey(), null);
    }

    @ZenCodeType.Method
    public static int getRangeExtension(Block block, KnownTag<Block> target) {
        return RangeExtender.DATA_MAP.values(manager()).getOrDefault(BuiltInRegistries.BLOCK.getKey(block), Map.of())
                .getOrDefault(target.getTagKey(), 0);
    }

    private static RecipeManager manager() {
        return CraftTweakerAPI.getAccessibleElementsProvider().recipeManager();
    }

    private static ResourceLocation itemId(IItemStack item) {
        if (item.isEmpty()) {
            throw new IllegalArgumentException("Processing data needs a nonempty item");
        }
        return BuiltInRegistries.ITEM.getKey(item.getInternal().getItem());
    }

    private static <R, V> void edit(RegistryDataMap<R, V> type, ResourceLocation id, UnaryOperator<V> change) {
        CraftTweakerAPI.apply(new EnderIOAction("Editing " + type.id() + " for " + id,
                () -> DataMaps.edit(manager(), type, id, change)));
    }

    private static <R, K, V> void editKey(RegistryDataMap<R, Map<TagKey<K>, V>> type, ResourceLocation id,
            TagKey<K> key, V value) {
        edit(type, id, old -> {
            var next = old == null ? new HashMap<TagKey<K>, V>() : new HashMap<>(old);
            if (value == null) {
                next.remove(key);
            } else {
                next.put(key, value);
            }
            return next.isEmpty() ? null : Map.copyOf(next);
        });
    }
}
