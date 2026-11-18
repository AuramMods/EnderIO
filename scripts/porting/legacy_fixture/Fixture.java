package porting.fixture;

import com.enderio.api.misc.ColorControl;
import com.enderio.api.misc.RedstoneControl;
import com.enderio.conduits.common.conduit.block.ConduitBlockEntity;
import com.enderio.conduits.common.conduit.connection.DynamicConnectionState;
import com.enderio.conduits.common.conduit.connection.StaticConnectionStates;
import com.enderio.conduits.common.init.EIOConduitTypes;
import com.enderio.core.common.capability.ItemFilterCapability;
import com.enderio.machines.common.blockentity.TravelAnchorBlockEntity;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

@Mod("port_legacy_fixture")
public class Fixture {
    public Fixture() {
        MinecraftForge.EVENT_BUS.addListener(this::register);
    }

    private void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("port_legacy_fixture").requires(source -> source.hasPermission(4))
            .executes(context -> {
                build(context.getSource().getLevel());
                context.getSource().sendSuccess(() -> Component.literal("Legacy conduit fixture created"), false);
                return 1;
            }));
        event.getDispatcher().register(Commands.literal("port_legacy_travel").requires(source -> source.hasPermission(4))
            .executes(context -> {
                var level = context.getSource().getLevel();
                anchor(level, new BlockPos(32, 80, 0), "travel_anchor", "Overworld Hub", Items.COMPASS, true);
                anchor(level, new BlockPos(40, 80, 0), "painted_travel_anchor", "Painted Workshop",
                    stack("copper_alloy_ingot").getItem(), true);
                anchor(level, new BlockPos(32, 88, 0), "travel_anchor", "Hidden Lift", Items.ENDER_PEARL, false);
                anchor(level.getServer().getLevel(Level.NETHER), new BlockPos(32, 80, 0), "travel_anchor",
                    "Nether Relay", Items.BLAZE_ROD, true);
                var barrelPos = new BlockPos(43, 79, 1);
                level.setBlockAndUpdate(barrelPos, Blocks.BARREL.defaultBlockState());
                var staff = stack("staff_of_travelling");
                var energy = staff.getCapability(ForgeCapabilities.ENERGY).orElseThrow(IllegalStateException::new);
                if (energy.receiveEnergy(6000, false) != 6000) throw new IllegalStateException("Could not charge the historical staff");
                ((BarrelBlockEntity) level.getBlockEntity(barrelPos)).setItem(0, staff);
                context.getSource().sendSuccess(() -> Component.literal("Legacy travel fixture created"), false);
                return 1;
            }));
    }

    private static void anchor(ServerLevel level, BlockPos pos, String blockId, String name, Item icon, boolean visible) {
        var block = ForgeRegistries.BLOCKS.getValue(ResourceLocation.fromNamespaceAndPath("enderio", blockId));
        level.setBlockAndUpdate(pos, block.defaultBlockState());
        var anchor = (TravelAnchorBlockEntity) level.getBlockEntity(pos);
        anchor.getInventory().setStackInSlot(0, new ItemStack(icon));
        anchor.setName(name);
        anchor.setIcon(icon);
        anchor.setVisibility(visible);
        anchor.setChanged();
        if (!name.equals(anchor.getName()) || anchor.getIcon() != icon || anchor.getVisibility() != visible) {
            throw new IllegalStateException("The historical anchor did not retain its settings at " + pos);
        }
    }

    private static void build(ServerLevel level) {
        var source = place(level, 15);
        var destination = place(level, 16);
        place(level, 10);
        var type = EIOConduitTypes.ITEM.get();
        var player = FakePlayerFactory.getMinecraft(level);
        source.addType(EIOConduitTypes.ENERGY.get(), player);
        destination.addType(EIOConduitTypes.ENERGY.get(), player);

        ItemStack filter = stack("basic_item_filter");
        new ItemFilterCapability(filter, 5).setEntry(0, new ItemStack(Items.DIAMOND));
        source.handleConnectionStateUpdate(Direction.WEST, type,
            new DynamicConnectionState(false, ColorControl.CYAN, true, ColorControl.BLUE,
                RedstoneControl.ALWAYS_ACTIVE, ColorControl.ORANGE, ItemStack.EMPTY, filter,
                stack("extraction_speed_upgrade_2")));
        destination.handleConnectionStateUpdate(Direction.EAST, type,
            new DynamicConnectionState(true, ColorControl.BLUE, false, ColorControl.PURPLE,
                RedstoneControl.NEVER_ACTIVE, ColorControl.LIGHT_BLUE, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY));

        var sourceData = source.getBundle().getNodeFor(type).getConduitData().compute(Direction.WEST);
        sourceData.isRoundRobin = true;
        sourceData.isSelfFeed = true;
        sourceData.rotatingIndex = 1;
        sourceData.setPriority(42);
        destination.getBundle().getNodeFor(type).getConduitData().compute(Direction.EAST).setPriority(71);
        source.getBundle().setConnectionState(Direction.NORTH, type, StaticConnectionStates.DISABLED);
        for (var side : Direction.values()) {
            destination.getBundle().setFacade(Blocks.POLISHED_ANDESITE.defaultBlockState(), side);
        }
        source.setChanged();
        destination.setChanged();
    }

    private static ConduitBlockEntity place(ServerLevel level, int x) {
        var support = new BlockPos(1008 + x, 79, 8);
        var player = FakePlayerFactory.getMinecraft(level);
        var stack = stack("item_conduit");
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        var hit = new BlockHitResult(Vec3.atCenterOf(support).add(0, 0.5, 0), Direction.UP, support, false);
        var context = new BlockPlaceContext(player, InteractionHand.MAIN_HAND, stack, hit);
        if (!((BlockItem) stack.getItem()).place(context).consumesAction()) {
            throw new IllegalStateException("Could not place old item conduit at " + x);
        }
        return (ConduitBlockEntity) level.getBlockEntity(support.above());
    }

    private static ItemStack stack(String item) {
        return new ItemStack(ForgeRegistries.ITEMS.getValue(ResourceLocation.fromNamespaceAndPath("enderio", item)));
    }
}
