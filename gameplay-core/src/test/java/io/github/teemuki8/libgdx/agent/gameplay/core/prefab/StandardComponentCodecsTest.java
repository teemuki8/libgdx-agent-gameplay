package io.github.teemuki8.libgdx.agent.gameplay.core.prefab;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.teemuki8.libgdx.agent.gameplay.core.component.Animation;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.Collider;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.Render;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.PrefabId;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

final class StandardComponentCodecsTest {
    @Test
    void decodesEveryStandardComponentWithoutReflection() {
        PrefabDefinition definition = new PrefabParser(
                StandardComponentCodecs.registry(), PrefabLimits.defaults())
                .parse(JSON.getBytes(StandardCharsets.UTF_8))
                .require(PrefabId.of("complete"));

        assertEquals(9, definition.components().size());
        assertEquals(Collider.Shape.BOX,
                ((Collider) definition.components().get(Collider.TYPE)).shape());
        assertEquals("idle",
                ((Animation) definition.components().get(Animation.TYPE)).currentClip());
        assertEquals("actors",
                ((Render) definition.components().get(Render.TYPE)).layer());
    }

    private static final String JSON = """
            {"schemaVersion":"gameplay-prefabs/1","prefabs":[{
              "id":"complete",
              "components":[
                {"type":"transform","position":[1,2],"rotationRadians":0.5,
                 "size":[1,1],"pivot":[0.5,0.5]},
                {"type":"movement","velocity":[0,0],"maxSpeed":4},
                {"type":"health","current":3,"max":3},
                {"type":"faction","value":"player"},
                {"type":"lifetime","remainingTicks":60},
                {"type":"collider","shape":"box","size":[0.8,0.8],"offset":[0,0],
                 "sensor":false,"categoryBits":1,"maskBits":65535},
                {"type":"sprite","asset":"arena/player","region":"player-idle",
                 "visualSize":[1,1],"origin":[0.5,0.5]},
                {"type":"animation","clips":[
                   {"name":"idle","frames":["player-idle"],"frameDurationTicks":6,"loop":true}
                 ],"currentClip":"idle","elapsedTicks":0,"frameIndex":0},
                {"type":"render","layer":"actors","order":10,"tint":[1,1,1,1],
                 "visible":true}
              ]
            }]}
            """;
}
