package com.enderio.enderio.compat.almostunified;

import com.almostreliable.unified.api.AlmostUnifiedLookup;
import com.enderio.enderio.foundation.tag.EIOTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraftforge.fml.ModList;
import org.jetbrains.annotations.Nullable;

public class AlmostUnifiedCompat {
    public static boolean isLoaded() {
        return ModList.get().isLoaded("almostunified");
    }

    public static @Nullable Item getTagTargetItem(TagKey<Item> tag) {
        if (isLoaded()) {
            return Adapter.getTagTargetItem(tag);
        }

        return null;
    }

    private static class Adapter {
        private static @Nullable Item getTagTargetItem(TagKey<Item> tag) {
            var lookup = AlmostUnifiedLookup.INSTANCE;
            Item preferred = lookup.getPreferredItemForTag(tag);
            if (preferred != null || !tag.location().getNamespace().equals(EIOTags.COMMON)) {
                return preferred;
            }

            // Forge 1.20.1 unification configs usually select the Forge material tag.
            Item forgePreferred = lookup.getPreferredItemForTag(EIOTags.Items.forgeEquivalent(tag));
            return forgePreferred != null && forgePreferred.builtInRegistryHolder().is(tag) ? forgePreferred : null;
        }
    }
}
