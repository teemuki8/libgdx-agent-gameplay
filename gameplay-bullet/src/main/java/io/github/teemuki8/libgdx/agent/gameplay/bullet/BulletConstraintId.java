package io.github.teemuki8.libgdx.agent.gameplay.bullet;

import java.util.Objects;

/** Stable application-visible identity for a private native constraint. */
public record BulletConstraintId(String value) implements Comparable<BulletConstraintId> {
    /** Validates a bounded semantic identifier. */
    public BulletConstraintId {
        Objects.requireNonNull(value, "value");
        if (value.length() > 64 || !value.matches("[a-z0-9]+(?:-[a-z0-9]+)*")) {
            throw new IllegalArgumentException("invalid constraint ID");
        }
    }
    @Override public int compareTo(BulletConstraintId other) { return value.compareTo(other.value); }
}
