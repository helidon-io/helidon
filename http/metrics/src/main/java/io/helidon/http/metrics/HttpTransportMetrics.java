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

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
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
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

import io.helidon.common.Api;
import io.helidon.http.HttpTransportObserver;
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
import io.helidon.metrics.api.Tag;
import io.helidon.metrics.api.Timer;

import static java.lang.System.Logger.Level.WARNING;

/**
 * Metrics API integration for HTTP transport lifecycle observation.
 *
 * <p>The adapter emits the following vendor-scope meters. Timer base units are seconds.
 * <table>
 *     <caption>HTTP transport meters</caption>
 *     <tr><th>Name</th><th>Type</th><th>Tags</th><th>Meaning</th></tr>
 *     <tr><td>{@value #CONNECTIONS_OPENED}</td><td>counter</td><td>role, transport, handshake</td>
 *         <td>Physical connection allocation</td></tr>
 *     <tr><td>{@value #CONNECTIONS_ESTABLISHED}</td><td>counter</td><td>role, transport, protocol</td>
 *         <td>First selected protocol for an established, usable connection</td></tr>
 *     <tr><td>{@value #CONNECTIONS_ACTIVE}</td><td>gauge</td><td>role, transport, protocol</td>
 *         <td>Active physical connections</td></tr>
 *     <tr><td>{@value #CONNECTIONS_CLOSED}</td><td>counter</td><td>role, transport, protocol, outcome</td>
 *         <td>Closed physical connections, attributed to the final protocol</td></tr>
 *     <tr><td>{@value #CONNECTIONS_DURATION}</td><td>timer</td><td>role, transport, protocol, outcome</td>
 *         <td>Complete physical connection lifetime, attributed to the final protocol</td></tr>
 *     <tr><td>{@value #HANDSHAKES}</td><td>counter</td><td>role, transport, handshake, outcome</td>
 *         <td>Completed transport security handshakes</td></tr>
 *     <tr><td>{@value #HANDSHAKES_DURATION}</td><td>timer</td><td>role, transport, handshake, outcome</td>
 *         <td>Transport security handshake duration</td></tr>
 *     <tr><td>{@value #STREAMS_OPENED}</td><td>counter</td><td>role, protocol, direction, initiator</td>
 *         <td>Opened multiplexed HTTP streams</td></tr>
 *     <tr><td>{@value #STREAMS_ACTIVE}</td><td>gauge</td><td>role, protocol, direction, initiator</td>
 *         <td>Active multiplexed HTTP streams</td></tr>
 *     <tr><td>{@value #STREAMS_CLOSED}</td><td>counter</td>
 *         <td>role, protocol, direction, initiator, outcome</td><td>Closed multiplexed HTTP streams</td></tr>
 *     <tr><td>{@value #STREAMS_DURATION}</td><td>timer</td>
 *         <td>role, protocol, direction, initiator, outcome</td><td>Multiplexed HTTP stream duration</td></tr>
 * </table>
 *
 * <p>Connections remain active under {@code protocol=unknown} until the first protocol selection. Only that first
 * selection increments {@value #CONNECTIONS_ESTABLISHED}, so an HTTP/1.1 connection upgraded to HTTP/2 is not counted
 * as a second physical establishment. Later selections move active attribution and determine final close attribution.
 *
 * <p>Known tag values are: roles {@code client|server}; transports {@code tcp|unix|quic}; protocols
 * {@code unknown|http/1.1|http/2|http/3}; handshakes
 * {@code none|tls|quic-tls}; directions {@code bidi|uni}; initiators {@code local|remote}; and the normalized outcome
 * names declared by the observer. Additional stable transport and protocol identifiers are used as tag values.
 * Connection or stream IDs, addresses, paths, SNI names, error text, and protocol error codes are not tags.
 *
 * <p>The adapter owns these vendor meter IDs. In particular, applications must not pre-register gauges with the same
 * IDs and tags because the Metrics API does not expose an existing gauge's backing value for safe adoption. Configured
 * registry wrappers retain their own filtering, listeners, and clock. Wrappers share lifecycle state only when their
 * returned meters expose the same native delegate identity.
 *
 * <p>Cold meter registration and removal are serialized on a virtual-thread dispatcher, so registry callbacks do not block
 * transport callbacks. At most {@value #MAX_PENDING_PROVIDER_ACTIONS} observed actions, plus one ordered cleanup action, are
 * admitted per native registry during one continuously busy dispatch wave. After that total admission budget is exhausted,
 * later observations in the same wave are discarded even if earlier work has drained and the retained backlog is small. The
 * budget resets only after no provider action remains outstanding; this prevents reentrant registry callbacks from feeding the
 * dispatcher indefinitely. Accepted actions are emitted exactly once if registration succeeds. A registry callback which does
 * not return can therefore delay accepted meter work and retain its bounded backlog, but it neither blocks transport processing
 * nor permits unbounded growth. Registration failures are logged and are not retried indefinitely.
 *
 * <p>Reaching zero leases ends an observation epoch. Successful cleanup starts the next epoch with new cumulative meters. If
 * cleanup cannot remove a meter after bounded attempts, the adapter severs its configured-registry handle but retains the native
 * meter identity and gauge backing so a later epoch can reuse it and retry cleanup; cumulative values then continue across that
 * boundary. Abandoned native registry and meter identities are weakly retained after the epoch ends, so this recovery state does
 * not keep an otherwise unreachable registry alive. A lease completion is ownership-aware: after that lease's connections have
 * closed, it completes when another lease for the same configured registry instance owns continued registry use, or when final
 * native-registry cleanup completes. Configured registry wrappers which expose the same native delegate keep a common final
 * cleanup barrier, because cleanup can still require any one of those wrappers. A registry owner must prevent later acquisition
 * and await the final owning lease's completion before closing the registry.
 */
@Api.Internal
public final class HttpTransportMetrics {
    static final String CONNECTIONS_OPENED = "http.connections.opened";
    static final String CONNECTIONS_ESTABLISHED = "http.connections.established";
    static final String CONNECTIONS_ACTIVE = "http.connections.active";
    static final String CONNECTIONS_CLOSED = "http.connections.closed";
    static final String CONNECTIONS_DURATION = "http.connections.duration";
    static final String HANDSHAKES = "http.handshakes";
    static final String HANDSHAKES_DURATION = "http.handshakes.duration";
    static final String STREAMS_OPENED = "http.streams.opened";
    static final String STREAMS_ACTIVE = "http.streams.active";
    static final String STREAMS_CLOSED = "http.streams.closed";
    static final String STREAMS_DURATION = "http.streams.duration";

    private static final String VENDOR = Meter.Scope.VENDOR;
    private static final String UNKNOWN_PROTOCOL = "";
    private static final int MAX_PENDING_PROVIDER_ACTIONS = 1024;
    private static final System.Logger LOGGER = System.getLogger(HttpTransportMetrics.class.getName());
    private static final ReferenceQueue<Object> COLLECTED_REGISTRY_IDENTITIES = new ReferenceQueue<>();
    private static final Map<IdentityReference, SharedRegistryState> INSTANCES = new HashMap<>();
    private static final ReentrantLock INSTANCES_LOCK = new ReentrantLock();

    private final ConcurrentMap<ConnectionOpenKey, Counter> connectionsOpened = new ConcurrentHashMap<>();
    private final ConcurrentMap<ConnectionKey, Counter> connectionsEstablished = new ConcurrentHashMap<>();
    private final ConcurrentMap<ConnectionKey, GaugeValue> connectionsActive = new ConcurrentHashMap<>();
    private final ConcurrentMap<ConnectionCloseKey, MeterPair> connectionsClosed = new ConcurrentHashMap<>();
    private final ConcurrentMap<HandshakeKey, MeterPair> handshakes = new ConcurrentHashMap<>();
    private final ConcurrentMap<StreamKey, Counter> streamsOpened = new ConcurrentHashMap<>();
    private final ConcurrentMap<StreamKey, GaugeValue> streamsActive = new ConcurrentHashMap<>();
    private final ConcurrentMap<StreamCloseKey, MeterPair> streamsClosed = new ConcurrentHashMap<>();
    private final Set<GaugeKey> resolvedGauges = ConcurrentHashMap.newKeySet();
    private final Set<MeterRegistration> meterRegistrations = new HashSet<>();
    private final AtomicLong pendingProviderActions = new AtomicLong();
    private final MeterRegistry registry;
    private final SharedRegistryState sharedRegistryState;
    private volatile boolean releaseRequested;
    private int referenceCount;

    private HttpTransportMetrics(MeterRegistry registry, SharedRegistryState sharedRegistryState) {
        this.registry = registry;
        this.sharedRegistryState = sharedRegistryState;
    }

    /**
     * Acquires an HTTP transport observer lease for a meter registry.
     *
     * <p>Each configured registry controls its own meter registration and filtering. Leases whose registries return the
     * same native registry share only native gauge state and final meter ownership. The caller must close the lease after
     * all transports using it have stopped. Closing is non-blocking; {@link Lease#completion()} completes after the lease's
     * connections have closed and either another lease for the same configured registry instance has taken ownership of
     * continued registry use, or accepted provider work and final meter cleanup have completed or been permanently abandoned.
     * A caller which also owns the meter registry must prevent later acquisition and await the final owning lease's completion
     * before closing the registry.
     *
     * @param registry meter registry
     * @return HTTP transport observer lease
     */
    public static Lease acquire(MeterRegistry registry) {
        Objects.requireNonNull(registry, "registry");
        Object registryIdentity = Objects.requireNonNull(registry.unwrap(Object.class), "registry delegate");
        Clock clock = registry.clock();
        List<CompletableFuture<Void>> transferredCompletions = List.of();
        Lease result;
        INSTANCES_LOCK.lock();
        try {
            expungeCollectedRegistryStates();
            IdentityReference lookupIdentity = new IdentityReference(registryIdentity);
            SharedRegistryState sharedRegistryState = INSTANCES.get(lookupIdentity);
            if (sharedRegistryState == null) {
                IdentityReference retainedIdentity = new IdentityReference(registryIdentity,
                                                                           COLLECTED_REGISTRY_IDENTITIES);
                sharedRegistryState = new SharedRegistryState(retainedIdentity, registryIdentity);
                INSTANCES.put(retainedIdentity, sharedRegistryState);
            } else if (sharedRegistryState.terminal) {
                sharedRegistryState.providerDispatchUnavailable = false;
                sharedRegistryState.rejectedProviderActions.set(0);
                sharedRegistryState.activeRegistryIdentity = registryIdentity;
                sharedRegistryState.terminal = false;
            } else if (sharedRegistryState.activeRegistryIdentity != registryIdentity) {
                throw new IllegalStateException("HTTP transport metrics registry identity changed");
            }
            HttpTransportMetrics observer = sharedRegistryState.observers.get(registry);
            if (observer == null) {
                observer = new HttpTransportMetrics(registry, sharedRegistryState);
                sharedRegistryState.observers.put(registry, observer);
            }
            LeaseImpl acquiredLease = new LeaseImpl(observer, clock);
            observer.releaseRequested = false;
            observer.referenceCount++;
            sharedRegistryState.activeLeaseCount++;
            List<CompletableFuture<Void>> pendingCompletions =
                    sharedRegistryState.pendingRegistryCompletions.remove(registry);
            if (pendingCompletions != null) {
                transferredCompletions = List.copyOf(pendingCompletions);
            }
            result = acquiredLease;
        } finally {
            INSTANCES_LOCK.unlock();
        }
        dispatchCompletions(transferredCompletions);
        return result;
    }

    private static void dispatchCompletions(List<CompletableFuture<Void>> completions) {
        if (completions.isEmpty()) {
            return;
        }
        try {
            Thread.ofVirtual()
                    .name("helidon-http-transport-metrics-completion")
                    .inheritInheritableThreadLocals(false)
                    .start(() -> completions.forEach(completion -> completion.complete(null)));
        } catch (Throwable failure) {
            LOGGER.log(WARNING, "HTTP transport metrics completion dispatcher could not start", failure);
            completions.forEach(completion -> completion.complete(null));
        }
    }

    private static void expungeCollectedRegistryStates() {
        IdentityReference collected;
        while ((collected = (IdentityReference) COLLECTED_REGISTRY_IDENTITIES.poll()) != null) {
            INSTANCES.remove(collected);
        }
    }

    private ConnectionObservation connectionOpened(Role role,
                                                   String transport,
                                                   Handshake handshake,
                                                   Clock clock,
                                                   Runnable onClosed) {
        Objects.requireNonNull(role, "role");
        requireIdentifier(transport, "transport");
        Objects.requireNonNull(handshake, "handshake");
        Objects.requireNonNull(onClosed, "onClosed");
        MetricsConnectionObservation observation = new MetricsConnectionObservation(role,
                                                                                     transport,
                                                                                     handshake,
                                                                                     clock,
                                                                                     onClosed);
        ConnectionOpenKey openKey = new ConnectionOpenKey(role, transport, handshake);
        ConnectionKey connectionKey = new ConnectionKey(role, transport, UNKNOWN_PROTOCOL);
        GaugeValue activeValue = activeConnections(connectionKey);
        activeValue.increment();
        observation.activeValue = activeValue;
        Counter opened = connectionsOpened.get(openKey);
        GaugeKey gaugeKey = new GaugeKey(CONNECTIONS_ACTIVE, connectionKey);
        if (opened != null && resolvedGauges.contains(gaugeKey)) {
            observe("connection open", opened::increment);
        } else {
            pendingProviderAction("connection open").submit(() -> {
                try {
                    cached(connectionsOpened, openKey, () -> counter(
                            CONNECTIONS_OPENED,
                            List.of(tag("role", openKey.role),
                                    tag("transport", openKey.transport),
                                    tag("handshake", openKey.handshake)),
                            "Number of opened physical HTTP connections")).increment();
                } finally {
                    registerActiveConnections(connectionKey, activeValue);
                }
            });
        }
        return observation;
    }

    private GaugeValue activeConnections(ConnectionKey key) {
        return cached(connectionsActive, key, () -> new GaugeValue(new AtomicLong()));
    }

    private void registerActiveConnections(ConnectionKey key, GaugeValue value) {
        registerGauge(new GaugeKey(CONNECTIONS_ACTIVE, key),
                      value,
                      List.of(tag("role", key.role),
                              tag("transport", key.transport),
                              tag("protocol", key.protocol)),
                      "Number of active physical HTTP connections");
    }

    private void registerGauge(GaugeKey key, GaugeValue value, List<Tag> tags, String description) {
        sharedRegistryState.register(
                registry,
                Gauge.class,
                key.name,
                tags,
                () -> registry.getOrCreate(Gauge.builder(key.name, value, GaugeValue::get)
                                                     .scope(VENDOR)
                                                     .tags(tags)
                                                     .description(description)),
                meterRegistrations,
                new GaugeRegistration(key, value));
        resolvedGauges.add(key);
    }

    private Counter counter(String name, List<Tag> tags, String description) {
        return sharedRegistryState.register(
                registry,
                Counter.class,
                name,
                tags,
                () -> registry.getOrCreate(Counter.builder(name)
                                                   .scope(VENDOR)
                                                   .tags(tags)
                                                   .description(description)),
                meterRegistrations,
                null);
    }

    private Timer timer(String name, List<Tag> tags, String description) {
        return sharedRegistryState.register(
                registry,
                Timer.class,
                name,
                tags,
                () -> registry.getOrCreate(Timer.builder(name)
                                                 .scope(VENDOR)
                                                 .tags(tags)
                                                 .baseUnit(TimeUnit.SECONDS)
                                                 .description(description)),
                meterRegistrations,
                null);
    }

    private static <K, V> V cached(ConcurrentMap<K, V> cache, K key, Supplier<V> supplier) {
        V existing = cache.get(key);
        if (existing != null) {
            return existing;
        }
        V candidate = supplier.get();
        V raced = cache.putIfAbsent(key, candidate);
        return raced == null ? candidate : raced;
    }

    private static void observe(String event, Runnable action) {
        try {
            action.run();
        } catch (Throwable failure) {
            LOGGER.log(WARNING, "HTTP transport metrics failed while processing " + event, failure);
        }
    }

    private PendingProviderAction pendingProviderAction(String event) {
        pendingProviderActions.incrementAndGet();
        return new PendingProviderAction(event);
    }

    private void release(CompletableFuture<Void> leaseCompletion) {
        boolean releaseBackend;
        boolean ownershipTransferred;
        INSTANCES_LOCK.lock();
        try {
            referenceCount--;
            sharedRegistryState.activeLeaseCount--;
            if (referenceCount != 0) {
                releaseBackend = false;
                ownershipTransferred = true;
            } else {
                ownershipTransferred = false;
                sharedRegistryState.pendingRegistryCompletions
                        .computeIfAbsent(registry, _ -> new ArrayList<>())
                        .add(leaseCompletion);
                releaseRequested = true;
                releaseBackend = pendingProviderActions.get() == 0;
                if (releaseBackend) {
                    releaseRequested = false;
                    sharedRegistryState.observers.remove(registry);
                    sharedRegistryState.pendingBackendReleases++;
                }
            }
        } finally {
            INSTANCES_LOCK.unlock();
        }

        if (ownershipTransferred) {
            dispatchCompletions(List.of(leaseCompletion));
        }
        if (releaseBackend) {
            releaseBackend();
        }
    }

    private void releaseBackend() {
        try {
            sharedRegistryState.meterLock.lock();
            try {
                for (MeterRegistration registration : meterRegistrations) {
                    MeterBinding binding = registration.binding;
                    binding.registrations.remove(registration);
                    if (binding.removalRegistration == registration) {
                        if (binding.registrations.isEmpty()) {
                            binding.removalRegistration = registration;
                        } else {
                            binding.removalRegistration = binding.registrations.iterator().next();
                        }
                    }
                }
                meterRegistrations.clear();
            } finally {
                sharedRegistryState.meterLock.unlock();
            }
            connectionsOpened.clear();
            connectionsEstablished.clear();
            connectionsActive.clear();
            connectionsClosed.clear();
            handshakes.clear();
            streamsOpened.clear();
            streamsActive.clear();
            streamsClosed.clear();
            resolvedGauges.clear();
        } finally {
            boolean cleanupMeters = false;
            INSTANCES_LOCK.lock();
            try {
                sharedRegistryState.pendingBackendReleases--;
                if (sharedRegistryState.activeLeaseCount == 0
                        && sharedRegistryState.pendingBackendReleases == 0
                        && sharedRegistryState.observers.isEmpty()
                        && !sharedRegistryState.cleanupInProgress) {
                    sharedRegistryState.cleanupInProgress = true;
                    cleanupMeters = true;
                }
            } finally {
                INSTANCES_LOCK.unlock();
            }
            if (cleanupMeters) {
                sharedRegistryState.submitProvider("meter cleanup",
                                                   sharedRegistryState::cleanupMeters,
                                                   sharedRegistryState::finishCleanup,
                                                   true);
            }
        }
    }

    private <K> void record(PendingProviderAction providerAction,
                            ConcurrentMap<K, MeterPair> meters,
                            K key,
                            Supplier<MeterPair> supplier,
                            long duration) {
        MeterPair existing = meters.get(key);
        Runnable recorder = () -> {
            MeterPair meterPair = existing == null ? cached(meters, key, supplier) : existing;
            meterPair.counter.increment();
            meterPair.timer.record(duration, TimeUnit.NANOSECONDS);
        };
        if (existing == null) {
            providerAction.submit(recorder);
        } else {
            providerAction.execute(recorder);
        }
    }

    private static long elapsed(Clock clock, TimeSample started, String event) {
        TimeSample ended = monotonicTime(clock, event);
        return started.valid() && ended.valid() ? Math.max(0, ended.value() - started.value()) : 0;
    }

    private static TimeSample monotonicTime(Clock clock, String event) {
        try {
            return new TimeSample(clock.monotonicTime(), true);
        } catch (Throwable failure) {
            LOGGER.log(WARNING, "HTTP transport metrics clock failed while processing " + event, failure);
            return new TimeSample(0, false);
        }
    }

    private static Tag tag(String name, String value) {
        return Tag.create(name, value.isEmpty() ? "unknown" : value);
    }

    private static Tag tag(String name, Enum<?> value) {
        String tagValue;
        if (value instanceof Direction direction) {
            tagValue = direction == Direction.BIDIRECTIONAL ? "bidi" : "uni";
        } else {
            tagValue = value.name().toLowerCase(Locale.ROOT).replace('_', '-');
        }
        return Tag.create(name, tagValue);
    }

    private static String requireIdentifier(String identifier, String name) {
        Objects.requireNonNull(identifier, name);
        if (identifier.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return identifier;
    }

    private static Map<String, String> tagMap(List<Tag> tags) {
        Map<String, String> result = new HashMap<>();
        tags.forEach(tag -> result.put(tag.key(), tag.value()));
        return result;
    }

    /**
     * Owned lease for observing HTTP transport lifecycle.
     */
    public interface Lease extends HttpTransportObserver, AutoCloseable {
        /**
         * Starts the non-blocking release of this lease.
         */
        @Override
        void close();

        /**
         * Completion of this lease's asynchronous release.
         *
         * <p>The stage completes only after {@link #close()} has been called, all connections opened through the lease have
         * closed, and either another lease for the same configured registry instance has taken ownership of continued registry
         * use, or all accepted provider work and final native-registry meter cleanup have completed or been permanently
         * abandoned. A stalled accepted registry callback can delay final completion indefinitely; observations rejected before
         * acceptance do not delay it. The registry must remain usable until this stage completes. A completion caused by
         * ownership handoff is not a registry-close barrier; a registry owner must prevent later acquisition and await the final
         * owning lease's completion before closing the registry.
         *
         * @return release completion
         */
        CompletionStage<Void> completion();
    }

    private final class PendingProviderAction {
        private final AtomicBoolean completed = new AtomicBoolean();
        private final String event;

        private PendingProviderAction(String event) {
            this.event = event;
        }

        private void execute(Runnable action) {
            try {
                observe(event, action);
            } finally {
                complete();
            }
        }

        private void submit(Runnable action) {
            sharedRegistryState.submitProvider(event,
                                               () -> {
                                                   try {
                                                       action.run();
                                                   } finally {
                                                       complete();
                                                   }
                                               },
                                               this::complete,
                                               false);
        }

        private void complete() {
            if (completed.compareAndSet(false, true)) {
                if (pendingProviderActions.decrementAndGet() != 0 || !releaseRequested) {
                    return;
                }
                boolean releaseBackend = false;
                INSTANCES_LOCK.lock();
                try {
                    if (referenceCount == 0 && releaseRequested && pendingProviderActions.get() == 0) {
                        releaseRequested = false;
                        sharedRegistryState.observers.remove(registry);
                        sharedRegistryState.pendingBackendReleases++;
                        releaseBackend = true;
                    }
                } finally {
                    INSTANCES_LOCK.unlock();
                }
                if (releaseBackend) {
                    releaseBackend();
                }
            }
        }
    }

    private static final class LeaseImpl implements Lease {
        private final ReentrantLock lifecycleLock = new ReentrantLock();
        private final Set<LeaseConnectionObservation> connections = new HashSet<>();
        private final Clock clock;
        private final CompletableFuture<Void> completion = new CompletableFuture<>();
        private final CompletionStage<Void> completionView = completion.minimalCompletionStage();
        private HttpTransportMetrics delegate;
        private int inFlightCreates;
        private boolean closed;
        private boolean released;

        private LeaseImpl(HttpTransportMetrics delegate, Clock clock) {
            this.delegate = delegate;
            this.clock = clock;
        }

        @Override
        public ConnectionObservation connectionOpened(Role role, String transport, Handshake handshake) {
            Objects.requireNonNull(role, "role");
            requireIdentifier(transport, "transport");
            Objects.requireNonNull(handshake, "handshake");
            HttpTransportMetrics current;
            LeaseConnectionObservation slot;
            lifecycleLock.lock();
            try {
                if (closed) {
                    return ConnectionObservation.noop();
                }
                current = delegate;
                slot = new LeaseConnectionObservation(this);
                connections.add(slot);
                inFlightCreates++;
            } finally {
                lifecycleLock.unlock();
            }
            try {
                slot.delegate = Objects.requireNonNull(current.connectionOpened(role,
                                                                                transport,
                                                                                handshake,
                                                                                clock,
                                                                                slot::terminal));
                return slot;
            } catch (Throwable failure) {
                slot.terminal();
                LOGGER.log(WARNING, "HTTP transport metrics failed while opening a connection", failure);
                return ConnectionObservation.noop();
            } finally {
                HttpTransportMetrics release = null;
                lifecycleLock.lock();
                try {
                    inFlightCreates--;
                    release = releaseIfEligible();
                } finally {
                    lifecycleLock.unlock();
                }
                if (release != null) {
                    release.release(completion);
                }
            }
        }

        @Override
        public void close() {
            HttpTransportMetrics release = null;
            lifecycleLock.lock();
            try {
                if (closed) {
                    return;
                }
                closed = true;
                release = releaseIfEligible();
            } finally {
                lifecycleLock.unlock();
            }
            if (release != null) {
                release.release(completion);
            }
        }

        @Override
        public CompletionStage<Void> completion() {
            return completionView;
        }

        private void connectionClosed(LeaseConnectionObservation connection) {
            HttpTransportMetrics release = null;
            lifecycleLock.lock();
            try {
                connections.remove(connection);
                release = releaseIfEligible();
            } finally {
                lifecycleLock.unlock();
            }
            if (release != null) {
                release.release(completion);
            }
        }

        private HttpTransportMetrics releaseIfEligible() {
            if (!closed || released || inFlightCreates != 0 || !connections.isEmpty()) {
                return null;
            }
            released = true;
            HttpTransportMetrics result = delegate;
            delegate = null;
            return result;
        }
    }

    private static final class LeaseConnectionObservation implements ConnectionObservation {
        private final AtomicBoolean closed = new AtomicBoolean();
        private final LeaseImpl owner;
        private volatile ConnectionObservation delegate = ConnectionObservation.noop();

        private LeaseConnectionObservation(LeaseImpl owner) {
            this.owner = owner;
        }

        @Override
        public HandshakeObservation handshakeStarted() {
            return closed.get() ? HandshakeObservation.noop() : delegate.handshakeStarted();
        }

        @Override
        public void protocolSelected(String protocol) {
            requireIdentifier(protocol, "protocol");
            if (!closed.get()) {
                delegate.protocolSelected(protocol);
            }
        }

        @Override
        public StreamObservation streamOpened(Direction direction, Initiator initiator) {
            Objects.requireNonNull(direction, "direction");
            Objects.requireNonNull(initiator, "initiator");
            return closed.get() ? StreamObservation.noop() : delegate.streamOpened(direction, initiator);
        }

        @Override
        public void close(ConnectionOutcome outcome) {
            Objects.requireNonNull(outcome, "outcome");
            if (closed.compareAndSet(false, true)) {
                try {
                    delegate.close(outcome);
                } finally {
                    terminal();
                }
            }
        }

        private void terminal() {
            closed.set(true);
            owner.connectionClosed(this);
        }
    }

    private static final class SharedRegistryState {
        private final Map<MeterRegistry, HttpTransportMetrics> observers = new IdentityHashMap<>();
        private final Map<MeterRegistry, List<CompletableFuture<Void>>> pendingRegistryCompletions =
                new IdentityHashMap<>();
        private final Map<IdentityReference, MeterBinding> meterBindings = new HashMap<>();
        private final ConcurrentMap<GaugeKey, AtomicLong> gaugeValues = new ConcurrentHashMap<>();
        private final ConcurrentLinkedQueue<ProviderTask> providerActions = new ConcurrentLinkedQueue<>();
        private final AtomicLong providerActionState = new AtomicLong();
        private final AtomicBoolean providerDispatchScheduled = new AtomicBoolean();
        private final AtomicLong rejectedProviderActions = new AtomicLong();
        private final ReferenceQueue<Object> collectedMeterIdentities = new ReferenceQueue<>();
        private final ReentrantLock meterLock = new ReentrantLock();
        private final IdentityReference registryIdentity;
        private Object activeRegistryIdentity;
        private volatile boolean providerDispatchUnavailable;
        private volatile boolean hasBindings;
        private int activeLeaseCount;
        private int pendingBackendReleases;
        private boolean cleanupInProgress;
        private boolean terminal;
        private long cleanupPass;

        private SharedRegistryState(IdentityReference registryIdentity, Object activeRegistryIdentity) {
            this.registryIdentity = registryIdentity;
            this.activeRegistryIdentity = activeRegistryIdentity;
        }

        private <M extends Meter> M register(MeterRegistry registry,
                                             Class<M> meterType,
                                             String name,
                                             List<Tag> tags,
                                             Supplier<M> registrar,
                                             Set<MeterRegistration> registrations,
                                             GaugeRegistration gaugeRegistration) {
            Map<String, String> tagValues = tagMap(tags);
            boolean enabled = registry.isMeterEnabled(name, tagValues, Optional.of(VENDOR));
            GaugeKey gaugeKey = gaugeRegistration == null ? null : gaugeRegistration.key;
            MeterKey meterKey = new MeterKey(name, Map.copyOf(tagValues));
            if (gaugeRegistration != null) {
                GaugeValue gaugeValue = gaugeRegistration.value;
                AtomicLong sharedValue = enabled ? gaugeValues.computeIfAbsent(gaugeKey, _ -> new AtomicLong()) : null;
                gaugeValue.resolutionLock.lock();
                try {
                    if (!gaugeValue.resolved) {
                        if (sharedValue != null && gaugeValue.value != sharedValue) {
                            sharedValue.addAndGet(gaugeValue.value.get());
                            gaugeValue.value = sharedValue;
                        }
                        gaugeValue.resolved = true;
                    }
                } finally {
                    gaugeValue.resolutionLock.unlock();
                }
            }
            M meter;
            try {
                meter = registrar.get();
            } catch (RuntimeException failure) {
                M recovered = null;
                if (enabled) {
                    try {
                        Optional<M> registered = vendorMeter(registry, meterType, name, tags);
                        if (registered.isPresent()) {
                            LOGGER.log(WARNING,
                                       "Meter registry callback failed after registering an HTTP transport meter",
                                       failure);
                            recovered = registered.get();
                        }
                    } catch (RuntimeException recoveryFailure) {
                        failure.addSuppressed(recoveryFailure);
                    }
                }
                if (recovered == null) {
                    try {
                        recovered = registrar.get();
                        LOGGER.log(WARNING,
                                   "Meter registry callback failed during the first HTTP transport meter registration",
                                   failure);
                    } catch (RuntimeException retryFailure) {
                        failure.addSuppressed(retryFailure);
                        throw failure;
                    }
                }
                meter = recovered;
            }
            if (!enabled) {
                return meter;
            }
            if (meter.scope().filter(VENDOR::equals).isEmpty()) {
                throw new IllegalStateException("HTTP transport meter must use vendor scope");
            }
            Object meterIdentity = Objects.requireNonNull(meter.unwrap(Object.class), "meter delegate");
            Meter.Type type = meter.type();
            meterLock.lock();
            try {
                expungeCollectedMeterBindings();
                IdentityReference lookupIdentity = new IdentityReference(meterIdentity);
                MeterBinding binding = meterBindings.get(lookupIdentity);
                if (binding == null) {
                    meterBindings.entrySet().removeIf(entry -> {
                        MeterBinding candidate = entry.getValue();
                        return candidate.registrations.isEmpty()
                                && candidate.removalRegistration == null
                                && candidate.type == type
                                && candidate.meterKey.equals(meterKey);
                    });
                    binding = new MeterBinding(type, gaugeKey, meterKey);
                    meterBindings.put(new IdentityReference(meterIdentity, collectedMeterIdentities), binding);
                    hasBindings = true;
                } else if (binding.type != type
                        || !Objects.equals(gaugeKey, binding.gaugeKey)
                        || !binding.meterKey.equals(meterKey)) {
                    throw new IllegalStateException("Meter registry reused an HTTP transport meter delegate");
                }
                binding.removing = false;
                binding.lastRemovalPass = 0;
                for (MeterRegistration registration : binding.registrations) {
                    if (registration.owner == registrations) {
                        binding.removalRegistration = registration;
                        return meter;
                    }
                }
                MeterRegistration registration = new MeterRegistration(registry,
                                                                        meter,
                                                                        meterIdentity,
                                                                        binding,
                                                                        registrations);
                registrations.add(registration);
                binding.registrations.add(registration);
                binding.removalRegistration = registration;
                return meter;
            } finally {
                meterLock.unlock();
            }
        }

        private void expungeCollectedMeterBindings() {
            IdentityReference collected;
            while ((collected = (IdentityReference) collectedMeterIdentities.poll()) != null) {
                meterBindings.remove(collected);
            }
        }

        private static <M extends Meter> Optional<M> vendorMeter(MeterRegistry registry,
                                                                 Class<M> meterType,
                                                                 String name,
                                                                 Iterable<Tag> tags) {
            Map<String, String> expectedTags = new HashMap<>();
            tags.forEach(tag -> expectedTags.put(tag.key(), tag.value()));
            for (Meter candidate : registry.meters(List.of(VENDOR))) {
                if (candidate.scope().filter(VENDOR::equals).isEmpty()
                        || !candidate.id().name().equals(name)
                        || !candidate.id().tagsMap().equals(expectedTags)) {
                    continue;
                }
                if (!meterType.isInstance(candidate)) {
                    throw new IllegalStateException("HTTP transport meter has an incompatible type");
                }
                return Optional.of(meterType.cast(candidate));
            }
            return Optional.empty();
        }

        private void submitProvider(String event, Runnable action, Runnable rejection, boolean control) {
            ProviderTask task = new ProviderTask(event, action, rejection, control);
            boolean admitted = false;
            while (!providerDispatchUnavailable) {
                long state = providerActionState.get();
                long admissions = state >>> 32;
                long outstanding = state & 0xFFFFFFFFL;
                if ((!control && admissions >= MAX_PENDING_PROVIDER_ACTIONS)
                        || outstanding >= MAX_PENDING_PROVIDER_ACTIONS + 1L) {
                    break;
                }
                long next = control
                        ? state + 1
                        : ((admissions + 1) << 32) | (outstanding + 1);
                if (providerActionState.compareAndSet(state, next)) {
                    admitted = true;
                    break;
                }
            }
            if (!admitted) {
                rejectedProviderActions.incrementAndGet();
                observe(event + " rejection", rejection);
                return;
            }
            providerActions.add(task);
            if (providerDispatchUnavailable && providerActions.remove(task)) {
                providerActionCompleted();
                observe(event + " rejection", rejection);
                return;
            }
            scheduleProviderDispatch();
        }

        private void scheduleProviderDispatch() {
            if (providerDispatchUnavailable) {
                return;
            }
            if (!providerDispatchScheduled.compareAndSet(false, true)) {
                return;
            }
            try {
                Thread.ofVirtual()
                        .name("helidon-http-transport-metrics")
                        .inheritInheritableThreadLocals(false)
                        .start(this::drainProviderActions);
            } catch (Throwable failure) {
                providerDispatchUnavailable = true;
                providerDispatchScheduled.set(false);
                List<ProviderTask> rejectedTasks = new ArrayList<>();
                ProviderTask rejected;
                while ((rejected = providerActions.poll()) != null) {
                    providerActionCompleted();
                    rejectedTasks.add(rejected);
                }
                for (ProviderTask rejectedTask : rejectedTasks) {
                    observe(rejectedTask.event + " rejection", rejectedTask.rejection);
                }
                LOGGER.log(WARNING, "HTTP transport metrics provider dispatcher could not start", failure);
            }
        }

        private void drainProviderActions() {
            try {
                ProviderTask task;
                while ((task = providerActions.poll()) != null) {
                    try {
                        observe(task.event, task.action);
                    } finally {
                        providerActionCompleted();
                        if (task.control) {
                            finishCleanup();
                        }
                    }
                    long rejected = rejectedProviderActions.getAndSet(0);
                    if (rejected != 0) {
                        LOGGER.log(WARNING,
                                   "HTTP transport metrics discarded " + rejected
                                           + " provider actions at the busy-wave admission limit");
                    }
                }
            } finally {
                providerDispatchScheduled.set(false);
                if (!providerActions.isEmpty()) {
                    scheduleProviderDispatch();
                }
            }
        }

        private void providerActionCompleted() {
            while (true) {
                long state = providerActionState.get();
                long outstanding = state & 0xFFFFFFFFL;
                long next = outstanding == 1 ? 0 : state - 1;
                if (providerActionState.compareAndSet(state, next)) {
                    return;
                }
            }
        }

        private void cleanupMeters() {
            int remainingPasses = 2;
            boolean repeat;
            do {
                List<RemovalAttempt> attempts = new ArrayList<>();
                long pass;
                meterLock.lock();
                try {
                    expungeCollectedMeterBindings();
                    pass = ++cleanupPass;
                    for (Map.Entry<IdentityReference, MeterBinding> entry : meterBindings.entrySet()) {
                        MeterBinding binding = entry.getValue();
                        if (!binding.removing) {
                            attempts.add(new RemovalAttempt(entry.getKey(), binding));
                        }
                    }
                } finally {
                    meterLock.unlock();
                }

                for (RemovalAttempt attempt : attempts) {
                    boolean cleanupAllowed;
                    INSTANCES_LOCK.lock();
                    try {
                        cleanupAllowed = activeLeaseCount == 0
                                && pendingBackendReleases == 0
                                && observers.isEmpty();
                    } finally {
                        INSTANCES_LOCK.unlock();
                    }
                    if (!cleanupAllowed) {
                        break;
                    }
                    boolean currentAttempt;
                    MeterRegistration registration;
                    meterLock.lock();
                    try {
                        currentAttempt = meterBindings.get(attempt.identity) == attempt.binding
                                && !attempt.binding.removing
                                && attempt.binding.registrations.isEmpty();
                        if (currentAttempt) {
                            attempt.binding.removing = true;
                            attempt.binding.lastRemovalPass = pass;
                            registration = attempt.binding.removalRegistration;
                        } else {
                            registration = null;
                        }
                    } finally {
                        meterLock.unlock();
                    }
                    if (!currentAttempt) {
                        continue;
                    }

                    boolean removed = false;
                    Throwable removalFailure = null;
                    Object nativeIdentity = registration == null
                            ? attempt.identity.get()
                            : registration.meterIdentity;
                    if (nativeIdentity == null) {
                        removed = true;
                    } else if (registration == null) {
                        removalFailure = new IllegalStateException("HTTP transport meter has no removal registry");
                    } else {
                        Meter meter = registration.meter;
                        try {
                            removed = registration.registry.remove(meter).isPresent();
                        } catch (Throwable failure) {
                            removalFailure = failure;
                        }
                        if (!removed) {
                            try {
                                removed = registration.registry.isDeleted(meter);
                            } catch (Throwable failure) {
                                if (removalFailure == null) {
                                    removalFailure = failure;
                                } else {
                                    removalFailure.addSuppressed(failure);
                                }
                            }
                        }
                        if (!removed) {
                            try {
                                Optional<Meter> registered = vendorMeter(registration.registry,
                                                                        Meter.class,
                                                                        meter.id().name(),
                                                                        meter.id().tags());
                                removed = registered.isEmpty()
                                        || registered.map(candidate -> candidate.unwrap(Object.class)
                                        != nativeIdentity).orElse(true);
                            } catch (Throwable failure) {
                                if (removalFailure == null) {
                                    removalFailure = failure;
                                } else {
                                    removalFailure.addSuppressed(failure);
                                }
                            }
                        }
                    }

                    meterLock.lock();
                    try {
                        if (meterBindings.get(attempt.identity) == attempt.binding
                                && attempt.binding.lastRemovalPass == pass
                                && attempt.binding.removing) {
                            if (removed) {
                                meterBindings.remove(attempt.identity);
                            } else {
                                attempt.binding.removing = false;
                            }
                        }
                        hasBindings = !meterBindings.isEmpty();
                    } finally {
                        meterLock.unlock();
                    }
                    if (removalFailure != null) {
                        LOGGER.log(WARNING, "Failed to remove an HTTP transport meter", removalFailure);
                    } else if (!removed) {
                        LOGGER.log(WARNING, "Meter registry did not confirm removal of an HTTP transport meter");
                    }
                }

                meterLock.lock();
                try {
                    hasBindings = !meterBindings.isEmpty();
                } finally {
                    meterLock.unlock();
                }
                INSTANCES_LOCK.lock();
                try {
                    repeat = --remainingPasses > 0
                            && activeLeaseCount == 0
                            && pendingBackendReleases == 0
                            && observers.isEmpty()
                            && hasBindings;
                } finally {
                    INSTANCES_LOCK.unlock();
                }
            } while (repeat);

        }

        private void finishCleanup() {
            List<CompletableFuture<Void>> completedLeases = List.of();
            int retainedBindings = 0;
            boolean cleanupCompleted = false;
            INSTANCES_LOCK.lock();
            try {
                expungeCollectedRegistryStates();
                cleanupInProgress = false;
                if (activeLeaseCount == 0
                        && pendingBackendReleases == 0
                        && observers.isEmpty()) {
                    meterLock.lock();
                    try {
                        expungeCollectedMeterBindings();
                        retainedBindings = meterBindings.size();
                        if (retainedBindings == 0) {
                            gaugeValues.clear();
                            hasBindings = false;
                            INSTANCES.remove(registryIdentity, this);
                        } else {
                            for (MeterBinding binding : meterBindings.values()) {
                                binding.registrations.clear();
                                binding.removalRegistration = null;
                                binding.removing = false;
                                binding.lastRemovalPass = 0;
                            }
                            hasBindings = true;
                        }
                    } finally {
                        meterLock.unlock();
                    }
                    terminal = true;
                    activeRegistryIdentity = null;
                    List<CompletableFuture<Void>> pendingCompletions = new ArrayList<>();
                    pendingRegistryCompletions.values().forEach(pendingCompletions::addAll);
                    pendingRegistryCompletions.clear();
                    completedLeases = List.copyOf(pendingCompletions);
                    cleanupCompleted = true;
                }
            } finally {
                INSTANCES_LOCK.unlock();
            }
            if (!cleanupCompleted) {
                return;
            }
            if (retainedBindings != 0) {
                LOGGER.log(WARNING,
                           "HTTP transport metrics retained " + retainedBindings
                                   + " meter bindings after bounded cleanup");
            }
            dispatchCompletions(completedLeases);
        }
    }

    private final class MetricsConnectionObservation implements ConnectionObservation {
        private final ReentrantLock lifecycleLock = new ReentrantLock();
        private final Set<MetricsStreamObservation> streams = new HashSet<>();
        private final Role role;
        private final String transport;
        private final Handshake handshake;
        private final Clock clock;
        private final Runnable onClosed;
        private final TimeSample started;
        private GaugeValue activeValue;
        private String protocol = UNKNOWN_PROTOCOL;
        private MetricsHandshakeObservation handshakeObservation;
        private long protocolGeneration;
        private boolean established;
        private boolean closed;

        private MetricsConnectionObservation(Role role,
                                             String transport,
                                             Handshake handshake,
                                             Clock clock,
                                             Runnable onClosed) {
            this.role = role;
            this.transport = transport;
            this.handshake = handshake;
            this.clock = clock;
            this.onClosed = onClosed;
            this.started = monotonicTime(clock, "connection open");
        }

        @Override
        public HandshakeObservation handshakeStarted() {
            TimeSample handshakeStarted = monotonicTime(clock, "handshake start");
            lifecycleLock.lock();
            try {
                if (closed) {
                    return HandshakeObservation.noop();
                }
                if (handshakeObservation == null) {
                    handshakeObservation = new MetricsHandshakeObservation(this, handshakeStarted);
                }
                return handshakeObservation;
            } finally {
                lifecycleLock.unlock();
            }
        }

        @Override
        public void protocolSelected(String protocol) {
            requireIdentifier(protocol, "protocol");
            ConnectionKey selectedKey;
            long generation;
            boolean firstSelection;
            PendingProviderAction providerAction;
            lifecycleLock.lock();
            try {
                if (closed || this.protocol.equals(protocol)) {
                    return;
                }
                this.protocol = protocol;
                generation = ++protocolGeneration;
                firstSelection = !established;
                if (firstSelection) {
                    established = true;
                }
                selectedKey = new ConnectionKey(role, transport, protocol);
                providerAction = pendingProviderAction("connection protocol selection");
            } finally {
                lifecycleLock.unlock();
            }

            GaugeValue selectedValue = activeConnections(selectedKey);
            lifecycleLock.lock();
            try {
                if (!closed && protocolGeneration == generation && this.protocol.equals(protocol)) {
                    GaugeValue previous = activeValue;
                    activeValue = selectedValue;
                    previous.decrement();
                    selectedValue.increment();
                }
            } finally {
                lifecycleLock.unlock();
            }

            GaugeKey gaugeKey = new GaugeKey(CONNECTIONS_ACTIVE, selectedKey);
            Counter establishedMeter = firstSelection ? connectionsEstablished.get(selectedKey) : null;
            Runnable recorder = () -> {
                try {
                    if (firstSelection) {
                        cached(connectionsEstablished,
                               selectedKey,
                               () -> counter(
                                       CONNECTIONS_ESTABLISHED,
                                       List.of(tag("role", selectedKey.role),
                                               tag("transport", selectedKey.transport),
                                               tag("protocol", selectedKey.protocol)),
                                       "Number of established physical HTTP connections")).increment();
                    }
                } finally {
                    registerActiveConnections(selectedKey, selectedValue);
                }
            };
            if ((!firstSelection || establishedMeter != null) && resolvedGauges.contains(gaugeKey)) {
                providerAction.execute(() -> {
                    if (firstSelection) {
                        establishedMeter.increment();
                    }
                });
            } else {
                providerAction.submit(recorder);
            }
        }

        @Override
        public StreamObservation streamOpened(Direction direction, Initiator initiator) {
            Objects.requireNonNull(direction, "direction");
            Objects.requireNonNull(initiator, "initiator");
            TimeSample streamStarted = monotonicTime(clock, "stream open");
            MetricsStreamObservation stream;
            StreamKey key;
            lifecycleLock.lock();
            try {
                if (closed) {
                    return StreamObservation.noop();
                }
                key = new StreamKey(role, protocol, direction, initiator);
                stream = new MetricsStreamObservation(this,
                                                      key,
                                                      streamStarted,
                                                      pendingProviderAction("stream open"));
                streams.add(stream);
            } finally {
                lifecycleLock.unlock();
            }

            GaugeValue activeStreamValue = cached(streamsActive,
                                                  key,
                                                  () -> new GaugeValue(new AtomicLong()));
            GaugeValue selectedActiveStreamValue = activeStreamValue;
            stream.lifecycleLock.lock();
            try {
                if (!stream.closed) {
                    stream.activeValue = selectedActiveStreamValue;
                    selectedActiveStreamValue.increment();
                }
            } finally {
                stream.lifecycleLock.unlock();
            }

            Counter opened = streamsOpened.get(key);
            GaugeKey gaugeKey = new GaugeKey(STREAMS_ACTIVE, key);
            if (opened != null && resolvedGauges.contains(gaugeKey)) {
                stream.openProviderAction.execute(opened::increment);
            } else {
                stream.openProviderAction.submit(() -> {
                    List<Tag> tags = List.of(tag("role", key.role),
                                             tag("protocol", key.protocol),
                                             tag("direction", key.direction),
                                             tag("initiator", key.initiator));
                    try {
                        cached(streamsOpened, key, () -> counter(
                                STREAMS_OPENED,
                                tags,
                                "Number of opened multiplexed HTTP streams")).increment();
                    } finally {
                        registerGauge(gaugeKey,
                                      selectedActiveStreamValue,
                                      tags,
                                      "Number of active multiplexed HTTP streams");
                    }
                });
            }
            return stream;
        }

        @Override
        public void close(ConnectionOutcome outcome) {
            Objects.requireNonNull(outcome, "outcome");
            MetricsHandshakeObservation handshakeToClose;
            List<MetricsStreamObservation> streamsToClose;
            GaugeValue activeToClose;
            String finalProtocol;
            PendingProviderAction providerAction;
            lifecycleLock.lock();
            try {
                if (closed) {
                    return;
                }
                providerAction = pendingProviderAction("connection close");
                closed = true;
                handshakeToClose = handshakeObservation;
                streamsToClose = List.copyOf(streams);
                streams.clear();
                activeToClose = activeValue;
                activeValue = null;
                finalProtocol = protocol;
            } finally {
                lifecycleLock.unlock();
            }

            try {
                if (handshakeToClose != null) {
                    HandshakeOutcome handshakeOutcome = switch (outcome) {
                        case NORMAL, LOCAL_CLOSE -> HandshakeOutcome.CANCELLED;
                        case REMOTE_CLOSE, ERROR -> HandshakeOutcome.FAILURE;
                        case TIMEOUT -> HandshakeOutcome.TIMEOUT;
                    };
                    handshakeToClose.close(handshakeOutcome);
                }
                StreamOutcome streamOutcome = switch (outcome) {
                    case NORMAL, LOCAL_CLOSE, REMOTE_CLOSE -> StreamOutcome.CANCELLED;
                    case TIMEOUT, ERROR -> StreamOutcome.ERROR;
                };
                for (MetricsStreamObservation stream : streamsToClose) {
                    stream.close(streamOutcome);
                }
                if (activeToClose != null) {
                    activeToClose.decrement();
                }
                ConnectionCloseKey key = new ConnectionCloseKey(role, transport, finalProtocol, outcome);
                record(providerAction, connectionsClosed, key, () -> {
                    List<Tag> tags = List.of(tag("role", key.role),
                                             tag("transport", key.transport),
                                             tag("protocol", key.protocol),
                                             tag("outcome", key.outcome));
                    return new MeterPair(counter(CONNECTIONS_CLOSED,
                                                 tags,
                                                 "Number of closed physical HTTP connections"),
                                         timer(CONNECTIONS_DURATION,
                                               tags,
                                               "Duration of physical HTTP connections"));
                }, elapsed(clock, started, "connection close"));
            } finally {
                observe("connection lease release", onClosed);
            }
        }

    }

    private final class MetricsHandshakeObservation implements HandshakeObservation {
        private final ReentrantLock lifecycleLock = new ReentrantLock();
        private final MetricsConnectionObservation owner;
        private final TimeSample started;
        private boolean closed;

        private MetricsHandshakeObservation(MetricsConnectionObservation owner, TimeSample started) {
            this.owner = owner;
            this.started = started;
        }

        @Override
        public void close(HandshakeOutcome outcome) {
            Objects.requireNonNull(outcome, "outcome");
            PendingProviderAction providerAction;
            lifecycleLock.lock();
            try {
                if (closed) {
                    return;
                }
                providerAction = pendingProviderAction("handshake close");
                closed = true;
            } finally {
                lifecycleLock.unlock();
            }
            HandshakeKey key = new HandshakeKey(owner.role, owner.transport, owner.handshake, outcome);
            record(providerAction, handshakes, key, () -> {
                List<Tag> tags = List.of(tag("role", key.role),
                                         tag("transport", key.transport),
                                         tag("handshake", key.handshake),
                                         tag("outcome", key.outcome));
                return new MeterPair(counter(HANDSHAKES,
                                             tags,
                                             "Number of completed HTTP transport handshakes"),
                                     timer(HANDSHAKES_DURATION,
                                           tags,
                                           "Duration of HTTP transport handshakes"));
            }, elapsed(owner.clock, started, "handshake close"));
        }
    }

    private final class MetricsStreamObservation implements StreamObservation {
        private final ReentrantLock lifecycleLock = new ReentrantLock();
        private final MetricsConnectionObservation owner;
        private final StreamKey key;
        private final TimeSample started;
        private final PendingProviderAction openProviderAction;
        private GaugeValue activeValue;
        private boolean closed;

        private MetricsStreamObservation(MetricsConnectionObservation owner,
                                         StreamKey key,
                                         TimeSample started,
                                         PendingProviderAction openProviderAction) {
            this.owner = owner;
            this.key = key;
            this.started = started;
            this.openProviderAction = openProviderAction;
        }

        @Override
        public void close(StreamOutcome outcome) {
            Objects.requireNonNull(outcome, "outcome");
            GaugeValue activeToClose;
            PendingProviderAction providerAction;
            lifecycleLock.lock();
            try {
                if (closed) {
                    return;
                }
                providerAction = pendingProviderAction("stream close");
                closed = true;
                activeToClose = activeValue;
                activeValue = null;
            } finally {
                lifecycleLock.unlock();
            }
            owner.lifecycleLock.lock();
            try {
                owner.streams.remove(this);
            } finally {
                owner.lifecycleLock.unlock();
            }
            if (activeToClose != null) {
                activeToClose.decrement();
            }
            StreamCloseKey closeKey = new StreamCloseKey(key, outcome);
            record(providerAction, streamsClosed, closeKey, () -> {
                List<Tag> tags = List.of(tag("role", closeKey.stream.role),
                                         tag("protocol", closeKey.stream.protocol),
                                         tag("direction", closeKey.stream.direction),
                                         tag("initiator", closeKey.stream.initiator),
                                         tag("outcome", closeKey.outcome));
                return new MeterPair(counter(STREAMS_CLOSED,
                                             tags,
                                             "Number of closed multiplexed HTTP streams"),
                                     timer(STREAMS_DURATION,
                                           tags,
                                           "Duration of multiplexed HTTP streams"));
            }, elapsed(owner.clock, started, "stream close"));
        }
    }

    private static final class GaugeValue {
        private final ReentrantLock resolutionLock = new ReentrantLock();
        private volatile AtomicLong value;
        private volatile boolean resolved;

        private GaugeValue(AtomicLong value) {
            this.value = value;
        }

        private void increment() {
            if (resolved) {
                value.incrementAndGet();
                return;
            }
            resolutionLock.lock();
            try {
                value.incrementAndGet();
            } finally {
                resolutionLock.unlock();
            }
        }

        private void decrement() {
            if (resolved) {
                value.decrementAndGet();
                return;
            }
            resolutionLock.lock();
            try {
                value.decrementAndGet();
            } finally {
                resolutionLock.unlock();
            }
        }

        private long get() {
            if (resolved) {
                return value.get();
            }
            resolutionLock.lock();
            try {
                return value.get();
            } finally {
                resolutionLock.unlock();
            }
        }
    }

    private static final class MeterBinding {
        private final Set<MeterRegistration> registrations = new HashSet<>();
        private final Meter.Type type;
        private final GaugeKey gaugeKey;
        private final MeterKey meterKey;
        private MeterRegistration removalRegistration;
        private boolean removing;
        private long lastRemovalPass;

        private MeterBinding(Meter.Type type, GaugeKey gaugeKey, MeterKey meterKey) {
            this.type = type;
            this.gaugeKey = gaugeKey;
            this.meterKey = meterKey;
        }
    }

    private static final class MeterRegistration {
        private final MeterRegistry registry;
        private final Meter meter;
        private final Object meterIdentity;
        private final MeterBinding binding;
        private final Set<MeterRegistration> owner;

        private MeterRegistration(MeterRegistry registry,
                                  Meter meter,
                                  Object meterIdentity,
                                  MeterBinding binding,
                                  Set<MeterRegistration> owner) {
            this.registry = registry;
            this.meter = meter;
            this.meterIdentity = meterIdentity;
            this.binding = binding;
            this.owner = owner;
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

    private record ProviderTask(String event, Runnable action, Runnable rejection, boolean control) {
    }

    private record TimeSample(long value, boolean valid) {
    }

    private record RemovalAttempt(IdentityReference identity, MeterBinding binding) {
    }

    private record ConnectionOpenKey(Role role, String transport, Handshake handshake) {
    }

    private record ConnectionKey(Role role, String transport, String protocol) {
    }

    private record ConnectionCloseKey(Role role,
                                      String transport,
                                      String protocol,
                                      ConnectionOutcome outcome) {
    }

    private record HandshakeKey(Role role,
                                String transport,
                                Handshake handshake,
                                HandshakeOutcome outcome) {
    }

    private record StreamKey(Role role, String protocol, Direction direction, Initiator initiator) {
    }

    private record StreamCloseKey(StreamKey stream, StreamOutcome outcome) {
    }

    private record GaugeKey(String name, Object tags) {
    }

    private record MeterKey(String name, Map<String, String> tags) {
    }

    private record GaugeRegistration(GaugeKey key, GaugeValue value) {
    }

    private record MeterPair(Counter counter, Timer timer) {
    }
}
