package com.enderio.enderio.gametests;

import com.enderio.enderio.gametests.util.GameplayTests;
import net.minecraft.gametest.framework.GlobalTestReporter;
import net.minecraft.gametest.framework.JUnitLikeTestReporter;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.fml.common.Mod;

@Mod(EnderIOTests.MOD_ID)
public final class EnderIOTests {
    public static final String MOD_ID = "enderio_tests";
    public static final String TEST_NUMBER = "enderio_tests:test_number";

    public EnderIOTests() throws javax.xml.parsers.ParserConfigurationException {
        MinecraftForge.EVENT_BUS.addGenericListener(net.minecraft.world.level.block.entity.BlockEntity.class,
            com.enderio.enderio.gametests.conduits.fluid.ChangingFluidHandler::attach);
        var directory = new java.io.File(System.getProperty("enderio.gametest.junit_dir", "../build/test-results/gameTestServer"));
        directory.mkdirs();
        GlobalTestReporter.replaceWith(new JUnitLikeTestReporter(new java.io.File(directory, "results.xml")));
        MinecraftForge.EVENT_BUS.addListener((ServerAboutToStartEvent event) -> GameplayTests.installTemplates(event.getServer()));
    }
}
