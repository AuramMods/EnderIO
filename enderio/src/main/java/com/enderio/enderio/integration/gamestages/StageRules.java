package com.enderio.enderio.integration.gamestages;

import com.enderio.enderio.foundation.block.entity.MachineBlockEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.fml.ModList;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/** Rules belong to a loaded server resource set and never modify the shared recipe list. */
public final class StageRules {
    public enum Kind { RECIPE, RECIPE_TYPE, BLOCK, ITEM, ACTION }

    private static final Map<RecipeManager, Rules> BY_MANAGER = new WeakHashMap<>();

    private StageRules() {}

    public static void validateStage(String stage) {
        if (!ModList.get().isLoaded("gamestages")) {
            throw new IllegalStateException("Ender IO stage rules require GameStages to be installed");
        }
        GameStagesAccess.validate(stage);
    }

    public static void validateTarget(Kind kind, ResourceLocation target) {
        boolean known = switch (kind) {
            case RECIPE -> true;
            case RECIPE_TYPE -> BuiltInRegistries.RECIPE_TYPE.containsKey(target);
            case BLOCK -> BuiltInRegistries.BLOCK.containsKey(target);
            case ITEM -> BuiltInRegistries.ITEM.containsKey(target);
            case ACTION -> StageFeature.isKnown(target);
        };
        if (!known) {
            throw new IllegalArgumentException("Unknown Ender IO stage target " + kind + " " + target);
        }
    }

    public static synchronized void require(RecipeManager manager, Kind kind, ResourceLocation target, String stage) {
        rules(manager).requirements().computeIfAbsent(new Key(kind, target), ignored -> new LinkedHashSet<>()).add(stage);
    }

    public static synchronized void clear(RecipeManager manager, Kind kind, ResourceLocation target) {
        rules(manager).requirements().remove(new Key(kind, target));
    }

    public static synchronized void setWorldStage(RecipeManager manager, String stage, boolean enabled) {
        var stages = rules(manager).worldStages();
        if (enabled) {
            stages.add(stage);
        } else {
            stages.remove(stage);
        }
    }

    public static boolean permitsRecipe(ServerLevel level, @Nullable UUID owner, Recipe<?> recipe) {
        return permits(level, owner, false, new Key(Kind.RECIPE, recipe.getId()),
                new Key(Kind.RECIPE_TYPE, BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType())));
    }

    public static boolean permitsWorldRecipe(ServerLevel level, Recipe<?> recipe) {
        return permits(level, null, true, new Key(Kind.RECIPE, recipe.getId()),
                new Key(Kind.RECIPE_TYPE, BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType())),
                new Key(Kind.ACTION, StageFeature.FIRE_CRAFTING.id()));
    }

    @Nullable
    public static CraftingRecipe resolveCrafterRecipe(ServerLevel level, @Nullable UUID owner, CraftingRecipe recipe) {
        if (!permitsRecipe(level, owner, recipe)) {
            return null;
        }
        if (ModList.get().isLoaded("recipestages")) {
            return RecipeStagesAccess.resolve(connectedOwner(level, owner), recipe);
        }
        return recipe;
    }

    public static boolean permits(ServerLevel level, @Nullable ServerPlayer player, Kind kind, ResourceLocation target) {
        return permits(level, player instanceof FakePlayer || player == null ? null : player.getUUID(), false,
                new Key(kind, target));
    }

    public static boolean permitsOwner(ServerLevel level, @Nullable UUID owner, Kind kind, ResourceLocation target) {
        return permits(level, owner, false, new Key(kind, target));
    }

    public static boolean permitsAction(Player player, StageFeature feature) {
        return !(player instanceof ServerPlayer serverPlayer)
                || permits(serverPlayer.serverLevel(), serverPlayer, Kind.ACTION, feature.id());
    }

    public static boolean permitsItem(Player player, ItemStack stack) {
        return !(player instanceof ServerPlayer serverPlayer) || stack.isEmpty()
                || permits(serverPlayer.serverLevel(), serverPlayer, Kind.ITEM, BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }

    public static boolean permitsWorldAction(ServerLevel level, StageFeature feature) {
        return permits(level, null, true, new Key(Kind.ACTION, feature.id()));
    }

    public static boolean permitsMachineAutomation(ServerLevel level, MachineBlockEntity machine) {
        if (!hasRules(level)) {
            return true;
        }
        UUID owner = machine.getMachineOwner();
        var block = BuiltInRegistries.BLOCK.getKey(machine.getBlockState().getBlock());
        if (!permitsOwner(level, owner, Kind.BLOCK, block)) {
            return false;
        }
        var feature = machine.automationStageFeature();
        return feature == null || permitsOwner(level, owner, Kind.ACTION, feature.id());
    }

    public static boolean permitsMachineMenu(Player player, MachineBlockEntity machine) {
        if (!(player instanceof ServerPlayer serverPlayer) || !hasRules(serverPlayer.serverLevel())) {
            return true;
        }
        var block = BuiltInRegistries.BLOCK.getKey(machine.getBlockState().getBlock());
        return permits(serverPlayer.serverLevel(), serverPlayer, Kind.BLOCK, block)
                && permitsAction(player, StageFeature.MACHINE_USE)
                && (machine.automationStageFeature() != StageFeature.OBELISK || permitsAction(player, StageFeature.OBELISK));
    }

    private static synchronized boolean hasRules(ServerLevel level) {
        return BY_MANAGER.containsKey(level.getRecipeManager());
    }

    private static synchronized boolean permits(ServerLevel level, @Nullable UUID owner, boolean world, Key... keys) {
        var rules = BY_MANAGER.get(level.getRecipeManager());
        if (rules == null) {
            return true;
        }
        for (var key : keys) {
            var stages = rules.requirements().get(key);
            if (stages == null || stages.isEmpty()) {
                continue;
            }
            if (world ? !rules.worldStages().containsAll(stages) : !hasPersonalStages(level, owner, stages)) {
                return false;
            }
        }
        return true;
    }

    private static boolean hasPersonalStages(ServerLevel level, @Nullable UUID owner, Set<String> stages) {
        var player = connectedOwner(level, owner);
        return player != null && GameStagesAccess.hasAll(player, stages);
    }

    @Nullable
    private static ServerPlayer connectedOwner(ServerLevel level, @Nullable UUID owner) {
        if (owner == null) {
            return null;
        }
        var player = level.getServer().getPlayerList().getPlayer(owner);
        return player instanceof FakePlayer ? null : player;
    }

    private static Rules rules(RecipeManager manager) {
        return BY_MANAGER.computeIfAbsent(manager, ignored -> new Rules(new HashMap<>(), new LinkedHashSet<>()));
    }

    private record Rules(Map<Key, Set<String>> requirements, Set<String> worldStages) {}

    private record Key(Kind kind, ResourceLocation target) {}
}
