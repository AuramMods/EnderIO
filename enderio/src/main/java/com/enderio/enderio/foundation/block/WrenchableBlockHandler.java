package com.enderio.enderio.foundation.block;

import com.enderio.enderio.foundation.block.entity.Wrenchable;
import com.enderio.enderio.foundation.tag.EIOTags;
import net.minecraft.world.ItemInteractionResult;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.UseItemOnBlockEvent;

@EventBusSubscriber(modid = "enderio", bus = EventBusSubscriber.Bus.FORGE)
public class WrenchableBlockHandler {
    @SubscribeEvent
    public static void onItemUse(UseItemOnBlockEvent event) {
        var level = event.getLevel();

        if (!event.getItemStack().is(EIOTags.Items.WRENCH)) {
            return;
        }

        if (level.getBlockEntity(event.getPos()) instanceof Wrenchable blockEntity) {
            var result = blockEntity.onWrenched(event.getUseOnContext());
            if (result != ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION) {
                event.cancelWithResult(result);
            }
        }
    }
}
