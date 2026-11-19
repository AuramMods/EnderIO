package com.enderio.enderio.content.tools.hang_glider;

import com.enderio.core.annotations.UseOnly;
import com.enderio.enderio.api.glider.GliderMovementInfo;
import com.enderio.enderio.api.integration.IntegrationManager;
import com.enderio.enderio.content.tools.ToolsLang;
import com.enderio.enderio.init.EIOCriterions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

@EventBusSubscriber(modid = "enderio", bus = EventBusSubscriber.Bus.FORGE)
public class PlayerMovementHandler {

    /**
     * {@linkplain net.minecraft.world.entity.LivingEntity#travel} 0.91 multiplicator
     */
    private static final double AIR_FRICTION_COEFFICIENT = 1 / 0.91D;

    private static final double MOVEMENT_CHANGE_EFFECT = 0.05d;
    private static final int DEPLOYMENT_DELAY = 12;

    @UseOnly(LogicalSide.CLIENT)
    private static final Map<Player, FallState> CLIENT_FALLS = new WeakHashMap<>();
    @UseOnly(LogicalSide.SERVER)
    private static final Map<Player, FallState> SERVER_FALLS = new WeakHashMap<>();

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent playerTickEvent) {
        if (playerTickEvent.phase != TickEvent.Phase.START) {
            return;
        }
        Player player = playerTickEvent.player;
        FallState fall = falls(player).computeIfAbsent(player, ignored -> new FallState());
        fall.sample(player);

        if (player.level().isClientSide() && !player.isLocalPlayer()) {
            return;
        }
        Optional<GliderMovementInfo> gliderMovementInfoOpt = calculateGliderMovementInfo(player, true, fall);
        if (gliderMovementInfoOpt.isEmpty()) {
            return;
        }

        GliderMovementInfo gliderMovementInfo = gliderMovementInfoOpt.get();
        player.resetFallDistance();
        if (player instanceof ServerPlayer serverPlayer) {
            EIOCriterions.USE_GLIDER.get().trigger(serverPlayer);
        } else {
            steer(player, gliderMovementInfo);
            ClientClassLoadingProtection.playSound(player);
        }
        gliderMovementInfo.cause().onHangGliderTick(player);
    }

    private static void steer(Player player, GliderMovementInfo gliderMovementInfo) {
        double verticalSpeed = gliderMovementInfo.fallSpeed();
        if (player.isSprinting()) {
            verticalSpeed *= 3;
        }

        double oldHorizontalSpeed = player.getDeltaMovement().horizontalDistance();
        double x = Math.cos(Math.toRadians(player.yHeadRot + 90))
                * (gliderMovementInfo.acceleration() + oldHorizontalSpeed * MOVEMENT_CHANGE_EFFECT);
        double z = Math.sin(Math.toRadians(player.yHeadRot + 90))
                * (gliderMovementInfo.acceleration() + oldHorizontalSpeed * MOVEMENT_CHANGE_EFFECT);

        Vec3 newDeltaMovement = new Vec3(player.getDeltaMovement().x() * (1 - MOVEMENT_CHANGE_EFFECT) + x,
                verticalSpeed, player.getDeltaMovement().z() * (1 - MOVEMENT_CHANGE_EFFECT) + z);
        double speed = newDeltaMovement.length();
        if (speed > gliderMovementInfo.maxSpeed()) {
            newDeltaMovement = newDeltaMovement.scale(gliderMovementInfo.maxSpeed() / newDeltaMovement.length());
        }

        newDeltaMovement = newDeltaMovement.scale(AIR_FRICTION_COEFFICIENT);
        player.setDeltaMovement(newDeltaMovement);
    }

    private static Optional<GliderMovementInfo> calculateGliderMovementInfo(Player player,
            boolean displayDisabledMessage, FallState fall) {
        if (fall == null || fall.ticks <= DEPLOYMENT_DELAY || !canGlide(player)) {
            return Optional.empty();
        }

        return IntegrationManager.getFirst(integration -> integration.getGliderMovementInfo(player)).filter(info -> {
            Optional<Component> disabledReason = info.cause().hangGliderDisabledReason(player);
            if (displayDisabledMessage && disabledReason.isPresent()) {
                player.displayClientMessage(ToolsLang.GLIDER_DISABLED.copy().append(disabledReason.get()), true);
            }
            return disabledReason.isEmpty();
        });
    }

    public static boolean isGliding(Player player) {
        return calculateGliderMovementInfo(player, false, falls(player).get(player)).isPresent();
    }

    private static Map<Player, FallState> falls(Player player) {
        return player.level().isClientSide() ? CLIENT_FALLS : SERVER_FALLS;
    }

    private static boolean canGlide(Player player) {
        return !player.isSpectator() && !player.onGround() && !player.getAbilities().flying
                && !player.isShiftKeyDown() && !player.isInWater() && !player.isPassenger();
    }

    private static final class FallState {
        private ResourceKey<Level> dimension;
        private double y;
        private int ticks;

        void sample(Player player) {
            double nextY = player.getY();
            var nextDimension = player.level().dimension();
            // The server and remote clients receive positions for player input, while the local player simulates velocity.
            double dy = player.isLocalPlayer() ? player.getDeltaMovement().y() : nextY - y;
            if (!canGlide(player) || dimension != nextDimension || dy > 0 || dy < -8) {
                ticks = 0;
            } else if (dy < 0 && ticks <= DEPLOYMENT_DELAY) {
                ticks++;
            }
            dimension = nextDimension;
            y = nextY;
        }
    }

    private static class ClientClassLoadingProtection {
        private static void playSound(Player player) {
            if (player instanceof LocalPlayer localPlayer && !isGliderPlaying()) {
                Minecraft.getInstance().getSoundManager().play(new WindSoundInstance(localPlayer));
            }
        }

        private static boolean isGliderPlaying() {
            for (SoundInstance soundInstance : Minecraft.getInstance().getSoundManager().soundEngine.instanceBySource
                    .get(SoundSource.PLAYERS)) {
                if (soundInstance instanceof WindSoundInstance) {
                    return true;
                }
            }

            return false;
        }
    }

    private static class WindSoundInstance extends AbstractTickableSoundInstance {
        private final LocalPlayer player;
        private int time;

        WindSoundInstance(LocalPlayer player) {
            super(SoundEvents.ELYTRA_FLYING, SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
            this.player = player;
            this.looping = true;
            this.delay = 0;
            this.volume = 0.1F;
        }

        @Override
        public void tick() {

            ++this.time;
            if (!this.player.isRemoved() && (this.time <= 20 || isGliding(player))) {
                this.x = this.player.getX();
                this.y = this.player.getY();
                this.z = this.player.getZ();
                float f = (float) this.player.getDeltaMovement().lengthSqr();
                if (f >= 1.0E-7D) {
                    this.volume = Mth.clamp(f / 4.0F, 0.0F, 1.0F);
                } else {
                    this.volume = 0.0F;
                }

                if (this.time < 20) {
                    this.volume = 0.0F;
                } else if (this.time < 40) {
                    this.volume *= (this.time - 20) / 20.0F;
                }

                if (this.volume > 0.8F) {
                    this.pitch = 1.0F + (this.volume - 0.8F);
                } else {
                    this.pitch = 1.0F;
                }
            } else {
                this.stop();
            }
        }
    }
}
