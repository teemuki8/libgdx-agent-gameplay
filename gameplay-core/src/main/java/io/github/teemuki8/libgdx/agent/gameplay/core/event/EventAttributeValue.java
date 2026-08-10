package io.github.teemuki8.libgdx.agent.gameplay.core.event;

import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayDiagnosticCode;
import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayException;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.EntityId;
import java.util.Objects;

/** Closed GL-free scalar values allowed in bounded event attributes. */
public sealed interface EventAttributeValue permits
        EventAttributeValue.StringValue,
        EventAttributeValue.IntegerValue,
        EventAttributeValue.DecimalValue,
        EventAttributeValue.BooleanValue,
        EventAttributeValue.EntityValue {
    /** Creates a bounded string attribute. */
    static StringValue string(String value) {
        return new StringValue(value);
    }

    /** Creates an integer attribute. */
    static IntegerValue integer(long value) {
        return new IntegerValue(value);
    }

    /** Creates a finite decimal attribute. */
    static DecimalValue decimal(double value) {
        return new DecimalValue(value);
    }

    /** Creates a boolean attribute. */
    static BooleanValue bool(boolean value) {
        return new BooleanValue(value);
    }

    /** Creates an entity-reference attribute. */
    static EntityValue entity(EntityId value) {
        return new EntityValue(value);
    }

    /** Bounded UTF-16 string value. */
    record StringValue(String value) implements EventAttributeValue {
        /** Validates the bounded string. */
        public StringValue {
            if (value == null || value.length() > 256) {
                throw GameplayException.validation(
                        GameplayDiagnosticCode.INVALID_COMPONENT_VALUE,
                        "create-event-attribute",
                        "string with at most 256 characters",
                        value == null ? "null" : value.substring(0, 256),
                        "Use a concise semantic event attribute.");
            }
        }
    }

    /** Signed integral value. */
    record IntegerValue(long value) implements EventAttributeValue {
    }

    /** Finite decimal value. */
    record DecimalValue(double value) implements EventAttributeValue {
        /** Rejects NaN and infinity. */
        public DecimalValue {
            if (!Double.isFinite(value)) {
                throw GameplayException.validation(
                        GameplayDiagnosticCode.INVALID_COMPONENT_VALUE,
                        "create-event-attribute",
                        "finite decimal",
                        Double.toString(value),
                        "Use a finite decimal event value.");
            }
        }
    }

    /** Boolean value. */
    record BooleanValue(boolean value) implements EventAttributeValue {
    }

    /** Stable entity-reference value. */
    record EntityValue(EntityId value) implements EventAttributeValue {
        /** Rejects a null entity reference. */
        public EntityValue {
            Objects.requireNonNull(value, "value");
        }
    }
}
