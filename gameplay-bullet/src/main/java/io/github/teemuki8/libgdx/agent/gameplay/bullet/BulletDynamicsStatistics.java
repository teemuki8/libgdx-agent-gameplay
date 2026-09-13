package io.github.teemuki8.libgdx.agent.gameplay.bullet;

/** Owner-thread resource and step counters without native identities. */
public record BulletDynamicsStatistics(int bodies, int constraints, long steps) { }
