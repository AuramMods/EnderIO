package com.enderio.enderio.registrytests;

import net.minecraft.server.MinecraftServer;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;

/** Supplies the running, fully loaded Forge server to the original JUnit cases. */
public final class ForgeServerExtension implements ParameterResolver {
    @Override
    public boolean supportsParameter(ParameterContext parameter, ExtensionContext context) {
        return parameter.getParameter().getType() == MinecraftServer.class;
    }

    @Override
    public Object resolveParameter(ParameterContext parameter, ExtensionContext context) {
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || !server.isSameThread()) {
            throw new IllegalStateException("Registry tests must execute on the running Forge server thread");
        }
        return server;
    }
}
