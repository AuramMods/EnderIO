package com.enderio.enderio.foundation.block;

import com.enderio.enderio.foundation.block.entity.Wrenchable;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

@EventBusSubscriber(modid = "enderio", bus = EventBusSubscriber.Bus.FORGE)
public final class WrenchableBlockHandler {
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onItemUse(PlayerInteractEvent.RightClickBlock event) {
        if (event.getEntity().isSpectator() || !event.getItemStack().is(Wrenchable.WRENCH_TAG)) return;
        if (event.getUseBlock() == Event.Result.DENY) {
            // Forge otherwise still calls onItemUseFirst, which can edit the same block's side capability.
            event.setUseItem(Event.Result.DENY);
            return;
        }
        if (event.getUseItem() == Event.Result.DENY) return;
        if (event.getLevel().getBlockEntity(event.getPos()) instanceof Wrenchable entity) {
            // Predict consumption only; all mutation occurs on the server exactly once.
            var result = event.getLevel().isClientSide ? InteractionResult.SUCCESS
                : entity.onWrenched(new UseOnContext(event.getEntity(), event.getHand(), event.getHitVec()));
            if (result != InteractionResult.PASS) {
                event.setCancellationResult(result);
                event.setCanceled(true);
            } else {
                // Tagged third-party wrenches must not rotate the machine a second time.
                event.setUseBlock(Event.Result.ALLOW);
                event.setUseItem(Event.Result.DENY);
            }
        }
    }
}
