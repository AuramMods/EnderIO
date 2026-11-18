package com.enderio.enderio.registrytests;

import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraftforge.gametest.GameTestHolder;
import org.junit.jupiter.engine.JupiterTestEngine;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.junit.platform.launcher.core.LauncherConfig;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;

/** One native GameTest per original JUnit case, executed by the real Jupiter engine. */
@GameTestHolder("enderio_registry_tests")
public final class RegistryTests {
    @GameTestGenerator
    public static Collection<TestFunction> generate() throws Exception {
        var launcher = LauncherFactory.create(LauncherConfig.builder()
            .enableTestEngineAutoRegistration(false)
            .addTestEngines(new JupiterTestEngine()).build());
        var request = LauncherDiscoveryRequestBuilder.request();
        try (var stream = RegistryTests.class.getResourceAsStream("/registry-tests.txt")) {
            if (stream == null) throw new IllegalStateException("Missing registry test class inventory");
            try (var reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                reader.lines().filter(line -> !line.isBlank()).forEach(name ->
                    request.selectors(DiscoverySelectors.selectClass(name)));
            }
        }
        var plan = launcher.discover(request.build());
        var functions = new ArrayList<TestFunction>();
        for (var root : plan.getRoots()) {
            for (var test : plan.getDescendants(root)) {
                if (!test.isTest()) continue;
                var source = (MethodSource) test.getSource().orElseThrow();
                var name = source.getClassName() + "." + source.getMethodName();
                functions.add(new TestFunction("registry", name, "enderio_registry_tests:empty", 100, 0, true, helper -> {
                    var listener = new SummaryGeneratingListener();
                    launcher.execute(LauncherDiscoveryRequestBuilder.request()
                        .selectors(DiscoverySelectors.selectUniqueId(test.getUniqueId())).build(), listener);
                    var summary = listener.getSummary();
                    if (!summary.getFailures().isEmpty()) {
                        var failure = summary.getFailures().get(0).getException();
                        throw new RuntimeException(name + " failed: " + failure.getMessage(), failure);
                    }
                    if (summary.getTestsFoundCount() != 1 || summary.getTestsSucceededCount() != 1
                        || summary.getTestsSkippedCount() != 0 || summary.getTestsAbortedCount() != 0) {
                        throw new IllegalStateException(name + ": expected one executed, successful JUnit test; found="
                            + summary.getTestsFoundCount() + ", passed=" + summary.getTestsSucceededCount()
                            + ", skipped=" + summary.getTestsSkippedCount() + ", aborted=" + summary.getTestsAbortedCount());
                    }
                    helper.succeed();
                }));
            }
        }
        functions.sort(Comparator.comparing(TestFunction::getTestName));
        if (functions.isEmpty()) throw new IllegalStateException("JUnit discovered no registry tests");
        return functions;
    }
}
