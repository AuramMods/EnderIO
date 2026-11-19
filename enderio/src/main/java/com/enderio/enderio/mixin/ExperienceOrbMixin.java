package com.enderio.enderio.mixin;

import com.enderio.enderio.content.tools.vials.VoidVialItem;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ExperienceOrb.class)
abstract class ExperienceOrbMixin {
    @Shadow
    private int repairPlayerItems(Player player, int amount) {
        throw new AssertionError();
    }

    @Redirect(method = "playerTouch", at = @At(value = "INVOKE", target =
        "Lnet/minecraft/world/entity/ExperienceOrb;repairPlayerItems(Lnet/minecraft/world/entity/player/Player;I)I"))
    private int routeVialPickup(ExperienceOrb orb, Player player, int amount) {
        int remainder = VoidVialItem.captureExperience(player, amount);
        if (remainder == amount) {
            return repairPlayerItems(player, amount);
        }
        if (remainder == 0) {
            return 0;
        }

        // Forge uses the field for its Mending ratio. Restore it before the next member of a merged orb is picked up.
        int original = orb.getValue();
        orb.value = remainder;
        try {
            return repairPlayerItems(player, remainder);
        } finally {
            orb.value = original;
        }
    }
}
