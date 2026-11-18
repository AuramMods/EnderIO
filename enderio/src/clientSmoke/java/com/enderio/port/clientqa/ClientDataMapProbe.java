package com.enderio.port.clientqa;

import com.enderio.core.common.datamap.ClientDataMaps;
import com.enderio.enderio.api.components.GrindingBallData;
import com.enderio.enderio.foundation.datamap.RangeExtender;
import com.enderio.enderio.foundation.datamap.VatReagent;
import com.enderio.enderio.init.EIOBlocks;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;

import java.util.Map;

/** Observes server-supplied data through the client APIs and Forge's real tooltip event. */
record ClientDataMapProbe(GrindingBallData flint, GrindingBallData charcoal, Double gunpowder, Double fireCharge,
                          Integer range, Integer advancedRange, boolean flintTagged, boolean charcoalTagged) {
    private static final ResourceLocation RANGE = ResourceLocation.fromNamespaceAndPath("enderio", "range_extender");
    private static final ResourceLocation EXPLOSIVES = ResourceLocation.fromNamespaceAndPath("enderio", "explosives");
    private static final ResourceLocation FIXTURE_TAG = ResourceLocation.fromNamespaceAndPath("enderio", "port_qa_grinding");

    static final ClientDataMapProbe BASE = new ClientDataMapProbe(new GrindingBallData(1.2f, 1.25f, 0.85f, 24000),
        null, 0.25, 0.5, 16, 32, false, false);
    static final ClientDataMapProbe OVERRIDE = new ClientDataMapProbe(new GrindingBallData(1.5f, 1.75f, 0.5f, 45678),
        new GrindingBallData(1.5f, 1.75f, 0.5f, 45678), 2.25, null, 29, null, true, true);
    static final ClientDataMapProbe FINAL = new ClientDataMapProbe(new GrindingBallData(2.0f, 1.25f, 0.75f, 33333),
        null, 0.25, 0.5, 16, 32, true, false);

    static ClientDataMapProbe read() {
        var explosives = TagKey.create(Registries.ITEM, EXPLOSIVES);
        var range = TagKey.create(Registries.BLOCK, RANGE);
        var fixture = TagKey.create(Registries.ITEM, FIXTURE_TAG);
        return new ClientDataMapProbe(
            ClientDataMaps.get(GrindingBallData.DATA_MAP_TYPE, Items.FLINT),
            ClientDataMaps.get(GrindingBallData.DATA_MAP_TYPE, Items.CHARCOAL),
            value(ClientDataMaps.get(VatReagent.DATA_MAP, Items.GUNPOWDER), explosives),
            value(ClientDataMaps.get(VatReagent.DATA_MAP, Items.FIRE_CHARGE), explosives),
            value(ClientDataMaps.get(RangeExtender.DATA_MAP, EIOBlocks.WIRELESS_CHARGER_ANTENNA.get()), range),
            value(ClientDataMaps.get(RangeExtender.DATA_MAP, EIOBlocks.WIRELESS_CHARGER_ANTENNA_ADVANCED.get()), range),
            Items.FLINT.getDefaultInstance().is(fixture), Items.CHARCOAL.getDefaultInstance().is(fixture));
    }

    private static <K, V> V value(Map<K, V> map, K key) {
        return map == null ? null : map.get(key);
    }

    boolean tooltipsMatch(Player player) {
        var flintLine = "Main Output " + (int) (flint.outputMultiplier() * 100) + "%";
        var flintTooltip = Items.FLINT.getDefaultInstance().getTooltipLines(player, TooltipFlag.NORMAL);
        if (flintTooltip.stream().noneMatch(line -> line.getString().equals(flintLine))) return false;
        var charcoalTooltip = Items.CHARCOAL.getDefaultInstance().getTooltipLines(player, TooltipFlag.NORMAL);
        return charcoalTooltip.stream().anyMatch(line -> line.getString().startsWith("Main Output ")) == (charcoal != null);
    }
}
