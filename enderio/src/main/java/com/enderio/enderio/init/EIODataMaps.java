package com.enderio.enderio.init;

import com.enderio.core.common.datamap.DataMaps;
import com.enderio.enderio.api.components.GrindingBallData;
import com.enderio.enderio.foundation.datamap.RangeExtender;
import com.enderio.enderio.foundation.datamap.VatReagent;

public final class EIODataMaps {
    private EIODataMaps() {}
    public static void register() {
        DataMaps.register(VatReagent.DATA_MAP);
        DataMaps.register(RangeExtender.DATA_MAP);
        DataMaps.register(GrindingBallData.DATA_MAP_TYPE);
    }
}
