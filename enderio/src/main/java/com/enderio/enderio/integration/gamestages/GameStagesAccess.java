package com.enderio.enderio.integration.gamestages;

import net.darkhax.gamestages.GameStageHelper;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;

final class GameStagesAccess {
    private GameStagesAccess() {}

    static void validate(String stage) {
        if (!GameStageHelper.isValidStageName(stage)) {
            throw new IllegalArgumentException("Invalid GameStages stage name " + stage);
        }
    }

    static boolean hasAll(ServerPlayer player, Collection<String> stages) {
        return GameStageHelper.hasAllOf(player, stages);
    }
}
