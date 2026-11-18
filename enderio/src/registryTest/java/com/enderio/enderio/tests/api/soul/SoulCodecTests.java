package com.enderio.enderio.tests.api.soul;

import com.enderio.enderio.api.soul.Soul;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.EntityType;
import com.enderio.enderio.registrytests.ForgeServerExtension;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ForgeServerExtension.class)
public class SoulCodecTests {
    // Test CODEC vs OPTIONAL_CODEC

    private static final String NULL_FORMAT = "{}";
    private static final String NULL_ENTITY_TYPE = """
        {
            "entity_type": null,
            "entity_tag": {
                "Health": 10.0
            }
        }""";

    @Test
    public void testOptionalCodecWorks(MinecraftServer server) {
        // Parse json
        var json = JsonParser.parseString(NULL_FORMAT);

        // Attempt to deserialize
        var ops = net.minecraft.resources.RegistryOps.create(JsonOps.INSTANCE, server.registryAccess());
        var result = Soul.OPTIONAL_CODEC.parse(ops, json);

        Assertions.assertTrue(result.result().isPresent());

        var soul = result.getOrThrow(false, message -> { throw new AssertionError(message); });
        Assertions.assertNotNull(soul);
        Assertions.assertEquals(Soul.EMPTY, soul);
    }

    @Test
    public void testCodecNullFails(MinecraftServer server) {
        // Parse json
        var json = JsonParser.parseString(NULL_FORMAT);

        // Attempt to deserialize
        var ops = net.minecraft.resources.RegistryOps.create(JsonOps.INSTANCE, server.registryAccess());
        var result = Soul.CODEC.parse(ops, json);

        Assertions.assertTrue(result.error().isPresent());
    }

    // This test is temporary, just to ensure that if any data has somehow saved with a null entity type, it resolves properly.
    @Test
    public void testOptionalCodecWithNullEntityTypeFails(MinecraftServer server) {
        // Parse json
        var json = JsonParser.parseString(NULL_ENTITY_TYPE);

        // Attempt to deserialize
        var ops = net.minecraft.resources.RegistryOps.create(JsonOps.INSTANCE, server.registryAccess());
        var result = Soul.OPTIONAL_CODEC.parse(ops, json);

        Assertions.assertTrue(result.result().isPresent());

        var soul = result.getOrThrow(false, message -> { throw new AssertionError(message); });
        Assertions.assertNotNull(soul);
        Assertions.assertEquals(Soul.EMPTY, soul);
    }

    @Test
    public void testCodecWithNullEntityTypeFails(MinecraftServer server) {
        // Parse json
        var json = JsonParser.parseString(NULL_ENTITY_TYPE);

        // Attempt to deserialize
        var ops = net.minecraft.resources.RegistryOps.create(JsonOps.INSTANCE, server.registryAccess());
        var result = Soul.CODEC.parse(ops, json);

        Assertions.assertTrue(result.error().isPresent());
    }

    // 8.0.5+ format
    private static final String NEW_FORMAT = """
        {
            "entity_type": "minecraft:allay",
            "entity_tag": {
                "id": "minecraft:allay",
                "Health": 10.0
            }
        }
        """;

    @Test
    public void testLoadNewFormat(MinecraftServer server) {
        // Parse json
        var json = JsonParser.parseString(NEW_FORMAT);

        // Attempt to deserialize
        var ops = net.minecraft.resources.RegistryOps.create(JsonOps.INSTANCE, server.registryAccess());
        var result = Soul.CODEC.parse(ops, json);

        Assertions.assertTrue(result.result().isPresent());

        var soul = result.getOrThrow(false, message -> { throw new AssertionError(message); });
        Assertions.assertNotNull(soul);

        // Ensure data matches
        Assertions.assertEquals(EntityType.ALLAY, soul.entityType());
        Assertions.assertEquals(10d, soul.entityTag().getDouble("Health"));

        // Ensure unwanted key is removed.
        Assertions.assertFalse(soul.entityTag().contains("id"));
    }

    // Pre 8.0.5 format
    private static final String OLD_FORMAT = """
        {
            "entityTag": {
                "id": "minecraft:allay",
                "Health": 10.0
            }
        }
        """;

    @Test
    public void testLoadOldFormat(MinecraftServer server) {
        // Parse json
        var json = JsonParser.parseString(OLD_FORMAT);

        // Attempt to deserialize
        var ops = net.minecraft.resources.RegistryOps.create(JsonOps.INSTANCE, server.registryAccess());
        var result = Soul.CODEC.parse(ops, json);

        Assertions.assertTrue(result.result().isPresent());

        var soul = result.getOrThrow(false, message -> { throw new AssertionError(message); });
        Assertions.assertNotNull(soul);

        // Ensure data matches
        Assertions.assertEquals(EntityType.ALLAY, soul.entityType());
        Assertions.assertEquals(10d, soul.entityTag().getDouble("Health"));
    }
}
