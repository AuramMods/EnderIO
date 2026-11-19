package com.enderio.enderio.content.filters.redstone;

import com.enderio.core.common.item.ItemDataKey;
import net.minecraft.core.Direction;
import com.enderio.enderio.api.EnderIOCapabilities;
import com.enderio.enderio.api.filter.RedstoneFilter;
import com.enderio.enderio.api.filter.RedstoneInputFilter;
import com.enderio.enderio.api.filter.RedstoneOutputFilter;
import com.enderio.enderio.content.filters.FiltersLang;
import com.enderio.enderio.init.EIODataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkHooks;
import java.util.function.BiFunction;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

public class RedstoneFilterItem extends Item {

    // Insert filters
    public static final BiFunction<ItemStack, Direction, RedstoneOutputFilter> AND_FILTER_PROVIDER =
        (stack, v) -> new RedstoneANDFilter(stack);
    public static final BiFunction<ItemStack, Direction, RedstoneOutputFilter> COUNT_FILTER_PROVIDER =
        (stack, v) -> new RedstoneCountFilter(stack);
    public static final BiFunction<ItemStack, Direction, RedstoneOutputFilter> NAND_FILTER_PROVIDER =
        (stack, v) -> new RedstoneNANDFilter(stack);
    public static final BiFunction<ItemStack, Direction, RedstoneOutputFilter> NOR_FILTER_PROVIDER =
        (stack, v) -> new RedstoneNORFilter(stack);
    public static final BiFunction<ItemStack, Direction, RedstoneOutputFilter> OR_FILTER_PROVIDER =
        (stack, v) -> new RedstoneORFilter(stack);
    public static final BiFunction<ItemStack, Direction, RedstoneOutputFilter> TLATCH_FILTER_PROVIDER =
        (stack, v) -> new RedstoneTLatchFilter(stack);
    public static final BiFunction<ItemStack, Direction, RedstoneOutputFilter> XNOR_FILTER_PROVIDER =
        (stack, v) -> new RedstoneXNORFilter(stack);
    public static final BiFunction<ItemStack, Direction, RedstoneOutputFilter> XOR_FILTER_PROVIDER =
        (stack, v) -> new RedstoneXORFilter(stack);

    // Extract filters
    public static final BiFunction<ItemStack, Direction, RedstoneInputFilter> SENSOR_FILTER_PROVIDER =
        (stack, v) -> RedstoneSensorFilter.INSTANCE;
    public static final BiFunction<ItemStack, Direction, RedstoneInputFilter> TIMER_FILTER_PROVIDER =
        (stack, v) -> new RedstoneTimerFilter(stack);

    // Insert & Extract filters
    public static final BiFunction<ItemStack, Direction, RedstoneOutputFilter> NOT_FILTER_PROVIDER_INSERT =
        (stack, v) -> RedstoneNOTFilter.INSTANCE;
    public static final BiFunction<ItemStack, Direction, RedstoneInputFilter> NOT_FILTER_PROVIDER_EXTRACT =
        (stack, v) -> RedstoneNOTFilter.INSTANCE;

    private final Type type;

    public RedstoneFilterItem(Properties properties, Type type) {
        super(properties);
        type.defaultApplicator().accept(this);
        this.type = type;
    }

    public void restoreForge6State(ItemStack stack) {
        var saved = stack.getTag();
        if (saved == null || ItemDataKey.hasStoredData(stack)) return;

        switch (type) {
            case OR, AND, NOR, NAND, XOR, XNOR -> {
                if (!saved.contains("Channel1", Tag.TAG_STRING) && !saved.contains("Channel2", Tag.TAG_STRING)) return;
                var defaults = DoubleRedstoneChannel.INSTANCE;
                EIODataComponents.REDSTONE_FILTER_DOUBLE_CHANNEL.set(stack, new DoubleRedstoneChannel.Component(
                    readChannel(saved, "Channel1", defaults.channel1()), readChannel(saved, "Channel2", defaults.channel2())));
                stack.removeTagKey("Channel1");
                stack.removeTagKey("Channel2");
            }
            case COUNT -> {
                if (!saved.contains("Channel", Tag.TAG_STRING) && !saved.contains("MaxCount", Tag.TAG_INT)
                    && !saved.contains("Ticks", Tag.TAG_INT) && !saved.contains("Deactivated", Tag.TAG_BYTE)) return;
                var defaults = RedstoneCountFilter.INSTANCE;
                int maxCount = saved.contains("MaxCount", Tag.TAG_INT) ? Math.max(0, saved.getInt("MaxCount")) : defaults.maxCount();
                EIODataComponents.REDSTONE_COUNT_FILTER.set(stack, new RedstoneCountFilter.Component(
                    readChannel(saved, "Channel", defaults.channel1()), maxCount,
                    Math.max(0, saved.getInt("Ticks")), saved.getBoolean("Deactivated")));
                stack.removeTagKey("Channel");
                stack.removeTagKey("MaxCount");
                stack.removeTagKey("Ticks");
                stack.removeTagKey("Deactivated");
            }
            case TIMER -> {
                if (!saved.contains("Ticks", Tag.TAG_INT) && !saved.contains("MaxTicks", Tag.TAG_INT)) return;
                int maxTicks = saved.getInt("MaxTicks");
                if (maxTicks <= 0) maxTicks = RedstoneTimerFilter.INSTANCE.maxTicks();
                EIODataComponents.REDSTONE_TIMER_FILTER.set(stack,
                    new RedstoneTimerFilter.Component(Math.max(0, saved.getInt("Ticks")), maxTicks));
                stack.removeTagKey("Ticks");
                stack.removeTagKey("MaxTicks");
            }
            case TLATCH -> {
                if (!saved.contains("Active", Tag.TAG_BYTE) && !saved.contains("Deactivated", Tag.TAG_BYTE)) return;
                EIODataComponents.REDSTONE_TLATCH_FILTER.set(stack, new RedstoneTLatchFilter.Component(
                    saved.getBoolean("Active"), !saved.contains("Deactivated", Tag.TAG_BYTE) || saved.getBoolean("Deactivated")));
                stack.removeTagKey("Active");
                stack.removeTagKey("Deactivated");
            }
            default -> { }
        }
    }

    private static DyeColor readChannel(CompoundTag saved, String name, DyeColor fallback) {
        return DyeColor.byName(saved.getString(name).toLowerCase(Locale.ROOT), fallback);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
        if (type.menu != null && player.isSteppingCarefully()) {
            var held = player.getItemInHand(usedHand);
            if (player instanceof ServerPlayer serverPlayer) {
                openMenu(serverPlayer, usedHand, held, type.menu);
            }
            return InteractionResultHolder.sidedSuccess(held, level.isClientSide);
        }
        return super.use(level, player, usedHand);
    }

    private void openMenu(ServerPlayer player, InteractionHand hand, ItemStack held, MenuFactory menu) {
        NetworkHooks.openScreen(player, new MenuProvider() {
            @Override
            public Component getDisplayName() {
                return getName(held);
            }

            @Override
            public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
                return menu.create(containerId, inventory, hand);
            }
        }, buffer -> buffer.writeEnum(hand));
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, level, tooltipComponents, tooltipFlag);
        
        // Create a filter instance to check if it's configured
        RedstoneFilter filter = stack.getCapability(EnderIOCapabilities.REDSTONE_INSERT_FILTER, null).orElse(null);
        if (filter == null) {
            filter = stack.getCapability(EnderIOCapabilities.REDSTONE_EXTRACT_FILTER, null).orElse(null);
        }

        if (filter != null) {
            if (filter.isConfigured()) {
                tooltipComponents.add(FiltersLang.CONFIGURED);
            } else if (type.menu != null) {
                tooltipComponents.add(FiltersLang.UNCONFIGURED_HINT);
            }
        }
    }

    public enum Type {
        NOT(p -> {}, null),
        OR(), AND(), NOR(), NAND(), XOR(), XNOR(),
        TLATCH(p -> EIODataComponents.REDSTONE_TLATCH_FILTER.withDefault(p, () -> RedstoneTLatchFilter.INSTANCE), null),
        COUNT(p -> EIODataComponents.REDSTONE_COUNT_FILTER.withDefault(p, () -> RedstoneCountFilter.INSTANCE), RedstoneCountFilterMenu::new),
        SENSOR(p -> {}, null),
        TIMER(p -> EIODataComponents.REDSTONE_TIMER_FILTER.withDefault(p, () -> RedstoneTimerFilter.INSTANCE), RedstoneTimerFilterMenu::new);

        private final Consumer<Item> defaultApplicator;

        @Nullable
        private final MenuFactory menu;

        Type() {
            this(p -> EIODataComponents.REDSTONE_FILTER_DOUBLE_CHANNEL.withDefault(p, () -> DoubleRedstoneChannel.INSTANCE),
                RedstoneDoubleChannelFilterMenu::new);
        }

        Type(Consumer<Item> defaultApplicator, @Nullable MenuFactory menu) {
            this.defaultApplicator = defaultApplicator;
            this.menu = menu;
        }

        public Consumer<Item> defaultApplicator() {
            return defaultApplicator;
        }

    }

    @FunctionalInterface
    private interface MenuFactory {
        AbstractContainerMenu create(int containerId, Inventory inventory, InteractionHand hand);
    }
}
