package com.enderio.enderio.api.conduits.connection.path;

import com.enderio.enderio.api.conduits.connection.ConduitBlockConnection;
import com.enderio.enderio.api.conduits.network.node.ConduitNode;
import com.enderio.enderio.compat.auram.RfTransferBridge;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.ApiStatus;

import java.util.Map;
import java.util.Objects;
import java.util.function.IntConsumer;

/**
 * Provides information about the path between two {@link ConduitBlockConnection}s.
 * Contains the length of the path and any properties gathered from {@link com.enderio.enderio.api.conduits.Conduit#collectNodePathProperties(ConduitNode, ConnectionPathPropertyConsumer)}
 */
@ApiStatus.AvailableSince("8.1.0")
public final class ConduitConnectionPath {
    private final ConduitBlockConnection start;
    private final ConduitBlockConnection end;
    private final int length;
    private final Map<ConnectionPathProperty<?>, ?> properties;
    private final @Nullable long[] route;
    private final boolean reversed;
    private @Nullable IntConsumer rfObserver;

    public ConduitConnectionPath(ConduitBlockConnection start, ConduitBlockConnection end, int length, Map<ConnectionPathProperty<?>, ?> properties) {
        this(start, end, length, properties, null, false);
    }

    @ApiStatus.Internal
    public ConduitConnectionPath(ConduitBlockConnection start, ConduitBlockConnection end, int length,
            Map<ConnectionPathProperty<?>, ?> properties, @Nullable long[] route) {
        this(start, end, length, properties, route, false);
    }

    private ConduitConnectionPath(ConduitBlockConnection start, ConduitBlockConnection end, int length,
            Map<ConnectionPathProperty<?>, ?> properties, @Nullable long[] route, boolean reversed) {
        this.route = route;
        this.reversed = reversed;
        this.start = start;
        this.end = end;
        this.length = length;
        this.properties = properties == null || properties.isEmpty() ? Map.of() : Map.copyOf(properties);
    }

    public ConduitBlockConnection start() {
        return start;
    }

    public ConduitBlockConnection end() {
        return end;
    }

    public int length() {
        return length;
    }

    public Map<ConnectionPathProperty<?>, ?> properties() {
        return properties;
    }

    @SuppressWarnings("unchecked")
    public <T> T property(ConnectionPathProperty<T> property) {
        Objects.requireNonNull(property);

        if (!properties.containsKey(property)) {
            return property.defaultValue();
        }

        return (T) properties.get(property);
    }

    public ConduitConnectionPath reverse() {
        return new ConduitConnectionPath(end, start, length, properties, route, !reversed);
    }

    @ApiStatus.Internal
    public void recordEnergyTransfer(ServerLevel level, int amount) {
        if (route == null) return;
        if (rfObserver == null) {
            long[] ordered = new long[route.length];
            for (int i = 0; i < route.length; i++) {
                ordered[i] = route[reversed ? route.length - 1 - i : i];
            }
            rfObserver = RfTransferBridge.observe(level, ordered);
        }
        rfObserver.accept(amount);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        if (!(other instanceof ConduitConnectionPath that)) {
            return false;
        }

        return length == that.length
            && Objects.equals(start, that.start)
            && Objects.equals(end, that.end)
            && Objects.equals(properties, that.properties);
    }

    @Override
    public int hashCode() {
        return Objects.hash(start, end, length, properties);
    }
}
