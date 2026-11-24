package com.enderio.enderio.content.conduits.network.pathing;

import com.enderio.enderio.api.conduits.connection.ConduitBlockConnection;
import com.enderio.enderio.api.conduits.connection.path.ConduitConnectionPath;
import com.enderio.enderio.api.conduits.connection.path.ConnectionPathProperty;
import com.enderio.enderio.api.conduits.network.node.ConduitNode;
import com.enderio.enderio.compat.auram.RfTransferBridge;
import com.enderio.enderio.content.conduits.type.energy.EnergyConduit;
import com.google.common.collect.Maps;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;

/**
 * Standard breadth-first search pathfinding implementation for conduit networks.
 * <p>
 * This algorithm finds the shortest path (by hop count) between two conduit connections
 * while aggregating path properties from each node along the way.
 * </p>
 * <p>
 * The BFS guarantees finding the shortest path in terms of number of hops, making it
 * suitable for conduit networks where all connections have equal cost.
 * </p>
 */
public class BreadthFirstPathingStrategy implements ConduitPathingStrategy {
    
    @Override
    public Optional<ConduitConnectionPath> findPath(
        ConduitBlockConnection from,
        ConduitBlockConnection to,
        PathfindingContext context
    ) {
        ConduitNode start = from.node();
        ConduitNode goal = to.node();
        
        // Collect starting node properties
        Map<ConnectionPathProperty<?>, List<Object>> startAggregates = 
            createAggregateMap(context.collectNodeProperties(start));
        
        boolean observeRoute = RfTransferBridge.enabled()
                && startAggregates.containsKey(EnergyConduit.PATH_MAX_TRANSFER_RATE);
        Map<ConduitNode, ConduitNode> parents = observeRoute ? new HashMap<>() : null;

        // Special case: same node
        if (start.equals(goal)) {
            return Optional.of(new ConduitConnectionPath(
                from, 
                to, 
                1, 
                aggregatePropertyValues(startAggregates),
                route(from, to, 1, parents)
            ));
        }
        
        // BFS data structures
        Queue<ConduitNode> queue = new LinkedList<>();
        Set<ConduitNode> visited = new HashSet<>();
        Map<ConduitNode, Integer> distance = new HashMap<>();
        Map<ConduitNode, Map<ConnectionPathProperty<?>, List<Object>>> aggregateProperties = new HashMap<>();
        
        // Initialize with start node
        queue.add(start);
        visited.add(start);
        distance.put(start, 1);
        aggregateProperties.put(start, startAggregates);
        
        // BFS traversal
        while (!queue.isEmpty()) {
            ConduitNode current = queue.poll();
            int currentDist = distance.get(current);
            
            for (ConduitNode neighbor : context.neighbors(current)) {
                // Skip already visited nodes
                if (visited.contains(neighbor)) {
                    continue;
                }
                
                if (parents != null) parents.put(neighbor, current);
                visited.add(neighbor);
                queue.add(neighbor);
                distance.put(neighbor, currentDist + 1);
                
                // Aggregate properties along the path
                Map<ConnectionPathProperty<?>, List<Object>> neighborAggregate = 
                    duplicatePropertyMap(aggregateProperties.get(current));
                mergePropertyMap(neighborAggregate, context.collectNodeProperties(neighbor));
                aggregateProperties.put(neighbor, neighborAggregate);
                
                // Check if we've reached the goal
                if (neighbor.equals(goal)) {
                    return Optional.of(new ConduitConnectionPath(
                        from,
                        to,
                        currentDist + 1,
                        aggregatePropertyValues(neighborAggregate),
                        route(from, to, currentDist + 1, parents)
                    ));
                }
            }
        }
        
        // No path found
        return Optional.empty();
    }
    
    private static long[] route(ConduitBlockConnection from, ConduitBlockConnection to, int length,
            @Nullable Map<ConduitNode, ConduitNode> parents) {
        if (parents == null) return null;
        long[] positions = new long[length + 2];
        positions[0] = from.connectedBlockPos().asLong();
        positions[length + 1] = to.connectedBlockPos().asLong();
        ConduitNode current = to.node();
        for (int i = length; i >= 1; i--) {
            positions[i] = current.pos().asLong();
            current = parents.get(current);
        }
        return positions;
    }

    // region Property Aggregation Helpers
    
    /**
     * Create an aggregate map from initial node properties.
     * Each property value is wrapped in a list to prepare for aggregation.
     */
    private Map<ConnectionPathProperty<?>, List<Object>> createAggregateMap(
        Map<ConnectionPathProperty<?>, Object> nodeProperties
    ) {
        Map<ConnectionPathProperty<?>, List<Object>> aggregateMap = Maps.newHashMap();
        nodeProperties.forEach((property, value) -> 
            aggregateMap.put(property, new ArrayList<>(List.of(value)))
        );
        return aggregateMap;
    }
    
    /**
     * Create a deep copy of a property map.
     * Required to maintain independent property lists for each path.
     */
    private Map<ConnectionPathProperty<?>, List<Object>> duplicatePropertyMap(
        @Nullable Map<ConnectionPathProperty<?>, List<Object>> source
    ) {
        Map<ConnectionPathProperty<?>, List<Object>> copy = Maps.newHashMap();
        if (source != null) {
            source.forEach((property, list) -> copy.put(property, new ArrayList<>(list)));
        }
        return copy;
    }
    
    /**
     * Merge new property values into an existing aggregate map.
     * Appends each new value to the corresponding property's list.
     */
    private void mergePropertyMap(
        Map<ConnectionPathProperty<?>, List<Object>> target,
        Map<ConnectionPathProperty<?>, Object> additions
    ) {
        additions.forEach((property, value) -> 
            target.computeIfAbsent(property, k -> new ArrayList<>()).add(value)
        );
    }
    
    /**
     * Aggregate all collected property values into their final forms.
     * Uses each property's aggregation function (e.g., min, max, sum).
     */
    private Map<ConnectionPathProperty<?>, Object> aggregatePropertyValues(
        Map<ConnectionPathProperty<?>, List<Object>> aggregatedLists
    ) {
        Map<ConnectionPathProperty<?>, Object> aggregatedValues = Maps.newHashMap();
        aggregatedLists.forEach((property, values) -> 
            aggregatedValues.put(property, aggregateProperty(property, values))
        );
        return aggregatedValues;
    }
    
    /**
     * Apply a property's aggregation function to a list of values.
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static Object aggregateProperty(ConnectionPathProperty property, List<Object> values) {
        return property.aggregate(values);
    }
    
    // endregion
}
