package com.enderio.core.data.model;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraftforge.client.model.generators.BlockModelBuilder;
import net.minecraftforge.client.model.generators.BlockModelProvider;

public class ModelHelper {

    public static BlockModelBuilder getExistingAsBuilder(BlockModelProvider prov, ResourceLocation model) {
        return new BlockModelBuilder(null, prov.existingFileHelper).parent(prov.getExistingFile(model));
    }

    public static TextureAtlasSprite getMissingTexture() {
        return getBlockSprite(MissingTextureAtlasSprite.getLocation());
    }

    public static TextureAtlasSprite getBlockSprite(ResourceLocation texture) {
        return Minecraft.getInstance()
                .getTextureAtlas(InventoryMenu.BLOCK_ATLAS)
                .apply(texture);
    }

    public static boolean isMissingTexture(TextureAtlasSprite sprite) {
        return MissingTextureAtlasSprite.getLocation().equals(sprite.contents().name());
    }
}
