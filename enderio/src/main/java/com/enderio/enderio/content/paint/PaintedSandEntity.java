package com.enderio.enderio.content.paint;

import com.enderio.enderio.foundation.EIONBTKeys;
import com.enderio.enderio.init.EIODataComponents;
import com.enderio.enderio.init.EIOEntities;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.entity.IEntityAdditionalSpawnData;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public class PaintedSandEntity extends FallingBlockEntity implements IEntityAdditionalSpawnData {

    public PaintedSandEntity(EntityType<? extends FallingBlockEntity> type, Level level) {
        super(type, level);
    }

    public PaintedSandEntity(Level level, double x, double y, double z, BlockState state) {
        super(level, x, y, z, state);
    }

    @Override
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getAddEntityPacket() {
        return net.minecraftforge.network.NetworkHooks.getEntitySpawningPacket(this);
    }

    @Override
    public EntityType<?> getType() {
        return EIOEntities.PAINTED_SAND.get();
    }

    @Nullable
    public Block getPaint() {
        if (blockData != null) {
            return PaintUtils.getBlockFromRL(blockData.getString(EIONBTKeys.PAINT));
        }
        return null;
    }

    public void setPaint(Block block) {
        if (blockData == null) {
            blockData = new CompoundTag();
        }

        blockData.putString(EIONBTKeys.PAINT, Objects.requireNonNull(BuiltInRegistries.BLOCK.getKey(block)).toString());
    }

    @Override
    public void writeSpawnData(FriendlyByteBuf buffer) {
        // Forge's custom spawn packet has no vanilla falling-block state ID.
        // Carry the native falling-block fields, including paint in TileEntityData.
        CompoundTag data = new CompoundTag();
        super.addAdditionalSaveData(data);
        buffer.writeNbt(data);
    }

    @Override
    public void readSpawnData(FriendlyByteBuf buffer) {
        CompoundTag data = buffer.readNbt();
        if (data != null) {
            super.readAdditionalSaveData(data);
        }
        blocksBuilding = true;
        setStartPos(blockPosition());
    }

    @Nullable
    @Override
    public ItemEntity spawnAtLocation(ItemStack stack, float offsetY) {
        Block paint = getPaint();
        if (!stack.isEmpty() && paint != null) {
            EIODataComponents.BLOCK_PAINT.set(stack, BlockPaintData.of(paint));
        }

        return super.spawnAtLocation(stack, offsetY);
    }
}
