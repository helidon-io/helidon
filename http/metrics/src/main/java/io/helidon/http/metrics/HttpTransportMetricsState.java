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

package io.helidon.http.metrics;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.StampedLock;
import java.util.function.Supplier;

import io.helidon.common.Wrapper;
import io.helidon.http.HttpTransportObserver.ConnectionObservation;
import io.helidon.http.HttpTransportObserver.ConnectionOutcome;
import io.helidon.http.HttpTransportObserver.Direction;
import io.helidon.http.HttpTransportObserver.Handshake;
import io.helidon.http.HttpTransportObserver.HandshakeObservation;
import io.helidon.http.HttpTransportObserver.HandshakeOutcome;
import io.helidon.http.HttpTransportObserver.Initiator;
import io.helidon.http.HttpTransportObserver.Role;
import io.helidon.http.HttpTransportObserver.StreamObservation;
import io.helidon.http.HttpTransportObserver.StreamOutcome;
import io.helidon.metrics.api.Clock;
import io.helidon.metrics.api.Counter;
import io.helidon.metrics.api.Meter;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.MetricsFactory;
import io.helidon.metrics.api.Tag;
import io.helidon.metrics.api.Timer;

import static io.helidon.http.metrics.HttpTransportMetrics.CONNECTIONS_ACTIVE;
import static io.helidon.http.metrics.HttpTransportMetrics.CONNECTIONS_CLOSED;
import static io.helidon.http.metrics.HttpTransportMetrics.CONNECTIONS_DURATION;
import static io.helidon.http.metrics.HttpTransportMetrics.CONNECTIONS_ESTABLISHED;
import static io.helidon.http.metrics.HttpTransportMetrics.CONNECTIONS_OPENED;
import static io.helidon.http.metrics.HttpTransportMetrics.HANDSHAKES;
import static io.helidon.http.metrics.HttpTransportMetrics.HANDSHAKES_DURATION;
import static io.helidon.http.metrics.HttpTransportMetrics.STREAMS_ACTIVE;
import static io.helidon.http.metrics.HttpTransportMetrics.STREAMS_CLOSED;
import static io.helidon.http.metrics.HttpTransportMetrics.STREAMS_DURATION;
import static io.helidon.http.metrics.HttpTransportMetrics.STREAMS_OPENED;
import static java.lang.System.Logger.Level.WARNING;

final class HttpTransportMetricsState {
    private static final String UNKNOWN_PROTOCOL = "unknown";
    private static final String VENDOR = Meter.Scope.VENDOR;
    private static final int MAX_METER_SERIES = 256;
    private static final int MAX_PROVIDER_ACTIONS = 1024;
    private static final int CLOSED_FLAG = Integer.MIN_VALUE;
    private static final int CONNECTION_COUNT_MASK = Integer.MAX_VALUE;
    private static final long INVALID_TIME = Long.MIN_VALUE;
    private static final int STREAM_OUTCOME_COUNT = StreamOutcome.values().length;
    private static final List<String> METER_NAMES = List.of(CONNECTIONS_OPENED,
                                                            CONNECTIONS_ESTABLISHED,
                                                            CONNECTIONS_ACTIVE,
                                                            CONNECTIONS_CLOSED,
                                                            CONNECTIONS_DURATION,
                                                            HANDSHAKES,
                                                            HANDSHAKES_DURATION,
                                                            STREAMS_OPENED,
                                                            STREAMS_ACTIVE,
                                                            STREAMS_CLOSED,
                                                            STREAMS_DURATION);
    private static final System.Logger LOGGER = System.getLogger(HttpTransportMetrics.class.getName());
    private static final ReferenceQueue<Object> COLLECTED_REGISTRIES = new ReferenceQueue<>();
    private static final Map<IdentityReference, NativeRegistryState> REGISTRIES = new HashMap<>();
    private static final ReentrantLock REGISTRIES_LOCK = new ReentrantLock();

    private HttpTransportMetricsState() {
    }

    static HttpTransportMetrics.Lease acquire(MeterRegistry registry) {
        Object registryIdentity = nativeIdentity(registry, "registry");
        MetricsFactory metricsFactory = Objects.requireNonNull(registry.metricsFactory(), "registry metrics factory");
        Clock clock = Objects.requireNonNull(registry.clock(), "registry clock");
        Acquisition acquisition;
        REGISTRIES_LOCK.lock();
        try {
            expungeCollectedRegistries();
            IdentityReference lookup = new IdentityReference(registryIdentity);
            NativeRegistryState state = REGISTRIES.get(lookup);
            if (state == null) {
                IdentityReference retained = new IdentityReference(registryIdentity, COLLECTED_REGISTRIES);
                state = new NativeRegistryState(retained);
                REGISTRIES.put(retained, state);
            } else if (state.activeRegistryIdentity != null && state.activeRegistryIdentity != registryIdentity) {
                throw new IllegalStateException("HTTP transport metrics registry identity changed");
            }
            state.activeRegistryIdentity = registryIdentity;
            acquisition = state.acquire(registry, metricsFactory, clock);
        } finally {
            REGISTRIES_LOCK.unlock();
        }
        acquisition.lease().recorder.initializeSelection();
        completeAsync(acquisition.transferredCompletions());
        return acquisition.lease();
    }

    private static void expungeCollectedRegistries() {
        IdentityReference collected;
        while ((collected = (IdentityReference) COLLECTED_REGISTRIES.poll()) != null) {
            REGISTRIES.remove(collected);
        }
    }

    private static Object nativeIdentity(Object source, String description) {
        Object current = Objects.requireNonNull(source, description);
        Set<Object> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        while (current instanceof Wrapper wrapper && seen.add(current)) {
            Object next;
            try {
                next = wrapper.unwrap(Object.class);
            } catch (UnsupportedOperationException _) {
                break;
            }
            next = Objects.requireNonNull(next, description + " delegate");
            if (next == current) {
                break;
            }
            if (seen.contains(next)) {
                throw new IllegalArgumentException(description + " wrapper cycle");
            }
            current = next;
        }
        return current;
    }

    private static void completeAsync(List<CompletableFuture<Void>> completions) {
        if (completions.isEmpty()) {
            return;
        }
        Runnable action = () -> completions.forEach(completion -> completion.complete(null));
        try {
            Thread.ofVirtual()
                    .name("helidon-http-transport-metrics-completion")
                    .inheritInheritableThreadLocals(false)
                    .start(action);
        } catch (RuntimeException _) {
            try {
                Thread.ofPlatform()
                        .daemon(true)
                        .name("helidon-http-transport-metrics-completion-fallback")
                        .inheritInheritableThreadLocals(false)
                        .start(action);
            } catch (RuntimeException _) {
                try {
                    CompletableFuture.runAsync(action);
                } catch (RuntimeException _) {
                    action.run();
                }
            }
        }
    }

    private static void requireIdentifier(String identifier, String description) {
        Objects.requireNonNull(identifier, description);
        if (identifier.isBlank()) {
            throw new IllegalArgumentException(description + " must not be blank");
        }
    }

    private static String tagValue(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    private static String tagValue(Direction value) {
        return switch (value) {
            case BIDIRECTIONAL -> "bidi";
            case UNIDIRECTIONAL -> "uni";
        };
    }

    private static TagValue tag(String key, Enum<?> value) {
        return new TagValue(key, tagValue(value));
    }

    private static TagValue tag(String key, Direction value) {
        return new TagValue(key, tagValue(value));
    }

    private static TagValue tag(String key, String value) {
        return new TagValue(key, value);
    }

    private static MetricId counter(String name, String description, TagValue... tags) {
        return new MetricId(name, Meter.Type.COUNTER, description, List.of(tags));
    }

    private static MetricId gauge(String name, String description, TagValue... tags) {
        return new MetricId(name, Meter.Type.GAUGE, description, List.of(tags));
    }

    private static MetricId timer(String name, String description, TagValue... tags) {
        return new MetricId(name, Meter.Type.TIMER, description, List.of(tags));
    }

    private static final class NativeRegistryState {
        private final IdentityReference registryIdentity;
        private final IdentityHashMap<MeterRegistry, Recorder> recorders = new IdentityHashMap<>();
        private final ProviderDispatcher dispatcher = new ProviderDispatcher(this);
        private volatile Epoch epoch = new Epoch();
        private volatile Object activeRegistryIdentity;
        private int activeLeaseCount;
        private boolean cleanupScheduled;

        private NativeRegistryState(IdentityReference registryIdentity) {
            this.registryIdentity = registryIdentity;
        }

        private Acquisition acquire(MeterRegistry registry, MetricsFactory metricsFactory, Clock clock) {
            Recorder recorder = recorders.get(registry);
            if (recorder == null) {
                recorder = new Recorder(this, epoch, registry, metricsFactory, clock);
                recorders.put(registry, recorder);
            }
            recorder.referenceCount++;
            activeLeaseCount++;
            List<CompletableFuture<Void>> transferred = List.copyOf(recorder.pendingCompletions);
            recorder.pendingCompletions.clear();
            return new Acquisition(new LeaseImpl(recorder), transferred);
        }

        private void release(Recorder recorder, CompletableFuture<Void> completion) {
            if (!REGISTRIES_LOCK.tryLock()) {
                dispatcher.submitControl(() -> releaseAfterContention(recorder, completion),
                                         () -> abandonRelease(recorder, completion));
                return;
            }
            releaseWithLockHeld(recorder, completion);
        }

        private void releaseAfterContention(Recorder recorder, CompletableFuture<Void> completion) {
            REGISTRIES_LOCK.lock();
            releaseWithLockHeld(recorder, completion);
        }

        private void releaseWithLockHeld(Recorder recorder, CompletableFuture<Void> completion) {
            List<CompletableFuture<Void>> transferred = List.of();
            boolean scheduleCleanup = false;
            try {
                recorder.referenceCount--;
                activeLeaseCount--;
                if (recorder.referenceCount > 0) {
                    transferred = List.of(completion);
                } else {
                    recorder.pendingCompletions.add(completion);
                    if (activeLeaseCount == 0 && !cleanupScheduled) {
                        cleanupScheduled = true;
                        scheduleCleanup = true;
                    }
                }
            } finally {
                REGISTRIES_LOCK.unlock();
            }
            completeAsync(transferred);
            if (scheduleCleanup) {
                dispatcher.submitControl(this::cleanup, this::abandonCleanup);
            }
        }

        private void abandonRelease(Recorder recorder, CompletableFuture<Void> completion) {
            List<CompletableFuture<Void>> completions = new ArrayList<>();
            REGISTRIES_LOCK.lock();
            try {
                recorder.referenceCount--;
                activeLeaseCount--;
                if (recorder.referenceCount > 0) {
                    completions.add(completion);
                } else {
                    recorder.pendingCompletions.add(completion);
                }
                if (activeLeaseCount == 0) {
                    recorders.values().forEach(candidate -> {
                        completions.addAll(candidate.pendingCompletions);
                        candidate.pendingCompletions.clear();
                        candidate.clear();
                    });
                    recorders.clear();
                    epoch.clear();
                    activeRegistryIdentity = null;
                    cleanupScheduled = false;
                    REGISTRIES.remove(registryIdentity);
                }
            } finally {
                REGISTRIES_LOCK.unlock();
            }
            completeAsync(completions);
        }

        private void abandonCleanup() {
            List<CompletableFuture<Void>> completions = new ArrayList<>();
            REGISTRIES_LOCK.lock();
            try {
                cleanupScheduled = false;
                if (activeLeaseCount != 0) {
                    return;
                }
                recorders.values().forEach(recorder -> {
                    completions.addAll(recorder.pendingCompletions);
                    recorder.pendingCompletions.clear();
                    recorder.clear();
                });
                recorders.clear();
                epoch.clear();
                activeRegistryIdentity = null;
                REGISTRIES.remove(registryIdentity);
            } finally {
                REGISTRIES_LOCK.unlock();
            }
            completeAsync(completions);
        }

        private GaugeValue gauge(Epoch selectedEpoch, MetricId id) {
            GaugeValue existing = selectedEpoch.gauges.get(id);
            if (existing != null) {
                return existing;
            }
            return selectedEpoch.gauges.getOrCreate(id, () -> {
                BoundedCache<MetricId, GaugeValue> inherited = selectedEpoch.inheritedGauges;
                GaugeValue value = inherited == null ? null : inherited.get(id);
                return value == null ? new GaugeValue() : value;
            });
        }

        private void bind(Epoch selectedEpoch, Recorder recorder, MetricId id, Meter meter) {
            Object meterIdentity = nativeIdentity(meter, "meter");
            selectedEpoch.expungeCollectedMeters();
            IdentityReference lookup = new IdentityReference(meterIdentity);
            MeterBinding binding = selectedEpoch.bindings.get(lookup);
            if (binding == null) {
                IdentityReference retained = new IdentityReference(meterIdentity, selectedEpoch.collectedMeters);
                binding = new MeterBinding(retained, id);
                selectedEpoch.bindings.put(retained, binding);
            } else if (!binding.id.equals(id)) {
                throw new IllegalStateException("A native meter is bound to more than one HTTP transport meter ID");
            }
            binding.add(recorder.registry, meter);
        }

        private void failure(String event, RuntimeException failure) {
            Epoch current = epoch;
            if (current.failureReported.compareAndSet(false, true)) {
                dispatcher.submitControl(() -> LOGGER.log(WARNING,
                                                          "HTTP transport metrics failed while processing " + event
                                                                  + "; further failures in this ownership epoch are suppressed",
                                                          failure));
            }
        }

        private void cleanup() {
            Epoch retiredEpoch;
            List<Recorder> retiredRecorders;
            REGISTRIES_LOCK.lock();
            try {
                cleanupScheduled = false;
                if (activeLeaseCount != 0) {
                    return;
                }
                retiredEpoch = epoch;
                epoch = retiredEpoch.nextEpoch();
                retiredRecorders = List.copyOf(recorders.values());
                recorders.clear();
            } finally {
                REGISTRIES_LOCK.unlock();
            }

            List<MeterBinding> failedBindings = retiredEpoch.cleanup(this);
            failedBindings.forEach(binding -> epoch.retain(binding, retiredEpoch.gauges.get(binding.id)));
            epoch.inheritedGauges = null;
            retiredRecorders.forEach(Recorder::clear);
            List<CompletableFuture<Void>> completions = new ArrayList<>();
            retiredRecorders.forEach(recorder -> {
                completions.addAll(recorder.pendingCompletions);
                recorder.pendingCompletions.clear();
            });

            REGISTRIES_LOCK.lock();
            try {
                if (activeLeaseCount == 0 && recorders.isEmpty() && !cleanupScheduled) {
                    activeRegistryIdentity = null;
                    if (epoch.bindings.isEmpty()) {
                        REGISTRIES.remove(registryIdentity);
                    }
                }
            } finally {
                REGISTRIES_LOCK.unlock();
            }
            completeAsync(completions);
        }
    }

    private static final class Recorder {
        private final NativeRegistryState state;
        private final Epoch epoch;
        private final MeterRegistry registry;
        private final MetricsFactory metricsFactory;
        private final Clock clock;
        private final BoundedCache<MetricId, MeterSlot> meters = new BoundedCache<>();
        private final BoundedCache<ProtocolKey, ProtocolStreamMetrics> streamMetrics = new BoundedCache<>();
        private final List<CompletableFuture<Void>> pendingCompletions = new ArrayList<>();
        private final ReentrantLock selectionLock = new ReentrantLock();
        private volatile Set<String> enabledMeters;
        private int referenceCount;

        private Recorder(NativeRegistryState state,
                         Epoch epoch,
                         MeterRegistry registry,
                         MetricsFactory metricsFactory,
                         Clock clock) {
            this.state = state;
            this.epoch = epoch;
            this.registry = registry;
            this.metricsFactory = metricsFactory;
            this.clock = clock;
        }

        private void initializeSelection() {
            if (enabledMeters != null) {
                return;
            }
            selectionLock.lock();
            try {
                if (enabledMeters == null) {
                    List<String> enabled = new ArrayList<>();
                    for (String name : METER_NAMES) {
                        try {
                            if (registry.isMeterEnabled(name)) {
                                enabled.add(name);
                            }
                        } catch (RuntimeException failure) {
                            enabled.add(name);
                            state.failure("meter selection", failure);
                        }
                    }
                    enabledMeters = Set.copyOf(enabled);
                }
            } finally {
                selectionLock.unlock();
            }
        }

        private boolean enabled(String name) {
            return enabledMeters.contains(name);
        }

        private ConnectionObservation connectionOpened(Role role,
                                                       String transport,
                                                       Handshake handshake,
                                                       Runnable onClosed) {
            return new MetricsConnectionObservation(this, role, transport, handshake, onClosed);
        }

        private GaugeValue active(MetricId id, String event) {
            if (!enabled(id.name)) {
                return null;
            }
            GaugeValue value = state.gauge(epoch, id);
            if (value != null) {
                registerGauge(id, value, event);
            }
            return value;
        }

        private ProtocolStreamMetrics streamMetrics(Role role, String protocol) {
            ProtocolKey key = new ProtocolKey(role, protocol);
            return streamMetrics.getOrCreate(key, () -> new ProtocolStreamMetrics(this, role, protocol));
        }

        private MeterSlot meter(MetricId id) {
            if (!enabled(id.name)) {
                return null;
            }
            return meters.getOrCreate(id, () -> new MeterSlot(id));
        }

        private void count(MetricId id, String event) {
            count(meter(id), event);
        }

        private void count(MeterSlot slot, String event) {
            if (slot == null) {
                return;
            }
            Optional<Meter> resolved = slot.resolved;
            if (resolved != null) {
                if (resolved.isPresent()) {
                    try {
                        ((Counter) resolved.get()).increment();
                    } catch (RuntimeException failure) {
                        state.failure(event, failure);
                    }
                }
                return;
            }
            state.dispatcher.submit(event, () -> resolve(slot).ifPresent(meter -> ((Counter) meter).increment()), null);
        }

        private void record(MetricId counterId, MetricId timerId, long duration, String event) {
            record(meter(counterId), meter(timerId), duration, event);
        }

        private void record(MeterSlot counterSlot, MeterSlot timerSlot, long duration, String event) {
            if (counterSlot == null && timerSlot == null) {
                return;
            }
            Optional<Meter> resolvedCounter = counterSlot == null ? Optional.empty() : counterSlot.resolved;
            Optional<Meter> resolvedTimer = timerSlot == null || duration == INVALID_TIME
                    ? Optional.empty() : timerSlot.resolved;
            if (resolvedCounter != null && resolvedTimer != null) {
                if (resolvedCounter.isPresent()) {
                    try {
                        ((Counter) resolvedCounter.get()).increment();
                    } catch (RuntimeException failure) {
                        state.failure(event, failure);
                    }
                }
                if (duration != INVALID_TIME && resolvedTimer.isPresent()) {
                    try {
                        ((Timer) resolvedTimer.get()).record(duration, TimeUnit.NANOSECONDS);
                    } catch (RuntimeException failure) {
                        state.failure(event, failure);
                    }
                }
                return;
            }
            state.dispatcher.submit(event, () -> {
                if (counterSlot != null) {
                    resolve(counterSlot).ifPresent(meter -> ((Counter) meter).increment());
                }
                if (timerSlot != null && duration != INVALID_TIME) {
                    resolve(timerSlot).ifPresent(meter -> ((Timer) meter).record(duration, TimeUnit.NANOSECONDS));
                }
            }, null);
        }

        private long sample(String event) {
            try {
                return clock.monotonicTime();
            } catch (RuntimeException failure) {
                state.failure(event, failure);
                return INVALID_TIME;
            }
        }

        private long elapsed(long started, String event) {
            if (started == INVALID_TIME) {
                return INVALID_TIME;
            }
            long finished = sample(event);
            if (finished == INVALID_TIME) {
                return INVALID_TIME;
            }
            return Math.max(0, finished - started);
        }

        private void registerGauge(MetricId id, GaugeValue value, String event) {
            MeterSlot slot = meters.getOrCreate(id, () -> new MeterSlot(id));
            if (slot == null || slot.resolved != null || !slot.registrationQueued.compareAndSet(false, true)) {
                return;
            }
            state.dispatcher.submit(event,
                                    () -> {
                                        try {
                                            resolve(slot, value);
                                        } finally {
                                            slot.registrationQueued.set(false);
                                        }
                                    },
                                    () -> slot.registrationQueued.set(false));
        }

        private Optional<Meter> resolve(MeterSlot slot) {
            return resolve(slot, null);
        }

        private Optional<Meter> resolve(MeterSlot slot, GaugeValue gaugeValue) {
            Optional<Meter> existing = slot.resolved;
            if (existing != null) {
                return existing;
            }
            MetricId id = slot.id;
            Optional<Meter> resolved;
            if (!registry.isMeterEnabled(id.name, id.tagMap, Optional.of(VENDOR))) {
                resolved = Optional.empty();
            } else {
                List<Tag> tags = id.tags.stream()
                        .map(tag -> metricsFactory.tagCreate(tag.key, tag.value))
                        .toList();
                Meter meter = switch (id.type) {
                    case COUNTER -> registry.getOrCreate(metricsFactory.counterBuilder(id.name)
                                                                 .scope(VENDOR)
                                                                 .tags(tags)
                                                                 .description(id.description));
                    case GAUGE -> registry.getOrCreate(metricsFactory.gaugeBuilder(id.name,
                                                                                    Objects.requireNonNull(gaugeValue),
                                                                                    GaugeValue::get)
                                                               .scope(VENDOR)
                                                               .tags(tags)
                                                               .description(id.description));
                    case TIMER -> {
                        Timer.Builder builder = metricsFactory.timerBuilder(id.name)
                                .scope(VENDOR)
                                .tags(tags)
                                .baseUnit(TimeUnit.SECONDS)
                                .description(id.description);
                        if (STREAMS_DURATION.equals(id.name)) {
                            builder.percentiles(new double[0]);
                        }
                        yield registry.getOrCreate(builder);
                    }
                    default -> throw new IllegalArgumentException("Unsupported HTTP transport meter type " + id.type);
                };
                state.bind(epoch, this, id, meter);
                resolved = Optional.of(meter);
            }
            slot.resolved = resolved;
            return resolved;
        }

        private void clear() {
            meters.clear();
            streamMetrics.clear();
        }
    }

    private static final class LeaseImpl implements HttpTransportMetrics.Lease {
        private final Recorder recorder;
        private final AtomicInteger state = new AtomicInteger();
        private final AtomicBoolean released = new AtomicBoolean();
        private final CompletableFuture<Void> completion = new CompletableFuture<>();
        private final CompletionStage<Void> completionView = completion.minimalCompletionStage();

        private LeaseImpl(Recorder recorder) {
            this.recorder = recorder;
        }

        @Override
        public ConnectionObservation connectionOpened(Role role, String transport, Handshake handshake) {
            Objects.requireNonNull(role, "role");
            requireIdentifier(transport, "transport");
            Objects.requireNonNull(handshake, "handshake");
            if (!reserveConnection()) {
                return ConnectionObservation.noop();
            }
            try {
                return recorder.connectionOpened(role, transport, handshake, this::connectionClosed);
            } catch (RuntimeException failure) {
                connectionClosed();
                recorder.state.failure("connection open", failure);
                return ConnectionObservation.noop();
            }
        }

        @Override
        public void close() {
            int previous;
            int updated;
            do {
                previous = state.get();
                if ((previous & CLOSED_FLAG) != 0) {
                    return;
                }
                updated = previous | CLOSED_FLAG;
            } while (!state.compareAndSet(previous, updated));
            if ((updated & CONNECTION_COUNT_MASK) == 0) {
                release();
            }
        }

        @Override
        public CompletionStage<Void> completion() {
            return completionView;
        }

        private boolean reserveConnection() {
            int previous;
            do {
                previous = state.get();
                if ((previous & CLOSED_FLAG) != 0 || (previous & CONNECTION_COUNT_MASK) == CONNECTION_COUNT_MASK) {
                    return false;
                }
            } while (!state.compareAndSet(previous, previous + 1));
            return true;
        }

        private void connectionClosed() {
            int previous;
            int updated;
            do {
                previous = state.get();
                updated = previous - 1;
            } while (!state.compareAndSet(previous, updated));
            if (updated == CLOSED_FLAG) {
                release();
            }
        }

        private void release() {
            if (released.compareAndSet(false, true)) {
                recorder.state.release(recorder, completion);
            }
        }
    }

    private static final class MetricsConnectionObservation implements ConnectionObservation {
        private static final VarHandle CLOSED;

        static {
            try {
                CLOSED = MethodHandles.lookup()
                        .findVarHandle(MetricsConnectionObservation.class, "closed", int.class);
            } catch (ReflectiveOperationException e) {
                throw new ExceptionInInitializerError(e);
            }
        }

        private final Recorder recorder;
        private final Role role;
        private final String transport;
        private final Handshake handshake;
        private final Runnable onClosed;
        private final long started;
        private final List<MetricsStreamObservation> streams = new ArrayList<>();
        private String protocol = UNKNOWN_PROTOCOL;
        private GaugeValue active;
        private MetricsHandshakeObservation handshakeObservation;
        private ProtocolStreamMetrics protocolStreamMetrics;
        private long protocolGeneration;
        private boolean established;
        private volatile int closed;

        private MetricsConnectionObservation(Recorder recorder,
                                             Role role,
                                             String transport,
                                             Handshake handshake,
                                             Runnable onClosed) {
            this.recorder = recorder;
            this.role = role;
            this.transport = transport;
            this.handshake = handshake;
            this.onClosed = onClosed;
            this.started = recorder.enabled(CONNECTIONS_DURATION) ? recorder.sample("connection open time") : INVALID_TIME;

            MetricId opened = counter(CONNECTIONS_OPENED,
                                      "Number of opened physical HTTP connections",
                                      tag("role", role),
                                      tag("transport", transport),
                                      tag("handshake", handshake));
            MetricId activeId = connectionActive(UNKNOWN_PROTOCOL);
            active = recorder.active(activeId, "connection active registration");
            if (active != null) {
                active.increment();
            }
            recorder.count(opened, "connection open");
        }

        @Override
        public HandshakeObservation handshakeStarted() {
            if (closed != 0 || (!recorder.enabled(HANDSHAKES) && !recorder.enabled(HANDSHAKES_DURATION))) {
                return HandshakeObservation.noop();
            }
            if (handshakeObservation == null) {
                long started = recorder.enabled(HANDSHAKES_DURATION)
                        ? recorder.sample("handshake start time") : INVALID_TIME;
                handshakeObservation = new MetricsHandshakeObservation(this, started);
            }
            return handshakeObservation;
        }

        @Override
        public void protocolSelected(String protocol) {
            requireIdentifier(protocol, "protocol");
            if (closed != 0 || this.protocol.equals(protocol)) {
                return;
            }
            this.protocol = protocol;
            long generation = ++protocolGeneration;
            boolean firstSelection = !established;
            established = true;
            protocolStreamMetrics = recorder.streamMetrics(role, protocol);

            GaugeValue selectedActive = recorder.active(connectionActive(protocol), "connection active registration");
            if (closed == 0 && protocolGeneration == generation && this.protocol.equals(protocol)) {
                if (active != null) {
                    active.decrement();
                }
                active = selectedActive;
                if (active != null) {
                    active.increment();
                }
            }
            if (firstSelection) {
                recorder.count(counter(CONNECTIONS_ESTABLISHED,
                                       "Number of established physical HTTP connections",
                                       tag("role", role),
                                       tag("transport", transport),
                                       tag("protocol", protocol)),
                               "connection establishment");
            }
        }

        @Override
        public StreamObservation streamOpened(Direction direction, Initiator initiator) {
            Objects.requireNonNull(direction, "direction");
            Objects.requireNonNull(initiator, "initiator");
            if (closed != 0) {
                return StreamObservation.noop();
            }
            ProtocolStreamMetrics selectedMetrics = protocolStreamMetrics;
            if (selectedMetrics == null) {
                selectedMetrics = recorder.streamMetrics(role, protocol);
                protocolStreamMetrics = selectedMetrics;
            }
            if (selectedMetrics == null) {
                return StreamObservation.noop();
            }
            StreamMetrics metrics = selectedMetrics.stream(direction, initiator);
            if (!metrics.enabled) {
                return StreamObservation.noop();
            }
            long started = metrics.durationEnabled ? recorder.sample("stream open time") : INVALID_TIME;
            MetricsStreamObservation stream = new MetricsStreamObservation(this,
                                                                            metrics,
                                                                            started,
                                                                            streams.size());
            streams.add(stream);
            stream.open();
            return stream;
        }

        @Override
        public void close(ConnectionOutcome outcome) {
            Objects.requireNonNull(outcome, "outcome");
            if (!(boolean) CLOSED.compareAndSet(this, 0, 1)) {
                return;
            }
            MetricsHandshakeObservation handshakeToClose = handshakeObservation;
            List<MetricsStreamObservation> streamsToClose = detachStreams();
            GaugeValue activeToClose = active;
            active = null;
            String finalProtocol = protocol;
            try {
                if (handshakeToClose != null) {
                    handshakeToClose.close(switch (outcome) {
                        case NORMAL, LOCAL_CLOSE -> HandshakeOutcome.CANCELLED;
                        case REMOTE_CLOSE, ERROR -> HandshakeOutcome.FAILURE;
                        case TIMEOUT -> HandshakeOutcome.TIMEOUT;
                    });
                }
                StreamOutcome streamOutcome = switch (outcome) {
                    case NORMAL, LOCAL_CLOSE, REMOTE_CLOSE -> StreamOutcome.CANCELLED;
                    case TIMEOUT, ERROR -> StreamOutcome.ERROR;
                };
                streamsToClose.forEach(stream -> stream.close(streamOutcome));
                if (activeToClose != null) {
                    activeToClose.decrement();
                }
                List<TagValue> tags = List.of(tag("role", role),
                                              tag("transport", transport),
                                              tag("protocol", finalProtocol),
                                              tag("outcome", outcome));
                recorder.record(counter(CONNECTIONS_CLOSED,
                                        "Number of closed physical HTTP connections",
                                        tags.toArray(TagValue[]::new)),
                                timer(CONNECTIONS_DURATION,
                                      "Duration of physical HTTP connections",
                                      tags.toArray(TagValue[]::new)),
                                recorder.elapsed(started, "connection close time"),
                                "connection close");
            } finally {
                onClosed.run();
            }
        }

        private List<MetricsStreamObservation> detachStreams() {
            List<MetricsStreamObservation> result = List.copyOf(streams);
            streams.clear();
            // Detach every child before invoking callbacks, including callbacks which might fail.
            for (MetricsStreamObservation stream : result) {
                stream.slot = MetricsStreamObservation.DETACHED;
            }
            return result;
        }

        private void removeStream(int slot) {
            MetricsStreamObservation last = streams.removeLast();
            if (slot < streams.size()) {
                streams.set(slot, last);
                last.slot = slot;
            }
        }

        private MetricId connectionActive(String protocol) {
            return gauge(CONNECTIONS_ACTIVE,
                         "Number of active physical HTTP connections",
                         tag("role", role),
                         tag("transport", transport),
                         tag("protocol", protocol));
        }
    }

    private static final class MetricsHandshakeObservation implements HandshakeObservation {
        private static final VarHandle CLOSED;

        static {
            try {
                CLOSED = MethodHandles.lookup()
                        .findVarHandle(MetricsHandshakeObservation.class, "closed", int.class);
            } catch (ReflectiveOperationException e) {
                throw new ExceptionInInitializerError(e);
            }
        }

        private final MetricsConnectionObservation owner;
        private final long started;
        private volatile int closed;

        private MetricsHandshakeObservation(MetricsConnectionObservation owner, long started) {
            this.owner = owner;
            this.started = started;
        }

        @Override
        public void close(HandshakeOutcome outcome) {
            Objects.requireNonNull(outcome, "outcome");
            if (!(boolean) CLOSED.compareAndSet(this, 0, 1)) {
                return;
            }
            List<TagValue> tags = List.of(tag("role", owner.role),
                                          tag("transport", owner.transport),
                                          tag("handshake", owner.handshake),
                                          tag("outcome", outcome));
            owner.recorder.record(counter(HANDSHAKES,
                                          "Number of completed HTTP transport handshakes",
                                          tags.toArray(TagValue[]::new)),
                                  timer(HANDSHAKES_DURATION,
                                        "Duration of HTTP transport handshakes",
                                        tags.toArray(TagValue[]::new)),
                                  owner.recorder.elapsed(started, "handshake close time"),
                                  "handshake close");
        }
    }

    private static final class MetricsStreamObservation implements StreamObservation {
        private static final int CLOSED = -1;
        private static final int DETACHED = -2;
        private static final VarHandle SLOT;

        static {
            try {
                SLOT = MethodHandles.lookup()
                        .findVarHandle(MetricsStreamObservation.class, "slot", int.class);
            } catch (ReflectiveOperationException e) {
                throw new ExceptionInInitializerError(e);
            }
        }

        private final MetricsConnectionObservation owner;
        private final StreamMetrics metrics;
        private final long started;
        // Lifecycle callbacks for one connection are published sequentially.
        // Non-negative values identify the owner's list entry; negative values preserve close state after detachment.
        private volatile int slot;

        private MetricsStreamObservation(MetricsConnectionObservation owner,
                                         StreamMetrics metrics,
                                         long started,
                                         int slot) {
            this.owner = owner;
            this.metrics = metrics;
            this.started = started;
            this.slot = slot;
        }

        @Override
        public void close(StreamOutcome outcome) {
            Objects.requireNonNull(outcome, "outcome");
            int previousSlot = (int) SLOT.getAndSet(this, CLOSED);
            if (previousSlot == CLOSED) {
                return;
            }
            if (previousSlot >= 0) {
                owner.removeStream(previousSlot);
            }
            if (metrics.active != null) {
                metrics.active.decrement();
            }
            StreamOutcomeMetrics outcomeMetrics = metrics.outcome(outcome);
            owner.recorder.record(outcomeMetrics.closed,
                                  outcomeMetrics.duration,
                                  owner.recorder.elapsed(started, "stream close time"),
                                  "stream close");
        }

        private void open() {
            if (metrics.active != null) {
                metrics.active.increment();
            }
            owner.recorder.count(metrics.opened, "stream open");
        }
    }

    private static final class ProtocolStreamMetrics {
        private static final int INITIATOR_COUNT = Initiator.values().length;
        private static final VarHandle STREAM_METRICS = MethodHandles.arrayElementVarHandle(StreamMetrics[].class);

        private final Recorder recorder;
        private final Role role;
        private final String protocol;
        private final StreamMetrics[] metrics = new StreamMetrics[Direction.values().length * INITIATOR_COUNT];

        private ProtocolStreamMetrics(Recorder recorder, Role role, String protocol) {
            this.recorder = recorder;
            this.role = role;
            this.protocol = protocol;
        }

        private StreamMetrics stream(Direction direction, Initiator initiator) {
            int index = direction.ordinal() * INITIATOR_COUNT + initiator.ordinal();
            StreamMetrics existing = (StreamMetrics) STREAM_METRICS.getAcquire(metrics, index);
            if (existing != null) {
                return existing;
            }
            StreamMetrics candidate = new StreamMetrics(recorder, role, protocol, direction, initiator);
            if ((boolean) STREAM_METRICS.compareAndSet(metrics, index, null, candidate)) {
                return candidate;
            }
            return (StreamMetrics) STREAM_METRICS.getAcquire(metrics, index);
        }
    }

    private static final class StreamMetrics {
        private static final VarHandle OUTCOME_METRICS =
                MethodHandles.arrayElementVarHandle(StreamOutcomeMetrics[].class);

        private final Recorder recorder;
        private final List<TagValue> tags;
        private final GaugeValue active;
        private final MeterSlot opened;
        private final boolean enabled;
        private final boolean durationEnabled;
        private final StreamOutcomeMetrics[] outcomes = new StreamOutcomeMetrics[STREAM_OUTCOME_COUNT];

        private StreamMetrics(Recorder recorder,
                              Role role,
                              String protocol,
                              Direction direction,
                              Initiator initiator) {
            this.recorder = recorder;
            durationEnabled = recorder.enabled(STREAMS_DURATION);
            enabled = durationEnabled
                    || recorder.enabled(STREAMS_OPENED)
                    || recorder.enabled(STREAMS_ACTIVE)
                    || recorder.enabled(STREAMS_CLOSED);
            tags = List.of(tag("role", role),
                           tag("protocol", protocol),
                           tag("direction", direction),
                           tag("initiator", initiator));
            active = recorder.active(gauge(STREAMS_ACTIVE,
                                           "Number of active HTTP request and response exchanges",
                                           tags.toArray(TagValue[]::new)),
                                     "stream active registration");
            opened = recorder.meter(counter(STREAMS_OPENED,
                                            "Number of opened HTTP request and response exchanges",
                                            tags.toArray(TagValue[]::new)));
        }

        private StreamOutcomeMetrics outcome(StreamOutcome outcome) {
            int index = outcome.ordinal();
            StreamOutcomeMetrics existing = (StreamOutcomeMetrics) OUTCOME_METRICS.getAcquire(outcomes, index);
            if (existing != null) {
                return existing;
            }
            List<TagValue> outcomeTags = new ArrayList<>(tags);
            outcomeTags.add(tag("outcome", outcome));
            StreamOutcomeMetrics candidate = new StreamOutcomeMetrics(
                    recorder.meter(counter(STREAMS_CLOSED,
                                           "Number of closed HTTP request and response exchanges",
                                           outcomeTags.toArray(TagValue[]::new))),
                    recorder.meter(timer(STREAMS_DURATION,
                                         "Duration of HTTP request and response exchanges",
                                         outcomeTags.toArray(TagValue[]::new))));
            if ((boolean) OUTCOME_METRICS.compareAndSet(outcomes, index, null, candidate)) {
                return candidate;
            }
            return (StreamOutcomeMetrics) OUTCOME_METRICS.getAcquire(outcomes, index);
        }
    }

    private record StreamOutcomeMetrics(MeterSlot closed, MeterSlot duration) {
    }

    private static final class Epoch {
        private final ReferenceQueue<Object> collectedMeters = new ReferenceQueue<>();
        private final Map<IdentityReference, MeterBinding> bindings = new HashMap<>();
        private final BoundedCache<MetricId, GaugeValue> gauges = new BoundedCache<>();
        private final AtomicBoolean failureReported = new AtomicBoolean();
        private volatile BoundedCache<MetricId, GaugeValue> inheritedGauges;

        private Epoch nextEpoch() {
            var next = new Epoch();
            // Reacquisition must use the backing values of native gauges until their removal has completed.
            next.inheritedGauges = gauges;
            return next;
        }

        private List<MeterBinding> cleanup(NativeRegistryState state) {
            expungeCollectedMeters();
            List<MeterBinding> failed = new ArrayList<>();
            for (MeterBinding binding : bindings.values()) {
                boolean removed = false;
                RuntimeException lastFailure = null;
                binding.removeCollectedRegistrations();
                for (int pass = 0; pass < 2 && !removed; pass++) {
                    for (MeterRegistration registration : binding.registrations) {
                        MeterRegistry registry = registration.registry.get();
                        Meter meter = registration.meter.get();
                        if (registry == null || meter == null) {
                            continue;
                        }
                        try {
                            registry.remove(meter);
                            removed = true;
                            break;
                        } catch (RuntimeException failure) {
                            lastFailure = failure;
                        }
                    }
                }
                if (removed) {
                    binding.registrations.clear();
                } else {
                    if (lastFailure != null) {
                        state.failure("meter cleanup", lastFailure);
                    }
                    failed.add(binding);
                }
            }
            bindings.clear();
            return failed;
        }

        private void retain(MeterBinding binding, GaugeValue gaugeValue) {
            if (binding.identity.get() != null) {
                bindings.put(binding.identity, binding);
                if (gaugeValue != null) {
                    gauges.getOrCreate(binding.id, () -> gaugeValue);
                }
            }
        }

        private void expungeCollectedMeters() {
            IdentityReference collected;
            while ((collected = (IdentityReference) collectedMeters.poll()) != null) {
                bindings.remove(collected);
            }
        }

        private void clear() {
            bindings.clear();
            gauges.clear();
            inheritedGauges = null;
        }
    }

    private static final class ProviderDispatcher {
        private static final long ADMISSION_INCREMENT = 1L << Integer.SIZE;
        private static final long OUTSTANDING_MASK = ADMISSION_INCREMENT - 1;

        private final ConcurrentLinkedQueue<ProviderTask> tasks = new ConcurrentLinkedQueue<>();
        private final AtomicInteger workInProgress = new AtomicInteger();
        // High bits count admitted observations; low bits include every task until its completion.
        private final AtomicLong waveState = new AtomicLong();
        private final StampedLock admissionLock = new StampedLock();
        private final AtomicBoolean unavailable = new AtomicBoolean();
        private final NativeRegistryState owner;

        private ProviderDispatcher(NativeRegistryState owner) {
            this.owner = owner;
        }

        private void submit(String event, Runnable action, Runnable rejection) {
            if (unavailable.get()) {
                if (rejection != null) {
                    rejection.run();
                }
                return;
            }
            long stamp = admissionLock.tryReadLock();
            if (stamp == 0) {
                if (rejection != null) {
                    rejection.run();
                }
                return;
            }
            boolean accepted;
            try {
                accepted = !unavailable.get() && reserveAdmission();
                if (accepted) {
                    tasks.offer(new ProviderTask(event, action, rejection));
                }
            } finally {
                admissionLock.unlockRead(stamp);
            }
            if (accepted) {
                start();
            } else if (rejection != null) {
                rejection.run();
            }
        }

        private boolean reserveAdmission() {
            long current;
            do {
                current = waveState.get();
                if ((current >>> Integer.SIZE) >= MAX_PROVIDER_ACTIONS) {
                    return false;
                }
                // Reserve outstanding work before publication so the final running task cannot reset this wave.
            } while (!waveState.compareAndSet(current, current + ADMISSION_INCREMENT + 1));
            return true;
        }

        private void completeTask() {
            long current;
            long next;
            do {
                current = waveState.get();
                next = (current & OUTSTANDING_MASK) == 1 ? 0 : current - 1;
            } while (!waveState.compareAndSet(current, next));
        }

        private void submitControl(Runnable action) {
            submitControl(action, null);
        }

        private void submitControl(Runnable action, Runnable abandonment) {
            if (unavailable.get()) {
                if (abandonment != null) {
                    abandonment.run();
                }
                return;
            }
            long stamp = admissionLock.readLock();
            boolean accepted;
            try {
                accepted = !unavailable.get();
                if (accepted) {
                    waveState.incrementAndGet();
                    tasks.offer(new ProviderTask("control", action, abandonment));
                }
            } finally {
                admissionLock.unlockRead(stamp);
            }
            if (accepted) {
                start();
            } else if (abandonment != null) {
                abandonment.run();
            }
        }

        private void start() {
            if (workInProgress.getAndIncrement() == 0) {
                try {
                    Thread.ofVirtual()
                            .name("helidon-http-transport-metrics-provider")
                            .inheritInheritableThreadLocals(false)
                            .start(this::drain);
                } catch (RuntimeException virtualThreadFailure) {
                    startFallback(virtualThreadFailure);
                }
            }
        }

        private void startFallback(RuntimeException virtualThreadFailure) {
            try {
                Thread.ofPlatform()
                        .daemon(true)
                        .name("helidon-http-transport-metrics-provider-fallback")
                        .inheritInheritableThreadLocals(false)
                        .start(() -> {
                            LOGGER.log(WARNING,
                                       "HTTP transport metrics virtual-thread dispatcher could not start",
                                       virtualThreadFailure);
                            drain();
                        });
            } catch (RuntimeException platformThreadFailure) {
                try {
                    CompletableFuture.runAsync(() -> {
                        LOGGER.log(WARNING,
                                   "HTTP transport metrics dedicated dispatchers could not start",
                                   platformThreadFailure);
                        drain();
                    });
                } catch (RuntimeException _) {
                    abandon();
                }
            }
        }

        private void abandon() {
            List<Runnable> abandonments = new ArrayList<>();
            long stamp = admissionLock.writeLock();
            try {
                unavailable.set(true);
                ProviderTask task;
                while ((task = tasks.poll()) != null) {
                    completeTask();
                    if (task.abandonment != null) {
                        abandonments.add(task.abandonment);
                    }
                }
                workInProgress.set(0);
            } finally {
                admissionLock.unlockWrite(stamp);
            }
            abandonments.forEach(Runnable::run);
        }

        private void drain() {
            int missed = 1;
            for (;;) {
                ProviderTask task;
                while ((task = tasks.poll()) != null) {
                    try {
                        task.action.run();
                    } catch (RuntimeException failure) {
                        owner.failure(task.event, failure);
                    } finally {
                        completeTask();
                    }
                }
                missed = workInProgress.addAndGet(-missed);
                if (missed == 0) {
                    return;
                }
            }
        }
    }

    private static final class BoundedCache<K, V> {
        private final Map<K, V> values = new ConcurrentHashMap<>();
        private final AtomicInteger size = new AtomicInteger();

        private V getOrCreate(K key, Supplier<V> supplier) {
            V existing = values.get(key);
            if (existing != null) {
                return existing;
            }
            int current;
            do {
                current = size.get();
                if (current >= MAX_METER_SERIES) {
                    return null;
                }
            } while (!size.compareAndSet(current, current + 1));
            V candidate = supplier.get();
            V raced = values.putIfAbsent(key, candidate);
            if (raced != null) {
                size.decrementAndGet();
                return raced;
            }
            return candidate;
        }

        private V get(K key) {
            return values.get(key);
        }

        private void clear() {
            values.clear();
            size.set(0);
        }
    }

    private static final class GaugeValue {
        private final AtomicLong value = new AtomicLong();

        private void increment() {
            value.incrementAndGet();
        }

        private void decrement() {
            value.decrementAndGet();
        }

        private long get() {
            return value.get();
        }
    }

    private static final class MetricId {
        private final String name;
        private final Meter.Type type;
        private final String description;
        private final List<TagValue> tags;
        private final Map<String, String> tagMap;

        private MetricId(String name, Meter.Type type, String description, List<TagValue> tags) {
            this.name = name;
            this.type = type;
            this.description = description;
            this.tags = tags;
            Map<String, String> tagMap = new HashMap<>();
            tags.forEach(tag -> tagMap.put(tag.key, tag.value));
            this.tagMap = Map.copyOf(tagMap);
        }

        @Override
        public int hashCode() {
            int result = name.hashCode();
            result = 31 * result + type.hashCode();
            return 31 * result + tags.hashCode();
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) {
                return true;
            }
            return object instanceof MetricId other
                    && name.equals(other.name)
                    && type == other.type
                    && tags.equals(other.tags);
        }
    }

    private static final class ProtocolKey {
        private final Role role;
        private final String protocol;

        private ProtocolKey(Role role, String protocol) {
            this.role = role;
            this.protocol = protocol;
        }

        @Override
        public int hashCode() {
            return 31 * role.hashCode() + protocol.hashCode();
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) {
                return true;
            }
            return object instanceof ProtocolKey other
                    && role == other.role
                    && protocol.equals(other.protocol);
        }
    }

    private static final class MeterSlot {
        private final AtomicBoolean registrationQueued = new AtomicBoolean();
        private final MetricId id;
        private volatile Optional<Meter> resolved;

        private MeterSlot(MetricId id) {
            this.id = id;
        }
    }

    private static final class MeterBinding {
        private final IdentityReference identity;
        private final MetricId id;
        private final List<MeterRegistration> registrations = new ArrayList<>();

        private MeterBinding(IdentityReference identity, MetricId id) {
            this.identity = identity;
            this.id = id;
        }

        private void add(MeterRegistry registry, Meter meter) {
            removeCollectedRegistrations();
            for (MeterRegistration registration : registrations) {
                if (registration.registry.get() == registry && registration.meter.get() == meter) {
                    return;
                }
            }
            registrations.add(new MeterRegistration(new WeakReference<>(registry), new WeakReference<>(meter)));
        }

        private void removeCollectedRegistrations() {
            registrations.removeIf(registration -> registration.registry.get() == null || registration.meter.get() == null);
        }
    }

    private static final class IdentityReference extends WeakReference<Object> {
        private final int identityHash;

        private IdentityReference(Object referent) {
            super(referent);
            identityHash = System.identityHashCode(referent);
        }

        private IdentityReference(Object referent, ReferenceQueue<Object> queue) {
            super(referent, queue);
            identityHash = System.identityHashCode(referent);
        }

        @Override
        public int hashCode() {
            return identityHash;
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) {
                return true;
            }
            if (!(object instanceof IdentityReference other)) {
                return false;
            }
            Object identity = get();
            return identity != null && identity == other.get();
        }
    }

    private record Acquisition(LeaseImpl lease,
                               List<CompletableFuture<Void>> transferredCompletions) {
    }

    private record TagValue(String key, String value) {
    }

    private record MeterRegistration(WeakReference<MeterRegistry> registry, WeakReference<Meter> meter) {
    }

    private record ProviderTask(String event, Runnable action, Runnable abandonment) {
    }
}
