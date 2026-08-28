package io.github.teemuki8.libgdx.agent.gameplay.box2d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.box2d.Box2d;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.Collider;
import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayException;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.EntityId;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.Vec2;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class Box2d3BackendTest {
    @BeforeAll
    static void initializeNativeBinding() {
        Box2d.initialize();
    }

    @Test
    void publicApiIsBackendNeutralAndWorldIsApplicationOwned() {
        for (Class<?> type : new Class<?>[] {
                GameplayBox2dWorld.class, GameplayBox2dBridge.class, Box2dBodyFactory.class,
                Box2dBodyState.class, Box2dBodySpec.class, Box2dBodySpecResolver.class,
                Box2dRaycastSpec.class, Box2dRaycastHit.class}) {
            assertFalse(type.getName().startsWith("com.badlogic.gdx.box2d"));
            for (Method method : type.getMethods()) {
                if (!Modifier.isPublic(method.getModifiers())) {
                    continue;
                }
                String signature = method.toGenericString();
                assertFalse(signature.contains("com.badlogic.gdx.box2d"), signature);
                assertFalse(signature.contains("com.badlogic.gdx.physics.box2d"), signature);
            }
            Arrays.stream(type.getConstructors()).forEach(constructor ->
                    assertBackendNeutral(constructor.toGenericString()));
            Arrays.stream(type.getFields()).forEach(field ->
                    assertBackendNeutral(field.toGenericString()));
        }

        GameplayBox2dWorld world = GameplayBox2dWorld.create(
                new Box2dWorldSpec(new Vec2(0.0, -9.81), 4, 0.1));
        assertFalse(world.isClosed());
        world.close();
        world.close();
        assertTrue(world.isClosed());
        assertThrows(IllegalStateException.class,
                () -> new GameplayBox2dBridge(world, Box2dTestSupport.dynamicBodies(),
                        Box2dTestSupport.UNITS, Box2dTestSupport.runtime(),
                        Box2dTestSupport.limits()));
    }

    @Test
    void resolvedBackendClasspathContainsOnlyOfficialBox2d3Binding() {
        assertThrows(ClassNotFoundException.class,
                () -> Class.forName("com.badlogic.gdx.physics.box2d.World"));
        String source = Box2d.class.getProtectionDomain().getCodeSource()
                .getLocation().toString();
        assertTrue(source.contains("3.1.1-0"), source);
    }

    @Test
    void raycastRejectsWrongThreadAndClosedBridge() throws InterruptedException {
        GameplayBox2dWorld world = Box2dTestSupport.world(Vec2.ZERO);
        var runtime = Box2dTestSupport.runtime();
        GameplayBox2dBridge bridge = Box2dTestSupport.bridge(world, runtime);
        Box2dRaycastSpec ray = new Box2dRaycastSpec(
                Vec2.ZERO, new Vec2(32, 0), 1, 0xffff, 1);
        AtomicReference<Throwable> observed = new AtomicReference<>();
        Thread other = new Thread(() -> {
            try {
                bridge.raycast(ray);
            } catch (Throwable failure) {
                observed.set(failure);
            }
        });
        other.start();
        other.join();
        assertTrue(observed.get() instanceof GameplayException, String.valueOf(observed.get()));
        bridge.close();
        assertThrows(GameplayException.class, () -> bridge.raycast(ray));
        runtime.close();
        world.close();
    }

    @Test
    void bodyFactoryIsClaimedByOneBridgeOnItsConstructionThread()
            throws InterruptedException {
        Box2dBodyFactory factory = Box2dTestSupport.dynamicBodies();
        GameplayBox2dWorld firstWorld = Box2dTestSupport.world(Vec2.ZERO);
        var firstRuntime = Box2dTestSupport.runtime();
        GameplayBox2dBridge first = new GameplayBox2dBridge(
                firstWorld, factory, Box2dTestSupport.UNITS,
                firstRuntime, Box2dTestSupport.limits());

        AtomicReference<Throwable> observed = new AtomicReference<>();
        AtomicReference<Boolean> runtimeMutated = new AtomicReference<>();
        Thread other = new Thread(() -> {
            GameplayBox2dWorld secondWorld = Box2dTestSupport.world(Vec2.ZERO);
            var secondRuntime = Box2dTestSupport.runtime();
            GameplayBox2dBridge unexpected = null;
            try {
                unexpected = new GameplayBox2dBridge(
                        secondWorld, factory, Box2dTestSupport.UNITS,
                        secondRuntime, Box2dTestSupport.limits());
            } catch (Throwable failure) {
                observed.set(failure);
            } finally {
                runtimeMutated.set(secondRuntime.entity(
                        io.github.teemuki8.libgdx.agent.runtime.core.EntityId.of(
                                "box2d.world.gameplay")).isPresent());
                if (unexpected != null) {
                    unexpected.close();
                }
                secondRuntime.close();
                secondWorld.close();
            }
        });
        other.start();
        other.join();
        assertTrue(observed.get() instanceof GameplayException, String.valueOf(observed.get()));
        assertFalse(runtimeMutated.get());

        GameplayBox2dWorld thirdWorld = Box2dTestSupport.world(Vec2.ZERO);
        var thirdRuntime = Box2dTestSupport.runtime();
        assertThrows(GameplayException.class, () -> new GameplayBox2dBridge(
                thirdWorld, factory, Box2dTestSupport.UNITS,
                thirdRuntime, Box2dTestSupport.limits()));
        thirdRuntime.close();
        thirdWorld.close();
        first.close();
        firstRuntime.close();
        firstWorld.close();
    }

    private static void assertBackendNeutral(String signature) {
        assertFalse(signature.contains("com.badlogic.gdx.box2d"), signature);
        assertFalse(signature.contains("com.badlogic.gdx.physics.box2d"), signature);
    }

    @Test
    void publicValuesValidateBodyMaterialsCapsulesAndRaycasts() {
        Box2dBodySpec spec = new Box2dBodySpec(
                Box2dBodyType.DYNAMIC, 2.0, 0.4, 0.2, 0.1, 0.2, 1.0, true, false);
        assertEquals(Box2dBodyType.DYNAMIC, spec.type());
        assertThrows(IllegalArgumentException.class,
                () -> new Box2dBodySpec(Box2dBodyType.DYNAMIC, 1.0, -1.0, 0.0,
                        0.0, 0.0, 1.0, false, false));
        assertThrows(IllegalArgumentException.class,
                () -> new Box2dRaycastSpec(Vec2.ZERO, new Vec2(1.0, 0.0), 1, 1, 65));
        assertEquals(Collider.Shape.CAPSULE, Collider.Shape.valueOf("CAPSULE"));
        assertTrue(Arrays.asList(Collider.Shape.values()).contains(Collider.Shape.CAPSULE));
    }

    @Test
    void copiedStateAndRayHitCarryNoNativeIdentity() {
        Box2dBodyState state = new Box2dBodyState(
                EntityId.of("body"), "body.collider", Box2dBodyType.DYNAMIC,
                Vec2.ZERO, Vec2.ZERO, 0.25, 0.5, 2.0, 0.75,
                Collider.Shape.CAPSULE, new Vec2(16.0, 48.0), Vec2.ZERO, false, true);
        assertEquals(0.75, state.rotationalInertiaKilogramMetresSquared());
        Box2dRaycastHit hit = new Box2dRaycastHit(
                EntityId.of("body"), "body.collider", Vec2.ZERO,
                new Vec2(1.0, 0.0), 0.5);
        assertEquals(0.5, hit.fraction());
    }
}
