package com.enderio.enderio.compat.ftb_ultimine;

import com.enderio.enderio.init.EIOBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Native hooks are supplied by the optional Ultimine mixins for the pinned 2001 API. */
public final class FTBUltimineCompat {
    private static final Map<Player, BlockHitResult> HITS = new ConcurrentHashMap<>();

    public static void init() {
        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> finishBreaking(event.getEntity()));
    }

    public static void captureHit(ServerPlayer player, HitResult hit) {
        finishBreaking(player);
        if (hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK) HITS.put(player, block);
    }

    public static boolean breakBlock(ServerPlayerGameMode gameMode, ServerPlayer player, BlockPos pos) {
        var level = player.serverLevel();
        var state = level.getBlockState(pos);
        if (!state.is(EIOBlocks.CONDUIT_BUNDLE.get())) return gameMode.destroyBlock(pos);
        var hit = HITS.get(player);
        if (hit == null || player.blockActionRestricted(level, pos, gameMode.getGameModeForPlayer())
            || !level.mayInteract(player, pos)
            || ForgeHooks.onBlockBreakEvent(level, gameMode.getGameModeForPlayer(), player, pos) == -1) return false;
        var result = ConduitBlockBreakHandler.INSTANCE.breakBlock(player, pos, state, hit);
        return result == InteractionResult.SUCCESS || result == InteractionResult.CONSUME;
    }

    public static void finishBreaking(Player player) {
        HITS.remove(player);
        ConduitBlockBreakHandler.INSTANCE.postBreak(player);
    }
}
