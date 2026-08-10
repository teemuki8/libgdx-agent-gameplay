package io.github.teemuki8.libgdx.agent.gameplay.core.prefab;

import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayDiagnosticCode;
import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayException;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.Rgba;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.Vec2;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/** Typed bounded access to one component object's already parsed fields. */
public final class ComponentFields {
    private final Map<String, PrefabValue> values;
    private final String pointer;

    ComponentFields(Map<String, PrefabValue> values, String pointer) {
        this.values = Map.copyOf(values);
        this.pointer = pointer;
    }

    /** Fails on every field not explicitly accepted by the codec. */
    public void requireOnly(Set<String> accepted) {
        Set<String> ordered = new TreeSet<>(accepted);
        for (Map.Entry<String, PrefabValue> entry : values.entrySet()) {
            if (!ordered.contains(entry.getKey())) {
                String correction = nearest(entry.getKey(), ordered);
                throw failure(
                        GameplayDiagnosticCode.UNKNOWN_PREFAB_FIELD,
                        entry.getValue(),
                        "one of " + ordered,
                        entry.getKey(),
                        correction.isEmpty()
                                ? "Remove the unknown field or consult the component schema."
                                : "Did you mean '" + correction + "'? Use only declared fields.");
            }
        }
    }

    /** Returns a required string field. */
    public String requireString(String name) {
        PrefabValue value = require(name);
        if (value instanceof PrefabValue.StringValue string) {
            return string.value();
        }
        throw typeFailure(name, value, "string");
    }

    /** Returns an optional string field or the supplied default. */
    public String optionalString(String name, String defaultValue) {
        return values.containsKey(name) ? requireString(name) : defaultValue;
    }

    /** Returns a required exact long field. */
    public long requireLong(String name) {
        PrefabValue value = require(name);
        if (value instanceof PrefabValue.NumberValue number) {
            try {
                return number.value().longValueExact();
            } catch (ArithmeticException failure) {
                throw typeFailure(name, value, "exact integral number");
            }
        }
        throw typeFailure(name, value, "integral number");
    }

    /** Returns an optional exact long field. */
    public long optionalLong(String name, long defaultValue) {
        return values.containsKey(name) ? requireLong(name) : defaultValue;
    }

    /** Returns a required finite double field. */
    public double requireDouble(String name) {
        PrefabValue value = require(name);
        if (value instanceof PrefabValue.NumberValue number) {
            double result = number.value().doubleValue();
            if (Double.isFinite(result)) {
                return result;
            }
        }
        throw typeFailure(name, value, "finite decimal number");
    }

    /** Returns an optional finite double field. */
    public double optionalDouble(String name, double defaultValue) {
        return values.containsKey(name) ? requireDouble(name) : defaultValue;
    }

    /** Returns a required boolean field. */
    public boolean requireBoolean(String name) {
        PrefabValue value = require(name);
        if (value instanceof PrefabValue.BooleanValue bool) {
            return bool.value();
        }
        throw typeFailure(name, value, "boolean");
    }

    /** Returns an optional boolean field. */
    public boolean optionalBoolean(String name, boolean defaultValue) {
        return values.containsKey(name) ? requireBoolean(name) : defaultValue;
    }

    /** Returns a required two-number vector. */
    public Vec2 requireVec2(String name) {
        List<PrefabValue> entries = requireArray(name, 2);
        return new Vec2(number(name, entries.get(0)), number(name, entries.get(1)));
    }

    /** Returns an optional two-number vector. */
    public Vec2 optionalVec2(String name, Vec2 defaultValue) {
        return values.containsKey(name) ? requireVec2(name) : defaultValue;
    }

    /** Returns a required four-number normalized color. */
    public Rgba requireRgba(String name) {
        List<PrefabValue> entries = requireArray(name, 4);
        return new Rgba(
                number(name, entries.get(0)),
                number(name, entries.get(1)),
                number(name, entries.get(2)),
                number(name, entries.get(3)));
    }

    /** Returns an optional normalized color. */
    public Rgba optionalRgba(String name, Rgba defaultValue) {
        return values.containsKey(name) ? requireRgba(name) : defaultValue;
    }

    /** Returns a required list of object values. */
    public List<PrefabValue.ObjectValue> requireObjects(String name) {
        PrefabValue value = require(name);
        if (!(value instanceof PrefabValue.ArrayValue array)) {
            throw typeFailure(name, value, "array of objects");
        }
        return array.values().stream().map(entry -> {
            if (entry instanceof PrefabValue.ObjectValue object) {
                return object;
            }
            throw typeFailure(name, entry, "object");
        }).toList();
    }

    /** Returns a required list of strings. */
    public List<String> requireStrings(String name) {
        PrefabValue value = require(name);
        if (!(value instanceof PrefabValue.ArrayValue array)) {
            throw typeFailure(name, value, "array of strings");
        }
        return array.values().stream().map(entry -> {
            if (entry instanceof PrefabValue.StringValue string) {
                return string.value();
            }
            throw typeFailure(name, entry, "string");
        }).toList();
    }

    /** Returns whether a field was declared. */
    public boolean contains(String name) {
        return values.containsKey(name);
    }

    /** Creates typed access for a nested object. */
    public static ComponentFields nested(PrefabValue.ObjectValue object) {
        return new ComponentFields(object.values(), object.pointer());
    }

    private PrefabValue require(String name) {
        PrefabValue value = values.get(name);
        if (value == null) {
            throw GameplayException.located(
                    GameplayDiagnosticCode.MISSING_PREFAB_FIELD,
                    "decode-prefab-component",
                    Map.of("jsonPointer", pointer),
                    "required field '" + name + "'",
                    "missing",
                    "Add the required '" + name + "' field with the documented type.");
        }
        return value;
    }

    private List<PrefabValue> requireArray(String name, int size) {
        PrefabValue value = require(name);
        if (value instanceof PrefabValue.ArrayValue array && array.values().size() == size) {
            return array.values();
        }
        throw typeFailure(name, value, "array of exactly " + size + " values");
    }

    private double number(String name, PrefabValue value) {
        if (value instanceof PrefabValue.NumberValue number) {
            double result = number.value().doubleValue();
            if (Double.isFinite(result)) {
                return result;
            }
        }
        throw typeFailure(name, value, "finite number");
    }

    private GameplayException typeFailure(String name, PrefabValue value, String expected) {
        return failure(GameplayDiagnosticCode.INVALID_PREFAB_VALUE, value,
                expected + " for '" + name + "'", describe(value),
                "Use the exact JSON type and bounded value documented by the component schema.");
    }

    private static GameplayException failure(
            GameplayDiagnosticCode code,
            PrefabValue value,
            String expected,
            String observed,
            String correction) {
        return GameplayException.located(
                code,
                "decode-prefab-component",
                Map.of(
                        "jsonPointer", value.pointer(),
                        "line", Long.toString(value.line()),
                        "column", Long.toString(value.column())),
                expected,
                observed,
                correction);
    }

    private static String describe(PrefabValue value) {
        if (value instanceof PrefabValue.NumberValue number) {
            return number.token();
        }
        if (value instanceof PrefabValue.StringValue string) {
            return string.value();
        }
        return value.getClass().getSimpleName();
    }

    private static String nearest(String unknown, Set<String> candidates) {
        Objects.requireNonNull(unknown, "unknown");
        String best = "";
        int distance = Integer.MAX_VALUE;
        for (String candidate : candidates) {
            int current = editDistance(unknown, candidate);
            if (current < distance) {
                best = candidate;
                distance = current;
            }
        }
        return distance <= 2 ? best : "";
    }

    private static int editDistance(String left, String right) {
        int[][] distance = new int[left.length() + 1][right.length() + 1];
        for (int index = 0; index <= left.length(); index++) {
            distance[index][0] = index;
        }
        for (int index = 0; index <= right.length(); index++) {
            distance[0][index] = index;
        }
        for (int leftIndex = 1; leftIndex <= left.length(); leftIndex++) {
            for (int rightIndex = 1; rightIndex <= right.length(); rightIndex++) {
                int substitution = left.charAt(leftIndex - 1) == right.charAt(rightIndex - 1)
                        ? 0 : 1;
                distance[leftIndex][rightIndex] = Math.min(
                        Math.min(distance[leftIndex - 1][rightIndex] + 1,
                                distance[leftIndex][rightIndex - 1] + 1),
                        distance[leftIndex - 1][rightIndex - 1] + substitution);
            }
        }
        return distance[left.length()][right.length()];
    }
}
