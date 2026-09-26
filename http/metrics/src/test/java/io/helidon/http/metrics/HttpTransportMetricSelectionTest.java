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

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;

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
import io.helidon.metrics.api.Gauge;
import io.helidon.metrics.api.Meter;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.MetricsFactory;
import io.helidon.metrics.api.Tag;
import io.helidon.metrics.api.Timer;

import org.junit.jupiter.api.Test;

import static io.helidon.http.HttpTransportObserver.PROTOCOL_HTTP_1_1;
import static io.helidon.http.HttpTransportObserver.PROTOCOL_HTTP_2;
import static io.helidon.http.HttpTransportObserver.TRANSPORT_TCP;
import static io.helidon.http.metrics.HttpTransportMetrics.CONNECTIONS_ACTIVE;
import static io.helidon.http.metrics.HttpTransportMetrics.CONNECTIONS_CLOSED;
import static io.helidon.http.metrics.HttpTransportMetrics.CONNECTIONS_DURATION;
import static io.helidon.http.metrics.HttpTransportMetrics.CONNECTIONS_OPENED;
import static io.helidon.http.metrics.HttpTransportMetrics.HANDSHAKES;
import static io.helidon.http.metrics.HttpTransportMetrics.HANDSHAKES_DURATION;
import static io.helidon.http.metrics.HttpTransportMetrics.STREAMS_ACTIVE;
import static io.helidon.http.metrics.HttpTransportMetrics.STREAMS_CLOSED;
import static io.helidon.http.metrics.HttpTransportMetrics.STREAMS_DURATION;
import static io.helidon.http.metrics.HttpTransportMetrics.STREAMS_OPENED;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;

class HttpTransportMetricSelectionTest {
    private static final Set<String> DURATIONS = Set.of(CONNECTIONS_DURATION, HANDSHAKES_DURATION, STREAMS_DURATION);

    @Test
    void disabledDurationsDoNotReadClockAndPreserveCountersAndGauges() throws Exception {
        TestRegistry registry = new TestRegistry(name -> !DURATIONS.contains(name), _ -> true);
        HttpTransportMetrics.Lease lease = HttpTransportMetrics.acquire(registry.registry);
        HttpTransportMetrics.Lease shared = HttpTransportMetrics.acquire(registry.registry);
        int exchanges = 0;
        for (Role role : Role.values()) {
            for (String protocol : List.of(PROTOCOL_HTTP_1_1, PROTOCOL_HTTP_2)) {
                for (Direction direction : Direction.values()) {
                    for (Initiator initiator : Initiator.values()) {
                        for (StreamOutcome outcome : StreamOutcome.values()) {
                            ConnectionObservation connection = lease.connectionOpened(role,
                                                                                      TRANSPORT_TCP,
                                                                                      Handshake.TLS);
                            connection.handshakeStarted().close(HandshakeOutcome.SUCCESS);
                            connection.protocolSelected(protocol);
                            connection.streamOpened(direction, initiator).close(outcome);
                            connection.close(ConnectionOutcome.LOCAL_CLOSE);
                            exchanges++;
                        }
                    }
                }
            }
        }
        finish(lease, shared);

        assertThat("disabled durations must not sample the clock", registry.clockReads.get(), is(0));
        assertThat(registry.updates(CONNECTIONS_OPENED), is((long) exchanges));
        assertThat(registry.updates(CONNECTIONS_CLOSED), is((long) exchanges));
        assertThat(registry.updates(HANDSHAKES), is((long) exchanges));
        assertThat(registry.updates(STREAMS_OPENED), is((long) exchanges));
        assertThat(registry.updates(STREAMS_CLOSED), is((long) exchanges));
        assertThat("every role, protocol, direction, initiator and outcome remains a distinct stream series",
                   registry.meters.values().stream()
                           .filter(meter -> meter.id.name.equals(STREAMS_CLOSED))
                           .map(meter -> meter.updates.get())
                           .toList(),
                   everyItem(is(1L)));
        for (String gauge : List.of(CONNECTIONS_ACTIVE, STREAMS_ACTIVE)) {
            assertThat("enabled active gauges are registered", registry.values(gauge).size(), greaterThan(0));
            assertThat("enabled active gauges return to zero", registry.values(gauge), everyItem(is(0.0)));
        }
        assertThat("duration meters must not be registered", registry.meters.keySet().stream()
                .anyMatch(id -> DURATIONS.contains(id.name)), is(false));
        assertThat("name selection is cached across leases and exchanges", registry.nameQueries.size(), is(11));
        assertThat(registry.nameQueries.values().stream().map(AtomicInteger::get).toList(), everyItem(is(1)));
    }

    @Test
    void enabledDurationsRemainIndependentOfDisabledOutcomeCounters() throws Exception {
        TestRegistry registry = new TestRegistry(DURATIONS::contains, _ -> true);
        HttpTransportMetrics.Lease lease = HttpTransportMetrics.acquire(registry.registry);
        ConnectionObservation connection = lease.connectionOpened(Role.CLIENT, TRANSPORT_TCP, Handshake.TLS);
        registry.time.set(10);
        HandshakeObservation handshake = connection.handshakeStarted();
        registry.time.set(30);
        handshake.close(HandshakeOutcome.SUCCESS);
        connection.protocolSelected(PROTOCOL_HTTP_2);
        StreamObservation stream = connection.streamOpened(Direction.BIDIRECTIONAL, Initiator.LOCAL);
        registry.time.set(70);
        stream.close(StreamOutcome.COMPLETED);
        connection.close(ConnectionOutcome.NORMAL);
        finish(lease);

        assertThat(registry.clockReads.get(), is(6));
        assertThat(registry.duration(CONNECTIONS_DURATION), is(70L));
        assertThat(registry.duration(HANDSHAKES_DURATION), is(20L));
        assertThat(registry.duration(STREAMS_DURATION), is(40L));
        assertThat("only the three independent timers are registered", registry.meters.size(), is(3));
        assertThat(registry.meters.keySet().stream().map(MetricId::type).toList(), everyItem(is(Meter.Type.TIMER)));
    }

    @Test
    void disabledStreamAndHandshakeFamiliesReturnCanonicalNoopsWithoutReleasingPhysicalLease() throws Exception {
        TestRegistry registry = new TestRegistry(_ -> false, _ -> true);
        HttpTransportMetrics.Lease lease = HttpTransportMetrics.acquire(registry.registry);
        ConnectionObservation connection = lease.connectionOpened(Role.SERVER, TRANSPORT_TCP, Handshake.TLS);
        try {
            assertThat(connection.handshakeStarted(), sameInstance(HandshakeObservation.noop()));
            connection.protocolSelected(PROTOCOL_HTTP_1_1);
            assertThat(connection.streamOpened(Direction.BIDIRECTIONAL, Initiator.REMOTE),
                       sameInstance(StreamObservation.noop()));
            lease.close();
            assertThat("physical observation still retains its lease",
                       lease.completion().toCompletableFuture().isDone(),
                       is(false));
        } finally {
            connection.close(ConnectionOutcome.NORMAL);
            finish(lease);
        }

        assertThat(registry.clockReads.get(), is(0));
        assertThat(registry.tagQueries.get(), is(0));
        assertThat(registry.meters.size(), is(0));
    }

    @Test
    void completeTagFiltersRetainTimingForPreviouslyUnknownProtocolAndOutcome() throws Exception {
        TestRegistry registry = new TestRegistry(_ -> true,
                id -> DURATIONS.contains(id.name)
                        && (id.name.equals(HANDSHAKES_DURATION)
                        ? "success".equals(id.tags.get("outcome"))
                        : "custom-protocol".equals(id.tags.get("protocol"))
                                && Set.of("reset", "error").contains(id.tags.get("outcome"))));
        HttpTransportMetrics.Lease lease = HttpTransportMetrics.acquire(registry.registry);
        ConnectionObservation connection = lease.connectionOpened(Role.SERVER, TRANSPORT_TCP, Handshake.TLS);
        HandshakeObservation handshake = connection.handshakeStarted();
        registry.time.set(10);
        handshake.close(HandshakeOutcome.SUCCESS);
        connection.protocolSelected("custom-protocol");
        StreamObservation stream = connection.streamOpened(Direction.UNIDIRECTIONAL, Initiator.REMOTE);
        registry.time.set(30);
        stream.close(StreamOutcome.RESET);
        connection.close(ConnectionOutcome.ERROR);
        finish(lease);

        assertThat(registry.duration(CONNECTIONS_DURATION), is(30L));
        assertThat(registry.duration(HANDSHAKES_DURATION), is(10L));
        assertThat(registry.duration(STREAMS_DURATION), is(20L));
        assertThat("selection must not use incomplete duration tags", registry.incompleteDurationQueries.get(), is(0));
        assertThat(registry.meters.size(), is(3));
    }

    private static void finish(HttpTransportMetrics.Lease... leases) throws Exception {
        for (HttpTransportMetrics.Lease lease : leases) {
            lease.close();
        }
        for (HttpTransportMetrics.Lease lease : leases) {
            lease.completion().toCompletableFuture().get(5, TimeUnit.SECONDS);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler);
    }

    private record MetricId(String name, Meter.Type type, Map<String, String> tags) {
    }

    private record TestTag(String key, String value) implements Tag {
        @Override
        public <T> T unwrap(Class<? extends T> type) {
            return type.cast(this);
        }
    }

    private static final class TestRegistry {
        private final AtomicInteger clockReads = new AtomicInteger();
        private final AtomicLong time = new AtomicLong();
        private final AtomicInteger tagQueries = new AtomicInteger();
        private final AtomicInteger incompleteDurationQueries = new AtomicInteger();
        private final Map<String, AtomicInteger> nameQueries = new ConcurrentHashMap<>();
        private final Map<MetricId, RecordedMeter> meters = new ConcurrentHashMap<>();
        private final Predicate<String> nameSelection;
        private final Predicate<MetricId> tagSelection;
        private final Clock clock = new Clock() {
            @Override
            public long wallTime() {
                return 0;
            }

            @Override
            public long monotonicTime() {
                clockReads.incrementAndGet();
                return time.get();
            }
        };
        private final MetricsFactory factory = proxy(MetricsFactory.class, this::factory);
        private final MeterRegistry registry = proxy(MeterRegistry.class, this::registry);

        private TestRegistry(Predicate<String> nameSelection, Predicate<MetricId> tagSelection) {
            this.nameSelection = nameSelection;
            this.tagSelection = tagSelection;
        }

        @SuppressWarnings("unchecked")
        private Object factory(Object proxy, Method method, Object[] args) {
            return switch (method.getName()) {
                case "counterBuilder" -> builder(Counter.Builder.class, (String) args[0], () -> 0.0);
                case "timerBuilder" -> builder(Timer.Builder.class, (String) args[0], () -> 0.0);
                case "gaugeBuilder" -> builder(Gauge.Builder.class,
                                               (String) args[0],
                                               () -> ((ToDoubleFunction<Object>) args[2]).applyAsDouble(args[1]));
                case "tagCreate" -> new TestTag((String) args[0], (String) args[1]);
                case "toString" -> "selection test factory";
                default -> throw new AssertionError("Unexpected metrics factory method: " + method);
            };
        }

        @SuppressWarnings("unchecked")
        private Object registry(Object proxy, Method method, Object[] args) {
            return switch (method.getName()) {
                case "unwrap" -> ((Class<?>) args[0]).cast(this);
                case "clock" -> clock;
                case "metricsFactory" -> factory;
                case "isMeterEnabled" -> {
                    String name = (String) args[0];
                    if (args.length == 1) {
                        nameQueries.computeIfAbsent(name, _ -> new AtomicInteger()).incrementAndGet();
                        yield nameSelection.test(name);
                    }
                    tagQueries.incrementAndGet();
                    Map<String, String> tags = (Map<String, String>) args[1];
                    if (DURATIONS.contains(name) && !tags.containsKey("outcome")) {
                        incompleteDurationQueries.incrementAndGet();
                    }
                    yield tagSelection.test(new MetricId(name, Meter.Type.TIMER, tags));
                }
                case "getOrCreate" -> {
                    Meter.Builder<?, ?> builder = (Meter.Builder<?, ?>) args[0];
                    Meter.Type type = builder instanceof Counter.Builder ? Meter.Type.COUNTER
                            : builder instanceof Timer.Builder ? Meter.Type.TIMER : Meter.Type.GAUGE;
                    MetricId id = new MetricId(builder.name(), type, Map.copyOf(builder.tags()));
                    yield meters.computeIfAbsent(id, _ -> new RecordedMeter(id,
                            builder instanceof Gauge.Builder<?> gauge ? gauge.supplier() : () -> 0.0)).meter;
                }
                case "remove" -> Optional.of(args[0]);
                case "toString" -> "selection test registry";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> throw new AssertionError("Unexpected meter registry method: " + method);
            };
        }

        private long updates(String name) {
            return meters.values().stream()
                    .filter(meter -> meter.id.name.equals(name))
                    .mapToLong(meter -> meter.updates.get())
                    .sum();
        }

        private long duration(String name) {
            return meters.values().stream()
                    .filter(meter -> meter.id.name.equals(name))
                    .mapToLong(meter -> meter.nanos.get())
                    .sum();
        }

        private List<Double> values(String name) {
            return meters.values().stream()
                    .filter(meter -> meter.id.name.equals(name))
                    .map(meter -> meter.value.get().doubleValue())
                    .toList();
        }

        private <T> T builder(Class<T> type, String name, Supplier<? extends Number> value) {
            Map<String, String> tags = new HashMap<>();
            return proxy(type, (proxy, method, args) -> switch (method.getName()) {
                case "name" -> name;
                case "tags" -> {
                    if (args == null || args.length == 0) {
                        yield tags;
                    }
                    for (Object suppliedTag : (Iterable<?>) args[0]) {
                        Tag tag = (Tag) suppliedTag;
                        tags.put(tag.key(), tag.value());
                    }
                    yield proxy;
                }
                case "scope", "description", "baseUnit", "percentiles" -> proxy;
                case "supplier" -> value;
                case "toString" -> name;
                default -> throw new AssertionError("Unexpected meter builder method: " + method);
            });
        }
    }

    private static final class RecordedMeter {
        private final MetricId id;
        private final Supplier<? extends Number> value;
        private final AtomicLong updates = new AtomicLong();
        private final AtomicLong nanos = new AtomicLong();
        private final Meter meter;

        private RecordedMeter(MetricId id, Supplier<? extends Number> value) {
            this.id = id;
            this.value = value;
            Class<? extends Meter> type = switch (id.type) {
                case COUNTER -> Counter.class;
                case GAUGE -> Gauge.class;
                case TIMER -> Timer.class;
                default -> throw new IllegalArgumentException(id.type.name().toLowerCase(Locale.ROOT));
            };
            meter = proxy(type, (proxy, method, args) -> switch (method.getName()) {
                case "unwrap" -> ((Class<?>) args[0]).cast(proxy);
                case "increment" -> {
                    updates.incrementAndGet();
                    yield null;
                }
                case "record" -> {
                    updates.incrementAndGet();
                    nanos.addAndGet(((TimeUnit) args[1]).toNanos((Long) args[0]));
                    yield null;
                }
                case "toString" -> id.toString();
                default -> throw new AssertionError("Unexpected meter update: " + method);
            });
        }
    }
}
