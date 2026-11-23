package com.enderio.enderio.integration.gamestages;

import com.enderio.enderio.EnderIO;
import com.enderio.enderio.content.conduits.ConduitBlockItem;
import com.enderio.enderio.content.conduits.bundle.ConduitBundleBlock;
import com.enderio.enderio.content.tools.LevitationStaffItem;
import com.enderio.enderio.content.tools.hang_glider.HangGliderItem;
import com.enderio.enderio.content.tools.vials.SoulVialItem;
import com.enderio.enderio.content.travel.TravelStaffItem;
import com.enderio.enderio.foundation.block.MachineBlock;
import com.enderio.enderio.foundation.block.entity.MachineBlockEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = EnderIO.MOD_ID)
final class StageEvents {
    private StageEvents() {}

    @SubscribeEvent
    static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel server)) {
            return;
        }
        var player = event.getEntity() instanceof ServerPlayer actor ? actor : null;
        var block = event.getPlacedBlock().getBlock();
        if (!StageRules.permits(server, player, StageRules.Kind.BLOCK, BuiltInRegistries.BLOCK.getKey(block))
                || block instanceof MachineBlock<?> && !StageRules.permits(server, player, StageRules.Kind.ACTION,
                        StageFeature.MACHINE_PLACEMENT.id())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    static void onBlockUse(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        var level = player.serverLevel();
        var block = level.getBlockState(event.getPos()).getBlock();
        var stack = event.getItemStack();
        if (!StageRules.permits(level, player, StageRules.Kind.BLOCK, BuiltInRegistries.BLOCK.getKey(block))
                || level.getBlockEntity(event.getPos()) instanceof MachineBlockEntity machine
                        && !StageRules.permitsMachineMenu(player, machine)
                || block instanceof ConduitBundleBlock && !(stack.getItem() instanceof ConduitBlockItem)
                        && !StageRules.permitsAction(player, StageFeature.CONDUIT_CONFIGURATION)) {
            event.setUseBlock(Event.Result.DENY);
        }
        if (!permitsTool(player, stack)) {
            event.setUseItem(Event.Result.DENY);
        }
    }

    @SubscribeEvent
    static void onItemUse(PlayerInteractEvent.RightClickItem event) {
        if (event.getEntity() instanceof ServerPlayer player && !permitsTool(player, event.getItemStack())) {
            event.setCancellationResult(InteractionResult.FAIL);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    static void onEntityUse(PlayerInteractEvent.EntityInteract event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            var stack = event.getItemStack();
            if (!StageRules.permitsItem(player, stack) || stack.getItem() instanceof SoulVialItem
                    && !StageRules.permitsAction(player, StageFeature.SOUL_CAPTURE)) {
                event.setCancellationResult(InteractionResult.FAIL);
                event.setCanceled(true);
            }
        }
    }

    private static boolean permitsTool(ServerPlayer player, ItemStack stack) {
        if (!StageRules.permitsItem(player, stack)) {
            return false;
        }
        var item = stack.getItem();
        if (item instanceof ConduitBlockItem) {
            return StageRules.permitsAction(player, StageFeature.CONDUIT_PLACEMENT);
        }
        if (item instanceof TravelStaffItem) {
            return StageRules.permitsAction(player, StageFeature.TRAVEL);
        }
        if (item instanceof HangGliderItem || item instanceof LevitationStaffItem) {
            return StageRules.permitsAction(player, StageFeature.MOBILITY);
        }
        return true;
    }
}
