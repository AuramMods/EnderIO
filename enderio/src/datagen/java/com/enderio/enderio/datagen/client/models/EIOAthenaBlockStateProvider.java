package com.enderio.enderio.datagen.client.models;

import com.enderio.core.data.athena.AthenaBlockStateProvider;
import com.enderio.enderio.EnderIO;
import com.enderio.enderio.init.EIOBlocks;
import net.minecraft.data.PackOutput;
import net.minecraftforge.client.model.generators.ConfiguredModel;
import net.minecraftforge.client.model.generators.ModelFile;
import net.minecraftforge.common.data.ExistingFileHelper;

public class EIOAthenaBlockStateProvider extends AthenaBlockStateProvider {
    public EIOAthenaBlockStateProvider(PackOutput output, ExistingFileHelper existingFileHelper) {
        super(output, EnderIO.MOD_ID, existingFileHelper);
    }

    @Override
    public String getName() {
        return "Athena " + super.getName();
    }

    @Override
    protected void registerStatesAndModels() {
        capacitorBanks();
        glassBlocks();
    }

    private void capacitorBanks() {
        for (var capacitorBank : EIOBlocks.CAPACITOR_BANKS.values()) {
            String name = capacitorBank.getId().getPath();
            var builder = getAthenaVariantBuilder(capacitorBank.get())
                .renderType("solid")
                .texture("particle", blockTexture(capacitorBank.get()));
            for (String part : new String[] { "center", "empty", "horizontal", "vertical" }) {
                builder.texture(part, EnderIO.rl("block/ctm/" + name + "_" + part));
            }

            var model = models().getExistingFile(EnderIO.rl("block/" + name));
            builder.variants().partialState().setModels(new ConfiguredModel(model));
        }
    }

    private void glassBlocks() {
        ModelFile quartzModel = models().getExistingFile(modLoc("block/fused_quartz"));
        ModelFile glassModel = models().getExistingFile(modLoc("block/clear_glass"));
        for (var family : EIOBlocks.GLASS_BLOCKS.values()) {
            for (var entry : family.getAllBlocks().toList()) {
                var block = entry.get();
                boolean quartz = block.glassIdentifier().explosionResistance();
                String texture = quartz ? "block/ctm/fused_quartz/" : "block/ctm/clear_glass/";
                var builder = getAthenaVariantBuilder(block).renderType("cutout").tint(0);
                for (String part : new String[] { "center", "empty", "horizontal", "particle", "vertical" }) {
                    builder.texture(part, modLoc(texture + part));
                }
                builder.variants().partialState().setModels(new ConfiguredModel(quartz ? quartzModel : glassModel));
            }
        }
    }
}
