package io.github.teemuki8.libgdx.agent.gameplay.box2d;

import io.github.teemuki8.libgdx.agent.gameplay.core.value.IdentifierRules;

/** Stable semantic identity for a bridge-owned Box2D joint. */
public record Box2dJointId(String value) implements Comparable<Box2dJointId> {
    /** Validates the bounded semantic ID. */
    public Box2dJointId {
        value = IdentifierRules.requireIdentifier(value, "box2dJointId");
    }

    /** Creates a validated joint ID. */
    public static Box2dJointId of(String value) {
        return new Box2dJointId(value);
    }

    @Override
    public int compareTo(Box2dJointId other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value;
    }
}
