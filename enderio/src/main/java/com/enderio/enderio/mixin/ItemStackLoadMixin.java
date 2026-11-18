package com.enderio.enderio.mixin;

import com.enderio.enderio.api.EnderIODataComponents;
import com.enderio.enderio.content.conduits.legacy.Forge6ConduitData;
import com.enderio.enderio.init.EIOItems;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemStack.class)
public abstract class ItemStackLoadMixin {
    @Inject(method = "of", at = @At("RETURN"))
    private static void enderio$restoreLegacyConduitType(CompoundTag saved, CallbackInfoReturnable<ItemStack> callback) {
        var stack = callback.getReturnValue();
        if (stack.isEmpty()) return;
        var id = saved.getString("id");
        if (!id.startsWith("enderio:")) return;
        var type = Forge6ConduitData.itemConduitId(id);
        // Forge aliases restore the common item. The original ID still identifies its variant.
        if (type != null && stack.is(EIOItems.CONDUIT.get()) && !EnderIODataComponents.CONDUIT.has(stack)) {
            EnderIODataComponents.CONDUIT.setId(stack, type);
        }
    }
}
