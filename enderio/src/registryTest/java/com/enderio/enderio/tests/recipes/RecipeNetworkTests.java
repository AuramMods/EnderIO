package com.enderio.enderio.tests.recipes;

import com.enderio.core.common.network.codec.PacketCodecs;
import com.enderio.enderio.content.machines.soul_binder.SoulBindingRecipe;
import com.enderio.enderio.registrytests.ForgeServerExtension;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundUpdateRecipesPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(ForgeServerExtension.class)
public class RecipeNetworkTests {
    @Test
    public void loadedRecipesSurviveNativeLoginPacket(MinecraftServer server) {
        var recipes = server.getRecipeManager().getRecipes();
        assertTrue(recipes.stream().anyMatch(recipe -> recipe instanceof SoulBindingRecipe binding
            && binding.mobCategory().isPresent()), "Exercise actual category-bearing datapack recipes");
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            new ClientboundUpdateRecipesPacket(recipes).write(buffer);
            var decoded = new ClientboundUpdateRecipesPacket(buffer).getRecipes();
            assertEquals(recipes.size(), decoded.size());
            assertFalse(buffer.isReadable(), "Entire native recipe packet must be consumed");
            for (var recipe : decoded) {
                var original = server.getRecipeManager().byKey(recipe.getId()).orElseThrow();
                assertSame(original.getSerializer(), recipe.getSerializer(), recipe.getId().toString());
                assertSame(original.getType(), recipe.getType(), recipe.getId().toString());
                if (original instanceof SoulBindingRecipe binding) {
                    assertEquals(binding.mobCategory(), ((SoulBindingRecipe) recipe).mobCategory());
                }
            }
        } finally {
            buffer.release();
        }
    }

    @Test
    public void soulBindingPacketsPreserveEveryMobCategory() {
        for (var category : MobCategory.values()) {
            var recipe = new SoulBindingRecipe(new ItemStack(Items.DIAMOND), Ingredient.of(Items.COAL),
                100, 2, Optional.empty(), Optional.of(category), Optional.empty(), true)
                .withId(ResourceLocation.fromNamespaceAndPath("enderio", "network_category_test"));
            var buffer = new FriendlyByteBuf(Unpooled.buffer());
            try {
                ClientboundUpdateRecipesPacket.toNetwork(buffer, recipe);
                var decoded = (SoulBindingRecipe) ClientboundUpdateRecipesPacket.fromNetwork(buffer);
                assertEquals(Optional.of(category), decoded.mobCategory());
                assertEquals(recipe.id(), decoded.id());
                assertEquals(100, decoded.energy());
                assertEquals(2, decoded.experience());
                assertTrue(decoded.copyInputComponents());
                assertFalse(buffer.isReadable());
            } finally {
                buffer.release();
            }
        }
    }

    @Test
    public void unknownSoulCategoryIsRejectedAsMalformedPacket() {
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            PacketCodecs.REQUIRED_ITEM_STACK_DATA.encode(buffer, new ItemStack(Items.DIAMOND));
            PacketCodecs.INGREDIENT.encode(buffer, Ingredient.of(Items.COAL));
            buffer.writeInt(100);
            buffer.writeInt(2);
            buffer.writeBoolean(false); // No entity-type selector.
            buffer.writeBoolean(true); // Present but unknown category selector.
            buffer.writeUtf("enderio_unknown_category");
            buffer.writeBoolean(false); // No soul-data selector.
            buffer.writeBoolean(false);
            assertThrows(DecoderException.class, () -> SoulBindingRecipe.Serializer.STREAM_CODEC.decode(buffer));
        } finally {
            buffer.release();
        }
    }
}
