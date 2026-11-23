package com.enderio.enderio.integration.crafttweaker;

import com.blamejared.crafttweaker.api.annotation.ZenRegister;
import com.blamejared.crafttweaker.api.tag.type.KnownTag;
import com.enderio.enderio.foundation.souldata.SoulDataReloadListener;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import org.openzen.zencode.java.ZenCodeType;

import java.util.Optional;

@ZenRegister
@ZenCodeType.Name("mods.enderio.SoulSelector")
public record SoulSelector(Optional<ResourceLocation> entity, Optional<MobCategory> category, Optional<String> rule,
                           Optional<TagKey<EntityType<?>>> entityTag) {
    @ZenCodeType.Method
    public static SoulSelector any() {
        return new SoulSelector(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
    }

    @ZenCodeType.Method
    public static SoulSelector entity(EntityType<?> entity) {
        return new SoulSelector(Optional.of(BuiltInRegistries.ENTITY_TYPE.getKey(entity)), Optional.empty(), Optional.empty(),
                Optional.empty());
    }

    @ZenCodeType.Method
    public static SoulSelector category(String name) {
        var category = MobCategory.byName(name);
        if (category == null) {
            throw new IllegalArgumentException("Unknown mob category " + name);
        }
        return new SoulSelector(Optional.empty(), Optional.of(category), Optional.empty(), Optional.empty());
    }

    @ZenCodeType.Method
    public static SoulSelector soulRule(String domain) {
        if (SoulDataReloadListener.fromString(domain) == null) {
            throw new IllegalArgumentException("Unknown Ender IO soul rule domain " + domain);
        }
        return new SoulSelector(Optional.empty(), Optional.empty(), Optional.of(domain), Optional.empty());
    }

    @ZenCodeType.Method
    public static SoulSelector tag(KnownTag<EntityType<?>> tag) {
        return new SoulSelector(Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(tag.getTagKey()));
    }
}
