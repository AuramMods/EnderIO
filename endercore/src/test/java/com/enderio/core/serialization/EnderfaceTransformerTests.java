package com.enderio.core.serialization;

import net.minecraft.world.Container;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.BasicVerifier;

import javax.script.Invocable;
import javax.script.ScriptEngineManager;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class EnderfaceTransformerTests {
    @Test
    void actualCoremodTransformsNativeForgeContainerWithValidStackFrames() throws Exception {
        var engine = new ScriptEngineManager().getEngineByName("nashorn");
        assertNotNull(engine, "The pinned Forge JavaScript engine must be available");
        var script = Path.of(System.getProperty("enderio.port.root"),
            "enderio/src/main/resources/coremods/enderface_menu_access.js");
        engine.eval(Files.readString(script));
        var invocable = (Invocable) engine;
        var definitions = (Map<?, ?>) invocable.invokeFunction("initializeCoreMod");
        var definition = (Map<?, ?>) definitions.get("enderface_container_access");
        var selector = (Map<?, ?>) definition.get("target");
        assertEquals("net.minecraft.world.Container", selector.get("class"));
        var node = new ClassNode();
        try (var source = Container.class.getResourceAsStream("Container.class")) {
            assertNotNull(source);
            new ClassReader(source).accept(node, 0);
        }
        var target = node.methods.stream().filter(method -> method.name.equals(selector.get("methodName"))
            && method.desc.equals(selector.get("methodDesc"))).findFirst().orElseThrow();
        assertSame(target, invocable.invokeMethod(definition, "transformer", target));
        int checkedReturns = 0;
        for (var instruction : target.instructions) {
            if (instruction.getOpcode() == Opcodes.IRETURN) {
                assertTrue(instruction.getPrevious() instanceof MethodInsnNode);
                var hook = (MethodInsnNode) instruction.getPrevious();
                assertEquals("com/enderio/enderio/content/enderface/EnderfaceMenuAccess", hook.owner);
                assertEquals("stillValid", hook.name);
                checkedReturns++;
            }
        }
        assertTrue(checkedReturns > 0, "No native return paths were transformed");
        new Analyzer<>(new BasicVerifier()).analyze(node.name, target);
    }
}
