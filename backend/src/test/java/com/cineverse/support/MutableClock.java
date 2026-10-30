package com.cineverse.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** A clock tests can move forward to exercise lock expiry and cancellation windows. */
public class MutableClock extends Clock {
    private volatile Instant now = Instant.now();

    public void advance(Duration d) { now = now.plus(d); }

    public void reset() { now = Instant.now(); }

    @Override public ZoneId getZone() { return ZoneOffset.UTC; }

    @Override public Clock withZone(ZoneId zone) { return Clock.fixed(now, zone); }

    @Override public Instant instant() { return now; }
}
