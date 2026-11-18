package com.enderio.modded_conduits.common.modules.appeng;

import appeng.api.networking.IGridNode;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.networking.IManagedGridNode;
import appeng.api.util.AECableType;
import com.enderio.enderio.api.conduits.network.node.NodeData;
import com.enderio.enderio.api.conduits.network.node.legacy.ConduitData;
import com.enderio.enderio.api.conduits.network.node.legacy.ConduitDataType;
import com.enderio.enderio.api.network.DumbStreamCodec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import com.enderio.core.common.network.codec.PacketCodec;
import org.jetbrains.annotations.Nullable;

public class ConduitInWorldGridNodeHost implements IInWorldGridNodeHost, ConduitData<ConduitInWorldGridNodeHost> {

    public static final MapCodec<ConduitInWorldGridNodeHost> CODEC = RecordCodecBuilder.mapCodec(instance -> instance
            .group(CompoundTag.CODEC.fieldOf("main_node").forGetter(ConduitInWorldGridNodeHost::saveMainNode))
            .apply(instance, ConduitInWorldGridNodeHost::new));

    public static final PacketCodec<ConduitInWorldGridNodeHost> STREAM_CODEC = DumbStreamCodec
            .of(ConduitInWorldGridNodeHost::new)
            ;

    @Nullable
    private IManagedGridNode mainNode = null;

    @Nullable
    private CompoundTag savedMainNode = null;

    private boolean isDense;

    public ConduitInWorldGridNodeHost() {
    }

    public ConduitInWorldGridNodeHost(CompoundTag mainNodeTag) {
        savedMainNode = mainNodeTag.copy();
    }

    @Override
    public ConduitInWorldGridNodeHost deepCopy() {
        var copy = new ConduitInWorldGridNodeHost(saveMainNode());
        copy.isDense = isDense;
        return copy;
    }

    @Override
    public ConduitDataType<ConduitInWorldGridNodeHost> type() {
        return AE2ConduitsModule.DATA.get();
    }

    @Nullable
    public IManagedGridNode getMainNode() {
        return mainNode;
    }

    public void setMainNode(IManagedGridNode mainNode, boolean isDense) {
        this.mainNode = mainNode;
        this.isDense = isDense;
    }

    public void clearMainNode() {
        this.savedMainNode = saveMainNode();
        this.mainNode = null;
        this.isDense = false;
    }

    protected CompoundTag saveMainNode() {
        var tag = savedMainNode == null ? new CompoundTag() : savedMainNode.copy();
        if (mainNode != null) {
            mainNode.saveToNBT(tag);
        }
        return tag;
    }

    public void loadMainNode() {
        if (mainNode == null) {
            throw new IllegalStateException("mainNode cannot be null.");
        }

        if (savedMainNode == null) {
            return;
        }

        this.mainNode.loadFromNBT(savedMainNode);
        savedMainNode = null;
    }

    @Nullable
    @Override
    public IGridNode getGridNode(Direction dir) {
        return mainNode != null ? mainNode.getNode() : null;
    }

    @Override
    public AECableType getCableConnectionType(Direction dir) {
        if (isDense) {
            return AECableType.DENSE_SMART;
        }

        return AECableType.SMART;
    }

    @Override
    public NodeData toNodeData() {
        return new MEConduitNodeData(mainNode, savedMainNode, isDense);
    }
}
