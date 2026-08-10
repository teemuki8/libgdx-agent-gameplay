package io.github.teemuki8.libgdx.agent.gameplay.core.component;

/** Bounded primitive-only canonical output used by explicit custom component codecs. */
public interface CanonicalComponentWriter {
    /** Writes one boolean. */
    void bool(boolean value);

    /** Writes one signed integer. */
    void integer(int value);

    /** Writes one signed long. */
    void longValue(long value);

    /** Writes one finite canonical IEEE-754 decimal. */
    void decimal(double value);

    /** Writes one length-prefixed UTF-8 string. */
    void text(String value);
}
