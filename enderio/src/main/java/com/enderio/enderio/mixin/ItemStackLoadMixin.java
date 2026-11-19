package com.enderio.enderio.mixin;

import com.enderio.enderio.api.EnderIODataComponents;
import com.enderio.enderio.content.broken_spawner.BrokenSpawnerItem;
import com.enderio.enderio.content.conduits.legacy.Forge6ConduitData;
import com.enderio.enderio.content.filters.Forge6TransferFilterData;
import com.enderio.enderio.content.filters.fluid.EnderFluidFilterItem;
import com.enderio.enderio.content.filters.item.general.EnderItemFilterItem;
import com.enderio.enderio.content.filters.redstone.RedstoneFilterItem;
import com.enderio.enderio.content.filters.soul.EnderSoulFilterItem;
import com.enderio.enderio.content.paint.PaintUtils;
import com.enderio.enderio.content.paint.item.PaintedBlockItem;
import com.enderio.enderio.content.tools.LevitationStaffItem;
import com.enderio.enderio.content.tools.vials.SoulVialItem;
import com.enderio.enderio.foundation.io.Forge6MachineItemData;
import com.enderio.enderio.foundation.soul.Forge6SoulData;
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
    private static void enderio$restoreLegacyItemData(CompoundTag saved, CallbackInfoReturnable<ItemStack> callback) {
        var stack = callback.getReturnValue();
        if (stack.isEmpty()) return;
        var id = saved.getString("id");
        if (!id.startsWith("enderio:")) return;
        boolean soulMachine = id.equals("enderio:powered_spawner") || id.equals("enderio:soul_engine");
        if (soulMachine || stack.getItem() instanceof SoulVialItem || stack.getItem() instanceof BrokenSpawnerItem) {
            Forge6SoulData.restoreItem(stack, soulMachine);
            return;
        }
        boolean poweredMachine = id.equals("enderio:painting_machine") || id.equals("enderio:impulse_hopper")
            || id.equals("enderio:sag_mill");
        if (poweredMachine || id.equals("enderio:xp_obelisk")) {
            Forge6MachineItemData.restoreMachineItem(stack, poweredMachine);
            return;
        }
        if (id.equals("enderio:aversion_obelisk") || id.equals("enderio:drain") || id.equals("enderio:vacuum_chest")
            || id.equals("enderio:xp_vacuum")) {
            Forge6MachineItemData.restoreRangedItem(stack);
            return;
        }
        if (stack.getItem() instanceof LevitationStaffItem) {
            LevitationStaffItem.restoreForge6State(stack);
            return;
        }
        if (stack.getItem() instanceof EnderSoulFilterItem) {
            Forge6SoulData.restoreFilterItem(stack);
            return;
        }
        if (stack.getItem() instanceof EnderItemFilterItem) {
            Forge6TransferFilterData.restoreItemFilter(stack);
            return;
        }
        if (stack.getItem() instanceof EnderFluidFilterItem) {
            Forge6TransferFilterData.restoreFluidFilter(stack);
            return;
        }
        if (stack.getItem() instanceof RedstoneFilterItem filter) {
            filter.restoreForge6State(stack);
            return;
        }
        if (stack.getItem() instanceof PaintedBlockItem) {
            PaintUtils.restoreLegacyItemPaint(stack);
            return;
        }
        var type = Forge6ConduitData.itemConduitId(id);
        // Forge aliases restore the common item. The original ID still identifies its variant.
        if (type != null && stack.is(EIOItems.CONDUIT.get()) && !EnderIODataComponents.CONDUIT.has(stack)) {
            EnderIODataComponents.CONDUIT.setId(stack, type);
        }
    }
}
