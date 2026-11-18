package com.enderio.enderio.gametests.util;

import com.enderio.enderio.gametests.EnderIOTests;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.StructureUtils;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraftforge.gametest.GameTestHolder;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Registers the original scenarios with the native Forge GameTest runner. */
@GameTestHolder(EnderIOTests.MOD_ID)
public final class GameplayTests {
    private static final Map<ResourceLocation, Supplier<StructureTemplate>> TEMPLATES = new LinkedHashMap<>();
    private static List<TestFunction> tests;

    @GameTestGenerator
    public static Collection<TestFunction> generate() throws Exception {
        if (tests != null) return tests;
        var result = new ArrayList<TestFunction>();
        try (var stream = GameplayTests.class.getResourceAsStream("/gameplay-tests.txt")) {
            if (stream == null) throw new IllegalStateException("Missing gameplay test inventory");
            try (var reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                for (var name : reader.lines().filter(line -> !line.isBlank()).toList()) {
                    var type = Class.forName(name);
                    for (var field : type.getDeclaredFields()) {
                        var fixture = field.getAnnotation(FixtureTemplate.class);
                        if (fixture == null) continue;
                        @SuppressWarnings("unchecked")
                        var supplier = (Supplier<StructureTemplate>) field.get(null);
                        TEMPLATES.put(ResourceLocation.parse(fixture.value()), supplier);
                    }
                    for (var method : type.getDeclaredMethods()) {
                        var options = method.getAnnotation(GameTest.class);
                        if (options == null) continue;
                        var id = ResourceLocation.fromNamespaceAndPath(EnderIOTests.MOD_ID,
                            (type.getSimpleName() + "." + method.getName()).toLowerCase(Locale.ROOT));
                        var template = options.template().isEmpty() ? id : ResourceLocation.parse(options.template());
                        Consumer<GameTestHelper> body;
                        if (method.getParameterTypes()[0] == GameplayTest.class) {
                            var scenario = new GameplayTest();
                            invoke(method, scenario);
                            TEMPLATES.put(template, scenario.template());
                            body = scenario.body();
                        } else {
                            var helperType = method.getParameterTypes()[0].asSubclass(EnderGameTestHelper.class);
                            body = helper -> invoke(method, GameplayTest.wrap(helperType, helper));
                        }
                        result.add(new TestFunction(type.getSimpleName(), name + "." + method.getName(),
                            template.toString(), StructureUtils.getRotationForRotationSteps(options.rotationSteps()),
                            options.timeoutTicks(), options.setupTicks(), options.required(), options.requiredSuccesses(),
                            options.attempts(), body));
                    }
                }
            }
        }
        result.sort(Comparator.comparing(TestFunction::getTestName));
        if (result.isEmpty()) throw new IllegalStateException("No gameplay tests discovered");
        tests = List.copyOf(result);
        var inventoryPath = System.getProperty("enderio.gametest.discovered");
        if (inventoryPath != null) {
            var path = java.nio.file.Path.of(inventoryPath);
            java.nio.file.Files.createDirectories(path.toAbsolutePath().getParent());
            java.nio.file.Files.write(path, tests.stream().map(TestFunction::getTestName).toList(), StandardCharsets.UTF_8);
        }
        return tests;
    }

    public static void installTemplates(MinecraftServer server) {
        try {
            generate();
            TEMPLATES.forEach((id, supplier) -> server.getStructureManager().getOrCreate(id)
                .load(BuiltInRegistries.BLOCK.asLookup(), supplier.get().save(new CompoundTag())));
        } catch (Exception e) {
            throw new RuntimeException("Could not install gameplay test fixtures", e);
        }
    }

    private static void invoke(Method method, Object argument) {
        try {
            method.invoke(null, argument);
        } catch (InvocationTargetException e) {
            throw new RuntimeException(method.getName() + " failed: " + e.getCause().getMessage(), e.getCause());
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Could not invoke " + method.getName(), e);
        }
    }
}
