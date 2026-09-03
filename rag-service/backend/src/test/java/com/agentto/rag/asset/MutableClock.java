package com.agentto.rag.asset;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

final class MutableClock extends Clock {

    private Instant instant;
    private final ZoneId zone;

    MutableClock(Instant instant) {
        this.instant = instant;
        this.zone = ZoneOffset.UTC;
    }

    void advance(Duration duration) {
        instant = instant.plus(duration);
    }

    void set(Instant instant) {
        this.instant = instant;
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return Clock.fixed(instant, zone);
    }

    @Override
    public Instant instant() {
        return instant;
    }
}
