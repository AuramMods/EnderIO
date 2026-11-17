package com.enderio.enderio.api.filter;

import net.minecraftforge.items.IItemHandler;

public record ItemFilterContext(IItemHandler target, ItemFilterDirection direction) {}
