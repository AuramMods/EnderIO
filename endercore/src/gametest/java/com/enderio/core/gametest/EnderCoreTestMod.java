package com.enderio.core.gametest;

import net.minecraft.gametest.framework.GlobalTestReporter;
import net.minecraft.gametest.framework.JUnitLikeTestReporter;
import net.minecraftforge.fml.common.Mod;

import java.io.File;
import javax.xml.parsers.ParserConfigurationException;

/** Loaded only by the dedicated test profile, never included in a production jar. */
@Mod("endercore_tests")
public class EnderCoreTestMod {
    public EnderCoreTestMod() throws ParserConfigurationException {
        File report = new File(System.getProperty("endercore.gametest.report", "gametest-results.xml"));
        report.getAbsoluteFile().getParentFile().mkdirs();
        GlobalTestReporter.replaceWith(new JUnitLikeTestReporter(report));
    }
}
