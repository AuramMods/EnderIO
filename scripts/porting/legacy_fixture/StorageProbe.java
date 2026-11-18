package porting.storageprobe;

import appeng.api.networking.GridHelper;
import appeng.api.networking.GridFlags;
import com.refinedmods.refinedstorage.api.network.node.INetworkNode;
import com.refinedmods.refinedstorage.api.util.Action;
import com.refinedmods.refinedstorage.capability.NetworkNodeProxyCapability;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

@Mod("port_storage_probe")
public class StorageProbe {
    private static final UUID RS_LEFT_OWNER = UUID.fromString("3e96b660-0e27-4020-a770-00d68996d801");
    private static final UUID RS_RIGHT_OWNER = UUID.fromString("3e96b660-0e27-4020-a770-00d68996d802");

    public StorageProbe() {
        MinecraftForge.EVENT_BUS.addListener(this::register);
    }

    private void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("port_storage_native").requires(source -> source.hasPermission(4))
            .executes(context -> check(context.getSource(), false))
            .then(Commands.literal("seed_legacy_owners").executes(context -> check(context.getSource(), true))));
    }

    private static int check(CommandSourceStack source, boolean seedOwners) {
        var level = source.getLevel();
        var aeOwner = FakePlayerFactory.getMinecraft(level).getUUID();
        try {
            checkAE(level, 4, false, false, aeOwner);
            checkAE(level, 7, true, true, aeOwner);
            checkAE(level, 10, false, true, aeOwner);
            var controller = rsNode(level, 1086);
            var left = rsNode(level, 1087);
            var right = rsNode(level, 1088);
            var drive = rsNode(level, 1089);
            var network = controller.getNetwork();
            if (seedOwners) {
                left.setOwner(RS_LEFT_OWNER);
                right.setOwner(RS_RIGHT_OWNER);
                level.getBlockEntity(new BlockPos(1087, 80, 13)).setChanged();
                level.getBlockEntity(new BlockPos(1088, 80, 13)).setChanged();
                // The 6.x conduit never invalidated the existing RS controller graph during placement.
                if (network != null && drive.getNetwork() != network) {
                    network.getNodeGraph().invalidate(Action.PERFORM, level, network.getPosition());
                }
            }
            require(network != null, "RS controller has no network");
            require(left.getNetwork() == network, "Left RS conduit is disconnected from its controller");
            require(right.getNetwork() == network, "Right RS conduit is disconnected from its controller");
            require(drive.getNetwork() == network, "RS disk drive is disconnected from its controller");
            require(RS_LEFT_OWNER.equals(left.getOwner()) && RS_RIGHT_OWNER.equals(right.getOwner()), "RS owners changed");
        } catch (IllegalStateException exception) {
            source.sendFailure(Component.literal(exception.getMessage()));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Native AE2 and RS storage routes and owners verified"), false);
        return 1;
    }

    private static void checkAE(ServerLevel level, int z, boolean denseLeft, boolean denseRight, UUID owner) {
        var power = GridHelper.getExposedNode(level, new BlockPos(1086, 80, z), Direction.EAST);
        var left = GridHelper.getExposedNode(level, new BlockPos(1087, 80, z), Direction.EAST);
        var right = GridHelper.getExposedNode(level, new BlockPos(1088, 80, z), Direction.WEST);
        var drive = GridHelper.getExposedNode(level, new BlockPos(1089, 80, z), Direction.WEST);
        require(power != null && left != null && right != null && drive != null, "Missing AE2 grid node at z=" + z);
        require(power.getGrid() == drive.getGrid() && left.getGrid() == drive.getGrid() && right.getGrid() == drive.getGrid()
            && drive.isActive(), "AE2 drive is disconnected or unpowered at z=" + z);
        require(left.hasFlag(GridFlags.DENSE_CAPACITY) == denseLeft && right.hasFlag(GridFlags.DENSE_CAPACITY) == denseRight,
            "AE2 conduit tiers changed at z=" + z);
        require(owner.equals(left.getOwningPlayerProfileId()) && owner.equals(right.getOwningPlayerProfileId()),
            "AE2 conduit owner changed at z=" + z);
    }

    private static INetworkNode rsNode(ServerLevel level, int x) {
        var pos = new BlockPos(x, 80, 13);
        var block = level.getBlockEntity(pos);
        require(block != null, "Missing RS block at " + pos);
        return block.getCapability(NetworkNodeProxyCapability.NETWORK_NODE_PROXY_CAPABILITY)
            .orElseThrow(() -> new IllegalStateException("Missing RS node proxy at " + pos)).getNode();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
