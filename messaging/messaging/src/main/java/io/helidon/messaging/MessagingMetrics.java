/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.messaging;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

import io.helidon.metrics.api.Clock;
import io.helidon.metrics.api.Counter;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.MetricsFactory;
import io.helidon.metrics.api.Tag;
import io.helidon.metrics.api.Timer;
import io.helidon.service.registry.ServiceRegistry;

import static java.lang.System.Logger.Level.WARNING;

final class MessagingMetrics {
    private static final String DURATION = "messaging.processing.duration";
    private static final String FAILURES = "messaging.processing.failures";
    private static final String ORIGIN = MessagingGraph.class.getName();
    private static final System.Logger LOGGER = System.getLogger(MessagingMetrics.class.getName());
    private static final ThreadLocal<Set<MeterRegistry>> REGISTERING =
            ThreadLocal.withInitial(() -> Collections.newSetFromMap(new IdentityHashMap<>()));
    private static final Recorder NOOP_RECORDER = new Recorder(null, null);
    private static final MessagingMetrics NOOP = new MessagingMetrics(null, null);

    private final MeterRegistry registry;
    private final FailureReporter failures;
    private final ReentrantLock lock = new ReentrantLock();
    private final List<Recorder> recorders = new ArrayList<>();
    private volatile boolean closed;

    private MessagingMetrics(MeterRegistry registry, FailureReporter failures) {
        this.registry = registry;
        this.failures = failures;
    }

    static MessagingMetrics create(MessagingConfig config, ServiceRegistry registry) {
        if (!config.metrics().enabled()) {
            return NOOP;
        }
        FailureReporter failures = new FailureReporter();
        try {
            return registry.first(MeterRegistry.class)
                    .map(meterRegistry -> new MessagingMetrics(meterRegistry, failures))
                    .orElse(NOOP);
        } catch (RuntimeException e) {
            failures.report(e);
            return NOOP;
        }
    }

    Recorder channel(String channel) {
        if (registry == null || closed) {
            return NOOP_RECORDER;
        }
        Set<MeterRegistry> registering = REGISTERING.get();
        if (!registering.add(registry)) {
            failures.report(new IllegalStateException("Reentrant messaging metrics registration "
                                                             + "from a metrics provider callback"));
            return NOOP_RECORDER;
        }
        try {
            // Provider callbacks may close this graph; never invoke them while holding the graph's metrics lock.
            Registration registration = register(channel);
            if (registration == null) {
                return NOOP_RECORDER;
            }
            lock.lock();
            try {
                if (closed) {
                    return NOOP_RECORDER;
                }
                Recorder recorder = new Recorder(registration, failures);
                recorders.add(recorder);
                return recorder;
            } finally {
                lock.unlock();
            }
        } catch (RuntimeException e) {
            failures.report(e);
            return NOOP_RECORDER;
        } finally {
            registering.remove(registry);
            if (registering.isEmpty()) {
                REGISTERING.remove();
            }
        }
    }

    void close() {
        if (registry == null) {
            return;
        }
        lock.lock();
        try {
            closed = true;
            recorders.forEach(Recorder::close);
            recorders.clear();
        } finally {
            lock.unlock();
        }
    }

    private Registration register(String channel) {
        Map<String, String> tags = Map.of("channel", channel);
        boolean timed = registry.isMeterEnabled(DURATION) && registry.isMeterEnabled(DURATION, tags);
        boolean counted = registry.isMeterEnabled(FAILURES) && registry.isMeterEnabled(FAILURES, tags);
        if (!timed && !counted) {
            return null;
        }
        MetricsFactory factory = registry.metricsFactory();
        List<Tag> meterTags = List.of(factory.tagCreate("channel", channel));
        Clock clock = timed ? registry.clock() : null;
        Timer timer = timed
                ? registry.getOrCreate(factory.timerBuilder(DURATION)
                        .tags(meterTags)
                        .description("Duration of completed channel batch processing attempts")
                        .origin(ORIGIN))
                : null;
        Counter counter = counted
                ? registry.getOrCreate(factory.counterBuilder(FAILURES)
                        .tags(meterTags)
                        .description("Failed channel batch processing attempts")
                        .origin(ORIGIN))
                : null;
        return new Registration(clock, timer, counter);
    }

    static final class Recorder {
        private final FailureReporter failures;
        // Published once, then only cleared on close or failure. Provider calls use a local snapshot.
        private volatile Registration registration;

        private Recorder(Registration registration, FailureReporter failures) {
            this.registration = registration;
            this.failures = failures;
        }

        long start() {
            Registration current = registration;
            if (current == null || current.timer == null) {
                return 0;
            }
            try {
                return current.clock.monotonicTime();
            } catch (RuntimeException e) {
                disable(e);
                return 0;
            }
        }

        void finish(long started, boolean failed) {
            Registration current = registration;
            if (current == null) {
                return;
            }
            try {
                if (current.timer != null) {
                    current.timer.record(current.clock.monotonicTime() - started, TimeUnit.NANOSECONDS);
                }
                if (failed && current.counter != null) {
                    current.counter.increment();
                }
            } catch (RuntimeException e) {
                disable(e);
            }
        }

        private void disable(RuntimeException failure) {
            registration = null;
            failures.report(failure);
        }

        private void close() {
            registration = null;
        }
    }

    private static final class FailureReporter {
        private final AtomicBoolean reported = new AtomicBoolean();

        private void report(RuntimeException failure) {
            if (reported.compareAndSet(false, true)) {
                try {
                    LOGGER.log(WARNING,
                               "Messaging metrics failed; further metrics failures for this graph are suppressed",
                               failure);
                } catch (RuntimeException _) {
                    // Logging must not replace a messaging result either.
                }
            }
        }
    }

    private static final class Registration {
        private final Clock clock;
        private final Timer timer;
        private final Counter counter;

        private Registration(Clock clock, Timer timer, Counter counter) {
            this.clock = clock;
            this.timer = timer;
            this.counter = counter;
        }
    }
}
