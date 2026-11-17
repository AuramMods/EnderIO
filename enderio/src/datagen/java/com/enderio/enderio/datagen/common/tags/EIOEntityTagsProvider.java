package com.enderio.enderio.datagen.common.tags;

import com.enderio.enderio.api.EnderIOAPI;
import com.enderio.enderio.foundation.util.EntityCaptureUtils;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.EntityTypeTagsProvider;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.common.data.ExistingFileHelper;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;

public class EIOEntityTagsProvider extends EntityTypeTagsProvider {
    public EIOEntityTagsProvider(PackOutput packOutput, CompletableFuture<HolderLookup.Provider> provider,
            @Nullable ExistingFileHelper existingFileHelper) {
        super(packOutput, provider, EnderIOAPI.MOD_ID, existingFileHelper);
    }

    @Override
    protected void addTags(HolderLookup.Provider provider) {
        // NeoForge supplies these common tags; keep their baseline content on Forge.
        tag(EntityCaptureUtils.BOSSES).add(EntityType.ENDER_DRAGON, EntityType.WITHER)
            .addOptionalTag(ResourceLocation.fromNamespaceAndPath("forge", "bosses"));
        tag(EntityCaptureUtils.CAPTURING_NOT_SUPPORTED);
        this.tag(EntityCaptureUtils.SOUL_VIAL_BLACKLIST)
                .add(EntityType.WARDEN)
                .addTag(EntityCaptureUtils.CAPTURING_NOT_SUPPORTED)
                .addTag(EntityCaptureUtils.BOSSES);

        this.tag(EntityCaptureUtils.SOUL_VIAL_WHITELIST);

        this.tag(TagKey.create(Registries.ENTITY_TYPE, EnderIOAPI.rl("spawner_blacklist"))).addTag(EntityCaptureUtils.BOSSES).add(EntityType.WARDEN);

        this.tag(TagKey.create(Registries.ENTITY_TYPE, EnderIOAPI.rl("spawner_whitelist")));

    }
}
