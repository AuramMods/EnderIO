package com.enderio.enderio.content.filters.redstone;

import net.minecraft.core.Direction;
import com.enderio.enderio.api.EnderIOCapabilities;
import com.enderio.enderio.api.filter.RedstoneFilter;
import com.enderio.enderio.api.filter.RedstoneInputFilter;
import com.enderio.enderio.api.filter.RedstoneOutputFilter;
import com.enderio.enderio.content.filters.FiltersLang;
import com.enderio.enderio.init.EIODataComponents;
import com.enderio.enderio.init.EIOMenus;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import java.util.function.BiFunction;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Supplier;
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

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
        var menu = type.menu();
        if (player instanceof ServerPlayer serverPlayer && menu != null && player.isSteppingCarefully()) {
            openMenu(serverPlayer);
        }
        return super.use(level, player, usedHand);
    }

    private void openMenu(ServerPlayer player) {
        player.openMenu(new MenuProvider() {
            @Override
            public Component getDisplayName() {
                return Component.literal("");
            }

            @Override
            public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
                return type.menu().create(containerId, inventory);
            }
        });
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
            } else if (type.menu() != null) {
                tooltipComponents.add(FiltersLang.UNCONFIGURED_HINT);
            }
        }
    }

    public enum Type {
        NOT(p -> {}, null),
        OR(p -> EIODataComponents.REDSTONE_FILTER_DOUBLE_CHANNEL.withDefault(p, () -> DoubleRedstoneChannel.INSTANCE), EIOMenus.REDSTONE_DOUBLE_CHANNEL_FILTER::get),
        AND(p -> EIODataComponents.REDSTONE_FILTER_DOUBLE_CHANNEL.withDefault(p, () -> DoubleRedstoneChannel.INSTANCE), EIOMenus.REDSTONE_DOUBLE_CHANNEL_FILTER::get),
        NOR(p -> EIODataComponents.REDSTONE_FILTER_DOUBLE_CHANNEL.withDefault(p, () -> DoubleRedstoneChannel.INSTANCE), EIOMenus.REDSTONE_DOUBLE_CHANNEL_FILTER::get),
        NAND(p -> EIODataComponents.REDSTONE_FILTER_DOUBLE_CHANNEL.withDefault(p, () -> DoubleRedstoneChannel.INSTANCE), EIOMenus.REDSTONE_DOUBLE_CHANNEL_FILTER::get),
        XOR(p -> EIODataComponents.REDSTONE_FILTER_DOUBLE_CHANNEL.withDefault(p, () -> DoubleRedstoneChannel.INSTANCE), EIOMenus.REDSTONE_DOUBLE_CHANNEL_FILTER::get),
        XNOR(p -> EIODataComponents.REDSTONE_FILTER_DOUBLE_CHANNEL.withDefault(p, () -> DoubleRedstoneChannel.INSTANCE), EIOMenus.REDSTONE_DOUBLE_CHANNEL_FILTER::get),
        TLATCH(p -> EIODataComponents.REDSTONE_TLATCH_FILTER.withDefault(p, () -> RedstoneTLatchFilter.INSTANCE), null),
        COUNT(p -> EIODataComponents.REDSTONE_COUNT_FILTER.withDefault(p, () -> RedstoneCountFilter.INSTANCE), EIOMenus.REDSTONE_COUNT_FILTER::get),
        SENSOR(p -> {}, null),
        TIMER(p -> EIODataComponents.REDSTONE_TIMER_FILTER.withDefault(p, () -> RedstoneTimerFilter.INSTANCE), EIOMenus.REDSTONE_TIMER_FILTER::get);

        private Consumer<Item> defaultApplicator;

        @Nullable
        private Supplier<MenuType<?>> menu;

        Type(Consumer<Item> defaultApplicator, @Nullable Supplier<MenuType<?>> menu) {
            this.defaultApplicator = defaultApplicator;
            this.menu = menu;
        }

        public Consumer<Item> defaultApplicator() {
            return defaultApplicator;
        }

        public @Nullable MenuType<?> menu() {
            if (menu == null) {
                return null;
            }

            return menu.get();
        }
    }
}
