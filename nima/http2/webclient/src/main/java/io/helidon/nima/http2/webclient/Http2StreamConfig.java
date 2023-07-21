package io.helidon.nima.http2.webclient;

import java.time.Duration;

interface Http2StreamConfig {
    boolean priorKnowledge();

    int priority();

    Duration timeout();
}
