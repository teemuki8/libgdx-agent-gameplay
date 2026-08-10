package io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic;

import java.io.Serial;
import java.util.Objects;

/** Exception carrying bounded typed diagnostic evidence. */
public final class GameplayException extends IllegalArgumentException {
    @Serial
    private static final long serialVersionUID = 1L;

    private final GameplayDiagnostic diagnostic;

    /** Creates a failure from immutable diagnostic evidence. */
    public GameplayException(GameplayDiagnostic diagnostic) {
        super(messageFor(diagnostic));
        this.diagnostic = Objects.requireNonNull(diagnostic, "diagnostic");
    }

    /** Returns the complete bounded diagnostic. */
    public GameplayDiagnostic diagnostic() {
        return diagnostic;
    }

    /** Returns the stable machine-readable code. */
    public GameplayDiagnosticCode code() {
        return diagnostic.code();
    }

    /** Creates a concise validation failure. */
    public static GameplayException validation(
            GameplayDiagnosticCode code,
            String operation,
            String expected,
            String observed,
            String correction) {
        return new GameplayException(
                GameplayDiagnostic.simple(code, operation, expected, observed, correction));
    }

    private static String messageFor(GameplayDiagnostic diagnostic) {
        Objects.requireNonNull(diagnostic, "diagnostic");
        return diagnostic.code() + " during " + diagnostic.operation()
                + ": expected " + diagnostic.expected()
                + ", observed " + diagnostic.observed()
                + ". " + diagnostic.correction();
    }
}
