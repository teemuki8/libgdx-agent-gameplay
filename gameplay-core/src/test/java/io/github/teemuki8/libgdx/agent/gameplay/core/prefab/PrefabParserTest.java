package io.github.teemuki8.libgdx.agent.gameplay.core.prefab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.teemuki8.libgdx.agent.gameplay.core.component.Health;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.Transform2D;
import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayDiagnosticCode;
import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayException;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.PrefabId;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.Vec2;
import java.nio.charset.StandardCharsets;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

final class PrefabParserTest {
    @Test
    void parsesTheClosedV1ArraySchemaIntoStandardComponents() {
        PrefabCatalog catalog = parser().parse(bytes("""
                {
                  "schemaVersion": "gameplay-prefabs/1",
                  "prefabs": [
                    {
                      "id": "player",
                      "components": [
                        {"type":"transform","position":[2,3],"size":[0.8,1.1],"pivot":[0.5,0.5]},
                        {"type":"health","current":3,"max":3}
                      ]
                    }
                  ]
                }
                """));

        PrefabDefinition player = catalog.require(PrefabId.of("player"));
        assertEquals(new Health(3, 3), player.components().get(Health.TYPE));
        assertEquals(new Vec2(2, 3),
                ((Transform2D) player.components().get(Transform2D.TYPE)).position());
    }

    @Test
    void rejectsUnknownFieldWithJsonPointerAndCorrection() {
        GameplayException failure = assertFailure("""
                {"schemaVersion":"gameplay-prefabs/1","prefabs":[
                  {"id":"player","components":[
                    {"type":"health","curent":3,"max":3}
                  ]}
                ]}
                """);

        assertEquals(GameplayDiagnosticCode.UNKNOWN_PREFAB_FIELD, failure.code());
        assertEquals("/prefabs/0/components/0/curent",
                failure.diagnostic().location().get("jsonPointer"));
        assertTrue(failure.diagnostic().correction().contains("current"));
    }

    @Test
    void rejectsDuplicateKeysPrefabsAndComponents() {
        assertCode(GameplayDiagnosticCode.DUPLICATE_JSON_KEY, """
                {"schemaVersion":"gameplay-prefabs/1","schemaVersion":"gameplay-prefabs/1",
                 "prefabs":[]}
                """);
        assertCode(GameplayDiagnosticCode.DUPLICATE_PREFAB_ID, """
                {"schemaVersion":"gameplay-prefabs/1","prefabs":[
                  {"id":"enemy","components":[]},{"id":"enemy","components":[]}
                ]}
                """);
        assertCode(GameplayDiagnosticCode.DUPLICATE_PREFAB_COMPONENT, """
                {"schemaVersion":"gameplay-prefabs/1","prefabs":[
                  {"id":"enemy","components":[
                    {"type":"health","current":3,"max":3},
                    {"type":"health","current":3,"max":3}
                  ]}
                ]}
                """);
    }

    @Test
    void rejectsUnsupportedSchemaUnknownTypeAndNonFiniteNumber() {
        assertCode(GameplayDiagnosticCode.UNSUPPORTED_PREFAB_SCHEMA, """
                {"schemaVersion":"gameplay-prefabs/2","prefabs":[]}
                """);
        assertCode(GameplayDiagnosticCode.UNKNOWN_COMPONENT_TYPE, """
                {"schemaVersion":"gameplay-prefabs/1","prefabs":[
                  {"id":"enemy","components":[{"type":"god-mode","enabled":true}]}
                ]}
                """);
        assertCode(GameplayDiagnosticCode.INVALID_PREFAB_VALUE, """
                {"schemaVersion":"gameplay-prefabs/1","prefabs":[
                  {"id":"enemy","components":[{"type":"health","current":1e309,"max":3}]}
                ]}
                """);
    }

    @Test
    void rejectsEncodedInputAboveOneMebibyteBeforeParsing() {
        byte[] oversized = new byte[PrefabLimits.MAX_DOCUMENT_BYTES + 1];
        java.util.Arrays.fill(oversized, (byte) ' ');

        GameplayException failure = assertThrows(GameplayException.class,
                () -> parser().parse(oversized));

        assertEquals(GameplayDiagnosticCode.PREFAB_INPUT_LIMIT_EXCEEDED, failure.code());
    }

    @Test
    void rejectsMissingFieldsWrongTypesAndOutOfRangeComponents() {
        assertCode(GameplayDiagnosticCode.MISSING_PREFAB_FIELD, """
                {"schemaVersion":"gameplay-prefabs/1","prefabs":[
                  {"id":"enemy","components":[{"type":"health","current":3}]}
                ]}
                """);
        assertCode(GameplayDiagnosticCode.INVALID_PREFAB_VALUE, """
                {"schemaVersion":"gameplay-prefabs/1","prefabs":[
                  {"id":"enemy","components":[{"type":"health","max":"three"}]}
                ]}
                """);
        assertCode(GameplayDiagnosticCode.INVALID_COMPONENT_VALUE, """
                {"schemaVersion":"gameplay-prefabs/1","prefabs":[
                  {"id":"enemy","components":[{"type":"health","current":4,"max":3}]}
                ]}
                """);
    }

    @Test
    void rejectsTrailingContentAndBoundedCollections() {
        assertCode(GameplayDiagnosticCode.TRAILING_PREFAB_CONTENT, """
                {"schemaVersion":"gameplay-prefabs/1","prefabs":[]} []
                """);
        assertCode(GameplayDiagnosticCode.PREFAB_COUNT_LIMIT_EXCEEDED,
                catalogWithPrefabs(PrefabLimits.MAX_PREFABS + 1));
        PrefabLimits oneComponent = new PrefabLimits(
                PrefabLimits.MAX_DOCUMENT_BYTES, PrefabLimits.MAX_DEPTH,
                PrefabLimits.MAX_PREFABS, 1, PrefabLimits.MAX_STRING_LENGTH,
                PrefabLimits.MAX_ARRAY_VALUES, PrefabLimits.MAX_NUMBER_LENGTH,
                PrefabLimits.MAX_DIAGNOSTICS, PrefabLimits.MAX_CORRECTION_LENGTH);
        GameplayException componentLimit = assertThrows(GameplayException.class,
                () -> new PrefabParser(StandardComponentCodecs.registry(), oneComponent)
                        .parse(bytes(prefabWithTwoComponents())));
        assertEquals(GameplayDiagnosticCode.PREFAB_COUNT_LIMIT_EXCEEDED,
                componentLimit.code());
    }

    @Test
    void rejectsExcessiveStringDepthAndOrdinaryArrayLength() {
        String longId = "x".repeat(PrefabLimits.MAX_STRING_LENGTH + 1);
        assertCode(GameplayDiagnosticCode.INVALID_PREFAB_VALUE,
                "{\"schemaVersion\":\"gameplay-prefabs/1\",\"prefabs\":["
                        + "{\"id\":\"" + longId + "\",\"components\":[]}]}");

        String nested = "[".repeat(PrefabLimits.MAX_DEPTH)
                + "0" + "]".repeat(PrefabLimits.MAX_DEPTH);
        assertCode(GameplayDiagnosticCode.PREFAB_DEPTH_EXCEEDED,
                componentWithExtra("\"unknown\":" + nested));

        String frames = IntStream.range(0, PrefabLimits.MAX_ARRAY_VALUES + 1)
                .mapToObj(index -> "\"frame-" + index + "\"")
                .collect(java.util.stream.Collectors.joining(","));
        assertCode(GameplayDiagnosticCode.PREFAB_COUNT_LIMIT_EXCEEDED,
                componentWithExtra("\"unknown\":[" + frames + "]"));
    }

    private static PrefabParser parser() {
        return new PrefabParser(StandardComponentCodecs.registry(), PrefabLimits.defaults());
    }

    private static GameplayException assertFailure(String json) {
        return assertThrows(GameplayException.class, () -> parser().parse(bytes(json)));
    }

    private static void assertCode(GameplayDiagnosticCode code, String json) {
        assertEquals(code, assertFailure(json).code());
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static String catalogWithPrefabs(int count) {
        String prefabs = IntStream.range(0, count)
                .mapToObj(index -> "{\"id\":\"p-" + index + "\",\"components\":[]}")
                .collect(java.util.stream.Collectors.joining(","));
        return "{\"schemaVersion\":\"gameplay-prefabs/1\",\"prefabs\":["
                + prefabs + "]}";
    }

    private static String prefabWithTwoComponents() {
        return "{\"schemaVersion\":\"gameplay-prefabs/1\",\"prefabs\":["
                + "{\"id\":\"enemy\",\"components\":["
                + "{\"type\":\"health\",\"current\":1,\"max\":1},"
                + "{\"type\":\"faction\",\"value\":\"enemy\"}]}]}";
    }

    private static String componentWithExtra(String extraField) {
        return "{\"schemaVersion\":\"gameplay-prefabs/1\",\"prefabs\":["
                + "{\"id\":\"enemy\",\"components\":[{\"type\":\"health\","
                + "\"current\":1,\"max\":1," + extraField + "}]}]}";
    }
}
