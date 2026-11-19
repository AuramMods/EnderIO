package com.enderio.enderio.content.conduits.legacy;

import com.enderio.core.common.registries.ItemDeferredRegister;
import com.enderio.enderio.api.EnderIORegistries;
import com.enderio.enderio.api.conduits.Conduit;
import com.enderio.enderio.api.io.RedstoneControl;
import com.enderio.enderio.api.conduits.network.node.NodeData;
import com.enderio.enderio.content.paint.BlockPaintData;
import com.enderio.enderio.init.EIOConduitTypes;
import com.enderio.enderio.init.EIOConduits;
import com.enderio.enderio.init.EIODataComponents;
import com.enderio.enderio.init.EIOItems;
import com.mojang.logging.LogUtils;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;
import java.util.stream.Stream;

/** Reads the NBT emitted before Ender IO moved conduit types into its datapack registry. */
public final class Forge6ConduitData {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<String, ResourceKey<Conduit<?, ?>>> CONDUITS = Map.of(
        "enderio:item_conduit", EIOConduits.ITEM, "enderio:energy_conduit", EIOConduits.ENERGY,
        "enderio:redstone_conduit", EIOConduits.REDSTONE, "enderio:fluid_conduit", EIOConduits.FLUID,
        "enderio:pressurized_fluid_conduit", EIOConduits.ENERGETIC_FLUID,
        "enderio:ender_fluid_conduit", EIOConduits.VIBRANT_FLUID);
    private static final Map<String, ResourceLocation> INTEGRATION_ITEMS = Map.of(
        "enderio:me_conduit", ResourceLocation.parse("enderio:me"),
        "enderio:dense_me_conduit", ResourceLocation.parse("enderio:dense_me"),
        "enderio:refined_storage_conduit", ResourceLocation.parse("enderio:rs"),
        "enderio:chemical_conduit", ResourceLocation.parse("enderio:chemical"),
        "enderio:pressurized_chemical_conduit", ResourceLocation.parse("enderio:pressurized_chemical"),
        "enderio:ender_chemical_conduit", ResourceLocation.parse("enderio:ender_chemical"),
        "enderio:heat_conduit", ResourceLocation.parse("enderio:heat"));

    // 6.x used ColorControl ordinals, which are unrelated to the vanilla dye IDs.
    private static final DyeColor[] COLORS = {DyeColor.GREEN, DyeColor.BROWN, DyeColor.BLUE, DyeColor.PURPLE,
        DyeColor.CYAN, DyeColor.LIGHT_GRAY, DyeColor.GRAY, DyeColor.PINK, DyeColor.LIME, DyeColor.YELLOW,
        DyeColor.LIGHT_BLUE, DyeColor.MAGENTA, DyeColor.ORANGE, DyeColor.WHITE, DyeColor.BLACK, DyeColor.RED};

    private Forge6ConduitData() {}

    public static @Nullable ResourceKey<Conduit<?, ?>> conduitKey(String id) {
        return switch (id) {
            case "enderio:me", "enderio:dense_me", "enderio:refined_storage", "enderio:chemical",
                 "enderio:pressurized_chemical", "enderio:ender_chemical", "enderio:heat" ->
                ResourceKey.create(EnderIORegistries.Keys.CONDUIT, INTEGRATION_ITEMS.get(id + "_conduit"));
            default -> CONDUITS.get(id);
        };
    }

    public static boolean rebuildGraphFromBundles(String id) {
        // 6.x saved the same mixed AE2 graph under both tiers without storing each node's tier.
        return id.equals("enderio:me") || id.equals("enderio:dense_me");
    }

    public static void registerItemAliases(ItemDeferredRegister items, ResourceLocation canonicalItem) {
        Stream.concat(CONDUITS.keySet().stream(), INTEGRATION_ITEMS.keySet().stream())
            .sorted().forEach(id -> items.addAlias(ResourceLocation.parse(id), canonicalItem));
    }

    public static @Nullable ResourceLocation itemConduitId(String id) {
        var core = CONDUITS.get(id);
        return core == null ? INTEGRATION_ITEMS.get(id) : core.location();
    }

    public static ConnectionState connection(CompoundTag tag) {
        if (tag.isEmpty()) return StaticConnectionStates.DISCONNECTED;
        if (tag.getBoolean("Static")) {
            return switch (tag.getInt("Index")) {
                case 0 -> StaticConnectionStates.CONNECTED;
                case 1 -> StaticConnectionStates.CONNECTED_ACTIVE;
                case 3 -> StaticConnectionStates.DISABLED;
                default -> StaticConnectionStates.DISCONNECTED;
            };
        }
        return new DynamicConnectionState(tag.getBoolean("IsInsert"), color(tag.getInt("Insert")),
            tag.getBoolean("IsExtract"), color(tag.getInt("Extract")),
            RedstoneControl.BY_ID.apply(tag.getInt("RedstoneControl")), color(tag.getInt("Channel")),
            ItemStack.of(tag.getCompound("InsertFilter")), ItemStack.of(tag.getCompound("ExtractFilter")),
            ItemStack.of(tag.getCompound("ExtractUpgrade")));
    }

    private static DyeColor color(int index) {
        return index >= 0 && index < COLORS.length ? COLORS[index] : DyeColor.GREEN;
    }

    public static ItemStack facade(CompoundTag facades) {
        if (facades.isEmpty()) return ItemStack.EMPTY;
        var down = facades.getCompound("down");
        var decoded = BlockState.CODEC.parse(NbtOps.INSTANCE, down).result();
        if (decoded.isPresent()) {
            var state = decoded.get();
            var block = state.getBlock();
            if (!state.isAir() && state == block.defaultBlockState() &&
                Arrays.stream(Direction.values()).allMatch(side -> down.equals(facades.getCompound(side.getName())))) {
                var stack = new ItemStack(EIOItems.CONDUIT_FACADE.get());
                EIODataComponents.BLOCK_PAINT.set(stack, BlockPaintData.of(block));
                return stack;
            }
        }
        LOGGER.warn("Legacy conduit facade has partial, mixed or non-default faces and requires manual replacement");
        return ItemStack.EMPTY;
    }

    public static LegacyItemConduitData itemData(CompoundTag tag) {
        var sides = new EnumMap<Direction, LegacyItemConduitData.ItemSidedData>(Direction.class);
        for (var side : Direction.values()) {
            if (!tag.contains(side.name(), Tag.TAG_COMPOUND)) continue;
            var data = tag.getCompound(side.name());
            sides.put(side, new LegacyItemConduitData.ItemSidedData(data.getBoolean("RoundRobin"),
                data.getInt("RotatingIndex"), data.getBoolean("SelfFeed"), data.getInt("Priority")));
        }
        return new LegacyItemConduitData(sides);
    }

    public static @Nullable NodeData node(Holder<Conduit<?, ?>> conduit, CompoundTag tag) {
        return conduit.value().type() == EIOConduitTypes.ITEM.get() ? itemData(tag).toNodeData()
            : conduit.value().readForge6NodeData(tag);
    }
}
