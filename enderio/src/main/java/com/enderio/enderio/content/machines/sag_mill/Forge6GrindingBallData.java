package com.enderio.enderio.content.machines.sag_mill;

import com.enderio.enderio.api.components.GrindingBallData;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/** Restores active stock balls that Forge 6.2.18-beta saved by recipe ID. */
final class Forge6GrindingBallData {
    private static final String RECIPE_PREFIX = "enderio:grindingball/";

    // These are the historical values from the release's grindingball recipes. An active ball
    // is a snapshot, so the current data map and item aliases must not change its modifiers.
    private static final Map<String, GrindingBallData> STOCK = Map.of(
        "conductive_alloy_grinding_ball", new GrindingBallData(1.35f, 1f, 1f, 40000),
        "copper_alloy_grinding_ball", new GrindingBallData(1.2f, 1.65f, 0.8f, 40000),
        "dark_steel_grinding_ball", new GrindingBallData(1.35f, 2f, 0.7f, 125000),
        "end_steel_grinding_ball", new GrindingBallData(1.4f, 2.4f, 0.7f, 75000),
        "energetic_alloy_grinding_ball", new GrindingBallData(1.6f, 1.1f, 1.1f, 80000),
        "flint", new GrindingBallData(1.2f, 1.25f, 0.85f, 24000),
        "pulsating_alloy_grinding_ball", new GrindingBallData(1f, 1.85f, 1f, 100000),
        "redstone_alloy_grinding_ball", new GrindingBallData(1f, 1f, 0.35f, 30000),
        "soularium_grinding_ball", new GrindingBallData(1.2f, 2.15f, 0.9f, 80000),
        "vibrant_alloy_grinding_ball", new GrindingBallData(1.75f, 1.35f, 1.13f, 80000));

    private Forge6GrindingBallData() {}

    @Nullable
    static GrindingBallData get(String recipeId) {
        return recipeId.startsWith(RECIPE_PREFIX) ? STOCK.get(recipeId.substring(RECIPE_PREFIX.length())) : null;
    }
}
