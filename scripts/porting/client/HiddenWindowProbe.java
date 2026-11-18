package com.enderio.port.testing;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.system.MemoryUtil;

/** Directly exercises guarded GLFW entry points before Minecraft is launched. */
public final class HiddenWindowProbe {
    public static void main(String[] args) throws Exception {
        GLFWErrorCallback.createPrint(System.err).set();
        if (!GLFW.glfwInit()) throw new AssertionError("Actual GLFW initialization failed");
        System.out.println("QA_EVT {\"event\":\"glfw_version\",\"status\":\"info\",\"details\":\"" + GLFW.glfwGetVersionString() + "\"}");
        GLFW.glfwDefaultWindowHints();
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 3);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 2);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_FORWARD_COMPAT, GLFW.GLFW_TRUE);
        // Intentionally request normally activating behavior; the agent must neutralize it.
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_TRUE);
        GLFW.glfwWindowHint(GLFW.GLFW_FOCUSED, GLFW.GLFW_TRUE);
        long window = GLFW.glfwCreateWindow(320, 180, "Ender IO hidden test probe", GLFW.glfwGetPrimaryMonitor(), 0);
        if (window == 0) throw new AssertionError("Actual native window/context creation failed");
        try {
            GLFW.glfwShowWindow(window);
            GLFW.glfwFocusWindow(window);
            GLFW.glfwRequestWindowAttention(window);
            GLFW.glfwSetWindowMonitor(window, GLFW.glfwGetPrimaryMonitor(), 0, 0, 320, 180, GLFW.GLFW_DONT_CARE);
            GLFW.glfwSetInputMode(window, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_DISABLED);
            GLFW.glfwSetCursorPos(window, 10, 10);
            GLFW.glfwMakeContextCurrent(window);
            GL.createCapabilities();
            for (int i = 0; i < 100; i++) {
                GLFW.glfwPollEvents();
                if (GLFW.glfwGetWindowAttrib(window, GLFW.GLFW_VISIBLE) != 0
                    || GLFW.glfwGetWindowAttrib(window, GLFW.GLFW_FOCUSED) != 0
                    || GLFW.glfwGetWindowMonitor(window) != 0
                    || GLFW.glfwGetInputMode(window, GLFW.GLFW_CURSOR) != GLFW.GLFW_CURSOR_NORMAL) {
                    throw new AssertionError("Native window focus/visibility/fullscreen/cursor invariant failed");
                }
                GL11.glClearColor(1, 0, 0, 1);
                GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
                var pixel = MemoryUtil.memAlloc(4);
                try {
                    GL11.glReadPixels(0, 0, 1, 1, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixel);
                    if ((pixel.get(0) & 255) != 255 || pixel.get(1) != 0 || pixel.get(2) != 0) {
                        throw new AssertionError("Hidden OpenGL framebuffer did not render red");
                    }
                } finally { MemoryUtil.memFree(pixel); }
                GLFW.glfwSwapBuffers(window);
                Thread.sleep(20);
            }
            System.out.println("QA_EVT {\"event\":\"hidden_glfw_probe\",\"status\":\"pass\",\"frames\":100,\"visible\":false,\"focused\":false,\"fullscreen\":false,\"cursorCaptured\":false}");
        } finally {
            GLFW.glfwDestroyWindow(window);
            GLFW.glfwTerminate();
        }
    }
}
