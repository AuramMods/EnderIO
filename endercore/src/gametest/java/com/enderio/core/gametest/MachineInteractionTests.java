package com.enderio.core.gametest;

import com.enderio.enderio.api.io.IOMode;
import com.enderio.enderio.api.io.RedstoneControl;
import com.enderio.enderio.api.io.SideConfig;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

import static com.enderio.core.gametest.MachineInteractionTestContent.*;

@GameTestHolder("endercore_tests")
@PrefixGameTestTemplate(false)
public class MachineInteractionTests {
    private static final class Peer implements AutoCloseable {
        final ServerPlayer player;
        final Connection connection = new Connection(PacketFlow.SERVERBOUND);
        final EmbeddedChannel socket = new EmbeddedChannel(connection);
        Peer(GameTestHelper helper, BlockPos pos) {
            var level = helper.getLevel();
            player = new ServerPlayer(level.getServer(), level, new GameProfile(UUID.randomUUID(), "MachineFixture"));
            new ServerGamePacketListenerImpl(level.getServer(), connection, player);
            connection.setProtocol(ConnectionProtocol.PLAY);
            player.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
            player.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() - 1.5);
        }
        InteractionResult use(BlockPos pos, InteractionHand hand) {
            return player.gameMode.useItemOn(player, player.level(), player.getItemInHand(hand), hand, hit(pos));
        }
        public void close() { socket.finishAndReleaseAll(); player.getTextFilter().leave(); }
    }

    private static BlockHitResult hit(BlockPos pos) { return new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false); }
    private static FixtureEntity machine(GameTestHelper helper, BlockPos pos) {
        helper.getLevel().setBlockAndUpdate(pos, BLOCK.get().defaultBlockState());
        return (FixtureEntity) helper.getLevel().getBlockEntity(pos);
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void nativeUsePrioritizesHeldItemsAndOpensMenuOnlyForMainHand(GameTestHelper helper) {
        var pos = helper.absolutePos(new BlockPos(1, 2, 1));
        var machine = machine(helper, pos);
        try (var peer = new Peer(helper, pos)) {
            peer.player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.SLIME_BALL));
            helper.assertTrue(peer.use(pos, InteractionHand.OFF_HAND).consumesAction() && machine.itemUses == 1 && machine.menuOpens == 0,
                "Offhand machine item action was lost or also opened the menu");
            peer.player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            helper.assertTrue(peer.use(pos, InteractionHand.OFF_HAND) == InteractionResult.PASS && machine.menuOpens == 0,
                "Offhand fallback opened a second menu");
            helper.assertTrue(peer.use(pos, InteractionHand.MAIN_HAND).consumesAction() && machine.menuOpens == 1
                && peer.player.containerMenu != peer.player.inventoryMenu, "Native Forge screen opening did not install the server menu");
            peer.player.closeContainer();
            peer.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.SLIME_BALL));
            peer.use(pos, InteractionHand.MAIN_HAND);
            helper.assertTrue(machine.itemUses == 2 && machine.menuOpens == 1, "Main-hand item handling did not precede menu fallback");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void actualYetaAndForeignWrenchCycleOnceAndInvalidateOldSideCapability(GameTestHelper helper) {
        var pos = helper.absolutePos(new BlockPos(1, 2, 1));
        var machine = machine(helper, pos);
        FOREIGN_WRENCH.get().calls = 0;
        try (var peer = new Peer(helper, pos)) {
            for (var wrench : new net.minecraft.world.item.Item[] {WRENCH.get(), FOREIGN_WRENCH.get()}) {
                var oldSide = machine.getCapability(SideConfig.CAPABILITY, Direction.UP);
                helper.assertTrue(oldSide.isPresent(), "Native side capability was missing");
                var expected = machine.getNextIOMode(Direction.UP);
                int oldChanges = machine.modeChanges;
                int oldUses = machine.wrenchUses;
                peer.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(wrench));
                helper.assertTrue(peer.use(pos, InteractionHand.MAIN_HAND).consumesAction(), "Wrench did not consume native interaction");
                helper.assertTrue(machine.getIOMode(Direction.UP) == expected && machine.modeChanges == oldChanges + 1
                    && machine.wrenchUses == oldUses + 1, "Wrench cycled twice or escaped authoritative server processing");
                helper.assertTrue(!oldSide.isPresent() && machine.getCapability(SideConfig.CAPABILITY, Direction.UP).isPresent(),
                    "Wrench did not invalidate and rebind the native side capability");
            }
            helper.assertTrue(machine.menuOpens == 0 && FOREIGN_WRENCH.get().calls == 0,
                "Consumed wrench action also opened a menu or invoked the foreign item");
        }
        helper.succeed();
    }

    private static final class Protection {
        final UUID player;
        int mode;
        Protection(ServerPlayer player) { this.player = player.getUUID(); }
        @SubscribeEvent(priority = EventPriority.HIGH)
        public void protect(PlayerInteractEvent.RightClickBlock event) {
            if (!event.getEntity().getUUID().equals(player)) return;
            if (mode == 0) event.setCanceled(true);
            if (mode == 1) event.setUseBlock(Event.Result.DENY);
            if (mode == 2) event.setUseItem(Event.Result.DENY);
        }
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void canceledDeniedAndSpectatorInteractionsDoNotWrenchMachines(GameTestHelper helper) {
        var pos = helper.absolutePos(new BlockPos(1, 2, 1));
        var machine = machine(helper, pos);
        try (var peer = new Peer(helper, pos)) {
            peer.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(WRENCH.get()));
            peer.player.setShiftKeyDown(true);
            var protection = new Protection(peer.player);
            MinecraftForge.EVENT_BUS.register(protection);
            try {
                for (protection.mode = 0; protection.mode < 3; protection.mode++) {
                    peer.use(pos, InteractionHand.MAIN_HAND);
                    helper.assertTrue(machine.wrenchUses == 0 && machine.modeChanges == 0,
                        "Wrench bypassed protection mode " + protection.mode);
                }
            } finally { MinecraftForge.EVENT_BUS.unregister(protection); }
            peer.player.gameMode.changeGameModeForPlayer(GameType.SPECTATOR);
            peer.use(pos, InteractionHand.MAIN_HAND);
            helper.assertTrue(helper.getLevel().getBlockEntity(pos) == machine && machine.wrenchUses == 0
                && machine.modeChanges == 0 && machine.menuOpens == 0, "Denied or spectator interaction mutated the machine");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void nativeWrenchRemovalAndPlacementPreserveActualMachineState(GameTestHelper helper) {
        var pos = helper.absolutePos(new BlockPos(1, 2, 1));
        var machine = machine(helper, pos);
        machine.getInventory().setStackInSlot(0, new ItemStack(Items.DIAMOND, 257));
        machine.setIOMode(Direction.UP, IOMode.DISABLED);
        machine.setRedstoneControl(RedstoneControl.NEVER_ACTIVE);
        try (var peer = new Peer(helper, pos)) {
            peer.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(WRENCH.get()));
            peer.player.setShiftKeyDown(true);
            peer.use(pos, InteractionHand.MAIN_HAND);
            helper.assertTrue(helper.getLevel().isEmptyBlock(pos) && machine.isRemoved(), "Wrench failed to remove the native block entity");
            ItemStack carried = ItemStack.EMPTY;
            int count = 0;
            for (var stack : peer.player.getInventory().items) if (stack.is(ITEM.get())) { count += stack.getCount(); carried = stack.copy(); }
            helper.assertTrue(count == 1 && REMOVED.getOrDefault(carried, false), "Drop was duplicated or exported before removal callbacks");
            helper.assertTrue(helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(2)).isEmpty(), "Inventory delivery also spawned duplicate drops");
            helper.getLevel().setBlockAndUpdate(pos.below(), Blocks.STONE.defaultBlockState());
            peer.player.setShiftKeyDown(false);
            peer.player.setItemInHand(InteractionHand.MAIN_HAND, carried);
            var context = new UseOnContext(peer.player, InteractionHand.MAIN_HAND, hit(pos.below()));
            helper.assertTrue(((BlockItem) carried.getItem()).place(new BlockPlaceContext(context)).consumesAction(), "Native machine item placement failed");
            var placed = (FixtureEntity) helper.getLevel().getBlockEntity(pos);
            helper.assertTrue(placed.getInventory().getStackInSlot(0).getCount() == 257 && placed.getIOMode(Direction.UP) == IOMode.DISABLED
                && placed.getRedstoneControl() == RedstoneControl.NEVER_ACTIVE, "Actual machine item hooks lost inventory or configuration");
            helper.assertTrue(peer.player.getUUID().equals(placed.getMachineOwner()), "Native placement did not assign the owner");
            var saved = placed.saveWithFullMetadata();
            var restored = (FixtureEntity) BlockEntity.loadStatic(pos, placed.getBlockState(), saved);
            helper.assertTrue(restored != null && restored.getInventory().getStackInSlot(0).getCount() == 257
                && restored.getIOMode(Direction.UP) == IOMode.DISABLED && restored.getRedstoneControl() == RedstoneControl.NEVER_ACTIVE
                && peer.player.getUUID().equals(restored.getMachineOwner()), "Native world load lost actual machine state before level attachment");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void fullInventoryWrenchOverflowDropsExactlyOneStatefulItem(GameTestHelper helper) {
        var pos = helper.absolutePos(new BlockPos(1, 2, 1));
        var machine = machine(helper, pos);
        machine.getInventory().setStackInSlot(0, new ItemStack(Items.EMERALD, 9));
        try (var peer = new Peer(helper, pos)) {
            for (int i = 0; i < peer.player.getInventory().items.size(); i++) peer.player.getInventory().setItem(i, new ItemStack(Items.STONE, 64));
            peer.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(WRENCH.get()));
            peer.player.setShiftKeyDown(true);
            peer.use(pos, InteractionHand.MAIN_HAND);
            peer.use(pos, InteractionHand.MAIN_HAND);
            var drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(3));
            helper.assertTrue(drops.size() == 1 && drops.get(0).getItem().is(ITEM.get()) && drops.get(0).getItem().getCount() == 1,
                "Full player inventory lost or duplicated dismantled machine");
            var contents = com.enderio.core.common.blockentity.EnderBlockEntity.ITEM_INVENTORY.get(drops.get(0).getItem());
            machine.getInventory().deserializeNBT(contents);
            helper.assertTrue(machine.getInventory().getStackInSlot(0).is(Items.EMERALD)
                && machine.getInventory().getStackInSlot(0).getCount() == 9, "Overflow drop lost carried contents");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void actualResettingLeversScheduleNativeTicksAndPreserveInvertedSignals(GameTestHelper helper) {
        var pos = helper.absolutePos(new BlockPos(1, 2, 1));
        var invertedPos = pos.east();
        for (var target : new BlockPos[] {pos, invertedPos}) helper.getLevel().setBlockAndUpdate(target.below(), Blocks.STONE.defaultBlockState());
        helper.getLevel().setBlockAndUpdate(pos, LEVER.get().defaultBlockState().setValue(LeverBlock.FACE, AttachFace.FLOOR));
        helper.getLevel().setBlockAndUpdate(invertedPos, INVERTED_LEVER.get().defaultBlockState().setValue(LeverBlock.FACE, AttachFace.FLOOR));
        try (var peer = new Peer(helper, pos)) {
            helper.assertTrue(peer.use(pos, InteractionHand.OFF_HAND) == InteractionResult.PASS, "Offhand activated the reset lever");
            peer.use(pos, InteractionHand.MAIN_HAND);
            peer.use(invertedPos, InteractionHand.MAIN_HAND);
        }
        helper.runAfterDelay(10, () -> {
            helper.assertTrue(helper.getLevel().getBlockState(pos).getValue(LeverBlock.POWERED)
                && helper.getLevel().getBlockState(invertedPos).getValue(LeverBlock.POWERED), "Lever reset before its configured delay");
            helper.assertTrue(helper.getLevel().getBlockState(pos).getSignal(helper.getLevel(), pos, Direction.NORTH) == 15
                && helper.getLevel().getBlockState(invertedPos).getSignal(helper.getLevel(), invertedPos, Direction.NORTH) == 0, "Active lever inversion changed");
        });
        helper.runAfterDelay(22, () -> {
            helper.assertTrue(!helper.getLevel().getBlockState(pos).getValue(LeverBlock.POWERED)
                && !helper.getLevel().getBlockState(invertedPos).getValue(LeverBlock.POWERED), "Native scheduled tick failed to reset levers");
            helper.assertTrue(helper.getLevel().getBlockState(pos).getSignal(helper.getLevel(), pos, Direction.NORTH) == 0
                && helper.getLevel().getBlockState(invertedPos).getSignal(helper.getLevel(), invertedPos, Direction.NORTH) == 15, "Reset lever inversion changed");
            helper.succeed();
        });
    }
}
