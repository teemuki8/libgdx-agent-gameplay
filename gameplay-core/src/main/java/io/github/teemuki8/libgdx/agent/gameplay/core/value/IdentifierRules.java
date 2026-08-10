package io.github.teemuki8.libgdx.agent.gameplay.core.value;

import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayDiagnosticCode;
import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayException;
import java.util.Objects;
import java.util.regex.Pattern;

/** Shared validation for bounded semantic identifiers and logical asset references. */
public final class IdentifierRules {
    public static final int MAX_LENGTH = 256;
    private static final Pattern IDENTIFIER = Pattern.compile(
            "[a-z][a-z0-9]*(?:[._-][a-z0-9]+)*");
    private static final Pattern LOGICAL_ASSET = Pattern.compile(
            "[A-Za-z0-9][A-Za-z0-9._/-]*");

    private IdentifierRules() {
    }

    /** Returns a validated semantic identifier. */
    public static String requireIdentifier(String value, String field) {
        Objects.requireNonNull(field, "field");
        if (value == null || value.length() > MAX_LENGTH || !IDENTIFIER.matcher(value).matches()) {
            throw GameplayException.validation(
                    GameplayDiagnosticCode.INVALID_IDENTIFIER,
                    "validate-identifier",
                    field + " matching " + IDENTIFIER.pattern() + " with at most 256 characters",
                    boundedObserved(value),
                    "Use a lower-case semantic ID such as enemy-primary or projectile-0001.");
        }
        return value;
    }

    /** Returns a validated logical asset or atlas-region reference. */
    public static String requireLogicalAsset(String value, String field) {
        Objects.requireNonNull(field, "field");
        boolean traversal = value != null
                && (value.contains("../") || value.contains("/..") || value.startsWith("/"));
        if (value == null || value.length() > MAX_LENGTH || traversal
                || !LOGICAL_ASSET.matcher(value).matches()) {
            throw GameplayException.validation(
                    GameplayDiagnosticCode.INVALID_IDENTIFIER,
                    "validate-logical-asset",
                    field + " as a bounded logical ID without traversal",
                    boundedObserved(value),
                    "Use a bundled logical reference such as enemies/goblin or enemy-idle.");
        }
        return value;
    }

    private static String boundedObserved(String value) {
        if (value == null) {
            return "null";
        }
        return value.length() <= MAX_LENGTH ? value : value.substring(0, MAX_LENGTH);
    }
}
