package com.enderio.port.testing;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import java.util.HashSet;
import java.util.Set;
import jdk.internal.org.objectweb.asm.*;

/** Test launcher instrumentation only. Never include this agent in a mod jar. */
public final class WindowGuard implements ClassFileTransformer {
    private static final String GLFW = "org/lwjgl/glfw/GLFW";
    private static final Set<String> BLOCKED = Set.of("glfwShowWindow", "glfwFocusWindow",
        "glfwRequestWindowAttention", "glfwRestoreWindow", "glfwMaximizeWindow",
        "glfwIconifyWindow", "glfwSetCursorPos");

    public static void premain(String arguments, Instrumentation instrumentation) {
        instrumentation.addTransformer(new WindowGuard());
        System.out.println("QA_EVT {\"event\":\"window_guard_installed\",\"status\":\"info\"}");
    }

    @Override
    public byte[] transform(ClassLoader loader, String name, Class<?> redefined, ProtectionDomain domain, byte[] bytes) {
        if (!GLFW.equals(name)) return null;
        try {
            ClassReader reader = new ClassReader(bytes);
            ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS) {
                @Override protected ClassLoader getClassLoader() { return loader; }
            };
            Set<String> changed = new HashSet<>();
            reader.accept(new ClassVisitor(Opcodes.ASM8, writer) {
                @Override public MethodVisitor visitMethod(int access, String method, String descriptor,
                        String signature, String[] exceptions) {
                    var original = super.visitMethod(access, method, descriptor, signature, exceptions);
                    if (BLOCKED.contains(method)) {
                        changed.add(method);
                        original.visitCode();
                        event(original, "window_guard_blocked_" + method);
                        original.visitInsn(Opcodes.RETURN);
                        original.visitMaxs(0, 0);
                        original.visitEnd();
                        return null;
                    }
                    return new MethodVisitor(Opcodes.ASM8, original) {
                        @Override public void visitCode() {
                            super.visitCode();
                            switch (method) {
                                case "glfwInit" -> {
                                    changed.add(method);
                                    callHint(this, "glfwInitHint", 0x00051002, 0); // COCOA_MENUBAR
                                    callHint(this, "glfwInitHint", 0x00051001, 0); // COCOA_CHDIR_RESOURCES
                                }
                                case "glfwInitHint" -> {
                                    changed.add(method);
                                    forceZeroHint(this, 0, 1, 0x00051002, 0x00051001);
                                }
                                case "glfwWindowHint" -> {
                                    changed.add(method);
                                    forceZeroHint(this, 0, 1, 0x00020004, 0x00020001, 0x0002000C); // VISIBLE, FOCUSED, FOCUS_ON_SHOW
                                }
                                case "glfwSetWindowAttrib" -> {
                                    changed.add(method);
                                    forceZeroHint(this, 2, 3, 0x00020004, 0x00020001, 0x0002000C);
                                }
                                case "nglfwCreateWindow" -> {
                                    changed.add(method);
                                    callHint(this, "glfwWindowHint", 0x00020004, 0);
                                    callHint(this, "glfwWindowHint", 0x00020001, 0);
                                    callHint(this, "glfwWindowHint", 0x0002000C, 0);
                                    visitInsn(Opcodes.LCONST_0);
                                    visitVarInsn(Opcodes.LSTORE, 4); // No fullscreen monitor, including direct native-address overload.
                                    event(this, "window_guard_hidden_create");
                                }
                                case "glfwSetWindowMonitor" -> {
                                    changed.add(method);
                                    visitInsn(Opcodes.LCONST_0);
                                    visitVarInsn(Opcodes.LSTORE, 2); // Keep actual windowed resize/position behavior.
                                }
                                case "glfwSetInputMode" -> {
                                    changed.add(method);
                                    Label other = new Label();
                                    visitVarInsn(Opcodes.ILOAD, 2);
                                    visitLdcInsn(0x00033001); // CURSOR
                                    visitJumpInsn(Opcodes.IF_ICMPNE, other);
                                    visitLdcInsn(0x00034001); // CURSOR_NORMAL; never hide or capture.
                                    visitVarInsn(Opcodes.ISTORE, 3);
                                    visitLabel(other);
                                    forceZeroHint(this, 2, 3, 0x00033005); // RAW_MOUSE_MOTION
                                }
                                default -> { }
                            }
                        }
                    };
                }
            }, 0);
            var required = new HashSet<>(BLOCKED);
            required.addAll(Set.of("glfwInit", "glfwInitHint", "glfwWindowHint", "glfwSetWindowAttrib",
                "nglfwCreateWindow", "glfwSetWindowMonitor", "glfwSetInputMode"));
            required.removeAll(changed);
            if (!required.isEmpty()) throw new IllegalStateException("Unprotected GLFW entry points: " + required);
            System.out.println("QA_EVT {\"event\":\"window_guard_transformed\",\"status\":\"pass\",\"methods\":" + changed.size() + "}");
            return writer.toByteArray();
        } catch (Throwable failure) {
            // A failed JVM transformer would otherwise silently load the unguarded class.
            failure.printStackTrace();
            Runtime.getRuntime().halt(90);
            return null;
        }
    }

    private static void callHint(MethodVisitor visitor, String method, int key, int value) {
        visitor.visitLdcInsn(key);
        visitor.visitLdcInsn(value);
        visitor.visitMethodInsn(Opcodes.INVOKESTATIC, GLFW, method, "(II)V", false);
    }

    private static void forceZeroHint(MethodVisitor visitor, int keySlot, int valueSlot, int... keys) {
        for (int key : keys) {
            Label next = new Label();
            visitor.visitVarInsn(Opcodes.ILOAD, keySlot);
            visitor.visitLdcInsn(key);
            visitor.visitJumpInsn(Opcodes.IF_ICMPNE, next);
            visitor.visitInsn(Opcodes.ICONST_0);
            visitor.visitVarInsn(Opcodes.ISTORE, valueSlot);
            visitor.visitLabel(next);
        }
    }

    private static void event(MethodVisitor visitor, String event) {
        visitor.visitFieldInsn(Opcodes.GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
        visitor.visitLdcInsn("QA_EVT {\"event\":\"" + event + "\",\"status\":\"info\"}");
        visitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
    }
}
