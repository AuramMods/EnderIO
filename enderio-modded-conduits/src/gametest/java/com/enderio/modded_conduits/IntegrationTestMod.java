package com.enderio.modded_conduits;

@net.minecraftforge.fml.common.Mod("enderio_modded_conduits_tests")
public class IntegrationTestMod {
    public IntegrationTestMod() throws javax.xml.parsers.ParserConfigurationException {
        var report = new java.io.File(System.getProperty("enderio.integration.report", "integration-results.xml"));
        report.getAbsoluteFile().getParentFile().mkdirs();
        net.minecraft.gametest.framework.GlobalTestReporter.replaceWith(new net.minecraft.gametest.framework.JUnitLikeTestReporter(report));
    }
}
