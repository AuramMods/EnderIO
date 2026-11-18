package com.enderio.enderio.registrytests;

import net.minecraft.gametest.framework.GlobalTestReporter;
import net.minecraft.gametest.framework.JUnitLikeTestReporter;
import net.minecraftforge.fml.common.Mod;

@Mod("enderio_registry_tests")
public final class RegistryTestMod {
    public RegistryTestMod() throws javax.xml.parsers.ParserConfigurationException {
        var report = new java.io.File(System.getProperty("enderio.registry.report", "registry-tests.xml"));
        report.getAbsoluteFile().getParentFile().mkdirs();
        GlobalTestReporter.replaceWith(new JUnitLikeTestReporter(report));
    }
}
