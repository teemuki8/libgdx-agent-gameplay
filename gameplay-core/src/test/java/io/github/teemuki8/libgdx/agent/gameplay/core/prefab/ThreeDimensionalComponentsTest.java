package io.github.teemuki8.libgdx.agent.gameplay.core.prefab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayException;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.PrefabId;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

final class ThreeDimensionalComponentsTest {
    @Test
    void decodesThreeDimensionalComponentsThroughClosedPublicSchema() {
        assertEquals(3, parse("""
                {"type":"transform3d","position":[1,2,3],"rotation":[0,0,0,1],"scale":[1,2,1]},
                {"type":"velocity3d","linear":[0,1,2]},
                {"type":"aim3d","yawRadians":0,"pitchRadians":0}
                """).components().size());
    }

    @Test
    void rejectsMalformedDimensionsUnknownFieldsAndNonUnitRotation() {
        for (String component : new String[] {
                "{\"type\":\"transform3d\",\"position\":[1,2]}",
                "{\"type\":\"transform3d\",\"rotation\":[0,0,0,0]}",
                "{\"type\":\"transform3d\",\"scale\":[1,0,1]}",
                "{\"type\":\"velocity3d\",\"linear\":[0,1,2],\"speed\":2}",
                "{\"type\":\"aim3d\",\"yawRadians\":0,\"pitchRadians\":2}"}) {
            assertThrows(GameplayException.class, () -> parse(component));
        }
    }

    private static PrefabDefinition parse(String components) {
        String json = "{\"schemaVersion\":\"gameplay-prefabs/1\",\"prefabs\":[{\"id\":\"room\",\"components\":["
                + components + "]}]}";
        return new PrefabParser(StandardComponentCodecs.registry(), PrefabLimits.defaults())
                .parse(json.getBytes(StandardCharsets.UTF_8)).require(PrefabId.of("room"));
    }
}
