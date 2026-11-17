package com.enderio.enderio.foundation.attachment;

import com.enderio.core.annotations.UseOnly;
import com.enderio.core.common.network.NetworkDataSlot;
import com.enderio.enderio.foundation.particle.RangeParticleData;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import com.enderio.core.common.network.codec.PacketCodecs;
import com.enderio.core.common.network.codec.PacketCodec;
import net.minecraft.util.Mth;
import net.minecraftforge.fml.LogicalSide;

public record ActionRange(int range, boolean isVisible) {

    public static final Codec<ActionRange> CODEC = RecordCodecBuilder
            .create(instance -> instance
                    .group(Codec.INT.fieldOf("range").forGetter(ActionRange::range),
                            Codec.BOOL.fieldOf("isVisible").forGetter(ActionRange::isVisible))
                    .apply(instance, ActionRange::new));

    public static final PacketCodec<ActionRange> STREAM_CODEC = PacketCodec.composite(PacketCodecs.INT,
            ActionRange::range, PacketCodecs.BOOL, ActionRange::isVisible, ActionRange::new);

    public ActionRange visible() {
        return new ActionRange(range, true);
    }

    public ActionRange invisible() {
        return new ActionRange(range, false);
    }

    public ActionRange increment() {
        return new ActionRange(range + 1, isVisible);
    }

    public ActionRange decrement() {
        return new ActionRange(range - 1, isVisible);
    }

    public ActionRange clamp(int min, int max) {
        return new ActionRange(Mth.clamp(range(), min, max), isVisible);
    }

    @UseOnly(LogicalSide.CLIENT)
    public void addClientParticle(ClientLevel level, BlockPos pos, String color) {
        if (!isVisible) {
            return;
        }

        if (level.isClientSide()) {
            level.addAlwaysVisibleParticle(new RangeParticleData(range, color), true, pos.getX(), pos.getY(),
                    pos.getZ(), 0, 0, 0);
        }
    }

    public Tag save(HolderLookup.Provider lookupProvider) {
        return CODEC.encodeStart(NbtOps.INSTANCE, this).getOrThrow(false, message -> { throw new IllegalArgumentException(message); });
    }

    public static ActionRange parse(HolderLookup.Provider lookupProvider, Tag tag) {
        return CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow(false, message -> { throw new IllegalArgumentException(message); });
    }
}
