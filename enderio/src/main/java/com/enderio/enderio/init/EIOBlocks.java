package com.enderio.enderio.init;

import com.enderio.enderio.EnderIO;
import com.enderio.enderio.api.soul.Soul;
import com.enderio.enderio.content.cold_fire.ColdFireBlock;
import com.enderio.enderio.content.conduits.bundle.ConduitBundleBlock;
import com.enderio.enderio.content.enchanter.EnchanterBlock;
import com.enderio.enderio.content.enderface.EnderfaceBlock;
import com.enderio.enderio.content.glass.GlassBlocks;
import com.enderio.enderio.content.glass.GlassCollisionPredicate;
import com.enderio.enderio.content.glass.GlassIdentifier;
import com.enderio.enderio.content.glass.GlassLighting;
import com.enderio.enderio.content.machines.block_detector.BlockDetectorBlock;
import com.enderio.enderio.content.machines.capacitor_bank.CapacitorBankBlock;
import com.enderio.enderio.content.machines.capacitor_bank.CapacitorBankBlockEntity;
import com.enderio.enderio.content.machines.capacitor_bank.CapacitorBankItem;
import com.enderio.enderio.content.machines.capacitor_bank.CapacitorTier;
import com.enderio.enderio.content.machines.farming_station.FarmingStationBlock;
import com.enderio.enderio.content.machines.niard.NiardBlock;
import com.enderio.enderio.content.machines.obelisks.attractor.AttractorObeliskBlockEntity;
import com.enderio.enderio.content.machines.obelisks.aversion.AversionObeliskBlockEntity;
import com.enderio.enderio.content.machines.obelisks.inhibitor.InhibitorObeliskBlockEntity;
import com.enderio.enderio.content.machines.obelisks.relocator.RelocatorObeliskBlockEntity;
import com.enderio.enderio.content.machines.obelisks.weather.WeatherObeliskBlockEntity;
import com.enderio.enderio.content.machines.obelisks.xp.XPObeliskBlockEntity;
import com.enderio.enderio.content.machines.powered_spawner.MindKillerBlock;
import com.enderio.enderio.content.machines.powered_spawner.PoweredSpawnerBlockEntity;
import com.enderio.enderio.content.machines.solar_panel.SolarPanelBlock;
import com.enderio.enderio.content.machines.solar_panel.SolarPanelBlockEntity;
import com.enderio.enderio.content.machines.solar_panel.SolarPanelTier;
import com.enderio.enderio.content.machines.soul_engine.SoulEngineBlockEntity;
import com.enderio.enderio.content.machines.vacuum.chest.VacuumChestBlockEntity;
import com.enderio.enderio.content.machines.vacuum.xp.XPVacuumBlockEntity;
import com.enderio.enderio.content.machines.vat.VatBlock;
import com.enderio.enderio.content.machines.wireless_charger.WirelessAntennaBlock;
import com.enderio.enderio.content.misc_blocks.EIOPressurePlateBlock;
import com.enderio.enderio.content.misc_blocks.IndustrialInsulationBlock;
import com.enderio.enderio.content.misc_blocks.ReinforcedObsidianBlock;
import com.enderio.enderio.content.misc_blocks.ResettingLeverBlock;
import com.enderio.enderio.content.misc_blocks.light.LegacyLightBlock;
import com.enderio.enderio.content.misc_blocks.light.LegacyLightNodeBlock;
import com.enderio.enderio.content.misc_blocks.light.LegacyPoweredLightBlock;
import com.enderio.enderio.content.misc_blocks.SilentPressurePlateBlock;
import com.enderio.enderio.content.misc_blocks.SilentWeightedPressurePlateBlock;
import com.enderio.enderio.content.misc_blocks.skull.EnderSkullBlock;
import com.enderio.enderio.content.misc_blocks.skull.WallEnderSkullBlock;
import com.enderio.enderio.content.paint.block.PaintedCraftingTableBlock;
import com.enderio.enderio.content.paint.block.PaintedFenceBlock;
import com.enderio.enderio.content.paint.block.PaintedFenceGateBlock;
import com.enderio.enderio.content.paint.block.PaintedRedstoneBlock;
import com.enderio.enderio.content.paint.block.PaintedSandBlock;
import com.enderio.enderio.content.paint.block.PaintedSlabBlock;
import com.enderio.enderio.content.paint.block.PaintedStairBlock;
import com.enderio.enderio.content.paint.block.PaintedTrapDoorBlock;
import com.enderio.enderio.content.paint.block.PaintedWallBlock;
import com.enderio.enderio.content.paint.block.PaintedWoodenPressurePlateBlock;
import com.enderio.enderio.content.paint.block.SinglePaintedBlock;
import com.enderio.enderio.content.paint.item.PaintedBlockItem;
import com.enderio.enderio.content.storage.fluid_tank.FluidTankBlock;
import com.enderio.enderio.content.storage.fluid_tank.FluidTankBlockItem;
import com.enderio.enderio.content.travel.travel_anchor.PaintedTravelAnchorBlock;
import com.enderio.enderio.content.travel.travel_anchor.TravelAnchorBlock;
import com.enderio.enderio.content.travel.travel_anchor.TravelAnchorBlockEntity;
import com.enderio.enderio.foundation.block.MachineBlock;
import com.enderio.enderio.foundation.block.ProgressMachineBlock;
import com.enderio.enderio.foundation.block.entity.MachineBlockEntity;
import com.enderio.enderio.foundation.block.legacy.LegacyMachineBlock;
import com.google.common.collect.ImmutableMap;
import it.unimi.dsi.fastutil.Pair;
import net.minecraft.Util;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChainBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.PressurePlateBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.WeightedPressurePlateBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraftforge.eventbus.api.IEventBus;
import com.enderio.core.common.registries.BlockDeferredHolder;
import com.enderio.core.common.registries.ItemDeferredHolder;
import com.enderio.core.common.registries.BlockDeferredRegister;
import com.enderio.core.common.registries.ItemDeferredRegister;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

@SuppressWarnings("unused")
public class EIOBlocks {
    public static final BlockDeferredRegister BLOCKS = BlockDeferredRegister.create(EnderIO.MOD_ID);
    public static final ItemDeferredRegister ITEMS = ItemDeferredRegister.create(EnderIO.MOD_ID);

    // ======== NEW

    // region Alloy Blocks

    public static final BlockDeferredHolder<Block> CONDUCTIVE_ALLOY_BLOCK = registerMetalBlock("conductive_alloy_block");
    public static final BlockDeferredHolder<Block> ENERGETIC_ALLOY_BLOCK = registerMetalBlock("energetic_alloy_block");
    public static final BlockDeferredHolder<Block> VIBRANT_ALLOY_BLOCK = registerMetalBlock("vibrant_alloy_block");
    public static final BlockDeferredHolder<Block> REDSTONE_ALLOY_BLOCK = registerMetalBlock("redstone_alloy_block");
    public static final BlockDeferredHolder<Block> PULSATING_ALLOY_BLOCK = registerMetalBlock("pulsating_alloy_block");
    public static final BlockDeferredHolder<Block> DARK_STEEL_BLOCK = registerMetalBlock("dark_steel_block");
    public static final BlockDeferredHolder<Block> SOULARIUM_BLOCK = registerMetalBlock("soularium_block");
    public static final BlockDeferredHolder<Block> END_STEEL_BLOCK = registerMetalBlock("end_steel_block");

    private static BlockDeferredHolder<Block> registerMetalBlock(String name) {
        return registerWithItem(name, Block::new,
            BlockBehaviour.Properties.of().sound(SoundType.METAL).mapColor(MapColor.METAL).strength(5, 6).requiresCorrectToolForDrops());
    }

    // endregion

    // region Chassis

    // Iron tier
    public static final BlockDeferredHolder<Block> VOID_CHASSIS = registerChassisBlock("void_chassis");

    // Void chassis + some kind of dragons breath derrived process
    //    public static final RegiliteBlock<Block> REKINDLED_VOID_CHASSIS = registerChassisBlock("rekindled_void_chassis");

    // Soularium + soul/nether
    public static final BlockDeferredHolder<Block> ENSOULED_CHASSIS = registerChassisBlock("ensouled_chassis");

    // Ensnared + Some kind of other material
    // This is for machines that require a bound soul
    //    public static final RegiliteBlock<Block> TRAPPED_CHASSIS = registerChassisBlock("trapped_chassis");

    // Dark steel + sculk
    //    public static final RegiliteBlock<Block> SCULK_CHASSIS = registerChassisBlock("sculk_chassis");

    private static BlockDeferredHolder<Block> registerChassisBlock(String name) {
        return registerWithItem(name, Block::new, BlockBehaviour.Properties.of().noOcclusion().sound(SoundType.METAL).mapColor(MapColor.METAL).strength(5, 6));
    }

    // endregion

    // region Dark Steel Building Blocks

    public static final BlockDeferredHolder<LadderBlock> DARK_STEEL_LADDER = registerWithItem("dark_steel_ladder", LadderBlock::new, BlockBehaviour.Properties.of().strength(0.4f).requiresCorrectToolForDrops().sound(SoundType.METAL).mapColor(MapColor.METAL).noOcclusion());

    public static final BlockDeferredHolder<IronBarsBlock> DARK_STEEL_BARS = registerWithItem("dark_steel_bars", IronBarsBlock::new,
            BlockBehaviour.Properties.of().strength(5.0f, 1000.0f).requiresCorrectToolForDrops().sound(SoundType.METAL).noOcclusion());

    public static final BlockDeferredHolder<DoorBlock> DARK_STEEL_DOOR = registerWithItem("dark_steel_door", props -> new DoorBlock(props, BlockSetType.IRON),
            BlockBehaviour.Properties.of().strength(5.0f, 2000.0f).sound(SoundType.METAL).mapColor(MapColor.METAL).noOcclusion());

    public static final BlockDeferredHolder<TrapDoorBlock> DARK_STEEL_TRAPDOOR = registerWithItem("dark_steel_trapdoor", props -> new TrapDoorBlock(props, BlockSetType.IRON),
            BlockBehaviour.Properties.of().strength(5.0f, 2000.0f).sound(SoundType.METAL).mapColor(MapColor.METAL).noOcclusion());

    public static final BlockDeferredHolder<IronBarsBlock> END_STEEL_BARS = registerWithItem("end_steel_bars", IronBarsBlock::new,
            BlockBehaviour.Properties.of().strength(5.0f, 1000.0f).requiresCorrectToolForDrops().sound(SoundType.METAL).noOcclusion());

    public static final BlockDeferredHolder<ReinforcedObsidianBlock> REINFORCED_OBSIDIAN = registerWithItem("reinforced_obsidian_block", ReinforcedObsidianBlock::new,
            BlockBehaviour.Properties.of()
                .sound(SoundType.STONE)
                .strength(50, 2000)
                .requiresCorrectToolForDrops()
                .mapColor(MapColor.COLOR_BLACK)
                .instrument(NoteBlockInstrument.BASEDRUM));

    // endregion

    // region Painted Blocks

    public static Set<Pair<BlockDeferredHolder<? extends Block>, Block>> PAINTED_BLOCKS = new HashSet<>();

    public static final BlockDeferredHolder<PaintedFenceBlock> PAINTED_FENCE = registerPainted("painted_fence", PaintedFenceBlock::new, Blocks.OAK_FENCE);
    public static final BlockDeferredHolder<PaintedFenceGateBlock> PAINTED_FENCE_GATE = registerPainted("painted_fence_gate", PaintedFenceGateBlock::new, Blocks.OAK_FENCE_GATE);
    public static final BlockDeferredHolder<PaintedSandBlock> PAINTED_SAND = registerPainted("painted_sand", PaintedSandBlock::new, Blocks.SAND);
    public static final BlockDeferredHolder<PaintedStairBlock> PAINTED_STAIRS = registerPainted("painted_stairs", PaintedStairBlock::new, Blocks.OAK_STAIRS);
    public static final BlockDeferredHolder<PaintedCraftingTableBlock> PAINTED_CRAFTING_TABLE = registerPainted("painted_crafting_table", PaintedCraftingTableBlock::new, Blocks.CRAFTING_TABLE);
    public static final BlockDeferredHolder<PaintedRedstoneBlock> PAINTED_REDSTONE_BLOCK = registerPainted("painted_redstone_block", PaintedRedstoneBlock::new, Blocks.REDSTONE_BLOCK);
    public static final BlockDeferredHolder<PaintedTrapDoorBlock> PAINTED_TRAPDOOR = registerPainted("painted_trapdoor", PaintedTrapDoorBlock::new, Blocks.OAK_TRAPDOOR);
    public static final BlockDeferredHolder<PaintedWoodenPressurePlateBlock> PAINTED_WOODEN_PRESSURE_PLATE = registerPainted("painted_wooden_pressure_plate", PaintedWoodenPressurePlateBlock::new, Blocks.OAK_PRESSURE_PLATE);
    public static final BlockDeferredHolder<PaintedSlabBlock> PAINTED_SLAB = registerPainted("painted_slab", PaintedSlabBlock::new, Blocks.OAK_SLAB);
    public static final BlockDeferredHolder<SinglePaintedBlock> PAINTED_GLOWSTONE = registerPainted("painted_glowstone", SinglePaintedBlock::new, Blocks.GLOWSTONE);

    public static final BlockDeferredHolder<LegacyLightBlock> LIGHT = legacyLight("light", properties -> new LegacyLightBlock(properties, false));
    public static final BlockDeferredHolder<LegacyLightBlock> LIGHT_INVERTED = legacyLight("light_inverted", properties -> new LegacyLightBlock(properties, true));
    public static final BlockDeferredHolder<LegacyPoweredLightBlock> POWERED_LIGHT = legacyLight("powered_light",
        properties -> new LegacyPoweredLightBlock(properties, false, false));
    public static final BlockDeferredHolder<LegacyPoweredLightBlock> POWERED_LIGHT_INVERTED = legacyLight("powered_light_inverted",
        properties -> new LegacyPoweredLightBlock(properties, true, false));
    public static final BlockDeferredHolder<LegacyPoweredLightBlock> POWERED_LIGHT_WIRELESS = legacyLight("powered_light_wireless",
        properties -> new LegacyPoweredLightBlock(properties, false, true));
    public static final BlockDeferredHolder<LegacyPoweredLightBlock> POWERED_LIGHT_INVERTED_WIRELESS = legacyLight("powered_light_inverted_wireless",
        properties -> new LegacyPoweredLightBlock(properties, true, true));
    public static final BlockDeferredHolder<LegacyLightNodeBlock> LIGHT_NODE = BLOCKS.registerBlock("light_node", LegacyLightNodeBlock::new,
        BlockBehaviour.Properties.of().replaceable().noCollission().noOcclusion().noLootTable().lightLevel(state -> 15)
            .pushReaction(PushReaction.DESTROY));

    private static <B extends LegacyLightBlock> BlockDeferredHolder<B> legacyLight(String id, Function<BlockBehaviour.Properties, B> factory) {
        return registerWithItem(id, factory, BlockBehaviour.Properties.of().noOcclusion().sound(SoundType.METAL).mapColor(MapColor.METAL)
            .lightLevel(state -> state.getValue(LegacyLightBlock.ENABLED) ? 15 : 0));
    }
    public static final BlockDeferredHolder<PaintedWallBlock> PAINTED_WALL = registerPainted("painted_wall", PaintedWallBlock::new, Blocks.COBBLESTONE_WALL);

    private static <B extends Block> BlockDeferredHolder<B> registerPainted(String name, Function<BlockBehaviour.Properties, B> factory, Block reference) {
        var blockHolder = registerWithItem(name, factory, BlockBehaviour.Properties.copy(reference).noOcclusion(), b -> new PaintedBlockItem(b.get(), new Item.Properties()));
        PAINTED_BLOCKS.add(Pair.of(blockHolder, reference));
        return blockHolder;
    }

    // endregion

    // region Resetting Levers

    public static final Set<BlockDeferredHolder<ResettingLeverBlock>> RESETTING_LEVERS = new HashSet<>();

    public static final BlockDeferredHolder<ResettingLeverBlock> RESETTING_LEVER_FIVE = registerResettingLever("resetting_lever_five", 5, false);
    public static final BlockDeferredHolder<ResettingLeverBlock> RESETTING_LEVER_FIVE_INV = registerResettingLever("resetting_lever_five_inv", 5, true);
    public static final BlockDeferredHolder<ResettingLeverBlock> RESETTING_LEVER_TEN = registerResettingLever("resetting_lever_ten", 10, false);
    public static final BlockDeferredHolder<ResettingLeverBlock> RESETTING_LEVER_TEN_INV = registerResettingLever("resetting_lever_ten_inv", 10, true);
    public static final BlockDeferredHolder<ResettingLeverBlock> RESETTING_LEVER_THIRTY = registerResettingLever("resetting_lever_thirty", 30, false);
    public static final BlockDeferredHolder<ResettingLeverBlock> RESETTING_LEVER_THIRTY_INV = registerResettingLever("resetting_lever_thirty_inv", 30, true);
    public static final BlockDeferredHolder<ResettingLeverBlock> RESETTING_LEVER_SIXTY = registerResettingLever("resetting_lever_sixty", 60, false);
    public static final BlockDeferredHolder<ResettingLeverBlock> RESETTING_LEVER_SIXTY_INV = registerResettingLever("resetting_lever_sixty_inv", 60, true);
    public static final BlockDeferredHolder<ResettingLeverBlock> RESETTING_LEVER_THREE_HUNDRED = registerResettingLever("resetting_lever_three_hundred", 300, false);
    public static final BlockDeferredHolder<ResettingLeverBlock> RESETTING_LEVER_THREE_HUNDRED_INV = registerResettingLever("resetting_lever_three_hundred_inv", 300, true);

    private static BlockDeferredHolder<ResettingLeverBlock> registerResettingLever(String name, int delay, boolean inverted) {
        var blockHolder = registerWithItem(name, p -> new ResettingLeverBlock(delay, inverted), BlockBehaviour.Properties.of());
        RESETTING_LEVERS.add(blockHolder);
        return blockHolder;
    }

    // endregion

    // region Pressure Plates

    public static final BlockDeferredHolder<EIOPressurePlateBlock> DARK_STEEL_PRESSURE_PLATE = registerWithItem("dark_steel_pressure_plate",
        props -> new EIOPressurePlateBlock(props, EIOPressurePlateBlock.PLAYER, false),
        BlockBehaviour.Properties.of().strength(5, 6).mapColor(MapColor.METAL));

    public static final BlockDeferredHolder<EIOPressurePlateBlock> SILENT_DARK_STEEL_PRESSURE_PLATE = registerWithItem("silent_dark_steel_pressure_plate",
        props -> new EIOPressurePlateBlock(props, EIOPressurePlateBlock.PLAYER, true),
        BlockBehaviour.Properties.of().strength(5, 6).mapColor(MapColor.METAL));

    public static final BlockDeferredHolder<EIOPressurePlateBlock> SOULARIUM_PRESSURE_PLATE = registerWithItem("soularium_pressure_plate",
        props -> new EIOPressurePlateBlock(props, EIOPressurePlateBlock.HOSTILE_MOB, false),
        BlockBehaviour.Properties.of().strength(5, 6).mapColor(MapColor.METAL));

    public static final BlockDeferredHolder<EIOPressurePlateBlock> SILENT_SOULARIUM_PRESSURE_PLATE = registerWithItem("silent_soularium_pressure_plate",
        props -> new EIOPressurePlateBlock(props, EIOPressurePlateBlock.HOSTILE_MOB, true),
        BlockBehaviour.Properties.of().strength(5, 6).mapColor(MapColor.METAL));

    public static final BlockDeferredHolder<SilentPressurePlateBlock> SILENT_OAK_PRESSURE_PLATE = silentPressurePlateBlock(
        (PressurePlateBlock) Blocks.OAK_PRESSURE_PLATE);

    public static final BlockDeferredHolder<SilentPressurePlateBlock> SILENT_ACACIA_PRESSURE_PLATE = silentPressurePlateBlock(
        (PressurePlateBlock) Blocks.ACACIA_PRESSURE_PLATE);

    public static final BlockDeferredHolder<SilentPressurePlateBlock> SILENT_DARK_OAK_PRESSURE_PLATE = silentPressurePlateBlock(
        (PressurePlateBlock) Blocks.DARK_OAK_PRESSURE_PLATE);

    public static final BlockDeferredHolder<SilentPressurePlateBlock> SILENT_SPRUCE_PRESSURE_PLATE = silentPressurePlateBlock(
        (PressurePlateBlock) Blocks.SPRUCE_PRESSURE_PLATE);

    public static final BlockDeferredHolder<SilentPressurePlateBlock> SILENT_BIRCH_PRESSURE_PLATE = silentPressurePlateBlock(
        (PressurePlateBlock) Blocks.BIRCH_PRESSURE_PLATE);

    public static final BlockDeferredHolder<SilentPressurePlateBlock> SILENT_JUNGLE_PRESSURE_PLATE = silentPressurePlateBlock(
        (PressurePlateBlock) Blocks.JUNGLE_PRESSURE_PLATE);

    public static final BlockDeferredHolder<SilentPressurePlateBlock> SILENT_CRIMSON_PRESSURE_PLATE = silentPressurePlateBlock(
        (PressurePlateBlock) Blocks.CRIMSON_PRESSURE_PLATE);

    public static final BlockDeferredHolder<SilentPressurePlateBlock> SILENT_WARPED_PRESSURE_PLATE = silentPressurePlateBlock(
        (PressurePlateBlock) Blocks.WARPED_PRESSURE_PLATE);

    public static final BlockDeferredHolder<SilentPressurePlateBlock> SILENT_STONE_PRESSURE_PLATE = silentPressurePlateBlock(
        (PressurePlateBlock) Blocks.STONE_PRESSURE_PLATE);

    public static final BlockDeferredHolder<SilentPressurePlateBlock> SILENT_POLISHED_BLACKSTONE_PRESSURE_PLATE = silentPressurePlateBlock(
        (PressurePlateBlock) Blocks.POLISHED_BLACKSTONE_PRESSURE_PLATE);

    public static final BlockDeferredHolder<SilentWeightedPressurePlateBlock> SILENT_HEAVY_WEIGHTED_PRESSURE_PLATE = silentWeightedPressurePlateBlock(
        (WeightedPressurePlateBlock) Blocks.HEAVY_WEIGHTED_PRESSURE_PLATE);

    public static final BlockDeferredHolder<SilentWeightedPressurePlateBlock> SILENT_LIGHT_WEIGHTED_PRESSURE_PLATE = silentWeightedPressurePlateBlock(
        (WeightedPressurePlateBlock) Blocks.LIGHT_WEIGHTED_PRESSURE_PLATE);

    private static BlockDeferredHolder<SilentPressurePlateBlock> silentPressurePlateBlock(final PressurePlateBlock block) {
        ResourceLocation upModelLoc = Objects.requireNonNull(BuiltInRegistries.BLOCK.getKey(block));
        return registerWithItem("silent_" + upModelLoc.getPath(), props -> new SilentPressurePlateBlock(block), BlockBehaviour.Properties.of());
    }

    private static BlockDeferredHolder<SilentWeightedPressurePlateBlock> silentWeightedPressurePlateBlock(WeightedPressurePlateBlock block) {
        ResourceLocation upModelLoc = Objects.requireNonNull(BuiltInRegistries.BLOCK.getKey(block));
        return registerWithItem("silent_" + upModelLoc.getPath(), props -> new SilentWeightedPressurePlateBlock(block), BlockBehaviour.Properties.of());
    }

    // endregion

    // region Miscellaneous

    // Note: Due to the unique nature of the conduit bundle, all block items are registered in EIOItems instead.
    public static final BlockDeferredHolder<ConduitBundleBlock> CONDUIT_BUNDLE = BLOCKS.registerBlock("conduit",
        ConduitBundleBlock::new, BlockBehaviour.Properties.of()
            .strength(1.5f, 10)
            .noLootTable()
            .noOcclusion()
            .dynamicShape()
            .mapColor(MapColor.STONE));

    public static final BlockDeferredHolder<ChainBlock> SOUL_CHAIN = registerWithItem("soul_chain", ChainBlock::new,
        BlockBehaviour.Properties.of()
            .requiresCorrectToolForDrops()
            .strength(5.0F, 6.0F)
            .sound(SoundType.CHAIN)
            .noOcclusion()
            .mapColor(MapColor.NONE));

    public static final BlockDeferredHolder<ColdFireBlock> COLD_FIRE = BLOCKS
        .registerBlock("cold_fire", ColdFireBlock::new, BlockBehaviour.Properties.copy(Blocks.FIRE).noLootTable());

    public static final BlockDeferredHolder<EnderSkullBlock> ENDERMAN_HEAD = registerWithItem("enderman_head", EnderSkullBlock::new,
            BlockBehaviour.Properties.copy(Blocks.SKELETON_SKULL).instrument(NoteBlockInstrument.SKELETON).strength(1.0F).pushReaction(PushReaction.DESTROY));

    public static final BlockDeferredHolder<WallEnderSkullBlock> WALL_ENDERMAN_HEAD = BLOCKS.registerBlock("wall_enderman_head", WallEnderSkullBlock::new,
        BlockBehaviour.Properties.copy(Blocks.SKELETON_SKULL).strength(1.0F).lootFrom(ENDERMAN_HEAD).pushReaction(PushReaction.DESTROY));

    public static final BlockDeferredHolder<IndustrialInsulationBlock> INDUSTRIAL_INSULATION = registerWithItem("industrial_insulation",
        IndustrialInsulationBlock::new, BlockBehaviour.Properties.copy(Blocks.SPONGE));

    // endregion

    // region Machine Blocks

    // Fluid Tanks
    public static final BlockDeferredHolder<FluidTankBlock> FLUID_TANK = BLOCKS.registerBlock("fluid_tank",
        props -> new FluidTankBlock(EIOBlockEntities.FLUID_TANK::get, props),
        BlockBehaviour.Properties.of()
            .strength(2.5f, 8)
            .isViewBlocking((state, level, pos) -> false)
            .noOcclusion());

    public static final BlockDeferredHolder<FluidTankBlock> PRESSURIZED_FLUID_TANK = BLOCKS.registerBlock("pressurized_fluid_tank",
        props -> new FluidTankBlock(EIOBlockEntities.PRESSURIZED_FLUID_TANK::get, props),
        BlockBehaviour.Properties.of()
            .strength(2.5f, 8)
            .isViewBlocking((state, level, pos) -> false)
            .noOcclusion());

    // Enchanter
    public static final BlockDeferredHolder<EnchanterBlock> ENCHANTER = registerWithItem("enchanter", EnchanterBlock::new,
        BlockBehaviour.Properties.of()
            .strength(2.5f, 8)
            .noOcclusion()
            .isViewBlocking((state, level, pos) -> false));

    // Enderface
    public static final BlockDeferredHolder<EnderfaceBlock> ENDERFACE = registerWithItem("enderface", EnderfaceBlock::new,
        BlockBehaviour.Properties.of()
            .strength(2.5f, 8)
            .noOcclusion()
            .isViewBlocking((state, level, pos) -> false)
            .requiredFeatures(EIOFeatureFlags.ENDERFACE));

    // Progress Machines
    public static final BlockDeferredHolder<ProgressMachineBlock<?>> ALLOY_SMELTER = progressMachine("alloy_smelter",
        () -> EIOBlockEntities.ALLOY_SMELTER::get);
    public static final BlockDeferredHolder<ProgressMachineBlock<?>> PRIMITIVE_ALLOY_SMELTER = progressMachine("primitive_alloy_smelter",
        () -> EIOBlockEntities.PRIMITIVE_ALLOY_SMELTER::get);

    public static final BlockDeferredHolder<ProgressMachineBlock<?>> PAINTING_MACHINE = progressMachine("painting_machine",
        () -> EIOBlockEntities.PAINTING_MACHINE::get);

    public static final BlockDeferredHolder<ProgressMachineBlock<?>> WIRELESS_CHARGER = progressMachine("wireless_charger",
        () -> EIOBlockEntities.WIRELESS_CHARGER::get);

    public static final BlockDeferredHolder<ProgressMachineBlock<?>> STIRLING_GENERATOR = progressMachine("stirling_generator",
        () -> EIOBlockEntities.STIRLING_GENERATOR::get);

    public static final BlockDeferredHolder<ProgressMachineBlock<?>> SAG_MILL = progressMachine("sag_mill",
        () -> EIOBlockEntities.SAG_MILL::get);

    public static final BlockDeferredHolder<ProgressMachineBlock<?>> SLICE_AND_SPLICE = progressMachine("slice_and_splice",
        () -> EIOBlockEntities.SLICE_AND_SPLICE::get);

    public static final BlockDeferredHolder<ProgressMachineBlock<?>> IMPULSE_HOPPER = progressMachine("impulse_hopper",
        () -> EIOBlockEntities.IMPULSE_HOPPER::get);

    public static final BlockDeferredHolder<ProgressMachineBlock<?>> SOUL_BINDER = progressMachine("soul_binder",
        () -> EIOBlockEntities.SOUL_BINDER::get);

    public static final BlockDeferredHolder<ProgressMachineBlock<?>> CRAFTER = progressMachine("crafter",
        () -> EIOBlockEntities.CRAFTER::get);

    public static final BlockDeferredHolder<ProgressMachineBlock<?>> DRAIN = progressMachine("drain",
        () -> EIOBlockEntities.DRAIN::get);

    // Machines
    public static final BlockDeferredHolder<MachineBlock<?>> WIRED_CHARGER = machine("wired_charger",
        () -> EIOBlockEntities.WIRED_CHARGER::get);

    // Wireless Antennas
    public static final BlockDeferredHolder<WirelessAntennaBlock> WIRELESS_CHARGER_ANTENNA = registerWithItem("wireless_charger_antenna",
        WirelessAntennaBlock::new,
        BlockBehaviour.Properties.of()
            .strength(2.5f, 8)
            .isViewBlocking((state, level, pos) -> false)
            .noOcclusion());

    public static final BlockDeferredHolder<WirelessAntennaBlock> WIRELESS_CHARGER_ANTENNA_ADVANCED = registerWithItem("wireless_charger_antenna_advanced",
        WirelessAntennaBlock::new,
        BlockBehaviour.Properties.of()
            .strength(2.5f, 8)
            .isViewBlocking((state, level, pos) -> false)
            .noOcclusion());

    // Creative Power
    public static final BlockDeferredHolder<LegacyMachineBlock> CREATIVE_POWER = registerWithItem("creative_power",
        props -> new LegacyMachineBlock(EIOBlockEntities.CREATIVE_POWER::get, props),
        BlockBehaviour.Properties.of());

    // Powered Spawner
    public static final BlockDeferredHolder<ProgressMachineBlock<PoweredSpawnerBlockEntity>> POWERED_SPAWNER = BLOCKS.registerBlock("powered_spawner",
        properties -> new ProgressMachineBlock<>(EIOBlockEntities.POWERED_SPAWNER::get, properties),
        BlockBehaviour.Properties.of().strength(2.5f, 8));

    // Mind Killer
    public static final BlockDeferredHolder<MindKillerBlock> MIND_KILLER = registerWithItem("mind_killer", MindKillerBlock::new,
        BlockBehaviour.Properties.of()
            .strength(2.5f, 8)
            .isViewBlocking((state, level, pos) -> false)
            .noOcclusion());

    // Vacuum Machines
    public static final BlockDeferredHolder<MachineBlock<VacuumChestBlockEntity>> VACUUM_CHEST = registerWithItem("vacuum_chest",
        p -> new MachineBlock<>(EIOBlockEntities.VACUUM_CHEST::get, p),
        BlockBehaviour.Properties.of().strength(2.5f, 8).noOcclusion());

    public static final BlockDeferredHolder<MachineBlock<XPVacuumBlockEntity>> XP_VACUUM = registerWithItem("xp_vacuum",
        p -> new MachineBlock<>(EIOBlockEntities.XP_VACUUM::get, p),
        BlockBehaviour.Properties.of().strength(2.5f, 8).noOcclusion());

    // Travel Anchors
    public static final BlockDeferredHolder<TravelAnchorBlock<TravelAnchorBlockEntity>> TRAVEL_ANCHOR = registerWithItem("travel_anchor",
        props -> new TravelAnchorBlock<>(EIOBlockEntities.TRAVEL_ANCHOR::get, props),
        BlockBehaviour.Properties.of().strength(2.5f, 8).noOcclusion());

    public static final BlockDeferredHolder<PaintedTravelAnchorBlock> PAINTED_TRAVEL_ANCHOR = registerWithItem("painted_travel_anchor",
        PaintedTravelAnchorBlock::new,
        BlockBehaviour.Properties.of().strength(2.5f, 8).noOcclusion(),
        block -> new PaintedBlockItem(block.get(), new Item.Properties()));

    // Solar Panels
    public static final Map<SolarPanelTier, BlockDeferredHolder<SolarPanelBlock>> SOLAR_PANELS = Util.make(() -> {
        Map<SolarPanelTier, BlockDeferredHolder<SolarPanelBlock>> panels = new HashMap<>();
        for (SolarPanelTier tier : SolarPanelTier.values()) {
            panels.put(tier, solarPanel(tier.name().toLowerCase(Locale.ROOT) + "_photovoltaic_module",
                () -> EIOBlockEntities.SOLAR_PANELS.get(tier)::get, tier));
        }
        return ImmutableMap.copyOf(panels);
    });

    // Capacitor Banks
    public static final Map<CapacitorTier, BlockDeferredHolder<CapacitorBankBlock>> CAPACITOR_BANKS = Util.make(() -> {
        Map<CapacitorTier, BlockDeferredHolder<CapacitorBankBlock>> banks = new HashMap<>();
        for (CapacitorTier tier : CapacitorTier.values()) {
            banks.put(tier, capacitorBank(tier.name().toLowerCase(Locale.ROOT) + "_capacitor_bank",
                () -> EIOBlockEntities.CAPACITOR_BANKS.get(tier)::get, tier));
        }
        return ImmutableMap.copyOf(banks);
    });

    // Soul Engine
    public static final BlockDeferredHolder<ProgressMachineBlock<SoulEngineBlockEntity>> SOUL_ENGINE = BLOCKS.registerBlock("soul_engine",
        p -> new ProgressMachineBlock<>(EIOBlockEntities.SOUL_ENGINE::get, p),
        BlockBehaviour.Properties.of().strength(2.5f, 8).noOcclusion());

    // Niard
    public static final BlockDeferredHolder<NiardBlock> NIARD = registerWithItem("niard", NiardBlock::new,
        BlockBehaviour.Properties.of()
            .strength(2.5f, 8)
            .isViewBlocking((state, level, pos) -> false)
            .noOcclusion()
            .requiredFeatures(EIOFeatureFlags.NIARD));

    // VAT
    public static final BlockDeferredHolder<VatBlock> VAT = registerWithItem("vat",
        VatBlock::new,
        BlockBehaviour.Properties.of().strength(2.5f, 8));

    // Block Detector
    public static final BlockDeferredHolder<BlockDetectorBlock> BLOCK_DETECTOR = registerWithItem("block_detector",
        BlockDetectorBlock::new,
        BlockBehaviour.Properties.copy(Blocks.OBSERVER));

    // Farming Station
    public static final BlockDeferredHolder<FarmingStationBlock> FARMING_STATION = registerWithItem("farming_station",
        FarmingStationBlock::new,
        BlockBehaviour.Properties.of().strength(2.5f, 8));

    // Obelisks
    public static final BlockDeferredHolder<MachineBlock<XPObeliskBlockEntity>> XP_OBELISK =
        obelisk("xp_obelisk", () -> EIOBlockEntities.XP_OBELISK::get);

    public static final BlockDeferredHolder<MachineBlock<InhibitorObeliskBlockEntity>> INHIBITOR_OBELISK =
        obelisk("inhibitor_obelisk", () -> EIOBlockEntities.INHIBITOR_OBELISK::get);

    public static final BlockDeferredHolder<MachineBlock<AversionObeliskBlockEntity>> AVERSION_OBELISK =
        obelisk("aversion_obelisk", () -> EIOBlockEntities.AVERSION_OBELISK::get);

    public static final BlockDeferredHolder<MachineBlock<RelocatorObeliskBlockEntity>> RELOCATOR_OBELISK =
        obelisk("relocator_obelisk", () -> EIOBlockEntities.RELOCATOR_OBELISK::get);

    public static final BlockDeferredHolder<MachineBlock<AttractorObeliskBlockEntity>> ATTRACTOR_OBELISK =
        obelisk("attractor_obelisk", () -> EIOBlockEntities.ATTRACTOR_OBELISK::get);

    public static final BlockDeferredHolder<MachineBlock<WeatherObeliskBlockEntity>> WEATHER_OBELISK =
        obelisk("weather_obelisk", () -> EIOBlockEntities.WEATHER_OBELISK::get);

    // Items that need capabilities (exposed as ItemDeferredHolders)
    public static final ItemDeferredHolder<FluidTankBlockItem> FLUID_TANK_ITEM = ITEMS.register("fluid_tank",
        () -> new FluidTankBlockItem(FLUID_TANK.get(), new Item.Properties(), 16000));
    public static final ItemDeferredHolder<FluidTankBlockItem> PRESSURIZED_FLUID_TANK_ITEM = ITEMS.register("pressurized_fluid_tank",
        () -> new FluidTankBlockItem(PRESSURIZED_FLUID_TANK.get(), new Item.Properties(), 32000));

    public static final ItemDeferredHolder<BlockItem> POWERED_SPAWNER_ITEM = ITEMS.register("powered_spawner",
        () -> EIODataComponents.SOUL.withDefault(new BlockItem(POWERED_SPAWNER.get(), new Item.Properties()), () -> Soul.EMPTY));

    public static final ItemDeferredHolder<BlockItem> SOUL_ENGINE_ITEM = ITEMS.register("soul_engine",
        () -> EIODataComponents.SOUL.withDefault(new BlockItem(SOUL_ENGINE.get(), new Item.Properties()), () -> Soul.EMPTY));

    public static final Map<SolarPanelTier, ItemDeferredHolder<BlockItem>> SOLAR_PANEL_ITEMS = Util.make(() -> {
        Map<SolarPanelTier, ItemDeferredHolder<BlockItem>> items = new HashMap<>();
        for (var entry : SOLAR_PANELS.entrySet()) {
            items.put(entry.getKey(), ITEMS.register(entry.getValue().getId().getPath(),
                () -> EIODataComponents.SOUL.withDefault(new BlockItem(entry.getValue().get(), new Item.Properties()), () -> Soul.EMPTY)));
        }
        return ImmutableMap.copyOf(items);
    });

    public static final Map<CapacitorTier, ItemDeferredHolder<CapacitorBankItem>> CAPACITOR_BANK_ITEMS = Util.make(() -> {
        Map<CapacitorTier, ItemDeferredHolder<CapacitorBankItem>> items = new HashMap<>();
        for (var entry : CAPACITOR_BANKS.entrySet()) {
            items.put(entry.getKey(), ITEMS.register(entry.getValue().getId().getPath(),
                () -> new CapacitorBankItem(entry.getValue().get(), new Item.Properties())));
        }
        return ImmutableMap.copyOf(items);
    });

    // Helper methods
    private static BlockDeferredHolder<MachineBlock<?>> machine(String name,
        Supplier<Supplier<BlockEntityType<? extends MachineBlockEntity>>> regiliteBlockEntity) {
        return registerWithItem(name,
            props -> new MachineBlock<>(regiliteBlockEntity.get(), props),
            BlockBehaviour.Properties.of().strength(2.5f, 8));
    }

    private static BlockDeferredHolder<ProgressMachineBlock<?>> progressMachine(String name,
        Supplier<Supplier<BlockEntityType<? extends MachineBlockEntity>>> regiliteBlockEntity) {
        return registerWithItem(name,
            props -> new ProgressMachineBlock<>(regiliteBlockEntity.get(), props),
            BlockBehaviour.Properties.of().strength(2.5f, 8));
    }

    private static <T extends MachineBlockEntity> BlockDeferredHolder<MachineBlock<T>> obelisk(String name,
        Supplier<Supplier<BlockEntityType<T>>> blockEntityType) {
        return registerWithItem(name,
            props -> new MachineBlock<T>(blockEntityType.get()::get, props),
            BlockBehaviour.Properties.of()
                .strength(2.5f, 8)
                .isViewBlocking((state, level, pos) -> false)
                .noOcclusion());
    }

    private static BlockDeferredHolder<SolarPanelBlock> solarPanel(String name,
        Supplier<Supplier<BlockEntityType<? extends SolarPanelBlockEntity>>> regiliteBlockEntity, SolarPanelTier tier) {
        return BLOCKS.registerBlock(name,
            props -> new SolarPanelBlock(regiliteBlockEntity.get(), props, tier),
            BlockBehaviour.Properties.of().strength(2.5f, 8));
    }

    private static BlockDeferredHolder<CapacitorBankBlock> capacitorBank(String name,
        Supplier<Supplier<BlockEntityType<? extends CapacitorBankBlockEntity>>> regiliteBlockEntity, CapacitorTier tier) {
        return BLOCKS.registerBlock(name,
            props -> new CapacitorBankBlock(regiliteBlockEntity.get(), props, tier),
            BlockBehaviour.Properties.of().strength(2.5f, 8));
    }

    // endregion

    // region Glass Blocks

    public static final Map<GlassIdentifier, GlassBlocks> GLASS_BLOCKS = fillGlassMap();

    private static Map<GlassIdentifier, GlassBlocks> fillGlassMap() {
        Map<GlassIdentifier, GlassBlocks> map = new HashMap<>();
        for (GlassLighting lighting : GlassLighting.values()) {
            for (GlassCollisionPredicate collisionPredicate : GlassCollisionPredicate.values()) {
                for (Boolean isFused : new boolean[] { false, true }) {
                    GlassIdentifier identifier = new GlassIdentifier(lighting, collisionPredicate, isFused);
                    map.put(identifier, new GlassBlocks(BLOCKS, ITEMS, identifier));
                }
            }
        }
        return map;
    }

    // endregion

    private static <B extends Block> BlockDeferredHolder<B> registerWithItem(String name, Function<BlockBehaviour.Properties, ? extends B> func, BlockBehaviour.Properties props) {
        var blockHolder = BLOCKS.<B>registerBlock(name, func, props);
        ITEMS.registerSimpleBlockItem(blockHolder);
        return blockHolder;
    }

    private static <B extends Block> BlockDeferredHolder<B> registerWithItem(String name, Function<BlockBehaviour.Properties, ? extends B> func, BlockBehaviour.Properties props, Function<Supplier<B>, Item> itemFactory) {
        var blockHolder = BLOCKS.<B>registerBlock(name, func, props);
        ITEMS.register(name, () -> itemFactory.apply(blockHolder));
        return blockHolder;
    }

    public static void register(IEventBus bus) {
        BLOCKS.addAlias(EnderIO.rl("industrial_insulation_block"), EnderIO.rl("industrial_insulation"));
        ITEMS.addAlias(EnderIO.rl("industrial_insulation_block"), EnderIO.rl("industrial_insulation"));
        BLOCKS.addAlias(EnderIO.rl("copper_alloy_block"), EnderIO.rl("conductive_alloy_block"));
        ITEMS.addAlias(EnderIO.rl("copper_alloy_block"), EnderIO.rl("conductive_alloy_block"));

        BLOCKS.register(bus);
        ITEMS.register(bus);
    }

}
