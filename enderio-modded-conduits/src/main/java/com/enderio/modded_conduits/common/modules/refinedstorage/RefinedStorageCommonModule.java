package com.enderio.modded_conduits.common.modules.refinedstorage;

import com.enderio.enderio.EnderIO;
import com.enderio.enderio.api.EnderIORegistries;
import com.enderio.enderio.api.conduits.Conduit;
import com.enderio.enderio.api.conduits.ConduitApi;
import com.enderio.enderio.api.conduits.ConduitType;
import com.enderio.enderio.api.conduits.connection.config.ConnectionConfigType;
import com.enderio.enderio.api.conduits.network.node.NodeDataType;
import com.enderio.enderio.api.conduits.network.node.legacy.ConduitDataType;
import com.enderio.enderio.init.EIOItems;
import com.enderio.modded_conduits.common.ModuleModIds;
import com.enderio.modded_conduits.common.modules.ConduitCommonModule;
import com.refinedmods.refinedstorage.RSBlocks;
import com.refinedmods.refinedstorage.capability.NetworkNodeProxyCapability;
import net.minecraft.advancements.critereon.InventoryChangeTrigger;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.recipes.RecipeCategory;
import com.enderio.core.data.recipe.RecipeOutput;
import com.enderio.core.data.recipe.StackShapedRecipeBuilder;
import net.minecraft.data.worldgen.BootstapContext;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.common.crafting.conditions.ICondition;
import net.minecraftforge.common.crafting.conditions.ModLoadedCondition;
import net.minecraftforge.registries.DeferredRegister;

import java.util.function.BiConsumer;
import java.util.function.Supplier;

public class RefinedStorageCommonModule implements ConduitCommonModule {

    public static final ConduitCommonModule INSTANCE = new RefinedStorageCommonModule();
    private static final ModLoadedCondition CONDITION = new ModLoadedCondition(ModuleModIds.REFINED_STORAGE);

    public static class ConduitKeys {
        public static final ResourceKey<Conduit<?, ?>> RS = ResourceKey.create(EnderIORegistries.Keys.CONDUIT,
                EnderIO.rl("rs"));
    }

    public static final DeferredRegister<ConduitType<?, ?>> CONDUIT_TYPES = DeferredRegister
            .create(EnderIORegistries.Keys.CONDUIT_TYPE, EnderIO.MOD_ID);
    public static final DeferredRegister<ConnectionConfigType<?>> CONDUIT_CONNECTION_CONFIG_TYPES = DeferredRegister
            .create(EnderIORegistries.Keys.CONDUIT_CONNECTION_CONFIG_TYPE, EnderIO.MOD_ID);
    public static final DeferredRegister<ConduitDataType<?>> CONDUIT_DATA_TYPES = DeferredRegister
            .create(EnderIORegistries.Keys.CONDUIT_DATA_TYPE, EnderIO.MOD_ID);

    public static final DeferredRegister<NodeDataType<?>> CONDUIT_NODE_DATA_TYPES = DeferredRegister
            .create(EnderIORegistries.Keys.CONDUIT_NODE_DATA_TYPE, EnderIO.MOD_ID);

    public static final Supplier<ConduitType<RSConduit, RSConduitConnectionConfig>> RS_CONDUIT = CONDUIT_TYPES.register("rs",
            () -> ConduitType.builder(RSConduit.CODEC, RSConduitConnectionConfig.TYPE)
                    .exposeCapability(
                            NetworkNodeProxyCapability.NETWORK_NODE_PROXY_CAPABILITY)
                    .build());

    static {
        // Connection types
        CONDUIT_CONNECTION_CONFIG_TYPES.register("rs", () -> RSConduitConnectionConfig.TYPE);

        // Node datum
        CONDUIT_NODE_DATA_TYPES.register("rs", () -> RSConduitNodeData.TYPE);
    }

    // TODO: 1.22: Remove.
    public static final Supplier<ConduitDataType<RSNetworkHost>> LEGACY_DATA_TYPE = CONDUIT_DATA_TYPES.register("rs",
            () -> new ConduitDataType<>(RSNetworkHost.CODEC, RSNetworkHost.STREAM_CODEC, RSNetworkHost::new));

    @Override
    public void initialize(IEventBus modEventBus) {
        CONDUIT_TYPES.register(modEventBus);
        CONDUIT_CONNECTION_CONFIG_TYPES.register(modEventBus);
        CONDUIT_DATA_TYPES.register(modEventBus);
        CONDUIT_NODE_DATA_TYPES.register(modEventBus);
        modEventBus.addListener((net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent event) ->
            event.enqueueWork(() -> com.refinedmods.refinedstorage.apiimpl.API.instance().getNetworkNodeRegistry()
                .add(ConduitRSNode.ID, (tag, level, pos) -> {
                    var node = new ConduitRSNode(level, pos);
                    node.read(tag);
                    return node;
                })));
    }

    @Override
    public void bootstrapConduits(BootstapContext<Conduit<?, ?>> context) {
        context.register(ConduitKeys.RS, new RSConduit(EnderIO.rl("block/conduit/rs"), Component.translatable(ConduitApi.INSTANCE.makeDescriptionId(ConduitKeys.RS))));
    }

    @Override
    public void buildConduitConditions(BiConsumer<ResourceKey<?>, ICondition> conditions) {
        conditions.accept(ConduitKeys.RS, CONDITION);
    }

    @Override
    public void buildRecipes(HolderLookup.Provider lookupProvider, RecipeOutput recipeOutput) {
        var rsRecipeOutput = recipeOutput.withConditions(CONDITION);

        var conduit = lookupProvider.lookupOrThrow(EnderIORegistries.Keys.CONDUIT).getOrThrow(ConduitKeys.RS);

        StackShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, ConduitApi.INSTANCE.getConduitItem(conduit, 3))
                .pattern("BBB")
                .pattern("III")
                .pattern("BBB")
                .define('B', EIOItems.CONDUIT_BINDER)
                .define('I', RSBlocks.CABLE.get())
                .unlockedBy("has_ingredient", InventoryChangeTrigger.TriggerInstance.hasItems(EIOItems.CONDUIT_BINDER))
                .save(rsRecipeOutput, EnderIO.rl("rs_conduit"));
    }
}
