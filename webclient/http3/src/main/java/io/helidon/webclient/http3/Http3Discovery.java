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

package io.helidon.webclient.http3;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

import io.helidon.common.uri.UriAuthority;
import io.helidon.http.ClientResponseHeaders;
import io.helidon.webclient.api.AltSvcHeader;
import io.helidon.webclient.api.ClientConnectionTarget;
import io.helidon.webclient.api.ClientUri;
import io.helidon.webclient.api.ConnectionKey;
import io.helidon.webclient.api.ProxyRoute;

final class Http3Discovery {
    private static final System.Logger LOGGER = System.getLogger(Http3Discovery.class.getName());
    private static final Duration NEGATIVE_CACHE_TTL = Duration.ofMinutes(5);
    private static final int MAX_ENTRIES = 10_000;
    private static final int MAX_EXACT_KEYS = 4;

    private final Clock clock;
    private final BiConsumer<EndpointContextKey, Target> invalidationListener;
    private final ReentrantLock lock = new ReentrantLock();
    private final LinkedHashMap<DiscoveryKey, RouteState> routes =
            new LinkedHashMap<>(16, 0.75F, true);
    private final LinkedHashMap<DiscoveryKey, Instant> tombstones = new LinkedHashMap<>();
    private final Map<DiscoveryHint, Integer> automaticHints = new HashMap<>();
    private long nextGeneration;
    private long networkGeneration;
    private Instant networkChangedAt = Instant.MIN;

    private Http3Discovery(Clock clock, BiConsumer<EndpointContextKey, Target> invalidationListener) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.invalidationListener = Objects.requireNonNull(invalidationListener, "invalidationListener");
    }

    static Http3Discovery create(BiConsumer<EndpointContextKey, Target> invalidationListener) {
        return new Http3Discovery(Clock.systemUTC(), invalidationListener);
    }

    static Http3Discovery create(Clock clock, BiConsumer<EndpointContextKey, Target> invalidationListener) {
        return new Http3Discovery(clock, invalidationListener);
    }

    Optional<Selection> automaticTarget(EndpointContextKey key,
                                        boolean altSvcEnabled,
                                        Predicate<Target> hasSession) {
        return select(key, altSvcEnabled, null, hasSession);
    }

    Optional<Selection> requestTarget(EndpointContextKey key,
                                      ClientUri uri,
                                      boolean altSvcEnabled,
                                      boolean allowDirect,
                                      Predicate<Target> hasSession) {
        return select(key, altSvcEnabled, allowDirect ? Target.direct(uri) : null, hasSession);
    }

    boolean hasAutomaticTarget(EndpointContextHint hint) {
        Objects.requireNonNull(hint, "hint");
        lock.lock();
        try {
            return automaticHints.containsKey(hint.discoveryHint());
        } finally {
            lock.unlock();
        }
    }

    private Optional<Selection> select(EndpointContextKey key,
                                       boolean altSvcEnabled,
                                       Target defaultTarget,
                                       Predicate<Target> hasSession) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(hasSession, "hasSession");
        List<Map.Entry<EndpointContextKey, Target>> invalidations = null;
        lock.lock();
        try {
            DiscoveryKey routeKey = key.discoveryKey();
            RouteState state = routes.get(routeKey);
            if (state == null) {
                if (defaultTarget == null) {
                    return Optional.empty();
                }
                state = new RouteState(nextGeneration(), null, null, null, null, null, null, exactKeys(key));
                invalidations = putState(routeKey, state, invalidations);
                return Optional.of(new Selection(key,
                                                 defaultTarget,
                                                 state.generation(),
                                                 networkGeneration,
                                                 true,
                                                 clock));
            }
            EndpointContextKey evictedKey = registerExactKey(state.exactKeys(), key);
            if (evictedKey != null) {
                invalidations = addInvalidations(evictedKey, state, invalidations);
                state = new RouteState(nextGeneration(expirationTime(state.alternative())),
                                       state.knownGood(),
                                       state.alternative(),
                                       state.alternativeFailure(),
                                       state.directFailure(),
                                       state.observedAt(),
                                       state.alternativeObservedAt(),
                                       state.exactKeys());
                invalidations = putState(routeKey, state, invalidations);
            }

            Instant now = clock.instant();
            AlternativeEntry alternative = state.alternative();
            NegativeEntry alternativeFailure = state.alternativeFailure();
            NegativeEntry directFailure = state.directFailure();
            boolean changed = false;
            if (alternativeFailure != null && !alternativeFailure.retryAt().isAfter(now)) {
                alternativeFailure = null;
                changed = true;
            }
            if (directFailure != null && !directFailure.retryAt().isAfter(now)) {
                directFailure = null;
                changed = true;
            }

            Target target = null;
            if (altSvcEnabled
                    && key.altSvcEnabled()
                    && alternative != null
                    && !failed(alternativeFailure, alternative.target())
                    && (alternative.expiresAt().isAfter(now)
                            || hasSession.test(alternative.target()))) {
                target = alternative.target();
            }
            if (target == null
                    && state.knownGood() != null
                    && !failed(directFailure, state.knownGood())) {
                target = state.knownGood();
            }

            if (changed) {
                state = new RouteState(state.generation(),
                                       state.knownGood(),
                                       alternative,
                                       alternativeFailure,
                                       directFailure,
                                       state.observedAt(),
                                       state.alternativeObservedAt(),
                                       state.exactKeys());
                invalidations = putState(routeKey, state, invalidations);
            }
            if (target == null && !failed(directFailure, defaultTarget)) {
                target = defaultTarget;
            }
            boolean successChangesState = target != null
                    && (target.alternative()
                            ? state.alternativeFailure() != null
                            : !target.equals(state.knownGood()) || state.directFailure() != null);
            return Optional.ofNullable(target == null
                                               ? null
                                               : new Selection(key,
                                                               target,
                                                               state.generation(),
                                                               networkGeneration,
                                                               successChangesState,
                                                               clock));
        } finally {
            lock.unlock();
            notifyInvalidations(invalidations);
        }
    }

    boolean current(Selection selection) {
        Objects.requireNonNull(selection, "selection");
        return selection.generation().current();
    }

    void recordSuccess(Selection selection) {
        Objects.requireNonNull(selection, "selection");
        if (!selection.successChangesState()) {
            return;
        }
        EndpointContextKey key = selection.key();
        List<Map.Entry<EndpointContextKey, Target>> invalidations = null;
        lock.lock();
        try {
            DiscoveryKey routeKey = key.discoveryKey();
            RouteState state = routes.get(routeKey);
            if (state == null || state.generation() != selection.generation()) {
                return;
            }
            Target target = selection.target();
            Target knownGood = target.alternative() ? state.knownGood() : target;
            invalidations = putState(routeKey,
                                     new RouteState(state.generation(),
                                                    knownGood,
                                                    state.alternative(),
                                                    target.alternative() ? null : state.alternativeFailure(),
                                                    target.alternative() ? state.directFailure() : null,
                                                    state.observedAt(),
                                                    state.alternativeObservedAt(),
                                                    state.exactKeys()),
                                     invalidations);
        } finally {
            lock.unlock();
            notifyInvalidations(invalidations);
        }
    }

    void recordFailure(Selection selection) {
        Objects.requireNonNull(selection, "selection");
        EndpointContextKey key = selection.key();
        List<Map.Entry<EndpointContextKey, Target>> invalidations = null;
        lock.lock();
        try {
            DiscoveryKey routeKey = key.discoveryKey();
            RouteState state = routes.get(routeKey);
            if (state == null || state.generation() != selection.generation()) {
                return;
            }
            Target target = selection.target();
            Target knownGood = target.equals(state.knownGood()) ? null : state.knownGood();
            Instant observedAt = latest(state.observedAt(), clock.instant());
            NegativeEntry failure = new NegativeEntry(target, observedAt.plus(NEGATIVE_CACHE_TTL));
            invalidations = putState(routeKey,
                                     new RouteState(nextGeneration(expirationTime(state.alternative())),
                                                    knownGood,
                                                    state.alternative(),
                                                    target.alternative() ? failure : state.alternativeFailure(),
                                                    target.alternative() ? state.directFailure() : failure,
                                                    observedAt,
                                                    state.alternativeObservedAt(),
                                                    state.exactKeys()),
                                     invalidations);
            if (!target.equals(state.knownGood())) {
                invalidations = addInvalidations(state.exactKeys(), target, invalidations);
            }
        } finally {
            lock.unlock();
            notifyInvalidations(invalidations);
        }
    }

    void recordAltSvc(EndpointContextKey key, ClientResponseHeaders responseHeaders) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(responseHeaders, "responseHeaders");
        Instant receivedAt = clock.instant();
        AltSvcHeader.create(responseHeaders, receivedAt)
                .ifPresent(header -> recordAltSvc(key, header, receivedAt));
    }

    void recordAltSvc(EndpointContextKey key, AltSvcHeader header, Instant receivedAt) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(header, "header");
        Objects.requireNonNull(receivedAt, "receivedAt");
        if (!key.altSvcEnabled()) {
            return;
        }

        List<Map.Entry<EndpointContextKey, Target>> invalidations = null;
        lock.lock();
        try {
            DiscoveryKey routeKey = key.discoveryKey();
            RouteState state = routes.get(routeKey);
            AlternativeEntry alternative = header.clear()
                    ? null
                    : selectAlternative(key.authority().host().value(), header, clock.instant());
            boolean withdrawal = alternative == null;
            Instant latestObservation = state == null ? tombstones.get(routeKey) : state.observedAt();
            if (withdrawal) {
                // Withdrawals win ties but cannot discard a newer advertisement.
                if (state != null
                        && state.alternative() != null
                        && receivedAt.isBefore(state.alternativeObservedAt())) {
                    return;
                }
            } else if (!receivedAt.isAfter(networkChangedAt)
                    || (latestObservation != null && !receivedAt.isAfter(latestObservation))) {
                // Advertisements must be strictly newer than state and network barriers.
                return;
            }
            EndpointContextKey evictedKey = state == null ? null : registerExactKey(state.exactKeys(), key);
            if (evictedKey != null) {
                invalidations = addInvalidations(evictedKey, state, invalidations);
            }

            Target knownGood = state != null && state.knownGood() != null && !state.knownGood().alternative()
                    ? state.knownGood()
                    : null;
            NegativeEntry directFailure = state == null ? null : state.directFailure();
            Target previousAlternative = state == null || state.alternative() == null
                    ? null
                    : state.alternative().target();
            Target updatedAlternative = alternative == null ? null : alternative.target();

            if (alternative == null && knownGood == null && directFailure == null) {
                invalidations = removeWithTombstone(routeKey,
                                                    state,
                                                    latest(latestObservation, receivedAt),
                                                    invalidations);
                return;
            }

            Set<EndpointContextKey> exactKeys = state == null ? exactKeys(key) : state.exactKeys();
            if (alternative != null && Objects.equals(previousAlternative, updatedAlternative)) {
                Generation generation;
                if (evictedKey == null) {
                    generation = state.generation();
                    generation.establishUntil(alternative.expiresAt());
                } else {
                    generation = nextGeneration(alternative.expiresAt());
                }
                invalidations = putState(routeKey,
                                         new RouteState(generation,
                                                        knownGood,
                                                        alternative,
                                                        state.alternativeFailure(),
                                                        state.directFailure(),
                                                        receivedAt,
                                                        receivedAt,
                                                        exactKeys),
                                         invalidations);
                return;
            }

            Generation generation = alternative == null
                    ? nextGeneration()
                    : nextGeneration(alternative.expiresAt());
            invalidations = putState(routeKey,
                                     new RouteState(generation,
                                                    knownGood,
                                                    alternative,
                                                    null,
                                                    directFailure,
                                                    latest(latestObservation, receivedAt),
                                                    alternative == null
                                                            ? latest(state == null ? null : state.alternativeObservedAt(),
                                                                     receivedAt)
                                                            : receivedAt,
                                                    exactKeys),
                                     invalidations);
        } finally {
            lock.unlock();
            notifyInvalidations(invalidations);
        }
    }

    void recordMisdirected(Selection selection) {
        Objects.requireNonNull(selection, "selection");
        EndpointContextKey key = selection.key();
        if (!selection.target().alternative()) {
            return;
        }
        List<Map.Entry<EndpointContextKey, Target>> invalidations = null;
        lock.lock();
        try {
            DiscoveryKey routeKey = key.discoveryKey();
            RouteState state = routes.get(routeKey);
            if (state == null
                    || state.generation() != selection.generation()
                    || state.alternative() == null
                    || !state.alternative().target().equals(selection.target())) {
                return;
            }
            Target knownGood = state.knownGood() != null && !state.knownGood().alternative()
                    ? state.knownGood()
                    : null;
            NegativeEntry directFailure = state.directFailure();
            Instant observedAt = latest(state.observedAt(), clock.instant());
            if (knownGood == null && directFailure == null) {
                invalidations = removeWithTombstone(routeKey, state, observedAt, invalidations);
            } else {
                invalidations = putState(routeKey,
                                         new RouteState(nextGeneration(),
                                                        knownGood,
                                                        null,
                                                        null,
                                                        directFailure,
                                                        observedAt,
                                                        latest(state.alternativeObservedAt(), observedAt),
                                                        state.exactKeys()),
                                         invalidations);
            }
        } finally {
            lock.unlock();
            notifyInvalidations(invalidations);
        }
    }

    void clear() {
        lock.lock();
        try {
            routes.values().forEach(state -> state.generation().invalidate());
            routes.clear();
            tombstones.clear();
            automaticHints.clear();
        } finally {
            lock.unlock();
        }
    }

    void networkChanged() {
        Instant observedAt = clock.instant();
        List<Map.Entry<EndpointContextKey, Target>> invalidations = null;
        lock.lock();
        try {
            networkChangedAt = latest(networkChangedAt, observedAt);
            networkGeneration++;
            var iterator = routes.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<DiscoveryKey, RouteState> entry = iterator.next();
                RouteState state = entry.getValue();
                state.generation().invalidate();
                if (state.alternative() != null
                        && state.alternative().persistent()
                        && entry.getKey().originKey().currentTlsGeneration()) {
                    RouteState updated = new RouteState(nextGeneration(state.alternative().expiresAt()),
                                                        null,
                                                        state.alternative(),
                                                        null,
                                                        null,
                                                        latest(state.observedAt(), networkChangedAt),
                                                        state.alternativeObservedAt(),
                                                        state.exactKeys());
                    updateAutomaticHint(entry.getKey(), state, updated);
                    entry.setValue(updated);
                } else {
                    iterator.remove();
                    updateAutomaticHint(entry.getKey(), state, null);
                    invalidations = addInvalidations(state, null, invalidations);
                    invalidations = putTombstoneLocked(entry.getKey(), networkChangedAt, invalidations);
                }
            }
        } finally {
            lock.unlock();
            notifyInvalidations(invalidations);
        }
    }

    private static AlternativeEntry selectAlternative(String originHost,
                                                        AltSvcHeader header,
                                                        Instant now) {
        AlternativeEntry firstFresh = null;
        AlternativeEntry firstExpired = null;
        for (AltSvcHeader.Alternative candidate : header.alternatives()) {
            if (!Http3Client.PROTOCOL_ID.equals(candidate.protocolId())) {
                continue;
            }
            String host = candidate.host().orElse(originHost);
            if (!sameAuthorityHost(host, originHost)) {
                continue;
            }
            AlternativeEntry alternative = new AlternativeEntry(Target.alternative(host, candidate.port()),
                                                                 candidate.expirationTime(),
                                                                 candidate.persist());
            if (alternative.expiresAt().isAfter(now)) {
                if (firstFresh == null) {
                    firstFresh = alternative;
                }
            } else if (firstExpired == null) {
                firstExpired = alternative;
            }
        }
        return firstFresh == null ? firstExpired : firstFresh;
    }

    private static boolean sameAuthorityHost(String candidateHost, String originHost) {
        return normalizeHost(candidateHost).equalsIgnoreCase(normalizeHost(originHost));
    }

    private static boolean failed(NegativeEntry failure, Target target) {
        return failure != null && failure.target().equals(target);
    }

    private static String normalizeHost(String host) {
        String normalized = host.trim();
        if (normalized.startsWith("[") && normalized.endsWith("]")) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        return normalized.toLowerCase(Locale.ROOT);
    }

    private List<Map.Entry<EndpointContextKey, Target>> putState(
            DiscoveryKey key,
            RouteState state,
            List<Map.Entry<EndpointContextKey, Target>> invalidations) {
        RouteState previous = routes.get(key);
        if (previous != null && previous.generation() != state.generation()) {
            previous.generation().invalidate();
        }
        updateAutomaticHint(key, previous, state);
        tombstones.remove(key);
        routes.put(key, state);
        invalidations = addInvalidations(previous, state, invalidations);
        return enforceCapacityLocked(invalidations, false);
    }

    private List<Map.Entry<EndpointContextKey, Target>> removeWithTombstone(
            DiscoveryKey key,
            RouteState expected,
            Instant observedAt,
            List<Map.Entry<EndpointContextKey, Target>> invalidations) {
        if (expected != null && routes.get(key) == expected) {
            expected.generation().invalidate();
            routes.remove(key, expected);
            updateAutomaticHint(key, expected, null);
            invalidations = addInvalidations(expected, null, invalidations);
        }
        return putTombstoneLocked(key, observedAt, invalidations);
    }

    private List<Map.Entry<EndpointContextKey, Target>> putTombstoneLocked(
            DiscoveryKey key,
            Instant observedAt,
            List<Map.Entry<EndpointContextKey, Target>> invalidations) {
        Instant previous = tombstones.get(key);
        if (previous != null && !observedAt.isAfter(previous)) {
            return invalidations;
        }
        tombstones.remove(key);
        tombstones.put(key, observedAt);
        return enforceCapacityLocked(invalidations, true);
    }

    private List<Map.Entry<EndpointContextKey, Target>> enforceCapacityLocked(
            List<Map.Entry<EndpointContextKey, Target>> invalidations,
            boolean evictRouteFirst) {
        while (routes.size() + tombstones.size() > MAX_ENTRIES) {
            if (!evictRouteFirst && tombstones.pollFirstEntry() != null) {
                continue;
            }
            Map.Entry<DiscoveryKey, RouteState> removed = routes.pollFirstEntry();
            if (removed != null) {
                removed.getValue().generation().invalidate();
                updateAutomaticHint(removed.getKey(), removed.getValue(), null);
                invalidations = addInvalidations(removed.getValue(), null, invalidations);
                continue;
            }
            if (tombstones.pollFirstEntry() != null) {
                continue;
            }
            throw new IllegalStateException("HTTP/3 discovery indexes are empty above capacity");
        }
        return invalidations;
    }

    private void updateAutomaticHint(DiscoveryKey key, RouteState previous, RouteState current) {
        boolean wasHinted = previous != null && (previous.knownGood() != null || previous.alternative() != null);
        boolean isHinted = current != null && (current.knownGood() != null || current.alternative() != null);
        if (wasHinted == isHinted) {
            return;
        }
        DiscoveryHint hint = key.hint();
        if (isHinted) {
            automaticHints.merge(hint, 1, Integer::sum);
        } else if (automaticHints.computeIfPresent(hint, (_, count) -> count == 1 ? null : count - 1) == null) {
            automaticHints.remove(hint);
        }
    }

    private static List<Map.Entry<EndpointContextKey, Target>> addInvalidations(
            RouteState previous,
            RouteState current,
            List<Map.Entry<EndpointContextKey, Target>> invalidations) {
        if (previous == null) {
            return invalidations;
        }
        Target currentKnownGood = current == null ? null : current.knownGood();
        Target currentAlternative = current == null || current.alternative() == null
                ? null
                : current.alternative().target();
        if (previous.knownGood() != null
                && !previous.knownGood().equals(currentKnownGood)
                && !previous.knownGood().equals(currentAlternative)) {
            invalidations = addInvalidations(previous.exactKeys(), previous.knownGood(), invalidations);
        }
        if (previous.alternative() != null) {
            Target previousAlternative = previous.alternative().target();
            if (!previousAlternative.equals(currentKnownGood)
                    && !previousAlternative.equals(currentAlternative)) {
                invalidations = addInvalidations(previous.exactKeys(), previousAlternative, invalidations);
            }
        }
        return invalidations;
    }

    private static List<Map.Entry<EndpointContextKey, Target>> addInvalidations(
            Set<EndpointContextKey> keys,
            Target target,
            List<Map.Entry<EndpointContextKey, Target>> invalidations) {
        List<Map.Entry<EndpointContextKey, Target>> result = invalidations;
        if (result == null) {
            result = new ArrayList<>();
        }
        for (EndpointContextKey key : keys) {
            result.add(Map.entry(key, target));
        }
        return result;
    }

    private static List<Map.Entry<EndpointContextKey, Target>> addInvalidations(
            EndpointContextKey key,
            RouteState state,
            List<Map.Entry<EndpointContextKey, Target>> invalidations) {
        List<Map.Entry<EndpointContextKey, Target>> result = invalidations;
        if (state.knownGood() != null) {
            result = addInvalidation(key, state.knownGood(), result);
        }
        if (state.alternative() != null) {
            result = addInvalidation(key, state.alternative().target(), result);
        }
        return result;
    }

    private static List<Map.Entry<EndpointContextKey, Target>> addInvalidation(
            EndpointContextKey key,
            Target target,
            List<Map.Entry<EndpointContextKey, Target>> invalidations) {
        List<Map.Entry<EndpointContextKey, Target>> result = invalidations;
        if (result == null) {
            result = new ArrayList<>();
        }
        result.add(Map.entry(key, target));
        return result;
    }

    private static Set<EndpointContextKey> exactKeys(EndpointContextKey key) {
        Set<EndpointContextKey> result = new LinkedHashSet<>();
        result.add(key);
        return result;
    }

    private static EndpointContextKey registerExactKey(Set<EndpointContextKey> keys, EndpointContextKey key) {
        if (keys.remove(key)) {
            keys.add(key);
            return null;
        }
        keys.add(key);
        if (keys.size() <= MAX_EXACT_KEYS) {
            return null;
        }
        var iterator = keys.iterator();
        EndpointContextKey evicted = iterator.next();
        iterator.remove();
        return evicted;
    }

    private static Instant expirationTime(AlternativeEntry alternative) {
        return alternative == null ? null : alternative.expiresAt();
    }

    private static Instant latest(Instant first, Instant second) {
        if (first == null) {
            return second;
        }
        if (second == null) {
            return first;
        }
        return first.isAfter(second) ? first : second;
    }

    private void notifyInvalidations(List<Map.Entry<EndpointContextKey, Target>> invalidations) {
        if (invalidations == null) {
            return;
        }
        for (Map.Entry<EndpointContextKey, Target> invalidation : invalidations) {
            try {
                invalidationListener.accept(invalidation.getKey(), invalidation.getValue());
            } catch (RuntimeException e) {
                LOGGER.log(System.Logger.Level.WARNING,
                           "Failed to retire invalidated HTTP/3 route " + invalidation.getValue(),
                           e);
            }
        }
    }

    private Generation nextGeneration() {
        return nextGeneration(null);
    }

    private Generation nextGeneration(Instant establishUntil) {
        return new Generation(++nextGeneration, establishUntil);
    }

    record EndpointContextKey(ConnectionKey connectionKey,
                              Http3ClientProtocolConfig protocolConfig,
                              String scheme,
                              UriAuthority authority,
                              long tlsGeneration,
                              boolean altSvcEnabled,
                              Object observerIdentity,
                              ProxyRoute proxyRoute) {
        EndpointContextKey(ConnectionKey connectionKey,
                           Http3ClientProtocolConfig protocolConfig,
                           String scheme,
                           UriAuthority authority,
                           long tlsGeneration,
                           Object observerIdentity) {
            this(connectionKey,
                 protocolConfig,
                 scheme,
                 authority,
                 tlsGeneration,
                 true,
                 observerIdentity,
                 connectionKey.proxy().effectiveRoute(scheme,
                                                      connectionKey.host(),
                                                      connectionKey.port(),
                                                      connectionKey.tls().enabled()));
        }

        EndpointContextKey {
            Objects.requireNonNull(connectionKey, "connectionKey");
            Objects.requireNonNull(protocolConfig, "protocolConfig");
            Objects.requireNonNull(scheme, "scheme");
            Objects.requireNonNull(authority, "authority");
            Objects.requireNonNull(observerIdentity, "observerIdentity");
            Objects.requireNonNull(proxyRoute, "proxyRoute");
            scheme = scheme.toLowerCase(Locale.ROOT);
        }

        ClientConnectionTarget connectionTarget() {
            return ClientConnectionTarget.create(connectionKey,
                                                 scheme,
                                                 authority,
                                                 proxyRoute,
                                                 tlsGeneration);
        }

        DiscoveryKey discoveryKey() {
            return new DiscoveryKey(connectionTarget().lookupKey().altSvcOriginKey(),
                                    protocolConfig,
                                    altSvcEnabled,
                                    observerIdentity,
                                    proxyRoute);
        }

        EndpointContextHint hint() {
            return new EndpointContextHint(connectionKey,
                                           protocolConfig,
                                           scheme,
                                           authority,
                                           tlsGeneration,
                                           altSvcEnabled,
                                           observerIdentity);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof EndpointContextKey other)) {
                return false;
            }
            return connectionKey.tls() == other.connectionKey.tls()
                    && connectionKey.equals(other.connectionKey)
                    && protocolConfig.equals(other.protocolConfig)
                    && scheme.equals(other.scheme)
                    && authority.equals(other.authority)
                    && tlsGeneration == other.tlsGeneration
                    && altSvcEnabled == other.altSvcEnabled
                    && observerIdentity == other.observerIdentity
                    && proxyRoute.equals(other.proxyRoute);
        }

        @Override
        public int hashCode() {
            int result = connectionKey.hashCode();
            result = 31 * result + System.identityHashCode(connectionKey.tls());
            result = 31 * result + protocolConfig.hashCode();
            result = 31 * result + scheme.hashCode();
            result = 31 * result + authority.hashCode();
            result = 31 * result + Long.hashCode(tlsGeneration);
            result = 31 * result + Boolean.hashCode(altSvcEnabled);
            result = 31 * result + System.identityHashCode(observerIdentity);
            return 31 * result + proxyRoute.hashCode();
        }
    }

    record EndpointContextHint(ConnectionKey connectionKey,
                               Http3ClientProtocolConfig protocolConfig,
                               String scheme,
                               UriAuthority authority,
                               long tlsGeneration,
                               boolean altSvcEnabled,
                               Object observerIdentity) {
        EndpointContextHint {
            Objects.requireNonNull(connectionKey, "connectionKey");
            Objects.requireNonNull(protocolConfig, "protocolConfig");
            Objects.requireNonNull(scheme, "scheme");
            Objects.requireNonNull(authority, "authority");
            Objects.requireNonNull(observerIdentity, "observerIdentity");
            scheme = scheme.toLowerCase(Locale.ROOT);
        }

        DiscoveryHint discoveryHint() {
            ProxyRoute route = connectionKey.proxy().effectiveRoute(scheme,
                                                                     connectionKey.host(),
                                                                     connectionKey.port(),
                                                                     connectionKey.tls().enabled());
            ClientConnectionTarget.LookupKey originKey = ClientConnectionTarget.create(connectionKey,
                                                                                         scheme,
                                                                                         authority,
                                                                                         route,
                                                                                         tlsGeneration)
                    .lookupKey()
                    .altSvcOriginKey();
            return new DiscoveryHint(originKey, protocolConfig, altSvcEnabled, observerIdentity);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof EndpointContextHint other)) {
                return false;
            }
            return connectionKey.tls() == other.connectionKey.tls()
                    && connectionKey.equals(other.connectionKey)
                    && protocolConfig.equals(other.protocolConfig)
                    && scheme.equals(other.scheme)
                    && authority.equals(other.authority)
                    && tlsGeneration == other.tlsGeneration
                    && altSvcEnabled == other.altSvcEnabled
                    && observerIdentity == other.observerIdentity;
        }

        @Override
        public int hashCode() {
            int result = connectionKey.hashCode();
            result = 31 * result + System.identityHashCode(connectionKey.tls());
            result = 31 * result + protocolConfig.hashCode();
            result = 31 * result + scheme.hashCode();
            result = 31 * result + authority.hashCode();
            result = 31 * result + Long.hashCode(tlsGeneration);
            result = 31 * result + Boolean.hashCode(altSvcEnabled);
            return 31 * result + System.identityHashCode(observerIdentity);
        }
    }

    private record DiscoveryKey(ClientConnectionTarget.LookupKey originKey,
                                Http3ClientProtocolConfig protocolConfig,
                                boolean altSvcEnabled,
                                Object observerIdentity,
                                ProxyRoute proxyRoute) {
        private DiscoveryKey {
            Objects.requireNonNull(originKey, "originKey");
            Objects.requireNonNull(protocolConfig, "protocolConfig");
            Objects.requireNonNull(observerIdentity, "observerIdentity");
            Objects.requireNonNull(proxyRoute, "proxyRoute");
        }

        DiscoveryHint hint() {
            return new DiscoveryHint(originKey, protocolConfig, altSvcEnabled, observerIdentity);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof DiscoveryKey other)) {
                return false;
            }
            return originKey.equals(other.originKey)
                    && protocolConfig.equals(other.protocolConfig)
                    && altSvcEnabled == other.altSvcEnabled
                    && observerIdentity == other.observerIdentity
                    && proxyRoute.equals(other.proxyRoute);
        }

        @Override
        public int hashCode() {
            int result = originKey.hashCode();
            result = 31 * result + protocolConfig.hashCode();
            result = 31 * result + Boolean.hashCode(altSvcEnabled);
            result = 31 * result + System.identityHashCode(observerIdentity);
            return 31 * result + proxyRoute.hashCode();
        }
    }

    private record DiscoveryHint(ClientConnectionTarget.LookupKey originKey,
                                 Http3ClientProtocolConfig protocolConfig,
                                 boolean altSvcEnabled,
                                 Object observerIdentity) {
        private DiscoveryHint {
            Objects.requireNonNull(originKey, "originKey");
            Objects.requireNonNull(protocolConfig, "protocolConfig");
            Objects.requireNonNull(observerIdentity, "observerIdentity");
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof DiscoveryHint other)) {
                return false;
            }
            return originKey.equals(other.originKey)
                    && protocolConfig.equals(other.protocolConfig)
                    && altSvcEnabled == other.altSvcEnabled
                    && observerIdentity == other.observerIdentity;
        }

        @Override
        public int hashCode() {
            int result = originKey.hashCode();
            result = 31 * result + protocolConfig.hashCode();
            result = 31 * result + Boolean.hashCode(altSvcEnabled);
            return 31 * result + System.identityHashCode(observerIdentity);
        }
    }

    record Target(String peerHost,
                  int peerPort,
                  boolean alternative) {
        Target {
            Objects.requireNonNull(peerHost, "peerHost");
            peerHost = normalizeHost(peerHost);
            if (peerPort < 1 || peerPort > 65535) {
                throw new IllegalArgumentException("Invalid peer port: " + peerPort);
            }
        }

        static Target direct(ClientUri originUri) {
            int port = effectivePort(originUri);
            return new Target(originUri.host(), port, false);
        }

        static Target alternative(String host, int port) {
            return new Target(host, port, true);
        }

        String altUsed() {
            String host = peerHost.indexOf(':') < 0 ? peerHost : "[" + peerHost + "]";
            return host + ':' + peerPort;
        }

        private static int effectivePort(ClientUri originUri) {
            if (originUri.port() > 0) {
                return originUri.port();
            }
            return "https".equalsIgnoreCase(originUri.scheme()) ? 443 : 80;
        }
    }

    record Selection(EndpointContextKey key,
                     Target target,
                     Generation generation,
                     long networkGeneration,
                     boolean successChangesState,
                     Clock clock) {
        Selection {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(target, "target");
            Objects.requireNonNull(generation, "generation");
            Objects.requireNonNull(clock, "clock");
        }

        boolean establishAllowed() {
            Instant establishUntil = generation.establishUntil();
            return !target.alternative()
                    || establishUntil != null && establishUntil.isAfter(clock.instant());
        }
    }

    private record RouteState(Generation generation,
                              Target knownGood,
                              AlternativeEntry alternative,
                              NegativeEntry alternativeFailure,
                              NegativeEntry directFailure,
                              Instant observedAt,
                              Instant alternativeObservedAt,
                              Set<EndpointContextKey> exactKeys) {
        private RouteState {
            Objects.requireNonNull(generation, "generation");
            Objects.requireNonNull(exactKeys, "exactKeys");
        }
    }

    private record AlternativeEntry(Target target, Instant expiresAt, boolean persistent) {
        private AlternativeEntry {
            Objects.requireNonNull(target, "target");
            Objects.requireNonNull(expiresAt, "expiresAt");
        }
    }

    private record NegativeEntry(Target target, Instant retryAt) {
        private NegativeEntry {
            Objects.requireNonNull(target, "target");
            Objects.requireNonNull(retryAt, "retryAt");
        }
    }

    private static final class Generation {
        private final long id;
        private volatile boolean current = true;
        private volatile Instant establishUntil;

        private Generation(long id, Instant establishUntil) {
            this.id = id;
            this.establishUntil = establishUntil;
        }

        private boolean current() {
            return current;
        }

        private void invalidate() {
            current = false;
        }

        private Instant establishUntil() {
            return establishUntil;
        }

        private void establishUntil(Instant establishUntil) {
            this.establishUntil = Objects.requireNonNull(establishUntil, "establishUntil");
        }

        @Override
        public String toString() {
            return Long.toString(id);
        }
    }

}
