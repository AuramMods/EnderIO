package com.enderio.enderio.content.filters.item.general;

import com.enderio.core.common.serialization.StackDataCodecs;
import com.enderio.core.common.serialization.OrderedListCodec;
import com.enderio.core.common.util.EqualityUtil;
import com.enderio.enderio.api.filter.ItemFilter;
import com.enderio.enderio.content.filters.item.ItemFilterUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.NonNullList;
import com.enderio.core.common.network.codec.PacketCodecs;
import com.enderio.core.common.network.codec.PacketCodec;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

public record EnderItemFilter(NonNullList<ItemStack> matches, boolean isDenyList, boolean shouldCompareComponents,
        DamageFilterMode damageFilterMode, boolean compareLegacyTags) implements ItemFilter, TooltipComponent {

    public static final EnderItemFilter EMPTY = new EnderItemFilter(0);

    // TODO: 1.22: Change field names
    public static final Codec<EnderItemFilter> CODEC = RecordCodecBuilder.create(componentInstance -> componentInstance
            .group(OrderedListCodec.create(256, StackDataCodecs.OPTIONAL_ITEM, ItemStack.EMPTY)
                    .fieldOf("items")
                    .forGetter(EnderItemFilter::matches),
                    Codec.BOOL.optionalFieldOf("isInvert", false).forGetter(EnderItemFilter::isDenyList),
                    Codec.BOOL.optionalFieldOf("isNbt", false).forGetter(EnderItemFilter::shouldCompareComponents),
                    DamageFilterMode.CODEC.optionalFieldOf("damageMode", DamageFilterMode.IGNORE)
                            .forGetter(EnderItemFilter::damageFilterMode),
                    Codec.BOOL.optionalFieldOf("compareLegacyTags", false).forGetter(EnderItemFilter::compareLegacyTags))
            .apply(componentInstance, EnderItemFilter::new));

    public static final PacketCodec<EnderItemFilter> STREAM_CODEC = PacketCodec.composite(
            PacketCodecs.ITEM_STACK_DATA.list(256), EnderItemFilter::matches,
            PacketCodecs.BOOL, EnderItemFilter::isDenyList, PacketCodecs.BOOL,
            EnderItemFilter::shouldCompareComponents, DamageFilterMode.STREAM_CODEC, EnderItemFilter::damageFilterMode,
            PacketCodecs.BOOL, EnderItemFilter::compareLegacyTags,
            EnderItemFilter::new);

    public EnderItemFilter(int size) {
        this(NonNullList.withSize(size, ItemStack.EMPTY), false, false, DamageFilterMode.IGNORE);
    }

    public EnderItemFilter(int size, boolean isDenyList, boolean shouldCompareComponents,
            DamageFilterMode damageFilterMode) {
        this(NonNullList.withSize(size, ItemStack.EMPTY), isDenyList, shouldCompareComponents, damageFilterMode);
    }

    public EnderItemFilter(NonNullList<ItemStack> matches, boolean isDenyList, boolean shouldCompareComponents,
            DamageFilterMode damageFilterMode) {
        this(matches, isDenyList, shouldCompareComponents, damageFilterMode, false);
    }

    public EnderItemFilter(List<ItemStack> matches, boolean isDenyList, boolean shouldCompareComponents,
            DamageFilterMode damageFilterMode) {
        this(matches, isDenyList, shouldCompareComponents, damageFilterMode, false);
    }

    public EnderItemFilter(List<ItemStack> matches, boolean isDenyList, boolean shouldCompareComponents,
            DamageFilterMode damageFilterMode, boolean compareLegacyTags) {
        this(NonNullList.withSize(matches.size(), ItemStack.EMPTY), isDenyList, shouldCompareComponents,
                damageFilterMode, compareLegacyTags);

        for (int i = 0; i < matches.size(); i++) {
            this.matches.set(i, matches.get(i));
        }
    }

    @Override
    public ItemStack test(@Nullable IItemHandler target, ItemStack itemStack) {
        if (!damageFilterMode.test(itemStack)) {
            return ItemStack.EMPTY;
        }

        for (var match : matches) {
            if (match.isEmpty()) {
                continue;
            }

            if (matches(match, itemStack)) {
                return isDenyList ? ItemStack.EMPTY : itemStack;
            }
        }

        return isDenyList ? itemStack : ItemStack.EMPTY;
    }

    private boolean matches(ItemStack sample, ItemStack stack) {
        if (shouldCompareComponents && compareLegacyTags) {
            // Forge 6 includes the raw damage tag in exact stack comparisons.
            return ItemStack.isSameItemSameTags(sample, stack);
        }
        return ItemStack.isSameItem(sample, stack)
            && (!shouldCompareComponents || ItemFilterUtils.doComponentsMatch(sample, stack));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        EnderItemFilter other = (EnderItemFilter) o;
        return isDenyList == other.isDenyList &&
            shouldCompareComponents == other.shouldCompareComponents &&
            damageFilterMode == other.damageFilterMode &&
            compareLegacyTags == other.compareLegacyTags &&
            EqualityUtil.areListsEqual(matches, other.matches, ItemStack::matches);
    }

    @Override
    public int hashCode() {
        return Objects.hash(EqualityUtil.hashListContents(matches, StackDataCodecs::hashItem), isDenyList,
            shouldCompareComponents, damageFilterMode, compareLegacyTags);
    }
}
