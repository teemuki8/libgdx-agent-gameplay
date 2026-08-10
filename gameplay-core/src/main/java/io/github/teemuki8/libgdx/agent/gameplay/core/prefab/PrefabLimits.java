package io.github.teemuki8.libgdx.agent.gameplay.core.prefab;

import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayDiagnosticCode;
import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayException;

/** Hard parse and validation limits for one V1 prefab document. */
public record PrefabLimits(
        int maxDocumentBytes,
        int maxDepth,
        int maxPrefabs,
        int maxComponentsPerPrefab,
        int maxStringLength,
        int maxArrayValues,
        int maxNumberLength,
        int maxDiagnostics,
        int maxCorrectionLength) {
    public static final int MAX_DOCUMENT_BYTES = 1024 * 1024;
    public static final int MAX_DEPTH = 32;
    public static final int MAX_PREFABS = 1_024;
    public static final int MAX_COMPONENTS_PER_PREFAB = 64;
    public static final int MAX_STRING_LENGTH = 256;
    public static final int MAX_ARRAY_VALUES = 256;
    public static final int MAX_NUMBER_LENGTH = 128;
    public static final int MAX_DIAGNOSTICS = 64;
    public static final int MAX_CORRECTION_LENGTH = 512;

    /** Validates positive values that do not exceed V1 maxima. */
    public PrefabLimits {
        within("maxDocumentBytes", maxDocumentBytes, MAX_DOCUMENT_BYTES);
        within("maxDepth", maxDepth, MAX_DEPTH);
        within("maxPrefabs", maxPrefabs, MAX_PREFABS);
        within("maxComponentsPerPrefab", maxComponentsPerPrefab,
                MAX_COMPONENTS_PER_PREFAB);
        within("maxStringLength", maxStringLength, MAX_STRING_LENGTH);
        within("maxArrayValues", maxArrayValues, MAX_ARRAY_VALUES);
        within("maxNumberLength", maxNumberLength, MAX_NUMBER_LENGTH);
        within("maxDiagnostics", maxDiagnostics, MAX_DIAGNOSTICS);
        within("maxCorrectionLength", maxCorrectionLength, MAX_CORRECTION_LENGTH);
    }

    /** Returns the fixed V1 defaults. */
    public static PrefabLimits defaults() {
        return new PrefabLimits(
                MAX_DOCUMENT_BYTES,
                MAX_DEPTH,
                MAX_PREFABS,
                MAX_COMPONENTS_PER_PREFAB,
                MAX_STRING_LENGTH,
                MAX_ARRAY_VALUES,
                MAX_NUMBER_LENGTH,
                MAX_DIAGNOSTICS,
                MAX_CORRECTION_LENGTH);
    }

    private static void within(String name, int value, int maximum) {
        if (value < 1 || value > maximum) {
            throw GameplayException.validation(
                    GameplayDiagnosticCode.LIMIT_OUT_OF_RANGE,
                    "configure-prefab-limits",
                    name + " in [1," + maximum + "]",
                    Integer.toString(value),
                    "Choose a positive value no greater than the V1 maximum.");
        }
    }
}
