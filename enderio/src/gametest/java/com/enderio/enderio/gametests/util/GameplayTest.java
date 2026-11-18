package com.enderio.enderio.gametests.util;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.lang.reflect.InvocationTargetException;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** A fixture and a native test body, assembled by the existing gameplay scenarios. */
public final class GameplayTest {
    private Supplier<StructureTemplate> template;
    private Consumer<GameTestHelper> body;

    public void registerGameTestTemplate(Supplier<FixtureBuilder> builder) {
        template = () -> builder.get().build();
    }

    public <T extends EnderGameTestHelper> void onGameTest(Class<T> helperType, Consumer<T> scenario) {
        body = helper -> scenario.accept(wrap(helperType, helper));
    }

    public Supplier<StructureTemplate> template() { return java.util.Objects.requireNonNull(template, "Missing fixture"); }
    public Consumer<GameTestHelper> body() { return java.util.Objects.requireNonNull(body, "Missing test body"); }

    public static <T extends EnderGameTestHelper> T wrap(Class<T> type, GameTestHelper helper) {
        try {
            return type.getConstructor(GameTestHelper.class).newInstance(helper);
        } catch (InvocationTargetException e) {
            throw new RuntimeException("Cannot construct " + type.getName(), e.getCause());
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Cannot construct " + type.getName(), e);
        }
    }
}
