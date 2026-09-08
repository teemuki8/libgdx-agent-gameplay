package io.github.teemuki8.libgdx.agent.gameplay.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.teemuki8.libgdx.agent.gameplay.core.GameplayLimits;
import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayException;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.Bounds2;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.SystemId;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.Vec2;
import io.github.teemuki8.libgdx.agent.gameplay.core.visual.WorldVisualSnapshot;
import io.github.teemuki8.libgdx.agent.gameplay.core.visual.WorldVisualEntry;
import io.github.teemuki8.libgdx.agent.gameplay.core.visual.ScreenBounds;
import io.github.teemuki8.libgdx.agent.gameplay.core.visual.VisualEvidenceStatus;
import io.github.teemuki8.libgdx.agent.runtime.core.AgentRuntime;
import io.github.teemuki8.libgdx.agent.runtime.core.RuntimeValues;
import io.github.teemuki8.libgdx.agent.runtime.core.RuntimeValue;
import io.github.teemuki8.libgdx.agent.runtime.core.RuntimeLimits;
import io.github.teemuki8.libgdx.agent.runtime.core.RuntimeConfiguration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class PresentationVisualRefreshTest {
    @Test void pausedRefreshChangesCapturedBoundsWithoutAdvancingWorldOrRepeatingEvents() {
        try (var runtime=AgentRuntime.builder().build();
             var bridge=new GameplayRuntimeBridge(runtime,StandardRuntimeProjections.registry(),GameplayLimits.defaults())) {
            runtime.start();
            try(var world=GameplayRuntimeBridgeTest.world(bridge)) {
                world.step();
                var original=world.snapshot();
                long tick=world.tick();
                var token=bridge.lastFrameToken();
                var frame=runtime.latestFrame().orElseThrow().frameId();
                assertFalse(runtime.latestFrame().orElseThrow().events().isEmpty(),"initial spawn supplies an event that must not be replayed");
                for(int index=0;index<50;index++) {
                    bridge.refreshPresentationVisuals(visuals(original.tick(),"player",100+index));
                    assertEquals(frame,runtime.latestFrame().orElseThrow().frameId(),"refresh cannot capture a frame itself");
                    runtime.frame(16_666_667L,()->{});
                    var captured=runtime.latestFrame().orElseThrow();
                    var visual=captured.entity(id("gameplay.visual.player")).orElseThrow();
                    assertEquals(RuntimeValues.object(RuntimeValues.field("minX",RuntimeValues.decimal(0)),
                            RuntimeValues.field("minY",RuntimeValues.decimal(0)),
                            RuntimeValues.field("maxX",RuntimeValues.decimal(100+index)),
                            RuntimeValues.field("maxY",RuntimeValues.decimal(100))),visual.property("screenBounds").orElseThrow());
                    assertEquals(RuntimeValues.integer(3),captured.entity(id("gameplay.entity.player")).orElseThrow().property("health.current").orElseThrow());
                    assertTrue(captured.events().isEmpty());
                    assertEquals(original,world.snapshot());
                    assertEquals(tick,world.tick());
                    assertEquals(token,bridge.lastFrameToken());
                    frame=captured.frameId();
                }
            }
        }
    }

    @Test void invalidRefreshCannotReplaceLastSuccessfulEvidence() {
        try(var runtime=AgentRuntime.builder().build();
            var bridge=new GameplayRuntimeBridge(runtime,StandardRuntimeProjections.registry(),GameplayLimits.defaults())) {
            runtime.start();
            assertThrows(GameplayException.class,()->bridge.refreshPresentationVisuals(visuals(0,"player",100)));
            try(var world=GameplayRuntimeBridgeTest.world(bridge)) {
                world.step();
                long tick=world.snapshot().tick();
                bridge.refreshPresentationVisuals(visuals(tick,"player",100));
                assertThrows(GameplayException.class,()->bridge.refreshPresentationVisuals(visuals(tick+1,"player",999)));
                assertThrows(GameplayException.class,()->bridge.refreshPresentationVisuals(visuals(tick,"unknown",999)));
                var entry=visuals(tick,"player",999).entries().getFirst();
                assertThrows(GameplayException.class,()->bridge.refreshPresentationVisuals(new WorldVisualSnapshot(tick,List.of(entry,entry))));
                runtime.frame(16_666_667L,()->{});
                var retained=(RuntimeValue.ObjectValue)runtime.latestFrame().orElseThrow().entity(id("gameplay.visual.player")).orElseThrow().property("screenBounds").orElseThrow();
                assertEquals(RuntimeValues.decimal(100),retained.fields().stream().filter(field->field.name().equals("maxX")).findFirst().orElseThrow().value());
            }
        }
    }

    @Test void ownerThreadAndClosedLifecycleAreEnforced() {
        try(var runtime=AgentRuntime.builder().build()) {
            var bridge=new GameplayRuntimeBridge(runtime,StandardRuntimeProjections.registry(),GameplayLimits.defaults());
            runtime.start();
            try(var world=GameplayRuntimeBridgeTest.world(bridge)) {
                world.step();
                var value=visuals(world.snapshot().tick(),"player",100);
                var failure=assertThrows(java.util.concurrent.CompletionException.class,()->
                        java.util.concurrent.CompletableFuture.runAsync(()->bridge.refreshPresentationVisuals(value)).join());
                assertInstanceOf(GameplayException.class,failure.getCause());
                bridge.close();
                assertThrows(GameplayException.class,()->bridge.refreshPresentationVisuals(value));
            }
        }
    }

    @Test void refreshIsRejectedWhileARealWorldTickIsOpen() {
        try(var runtime=AgentRuntime.builder().build();
            var bridge=new GameplayRuntimeBridge(runtime,StandardRuntimeProjections.registry(),GameplayLimits.defaults())) {
            var builder=io.github.teemuki8.libgdx.agent.gameplay.core.world.GameWorld.builder(
                    GameplayLimits.defaults(),io.github.teemuki8.libgdx.agent.gameplay.core.component.StandardComponents.registry());
            bridge.systems().forEach(builder::system);
            builder.system(new io.github.teemuki8.libgdx.agent.gameplay.core.system.GameSystem(){
                @Override public io.github.teemuki8.libgdx.agent.gameplay.core.system.SystemDescriptor descriptor(){
                    return new io.github.teemuki8.libgdx.agent.gameplay.core.system.SystemDescriptor(SystemId.of("visuals"),
                            io.github.teemuki8.libgdx.agent.gameplay.core.system.SystemPhase.RENDER_PREP,10);
                }
                @Override public void update(io.github.teemuki8.libgdx.agent.gameplay.core.system.SystemContext context){
                    var empty=new WorldVisualSnapshot(context.tick(),List.of());
                    assertThrows(GameplayException.class,()->bridge.refreshPresentationVisuals(empty));
                    bridge.prepareVisuals(empty);
                }
            });
            runtime.start();
            try(var world=builder.build()){
                world.step();
                world.step();
            }
        }
    }

    private static WorldVisualSnapshot visuals(long tick,String entity,int right) {
        return new WorldVisualSnapshot(tick,List.of(new WorldVisualEntry(
                io.github.teemuki8.libgdx.agent.gameplay.core.value.EntityId.of(entity),"player","player",new Vec2(2,3),
                new Bounds2(1.5,2.5,2.5,3.5),Optional.of(new ScreenBounds(0,0,right,100)),new Vec2(.5,.5),0,
                true,true,"world",0,Optional.empty(),1,Optional.empty(),VisualEvidenceStatus.AVAILABLE)));
    }

    @Test void failedCaptureCannotBePromotedByAPresentationRefresh() {
        var fail=new java.util.concurrent.atomic.AtomicBoolean();
        var projections=RuntimeProjectionRegistry.builder();
        StandardRuntimeProjections.registry().projections().stream()
                .filter(value->!value.componentType().equals(io.github.teemuki8.libgdx.agent.gameplay.core.component.Health.TYPE))
                .forEach(projections::register);
        projections.register(new RuntimeProjection<io.github.teemuki8.libgdx.agent.gameplay.core.component.Health>() {
            @Override public io.github.teemuki8.libgdx.agent.gameplay.core.component.ComponentType<io.github.teemuki8.libgdx.agent.gameplay.core.component.Health> componentType(){
                return io.github.teemuki8.libgdx.agent.gameplay.core.component.Health.TYPE;
            }
            @Override public java.util.Map<String,RuntimeValue> project(
                    io.github.teemuki8.libgdx.agent.gameplay.core.world.EntitySnapshot entity,
                    io.github.teemuki8.libgdx.agent.gameplay.core.component.Health value){
                var values=new java.util.LinkedHashMap<String,RuntimeValue>();
                for(int index=0;index<(fail.get()?64:1);index++) values.put("health"+index,RuntimeValues.integer(value.current()));
                return values;
            }
        });
        var limits=new RuntimeLimits(10,100,100,32,10,10,64,4096,256,16,100);
        try(var runtime=AgentRuntime.builder().configuration(new RuntimeConfiguration(true,limits)).build();
            var bridge=new GameplayRuntimeBridge(runtime,projections.build(),GameplayLimits.defaults())) {
            bridge.validateCapacity(1,0);
            runtime.start();
            try(var world=GameplayRuntimeBridgeTest.world(bridge)) {
                world.step();
                long failedTick=world.tick();
                var token=bridge.lastFrameToken();
                fail.set(true);
                assertThrows(GameplayException.class,world::step);
                assertEquals(token,bridge.lastFrameToken());
                assertThrows(GameplayException.class,()->bridge.refreshPresentationVisuals(visuals(failedTick,"player",999)));
            }
        }
    }

    @Test void applicationVisualLimitRejectsExcessKnownEntitiesTransactionally() {
        var d=GameplayLimits.defaults();
        var limits=new GameplayLimits(d.maxEntities(),d.maxComponentsPerEntity(),d.maxSystems(),d.maxQueuedCommands(),
                d.maxPendingMutations(),d.maxEventsPerTick(),1,d.maxSnapshotBytes());
        try(var runtime=AgentRuntime.builder().build();
            var bridge=new GameplayRuntimeBridge(runtime,StandardRuntimeProjections.registry(),limits)) {
            var builder=io.github.teemuki8.libgdx.agent.gameplay.core.world.GameWorld.builder(limits,
                    io.github.teemuki8.libgdx.agent.gameplay.core.component.StandardComponents.registry());
            builder.initializer(sink->{for(String name:List.of("player","other")) sink.spawn(
                    io.github.teemuki8.libgdx.agent.gameplay.core.world.EntityDraft.builder(
                            io.github.teemuki8.libgdx.agent.gameplay.core.value.EntityId.of(name)).build());});
            bridge.systems().forEach(builder::system);
            builder.system(new io.github.teemuki8.libgdx.agent.gameplay.core.system.GameSystem(){
                @Override public io.github.teemuki8.libgdx.agent.gameplay.core.system.SystemDescriptor descriptor(){
                    return new io.github.teemuki8.libgdx.agent.gameplay.core.system.SystemDescriptor(SystemId.of("visuals"),
                            io.github.teemuki8.libgdx.agent.gameplay.core.system.SystemPhase.RENDER_PREP,10);
                }
                @Override public void update(io.github.teemuki8.libgdx.agent.gameplay.core.system.SystemContext context){
                    bridge.prepareVisuals(new WorldVisualSnapshot(context.tick(),List.of()));
                }
            });
            runtime.start();
            try(var world=builder.build()){
                world.step();
                long tick=world.snapshot().tick();
                bridge.refreshPresentationVisuals(visuals(tick,"player",100));
                var excess=new WorldVisualSnapshot(tick,List.of(visuals(tick,"player",999).entries().getFirst(),
                        visuals(tick,"other",999).entries().getFirst()));
                var failure=assertThrows(GameplayException.class,()->bridge.refreshPresentationVisuals(excess));
                assertEquals("visual entries <= 1",failure.diagnostic().expected());
                runtime.frame(16_666_667L,()->{});
                assertEquals(RuntimeValues.enumValue("UNAVAILABLE"),runtime.latestFrame().orElseThrow()
                        .entity(id("gameplay.visual.other")).orElseThrow().property("status").orElseThrow());
            }
        }
    }
    private static io.github.teemuki8.libgdx.agent.runtime.core.EntityId id(String value){
        return io.github.teemuki8.libgdx.agent.runtime.core.EntityId.of(value);
    }
}
