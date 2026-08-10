package io.github.teemuki8.libgdx.agent.gameplay.core.replay;

import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayDiagnosticCode;
import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayException;
import java.util.regex.Pattern;

/** SHA-256 identity of one canonical completed-tick record. */
public record WorldDigest(long tick, String sha256) {
    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");

    /** Validates tick and lowercase digest representation. */
    public WorldDigest {
        if (tick < 0 || sha256 == null || !SHA_256.matcher(sha256).matches()) {
            throw GameplayException.validation(
                    GameplayDiagnosticCode.UNSUPPORTED_CANONICAL_VALUE,
                    "create-world-digest",
                    "non-negative tick and lowercase 64-character SHA-256",
                    tick + ":" + String.valueOf(sha256),
                    "Use a digest produced by CanonicalWorldEncoder.");
        }
    }
}
