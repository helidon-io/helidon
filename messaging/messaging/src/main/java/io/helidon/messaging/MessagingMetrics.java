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

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

import io.helidon.metrics.api.Clock;
import io.helidon.metrics.api.Counter;
import io.helidon.metrics.api.Meter;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.MetricsFactory;
import io.helidon.metrics.api.Tag;
import io.helidon.metrics.api.Timer;
import io.helidon.service.registry.GlobalServiceRegistry;

import static java.lang.System.Logger.Level.WARNING;

final class MessagingMetrics {
    private static final String DURATION = "messaging.processing.duration";
    private static final String FAILURES = "messaging.processing.failures";
    private static final String ORIGIN = MessagingGraph.class.getName();
    private static final System.Logger LOGGER = System.getLogger(MessagingMetrics.class.getName());
    private static final ReentrantLock REGISTRATIONS_LOCK = new ReentrantLock();
    private static final Map<MeterRegistry, Map<String, Registration>> REGISTRATIONS = new IdentityHashMap<>();
    private static final Recorder NOOP_RECORDER = new Recorder(null, null);
    private static final MessagingMetrics NOOP = new MessagingMetrics(null, null);

    private final MeterRegistry registry;
    private final FailureReporter failures;
    private final ReentrantLock lock = new ReentrantLock();
    private final Map<String, Recorder> recorders = new HashMap<>();
    private boolean closed;

    private MessagingMetrics(MeterRegistry registry, FailureReporter failures) {
        this.registry = registry;
        this.failures = failures;
    }

    static MessagingMetrics create(MessagingConfig config) {
        if (!config.enableMetrics()) {
            return NOOP;
        }
        FailureReporter failures = new FailureReporter();
        try {
            var registry = config.serviceRegistry().orElseGet(GlobalServiceRegistry::registry);
            return registry.first(MeterRegistry.class)
                    .map(meterRegistry -> new MessagingMetrics(meterRegistry, failures))
                    .orElse(NOOP);
        } catch (RuntimeException e) {
            failures.report(e);
            return NOOP;
        }
    }

    Recorder channel(String channel) {
        if (registry == null) {
            return NOOP_RECORDER;
        }
        lock.lock();
        try {
            if (closed) {
                return NOOP_RECORDER;
            }
            return recorders.computeIfAbsent(channel, this::acquire);
        } finally {
            lock.unlock();
        }
    }

    void close() {
        if (registry == null) {
            return;
        }
        List<Recorder> retiring;
        lock.lock();
        try {
            if (closed) {
                return;
            }
            closed = true;
            retiring = List.copyOf(recorders.values());
            recorders.clear();
        } finally {
            lock.unlock();
        }
        retiring.forEach(Recorder::close);
    }

    private Recorder acquire(String channel) {
        // Registry listeners run synchronously inside registration and removal. A nested acquisition cannot wait
        // for its enclosing operation, and must not share a partially registered or retiring set of meters.
        if (REGISTRATIONS_LOCK.isHeldByCurrentThread()) {
            failures.report(new IllegalStateException("Reentrant messaging metrics registration "
                                                             + "from a metrics provider callback"));
            return NOOP_RECORDER;
        }
        REGISTRATIONS_LOCK.lock();
        try {
            Map<String, Registration> channels = REGISTRATIONS.get(registry);
            Registration registration = channels == null ? null : channels.get(channel);
            if (registration == null) {
                registration = Registration.create(registry, channel, failures);
                if (registration == null) {
                    return NOOP_RECORDER;
                }
                // A provider listener can close another graph and remove the map observed before registration.
                channels = REGISTRATIONS.computeIfAbsent(registry, _ -> new HashMap<>());
                channels.put(channel, registration);
            }
            registration.owners++;
            return new Recorder(registration, failures);
        } catch (RuntimeException e) {
            failures.report(e);
            return NOOP_RECORDER;
        } finally {
            REGISTRATIONS_LOCK.unlock();
        }
    }

    static final class Recorder {
        private final Registration registration;
        private final FailureReporter failures;
        private final ReentrantLock lock = new ReentrantLock();
        private boolean retired;
        private boolean disabled;

        private Recorder(Registration registration, FailureReporter failures) {
            this.registration = registration;
            this.failures = failures;
        }

        long start() {
            if (registration == null || registration.timer == null) {
                return 0;
            }
            lock.lock();
            try {
                if (retired || disabled) {
                    return 0;
                }
                return registration.clock.monotonicTime();
            } catch (RuntimeException e) {
                disabled = true;
                failures.report(e);
                return 0;
            } finally {
                lock.unlock();
            }
        }

        void finish(long started, boolean failed) {
            if (registration == null) {
                return;
            }
            lock.lock();
            try {
                if (retired || disabled) {
                    return;
                }
                if (registration.timer != null) {
                    registration.timer.record(registration.clock.monotonicTime() - started, TimeUnit.NANOSECONDS);
                }
                if (failed && registration.counter != null) {
                    registration.counter.increment();
                }
            } catch (RuntimeException e) {
                disabled = true;
                failures.report(e);
            } finally {
                lock.unlock();
            }
        }

        private void close() {
            if (registration == null) {
                return;
            }
            lock.lock();
            try {
                if (retired) {
                    return;
                }
                retired = true;
                registration.release(failures);
            } finally {
                lock.unlock();
            }
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
        private final MeterRegistry registry;
        private final String channel;
        private final Clock clock;
        private final Timer timer;
        private final Counter counter;
        private int owners;

        private Registration(MeterRegistry registry, String channel, Clock clock, Timer timer, Counter counter) {
            this.registry = registry;
            this.channel = channel;
            this.clock = clock;
            this.timer = timer;
            this.counter = counter;
        }

        private static Registration create(MeterRegistry registry, String channel, FailureReporter failures) {
            Map<String, String> tags = Map.of("channel", channel);
            boolean timed = registry.isMeterEnabled(DURATION) && registry.isMeterEnabled(DURATION, tags);
            boolean counted = registry.isMeterEnabled(FAILURES) && registry.isMeterEnabled(FAILURES, tags);
            if (!timed && !counted) {
                return null;
            }
            var factory = registry.metricsFactory();
            List<Tag> meterTags = List.of(factory.tagCreate("channel", channel));
            Clock clock = timed ? registry.clock() : null;
            Timer timer = null;
            try {
                if (timed) {
                    timer = register(registry,
                                     factory,
                                     factory.timerBuilder(DURATION)
                                             .tags(meterTags)
                                             .description("Duration of completed channel batch processing attempts")
                                             .origin(ORIGIN),
                                     failures);
                }
                Counter counter = counted
                        ? register(registry,
                                   factory,
                                   factory.counterBuilder(FAILURES)
                                           .tags(meterTags)
                                           .description("Failed channel batch processing attempts")
                                           .origin(ORIGIN),
                                   failures)
                        : null;
                return new Registration(registry, channel, clock, timer, counter);
            } catch (RuntimeException e) {
                remove(registry, timer, failures);
                throw e;
            }
        }

        private static <B extends Meter.Builder<B, M>, M extends Meter> M register(MeterRegistry registry,
                                                                                  MetricsFactory factory,
                                                                                  B builder,
                                                                                  FailureReporter failures) {
            try {
                return registry.getOrCreate(builder);
            } catch (RuntimeException e) {
                // A synchronous add-listener can throw after registration but before returning the meter handle.
                // Use the attempted builder's final tags, including any mutations made by factory customizers.
                try {
                    List<Tag> tags = builder.tags().entrySet().stream()
                            .map(entry -> factory.tagCreate(entry.getKey(), entry.getValue()))
                            .toList();
                    registry.remove(builder.name(), tags);
                } catch (RuntimeException cleanupFailure) {
                    failures.report(cleanupFailure);
                }
                throw e;
            }
        }

        private static void remove(MeterRegistry registry, Meter meter, FailureReporter failures) {
            if (meter != null) {
                try {
                    registry.remove(meter);
                } catch (RuntimeException e) {
                    failures.report(e);
                }
            }
        }

        private void release(FailureReporter failures) {
            REGISTRATIONS_LOCK.lock();
            try {
                if (--owners != 0) {
                    return;
                }
                // Removal is serialized with acquisition so a replacement cannot inherit a retiring series.
                Map<String, Registration> channels = REGISTRATIONS.get(registry);
                channels.remove(channel);
                if (channels.isEmpty()) {
                    REGISTRATIONS.remove(registry);
                }
                remove(registry, timer, failures);
                remove(registry, counter, failures);
            } finally {
                REGISTRATIONS_LOCK.unlock();
            }
        }
    }
}
