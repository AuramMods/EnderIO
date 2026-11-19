package porting.fixture;

import com.enderio.api.conduit.ConduitType;
import com.enderio.api.io.IOMode;
import com.enderio.api.misc.ColorControl;
import com.enderio.api.misc.RedstoneControl;
import com.enderio.conduits.common.conduit.block.ConduitBlockEntity;
import com.enderio.conduits.common.conduit.connection.DynamicConnectionState;
import com.enderio.conduits.common.conduit.connection.StaticConnectionStates;
import com.enderio.conduits.common.init.EIOConduitTypes;
import com.enderio.conduits.common.redstone.DoubleRedstoneChannel;
import com.enderio.conduits.common.redstone.RedstoneCountFilter;
import com.enderio.conduits.common.redstone.RedstoneTimerFilter;
import com.enderio.core.common.capability.FluidFilterCapability;
import com.enderio.core.common.capability.ItemFilterCapability;
import com.enderio.machines.common.blockentity.TravelAnchorBlockEntity;
import com.enderio.machines.common.blockentity.FluidTankBlockEntity;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.CompoundTag;
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
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
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
        event.getDispatcher().register(Commands.literal("port_legacy_storage_conduits").requires(source -> source.hasPermission(4))
            .executes(context -> {
                var level = context.getSource().getLevel();
                place(level, new BlockPos(1087, 79, 4), "me_conduit");
                place(level, new BlockPos(1088, 79, 4), "me_conduit");
                place(level, new BlockPos(1087, 79, 7), "dense_me_conduit");
                place(level, new BlockPos(1088, 79, 7), "dense_me_conduit");
                place(level, new BlockPos(1087, 79, 10), "me_conduit");
                place(level, new BlockPos(1088, 79, 10), "dense_me_conduit");
                place(level, new BlockPos(1087, 79, 13), "refined_storage_conduit");
                place(level, new BlockPos(1088, 79, 13), "refined_storage_conduit");
                context.getSource().sendSuccess(() -> Component.literal("Legacy storage conduits created"), false);
                return 1;
            }));
        event.getDispatcher().register(Commands.literal("port_legacy_mekanism_conduits").requires(source -> source.hasPermission(4))
            .executes(context -> {
                var level = context.getSource().getLevel();
                mekanismRoute(level, 0, "chemical", false);
                mekanismRoute(level, 3, "pressurized_chemical", false);
                mekanismRoute(level, 6, "ender_chemical", false);
                mekanismRoute(level, 9, "ender_chemical", false);
                mekanismRoute(level, 12, "heat", false);
                mekanismRoute(level, 15, "chemical", true);
                context.getSource().sendSuccess(() -> Component.literal("Legacy Mekanism conduits created"), false);
                return 1;
            }));
        event.getDispatcher().register(Commands.literal("port_legacy_extraction_upgrades").requires(source -> source.hasPermission(4))
            .executes(context -> {
                buildExtractionRoutes(context.getSource().getLevel());
                context.getSource().sendSuccess(() -> Component.literal("Legacy extraction upgrade routes created"), false);
                return 1;
            }));
        event.getDispatcher().register(Commands.literal("port_legacy_redstone_filters").requires(source -> source.hasPermission(4))
            .executes(context -> {
                buildRedstoneFilterRoutes(context.getSource().getLevel());
                context.getSource().sendSuccess(() -> Component.literal("Legacy redstone filter routes created"), false);
                return 1;
            }));
        event.getDispatcher().register(Commands.literal("port_legacy_transfer_filters").requires(source -> source.hasPermission(4))
            .executes(context -> {
                buildTransferFilterRoutes(context.getSource().getLevel());
                context.getSource().sendSuccess(() -> Component.literal("Legacy transfer filter routes created"), false);
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
        event.getDispatcher().register(Commands.literal("port_legacy_carried_tanks").requires(source -> source.hasPermission(4))
            .executes(context -> {
                var level = context.getSource().getLevel();
                var barrelPos = new BlockPos(6, 80, 3);
                level.setBlockAndUpdate(barrelPos, Blocks.BARREL.defaultBlockState());
                var barrel = (BarrelBlockEntity) level.getBlockEntity(barrelPos);
                var water = new FluidStack(Fluids.WATER, 11500);
                var xp = new FluidStack(ForgeRegistries.FLUIDS.getValue(ResourceLocation.fromNamespaceAndPath("enderio", "xp_juice")), 21750);
                water.getOrCreateTag().putInt("PortLegacyCarried", 31);
                xp.getOrCreateTag().putInt("PortLegacyCarried", 51);
                barrel.setItem(0, carriedTank(level, "fluid_tank", water));
                barrel.setItem(1, carriedTank(level, "pressurized_fluid_tank", xp));
                barrel.setItem(2, carriedTank(level, "fluid_tank", water));
                context.getSource().sendSuccess(() -> Component.literal("Legacy carried tanks created"), false);
                return 1;
            }));
    }

    private static ItemStack carriedTank(ServerLevel level, String id, FluidStack contents) {
        var tank = stack(id);
        var source = new BlockPos(6, 80, 5);
        level.setBlockAndUpdate(source, ((BlockItem) tank.getItem()).getBlock().defaultBlockState());
        var entity = (FluidTankBlockEntity) level.getBlockEntity(source);
        entity.getInventory().setStackInSlot(1, new ItemStack(Items.DIAMOND, 7));
        entity.getIOConfig().setMode(Direction.EAST, IOMode.PULL);
        entity.setRedstoneControl(RedstoneControl.ACTIVE_WITH_SIGNAL);
        entity.saveToItem(tank);
        var handler = tank.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM).orElseThrow(IllegalStateException::new);
        if (handler.fill(contents.copy(), FluidAction.EXECUTE) != contents.getAmount()) {
            throw new IllegalStateException("Could not fill the historical tank " + id);
        }
        level.removeBlock(source, false);
        return handler.getContainer();
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
        return place(level, new BlockPos(1008 + x, 79, 8), "item_conduit");
    }

    private static void mekanismRoute(ServerLevel level, int z, String conduit, boolean locked) {
        var type = EIOConduitTypes.REGISTRY.get().getValue(ResourceLocation.fromNamespaceAndPath("enderio", conduit));
        if (type == null) throw new IllegalStateException("Missing old conduit " + conduit);
        var source = place(level, new BlockPos(1151, 79, z), conduit + "_conduit");
        var destination = place(level, new BlockPos(1152, 79, z), conduit + "_conduit");
        source.handleConnectionStateUpdate(Direction.WEST, type,
            new DynamicConnectionState(false, ColorControl.CYAN, true, ColorControl.BLUE,
                RedstoneControl.ALWAYS_ACTIVE, ColorControl.ORANGE, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY));
        destination.handleConnectionStateUpdate(Direction.EAST, type,
            new DynamicConnectionState(true, ColorControl.BLUE, false, ColorControl.PURPLE,
                RedstoneControl.NEVER_ACTIVE, ColorControl.LIGHT_BLUE, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY));
        source.getBundle().setConnectionState(Direction.NORTH, type, StaticConnectionStates.DISABLED);
        if (locked) {
            var lock = new CompoundTag();
            lock.putString("chemicalType", "gas");
            lock.putString("gasName", "mekanism:hydrogen");
            var data = new CompoundTag();
            data.put("LockedFluid", lock);
            source.getBundle().getNodeFor(type).getConduitData().deserializeNBT(data);
            destination.getBundle().getNodeFor(type).getConduitData().deserializeNBT(data);
        }
        source.setChanged();
        destination.setChanged();
    }

    private static void buildExtractionRoutes(ServerLevel level) {
        var type = EIOConduitTypes.ITEM.get();
        for (int tier = 1; tier <= 4; tier++) {
            int z = tier * 3 - 2;
            var source = place(level, new BlockPos(1215, 79, z), "item_conduit");
            var destination = place(level, new BlockPos(1216, 79, z), "item_conduit");
            var upgrade = stack("extraction_speed_upgrade_" + tier);
            upgrade.getOrCreateTag().putInt("PortInstalledTier", tier);
            source.handleConnectionStateUpdate(Direction.WEST, type,
                new DynamicConnectionState(false, ColorControl.GREEN, true, ColorControl.GREEN,
                    RedstoneControl.ALWAYS_ACTIVE, ColorControl.RED, ItemStack.EMPTY, ItemStack.EMPTY, upgrade));
            destination.handleConnectionStateUpdate(Direction.EAST, type,
                new DynamicConnectionState(true, ColorControl.GREEN, false, ColorControl.GREEN,
                    RedstoneControl.NEVER_ACTIVE, ColorControl.RED, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY));
            source.setChanged();
            destination.setChanged();
        }
    }

    private static void buildRedstoneFilterRoutes(ServerLevel level) {
        var dueTimer = taggedFilter("redstone_timer_filter", "due-timer");
        var dueFilter = new RedstoneTimerFilter(dueTimer);
        dueFilter.setTimer(30, 80);
        // The historical timer editor changed the period without resetting the elapsed ticks.
        dueFilter.setMaxTicks(30);
        redstoneFilterRoute(level, 1, ColorControl.LIME, false, dueTimer,
            taggedCounter("due-counter", ColorControl.LIME, 2, 3));

        var gate = taggedFilter("redstone_xor_filter", "gate");
        new DoubleRedstoneChannel(gate).setChannels(ColorControl.RED, ColorControl.BLUE);
        var gateSource = redstoneFilterRoute(level, 4, ColorControl.RED, true, ItemStack.EMPTY, gate);
        gateSource.handleConnectionStateUpdate(Direction.NORTH, EIOConduitTypes.REDSTONE.get(),
            new DynamicConnectionState(false, ColorControl.WHITE, true, ColorControl.BLUE,
                RedstoneControl.ALWAYS_ACTIVE, ColorControl.GREEN, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY));
        gateSource.setChanged();

        redstoneFilterRoute(level, 8, ColorControl.YELLOW, true, ItemStack.EMPTY,
            taggedFilter("redstone_toggle_filter", "latch"));

        var timer = taggedFilter("redstone_timer_filter", "timer");
        new RedstoneTimerFilter(timer).setTimer(30, 80);
        redstoneFilterRoute(level, 12, ColorControl.PURPLE, false, timer,
            taggedCounter("counter", ColorControl.PURPLE, 7, 8));

        redstoneFilterRoute(level, 15, ColorControl.CYAN, true,
            taggedFilter("redstone_sensor_filter", "sensor"), taggedFilter("redstone_not_filter", "inverter"));
    }

    private static void buildTransferFilterRoutes(ServerLevel level) {
        var basic = taggedFilter("basic_item_filter", "basic");
        var allow = new ItemFilterCapability(basic, 5);
        var paper = new ItemStack(Items.PAPER);
        paper.getOrCreateTag().putInt("Sample", 41);
        allow.setEntry(4, paper);
        allow.setInverted(false);
        allow.setNbt(false);

        var advanced = taggedFilter("advanced_item_filter", "advanced");
        var deny = new ItemFilterCapability(advanced, 10);
        var pick = new ItemStack(Items.IRON_PICKAXE);
        pick.setDamageValue(27);
        pick.getOrCreateTag().putInt("Sample", 42);
        deny.setEntry(2, stack("copper_alloy_ingot"));
        deny.setEntry(9, pick);
        deny.setInverted(true);
        deny.setNbt(true);

        var fluid = taggedFilter("basic_fluid_filter", "fluid");
        var allowFluid = new FluidFilterCapability(fluid, 5);
        var sample = new FluidStack(Fluids.WATER, 37);
        sample.getOrCreateTag().putInt("Sample", 43);
        allowFluid.setEntry(4, sample);
        allowFluid.setInverted(false);
        allowFluid.setNbt(true);

        var invertedFluid = taggedFilter("basic_fluid_filter", "inverted-fluid");
        var denyFluid = new FluidFilterCapability(invertedFluid, 5);
        var xp = new FluidStack(ForgeRegistries.FLUIDS.getValue(ResourceLocation.fromNamespaceAndPath("enderio", "xp_juice")), 11);
        xp.getOrCreateTag().putInt("Sample", 44);
        var deniedWater = new FluidStack(Fluids.WATER, 19, xp.getTag().copy());
        denyFluid.setEntry(1, xp);
        denyFluid.setEntry(3, new FluidStack(Fluids.LAVA, 23));
        denyFluid.setEntry(4, deniedWater);
        denyFluid.setInverted(true);
        denyFluid.setNbt(false);

        var supplies = (BarrelBlockEntity) level.getBlockEntity(new BlockPos(4638, 80, 14));
        supplies.setItem(0, basic.copy());
        supplies.setItem(3, advanced.copy());
        supplies.setItem(8, fluid.copy());
        supplies.setItem(15, invertedFluid.copy());
        supplies.setChanged();

        var itemType = EIOConduitTypes.ITEM.get();
        var fluidType = EIOConduitTypes.FLUID.get();
        transferFilterRoute(level, 2, "item_conduit", itemType, basic, ItemStack.EMPTY);
        transferFilterRoute(level, 5, "item_conduit", itemType, ItemStack.EMPTY, advanced);
        transferFilterRoute(level, 8, "fluid_conduit", fluidType, fluid, ItemStack.EMPTY);
        transferFilterRoute(level, 11, "fluid_conduit", fluidType, ItemStack.EMPTY, invertedFluid);
    }

    private static void transferFilterRoute(ServerLevel level, int z, String conduit,
            ConduitType<?> type, ItemStack extract, ItemStack insert) {
        var source = place(level, new BlockPos(4639, 79, z), conduit);
        var destination = place(level, new BlockPos(4640, 79, z), conduit);
        source.handleConnectionStateUpdate(Direction.WEST, type,
            new DynamicConnectionState(false, ColorControl.WHITE, true, ColorControl.ORANGE,
                RedstoneControl.ALWAYS_ACTIVE, ColorControl.GREEN, ItemStack.EMPTY, extract, ItemStack.EMPTY));
        destination.handleConnectionStateUpdate(Direction.EAST, type,
            new DynamicConnectionState(true, ColorControl.ORANGE, false, ColorControl.WHITE,
                RedstoneControl.ALWAYS_ACTIVE, ColorControl.GREEN, insert, ItemStack.EMPTY, ItemStack.EMPTY));
        source.setChanged();
        destination.setChanged();
    }

    private static ConduitBlockEntity redstoneFilterRoute(ServerLevel level, int z, ColorControl color, boolean enabled,
            ItemStack extract, ItemStack insert) {
        var source = place(level, new BlockPos(4527, 79, z), "redstone_conduit");
        var destination = place(level, new BlockPos(4528, 79, z), "redstone_conduit");
        var type = EIOConduitTypes.REDSTONE.get();
        if (level.getBlockState(source.getBlockPos().west()).is(Blocks.CHEST)) {
            source.tryConnectTo(Direction.WEST, type, false, false, true);
        }
        source.handleConnectionStateUpdate(Direction.WEST, type,
            new DynamicConnectionState(false, ColorControl.WHITE, enabled, color,
                RedstoneControl.ALWAYS_ACTIVE, ColorControl.GREEN, ItemStack.EMPTY, extract, ItemStack.EMPTY));
        destination.handleConnectionStateUpdate(Direction.EAST, type,
            new DynamicConnectionState(enabled, color, false, ColorControl.WHITE,
                RedstoneControl.ALWAYS_ACTIVE, ColorControl.GREEN, insert, ItemStack.EMPTY, ItemStack.EMPTY));
        source.setChanged();
        destination.setChanged();
        return source;
    }

    private static ItemStack taggedFilter(String item, String marker) {
        var stack = stack(item);
        stack.getOrCreateTag().putString("PortFilter", marker);
        return stack;
    }

    private static ItemStack taggedCounter(String marker, ColorControl color, int count, int limit) {
        var stack = taggedFilter("redstone_counting_filter", marker);
        var filter = new RedstoneCountFilter(stack);
        filter.setChannel(color);
        filter.setMaxCount(limit);
        filter.setCount(count);
        filter.setDeactivated(true);
        return stack;
    }

    private static ConduitBlockEntity place(ServerLevel level, BlockPos support, String item) {
        var player = FakePlayerFactory.getMinecraft(level);
        var stack = stack(item);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        var hit = new BlockHitResult(Vec3.atCenterOf(support).add(0, 0.5, 0), Direction.UP, support, false);
        var context = new BlockPlaceContext(player, InteractionHand.MAIN_HAND, stack, hit);
        if (!((BlockItem) stack.getItem()).place(context).consumesAction()) {
            throw new IllegalStateException("Could not place old " + item + " at " + support);
        }
        return (ConduitBlockEntity) level.getBlockEntity(support.above());
    }

    private static ItemStack stack(String item) {
        return new ItemStack(ForgeRegistries.ITEMS.getValue(ResourceLocation.fromNamespaceAndPath("enderio", item)));
    }
}
