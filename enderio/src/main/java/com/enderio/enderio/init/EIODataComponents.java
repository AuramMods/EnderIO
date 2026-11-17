package com.enderio.enderio.init;

import com.enderio.core.common.util.NamedFluidContents;
import com.enderio.core.common.item.ItemDataKey;
import com.enderio.enderio.EnderIO;
import com.enderio.enderio.api.attachment.CoordinateSelection;
import com.enderio.enderio.api.capacitor.CapacitorData;
import com.enderio.enderio.api.components.GrindingBallData;
import com.enderio.enderio.api.conduits.facade.FacadeType;
import com.enderio.enderio.api.io.RedstoneControl;
import com.enderio.enderio.api.soul.Soul;
import com.enderio.enderio.content.conduits.probe.ConduitProbeItem;
import com.enderio.enderio.content.filters.fluid.EnderFluidFilter;
import com.enderio.enderio.content.filters.item.general.EnderItemFilter;
import com.enderio.enderio.content.filters.item.limited.LimitedItemFilter;
import com.enderio.enderio.content.filters.redstone.DoubleRedstoneChannel;
import com.enderio.enderio.content.filters.redstone.RedstoneCountFilter;
import com.enderio.enderio.content.filters.redstone.RedstoneTLatchFilter;
import com.enderio.enderio.content.filters.redstone.RedstoneTimerFilter;
import com.enderio.enderio.content.filters.soul.EnderSoulFilter;
import com.enderio.enderio.content.machines.alloy.AlloySmelterMode;
import com.enderio.enderio.content.paint.BlockPaintData;
import com.enderio.enderio.foundation.attachment.ActionRange;
import com.enderio.enderio.foundation.io.IOConfig;
import com.mojang.serialization.Codec;
import net.minecraftforge.fluids.FluidStack;


/** Persistent item keys for Forge 1.20.1; native stack NBT provides save and network transport. */
public class EIODataComponents {

    // region Generic

    public static final ItemDataKey<FluidStack> ITEM_FLUID_CONTENT = new ItemDataKey<>(EnderIO.rl("item_fluid_content"), FluidStack.CODEC, false);

    public static final ItemDataKey<NamedFluidContents> NAMED_FLUID_CONTENTS = new ItemDataKey<>(EnderIO.rl("named_fluid_contents"), NamedFluidContents.CODEC);

    public static final ItemDataKey<Integer> ENERGY = new ItemDataKey<>(EnderIO.rl("energy"), Codec.intRange(0, Integer.MAX_VALUE), false);

    public static final ItemDataKey<Soul> SOUL = new ItemDataKey<>(EnderIO.rl("soul"), Soul.OPTIONAL_CODEC, false, EnderIO.rl("stored_entity"));

    // endregion

    // region Capacitors

    public static final ItemDataKey<CapacitorData> CAPACITOR_DATA = CapacitorData.ITEM_DATA;

    // endregion

    // region Conduits

    public static final ItemDataKey<FacadeType> FACADE_TYPE = new ItemDataKey<>(EnderIO.rl("facade_type"), FacadeType.CODEC);

    public static final ItemDataKey<ConduitProbeItem.ProbeConfigData> PROBE_CONFIG = new ItemDataKey<>(EnderIO.rl("probe_config"), ConduitProbeItem.ProbeConfigData.CODEC);

    public static final ItemDataKey<ConduitProbeItem.State> PROBE_STATE = new ItemDataKey<>(EnderIO.rl("probe_state"), ConduitProbeItem.State.CODEC);

    // endregion

    // region Filters

    public static final ItemDataKey<EnderItemFilter> ITEM_FILTER = new ItemDataKey<>(EnderIO.rl("item_filter"), EnderItemFilter.CODEC);

    public static final ItemDataKey<LimitedItemFilter> LIMITED_ITEM_FILTER = new ItemDataKey<>(EnderIO.rl("limited_item_filter"), LimitedItemFilter.CODEC);

    public static final ItemDataKey<EnderFluidFilter> FLUID_FILTER = new ItemDataKey<>(EnderIO.rl("fluid_filter"), EnderFluidFilter.CODEC);

    public static final ItemDataKey<EnderSoulFilter> SOUL_FILTER = new ItemDataKey<>(EnderIO.rl("soul_filter"), EnderSoulFilter.CODEC, true, EnderIO.rl("entity_filter"));

    public static final ItemDataKey<DoubleRedstoneChannel.Component> REDSTONE_FILTER_DOUBLE_CHANNEL = new ItemDataKey<>(EnderIO.rl("redstone_filter_double_channel"), DoubleRedstoneChannel.Component.CODEC, false, EnderIO.rl("redstone_and_filter"), EnderIO.rl("redstone_or_filter"), EnderIO.rl("redstone_nand_filter"), EnderIO.rl("redstone_nor_filter"), EnderIO.rl("redstone_xnor_filter"), EnderIO.rl("redstone_xor_filter"));

    public static final ItemDataKey<RedstoneCountFilter.Component> REDSTONE_COUNT_FILTER = new ItemDataKey<>(EnderIO.rl("redstone_count_filter"), RedstoneCountFilter.Component.CODEC, false);

    public static final ItemDataKey<RedstoneTimerFilter.Component> REDSTONE_TIMER_FILTER = new ItemDataKey<>(EnderIO.rl("redstone_timer_filter"), RedstoneTimerFilter.Component.CODEC, false);

    public static final ItemDataKey<RedstoneTLatchFilter.Component> REDSTONE_TLATCH_FILTER = new ItemDataKey<>(EnderIO.rl("redstone_tlatch_filter"), RedstoneTLatchFilter.Component.CODEC, false);

    // endregion

    // region Machines

    public static final ItemDataKey<IOConfig> IO_CONFIG = IOConfig.ITEM_DATA;

    public static final ItemDataKey<RedstoneControl> REDSTONE_CONTROL = RedstoneControl.ITEM_DATA;

    public static final ItemDataKey<ActionRange> ACTION_RANGE = new ItemDataKey<>(EnderIO.rl("action_range"), ActionRange.CODEC);

    public static final ItemDataKey<Boolean> IS_RANGE_VISIBLE = new ItemDataKey<>(EnderIO.rl("is_range_visible"), Codec.BOOL);

    public static final ItemDataKey<AlloySmelterMode> ALLOY_SMELTER_MODE = new ItemDataKey<>(EnderIO.rl("alloy_smelter_mode"), AlloySmelterMode.CODEC);
    public static final ItemDataKey<Integer> ALLOY_SMELTER_PROCESSED_INPUTS = new ItemDataKey<>(EnderIO.rl("alloy_smelter_processed_inputs"), Codec.INT);

    // Could use the GRINDING_BALL component, but then you could use a sag mill as a
    // grinding ball :P
    public static final ItemDataKey<GrindingBallData> SAG_MILL_GRINDING_BALL = new ItemDataKey<>(EnderIO.rl("sag_mill_grinding_ball"), GrindingBallData.CODEC);
    public static final ItemDataKey<Integer> SAG_MILL_GRINDING_BALL_DAMAGE = new ItemDataKey<>(EnderIO.rl("sag_mill_grinding_ball_damage"), Codec.INT);

    // endregion

    // region Paint

    public static final ItemDataKey<BlockPaintData> BLOCK_PAINT = new ItemDataKey<>(EnderIO.rl("block_paint"), BlockPaintData.CODEC);

    // endregion

    // region Tools

    public static final ItemDataKey<Boolean> TOGGLED = new ItemDataKey<>(EnderIO.rl("toggled"), Codec.BOOL);

    public static final ItemDataKey<CoordinateSelection> COORDINATE_SELECTION = new ItemDataKey<>(EnderIO.rl("coordinate_selection"), CoordinateSelection.CODEC);

    // endregion

    // region Travel

    public static final ItemDataKey<Boolean> TRAVEL_ITEM = new ItemDataKey<>(EnderIO.rl("travel_item"), Codec.BOOL);

    // endregion

    // region Vials

    public static final ItemDataKey<Float> ENTITY_MAX_HEALTH = new ItemDataKey<>(EnderIO.rl("entity_max_health"), Codec.FLOAT);

    // endregion

}
