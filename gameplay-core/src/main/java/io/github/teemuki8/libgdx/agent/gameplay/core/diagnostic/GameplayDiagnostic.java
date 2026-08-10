package io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Immutable bounded evidence accompanying a gameplay failure. */
public record GameplayDiagnostic(
        GameplayDiagnosticCode code,
        boolean retryable,
        String operation,
        Map<String, String> location,
        String expected,
        String observed,
        String correction,
        String correlationId) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    private static final int MAX_FIELD_LENGTH = 512;
    private static final int MAX_LOCATION_ENTRIES = 16;

    /** Validates and defensively copies the diagnostic evidence. */
    public GameplayDiagnostic {
        Objects.requireNonNull(code, "code");
        operation = bounded(operation, "operation");
        expected = bounded(expected, "expected");
        observed = bounded(observed, "observed");
        correction = bounded(correction, "correction");
        correlationId = boundedNullable(correlationId, "correlationId");
        Objects.requireNonNull(location, "location");
        if (location.size() > MAX_LOCATION_ENTRIES) {
            throw new IllegalArgumentException("diagnostic location exceeds 16 entries");
        }
        TreeMap<String, String> copy = new TreeMap<>();
        location.forEach((key, value) -> copy.put(
                bounded(key, "location key"), bounded(value, "location value")));
        location = Collections.unmodifiableMap(copy);
    }

    /** Creates a diagnostic without a location or correlation. */
    public static GameplayDiagnostic simple(
            GameplayDiagnosticCode code,
            String operation,
            String expected,
            String observed,
            String correction) {
        return new GameplayDiagnostic(
                code, false, operation, Map.of(), expected, observed, correction, null);
    }

    private static String bounded(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.length() > MAX_FIELD_LENGTH) {
            throw new IllegalArgumentException(field + " exceeds 512 characters");
        }
        return value;
    }

    private static String boundedNullable(String value, String field) {
        return value == null ? null : bounded(value, field);
    }
}
