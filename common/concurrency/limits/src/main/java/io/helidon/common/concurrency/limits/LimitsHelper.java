/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.helidon.common.concurrency.limits;

import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import io.helidon.metrics.api.Timer;
import io.helidon.tracing.Span;
import io.helidon.tracing.Tracer;

class LimitsHelper {

    private final LimitHandlers.LimiterHandler handler;
    private final Supplier<Long> clock;
    private final int queueLength;
    private final Timer queueWaitTimer;
    private final AtomicInteger rejectedRequests;
    private final Tracer tracer;

    LimitsHelper(LimitHandlers.LimiterHandler handler,
                 Supplier<Long> clock,
                 int queueLength,
                 Timer queueWaitTimer,
                 AtomicInteger rejectedRequests,
                 Tracer tracer) {
        this.handler = handler;
        this.clock = clock;
        this.queueLength = queueLength;
        this.queueWaitTimer = queueWaitTimer;
        this.rejectedRequests = rejectedRequests;
        this.tracer = tracer;
    }

    Optional<LimitAlgorithm.Token> tryAcquire(boolean wait) {
        var token = handler.tryAcquire(false);
        if (token.isPresent()) {
            return token;
        }
        if (wait && queueLength > 0) {
            long startWait = clock.get();
            var span = tracer != null ? tracer.spanBuilder("limits-wait").start() : null;
            var scope = span != null ? span.activate() : null;
            token = handler.tryAcquire(true);
            if (token.isPresent()) {
                if (queueWaitTimer != null) {
                    queueWaitTimer.record(clock.get() - startWait, TimeUnit.NANOSECONDS);
                }
                if (scope != null) {
                    scope.close();
                    span.end();
                }
                return token;
            }
            if (scope != null) {
                scope.close();
                span.status(Span.Status.ERROR);
                span.end();
            }
        }
        rejectedRequests.getAndIncrement();
        return token;
    }
}
