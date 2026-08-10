package io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic;

/** Stable machine-readable failure categories exposed by gameplay APIs. */
public enum GameplayDiagnosticCode {
    INVALID_IDENTIFIER,
    INVALID_COMPONENT_VALUE,
    DUPLICATE_COMPONENT_TYPE,
    UNKNOWN_COMPONENT_TYPE,
    LIMIT_OUT_OF_RANGE,
    DUPLICATE_COMMAND_SEQUENCE,
    NON_MONOTONIC_COMMAND_SEQUENCE,
    LATE_COMMAND,
    COMMAND_WINDOW_EXCEEDED,
    COMMAND_LIMIT_EXCEEDED,
    EVENT_TICK_ALREADY_OPEN,
    EVENT_TICK_NOT_OPEN,
    EVENT_LIMIT_EXCEEDED
}
