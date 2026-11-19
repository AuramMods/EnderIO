package porting.storageprobe;

import appeng.api.config.Actionable;
import appeng.api.networking.GridFlags;
import appeng.api.networking.GridHelper;
import appeng.api.networking.security.IActionSource;
import appeng.api.parts.IPartItem;
import appeng.api.parts.PartHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.MEStorage;
import appeng.api.util.AEColor;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.core.definitions.AEItems;
import appeng.core.definitions.AEParts;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.refinedmods.refinedstorage.RSItems;
import com.refinedmods.refinedstorage.api.network.node.INetworkNode;
import com.refinedmods.refinedstorage.api.util.Action;
import com.refinedmods.refinedstorage.api.util.IComparer;
import com.refinedmods.refinedstorage.apiimpl.network.node.diskdrive.DiskDriveNetworkNode;
import com.refinedmods.refinedstorage.apiimpl.storage.ItemStorageType;
import com.refinedmods.refinedstorage.capability.NetworkNodeProxyCapability;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;
import java.util.function.Consumer;

@Mod("port_storage_probe")
public class StorageProbe {
    private static final int[] AE_ROUTES = {4, 7, 10};
    private static final BlockPos TERMINAL = new BlockPos(1085, 80, 4);
    private static final BlockPos GRID = new BlockPos(1085, 80, 13);
    private static final IActionSource AE_SOURCE = IActionSource.empty();
    private static final UUID RS_LEFT_OWNER = UUID.fromString("3e96b660-0e27-4020-a770-00d68996d801");
    private static final UUID RS_RIGHT_OWNER = UUID.fromString("3e96b660-0e27-4020-a770-00d68996d802");

    public StorageProbe() {
        MinecraftForge.EVENT_BUS.addListener(this::register);
    }

    private void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("port_storage_native").requires(source -> source.hasPermission(4))
            .executes(context -> check(context.getSource(), false))
            .then(Commands.literal("seed_legacy_owners").executes(context -> check(context.getSource(), true)))
            .then(Commands.literal("place_terminal")
                .executes(context -> run(context.getSource(), "Native AE2 terminal placed", StorageProbe::placeTerminal)))
            .then(Commands.literal("check_grid")
                .executes(context -> run(context.getSource(), "Native RS grid connects to its controller", StorageProbe::checkGrid)))
            .then(Commands.literal("mount_items").executes(context -> run(context.getSource(), "Native item storage mounted", StorageProbe::mount)))
            .then(Commands.literal("write_items").then(Commands.argument("count", IntegerArgumentType.integer(1, 64))
                .executes(context -> run(context.getSource(), "Native item storage accepted the write",
                    level -> write(level, IntegerArgumentType.getInteger(context, "count"))))))
            .then(Commands.literal("check_items").then(Commands.argument("count", IntegerArgumentType.integer(0, 64))
                .executes(context -> run(context.getSource(), "Native item storage counts verified",
                    level -> checkItems(level, IntegerArgumentType.getInteger(context, "count"))))))
            .then(Commands.literal("check_route_items").then(Commands.argument("route", IntegerArgumentType.integer(4, 13))
                .then(Commands.argument("count", IntegerArgumentType.integer(0, 64))
                    .executes(context -> run(context.getSource(), "Native route item count verified",
                        level -> checkRouteItems(level, IntegerArgumentType.getInteger(context, "route"),
                            IntegerArgumentType.getInteger(context, "count")))))))
            .then(Commands.literal("take_items").then(Commands.argument("count", IntegerArgumentType.integer(1, 64))
                .executes(context -> run(context.getSource(), "Native item storage delivered the requested items",
                    level -> take(level, IntegerArgumentType.getInteger(context, "count")))))));
    }

    private static int run(CommandSourceStack source, String message, Consumer<ServerLevel> operation) {
        try {
            operation.accept(source.getLevel());
        } catch (IllegalStateException exception) {
            source.sendFailure(Component.literal(exception.getMessage()));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(message), false);
        return 1;
    }

    private static void placeTerminal(ServerLevel level) {
        var player = FakePlayerFactory.getMinecraft(level);
        var cable = PartHelper.setPart(level, TERMINAL, null, player, AEParts.GLASS_CABLE.item(AEColor.TRANSPARENT));
        require(cable != null, "AE2 did not place the terminal's glass cable");
        require(PartHelper.setPart(level, TERMINAL, Direction.WEST, player, IPartItem.byId(AEParts.TERMINAL.id())) != null,
            "AE2 did not place the item terminal on the west face");
    }

    private static void checkGrid(ServerLevel level) {
        var controller = rsNode(level, 1086);
        var grid = rsNode(level, GRID.getX());
        require(grid.getNetwork() != null && grid.getNetwork() == controller.getNetwork() && grid.isActive(),
            "The RS grid is not active on the fixture controller's native network");
    }

    private static void mount(ServerLevel level) {
        for (int z : AE_ROUTES) {
            require(aeDrive(level, z).getInternalInventory().insertItem(0, AEItems.ITEM_CELL_1K.stack(), false).isEmpty(),
                "AE2 rejected its 1k cell at z=" + z);
        }
        var disk = new ItemStack(RSItems.ITEM_STORAGE_DISKS.get(ItemStorageType.ONE_K).get());
        // Refined Storage creates the disk's persistent ID on its first player inventory tick.
        disk.inventoryTick(level, FakePlayerFactory.getMinecraft(level), 0, false);
        require(rsDrive(level).getDisks().insertItem(0, disk, false).isEmpty(), "RS rejected its 1k disk");
    }

    private static void write(ServerLevel level, int count) {
        for (int z : AE_ROUTES) {
            require(aeStorage(level, z).insert(AEItemKey.of(sample(z)), count, Actionable.MODULATE, AE_SOURCE) == count,
                "AE2 storage write failed at z=" + z);
        }
        var network = rsNode(level, 1086).getNetwork();
        require(network != null && network.insertItem(sample(13), count, Action.PERFORM).isEmpty(), "RS storage write failed");
    }

    private static void checkItems(ServerLevel level, int count) {
        for (int z : AE_ROUTES) {
            checkAEItems(level, z, count);
        }
        checkRSItems(level, count);
    }

    private static void checkRouteItems(ServerLevel level, int route, int count) {
        switch (route) {
            case 4, 7, 10 -> checkAEItems(level, route, count);
            case 13 -> checkRSItems(level, count);
            default -> throw new IllegalStateException("Unknown storage route " + route);
        }
    }

    private static void checkAEItems(ServerLevel level, int z, int count) {
        var key = AEItemKey.of(sample(z));
        var storage = aeStorage(level, z);
        var cell = aeDrive(level, z).getCellInventory(0);
        require(cell != null && cell.getAvailableStacks().get(key) == count, "Wrong physical AE2 cell count at z=" + z);
        require(storage.extract(key, 64, Actionable.SIMULATE, AE_SOURCE) == count
            && storage.extract(AEItemKey.of(sample(0)), 1, Actionable.SIMULATE, AE_SOURCE) == 0,
            "Wrong AE2 item count or tag at z=" + z);
        if (count == 0) {
            require(storage.insert(key, 64, Actionable.SIMULATE, AE_SOURCE) == 64, "AE2 cell is not mounted at z=" + z);
        }
    }

    private static void checkRSItems(ServerLevel level, int count) {
        var network = rsNode(level, 1086).getNetwork();
        var disk = rsDrive(level).getItemDisks()[0];
        var stack = sample(13);
        require(network != null && disk != null && disk.getStored() == count, "Wrong physical RS disk count");
        var observed = network.extractItem(stack, 64, IComparer.COMPARE_NBT, Action.SIMULATE);
        require((count == 0 ? observed.isEmpty() && network.insertItem(stack, 64, Action.SIMULATE).isEmpty()
            : observed.getCount() == count && ItemStack.isSameItemSameTags(observed, stack))
            && network.extractItem(sample(0), 1, IComparer.COMPARE_NBT, Action.SIMULATE).isEmpty(),
            "Wrong RS item count, tag or mount state");
    }

    private static void take(ServerLevel level, int count) {
        for (int z : AE_ROUTES) {
            require(aeStorage(level, z).extract(AEItemKey.of(sample(z)), count, Actionable.MODULATE, AE_SOURCE) == count,
                "AE2 storage extraction failed at z=" + z);
        }
        var network = rsNode(level, 1086).getNetwork();
        require(network != null, "RS controller has no storage network");
        var stack = sample(13);
        var result = network.extractItem(stack, count, IComparer.COMPARE_NBT, Action.PERFORM);
        require(result.getCount() == count && ItemStack.isSameItemSameTags(result, stack), "RS returned the wrong items");
    }

    private static ItemStack sample(int route) {
        var stack = new ItemStack(Items.IRON_INGOT);
        stack.getOrCreateTag().putInt("PortRoute", route);
        return stack;
    }

    private static MEStorage aeStorage(ServerLevel level, int z) {
        var source = GridHelper.getExposedNode(level, new BlockPos(1086, 80, z), Direction.EAST);
        require(source != null && source.isActive(), "Missing powered AE2 source at z=" + z);
        return source.getGrid().getStorageService().getInventory();
    }

    private static DriveBlockEntity aeDrive(ServerLevel level, int z) {
        var block = level.getBlockEntity(new BlockPos(1089, 80, z));
        if (block instanceof DriveBlockEntity drive) return drive;
        throw new IllegalStateException("Missing AE2 drive at z=" + z);
    }

    private static DiskDriveNetworkNode rsDrive(ServerLevel level) {
        var node = rsNode(level, 1089);
        if (node instanceof DiskDriveNetworkNode drive) return drive;
        throw new IllegalStateException("Missing RS disk drive");
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
