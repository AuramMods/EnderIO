package com.enderio.enderio.content.paint;

import com.enderio.enderio.content.paint.block.PaintedBlock;
import com.enderio.enderio.foundation.EIONBTKeys;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class PaintUtils {
    public static Block getBlockFromRL(String rl) {
        return BuiltInRegistries.BLOCK.get(ResourceLocation.parse(rl));
    }

    @Nullable
    public static BlockPaintData getItemPaint(ItemStack stack) {
        var data = BlockPaintData.ITEM_DATA.get(stack);
        if (data != null) {
            return data;
        }

        var legacyTag = BlockItem.getBlockEntityData(stack);
        if (legacyTag == null) {
            return null;
        }

        // Forge 6 block loot used the half's stored key on each separate slab item.
        String key = legacyTag.contains(EIONBTKeys.PAINT, Tag.TAG_STRING) ? EIONBTKeys.PAINT : EIONBTKeys.PAINT_2;
        var id = ResourceLocation.tryParse(legacyTag.getString(key));
        if (id == null) {
            return null;
        }

        var paint = BuiltInRegistries.BLOCK.get(id);
        return paint == Blocks.AIR || paint instanceof PaintedBlock ? null : BlockPaintData.of(paint);
    }

    public static Optional<SoundEvent> getPlaceSound(BlockState state, Level level, BlockPos pos, Player player, Class<? extends BlockItem> blockItemClass) {
        if (level.isClientSide()) {
            return Optional.of(List.of(InteractionHand.values()).stream()
                .map(player::getItemInHand)
                .filter(itemStack -> blockItemClass.isInstance(itemStack.getItem()))
                .map(PaintUtils::getItemPaint)
                .filter(Objects::nonNull)
                .map(BlockPaintData::paint)
                .map(block -> block.getSoundType(block.defaultBlockState(), level, pos, player))
                .map(SoundType::getPlaceSound)
                .findFirst()
                .orElse(SoundType.WOOD.getPlaceSound()));
        }
        if (state.getBlock() instanceof PaintedBlock paintedBlock) {
            return Optional.of(paintedBlock.getSoundType(state, level, pos, player).getPlaceSound());
        } else {
            return Optional.empty();
        }
    }
}
