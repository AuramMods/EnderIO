package com.enderio.modded_conduits;

@net.minecraftforge.fml.common.Mod("enderio_modded_conduits_tests")
public class IntegrationTestMod {
    public IntegrationTestMod() throws javax.xml.parsers.ParserConfigurationException {
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addGenericListener(
            net.minecraft.world.level.block.entity.BlockEntity.class,
            (net.minecraftforge.event.AttachCapabilitiesEvent<net.minecraft.world.level.block.entity.BlockEntity> event) ->
                ChangingChemicalTank.attach(event));
        var report = new java.io.File(System.getProperty("enderio.integration.report", "integration-results.xml"));
        report.getAbsoluteFile().getParentFile().mkdirs();
        net.minecraft.gametest.framework.GlobalTestReporter.replaceWith(new net.minecraft.gametest.framework.JUnitLikeTestReporter(report));
    }
}
