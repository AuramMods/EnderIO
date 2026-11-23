package com.enderio.enderio.integration.crafttweaker;

import com.blamejared.crafttweaker.api.annotation.ZenRegister;
import com.blamejared.crafttweaker.api.item.IItemStack;
import com.blamejared.crafttweaker.api.tag.type.KnownTag;
import com.enderio.enderio.content.machines.sag_mill.SagMillingRecipe;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import org.openzen.zencode.java.ZenCodeType;

@ZenRegister
@ZenCodeType.Name("mods.enderio.SagOutput")
public record SagOutput(SagMillingRecipe.OutputItem value) {
    @ZenCodeType.Method
    public static SagOutput item(IItemStack stack, float chance, boolean optional) {
        return new SagOutput(SagMillingRecipe.OutputItem.of(
                ProcessingRecipeManager.outputStack(stack), probability(chance), optional));
    }

    @ZenCodeType.Method
    public static SagOutput tag(KnownTag<Item> tag, int count, float chance, boolean optional) {
        TagKey<Item> key = tag.getTagKey();
        return new SagOutput(SagMillingRecipe.OutputItem.of(key,
                ProcessingRecipeManager.positive(count, "Count"), probability(chance), optional));
    }

    private static float probability(float chance) {
        if (!Float.isFinite(chance) || chance < 0 || chance > 1) {
            throw new IllegalArgumentException("SAG output chance must be between 0 and 1");
        }
        return chance;
    }
}
