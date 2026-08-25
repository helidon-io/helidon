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

package io.helidon.webclient.http2;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Predicate;

import io.helidon.common.uri.UriAuthority;
import io.helidon.common.uri.UriHost;
import io.helidon.webclient.api.AltSvcHeader;
import io.helidon.webclient.api.ClientConnectionTarget;
import io.helidon.webclient.api.Proxy;
import io.helidon.webclient.api.ProxyRoute;

final class Http2AltSvcCache implements AutoCloseable {
    private static final System.Logger LOGGER = System.getLogger(Http2AltSvcCache.class.getName());
    private static final Duration NEGATIVE_CACHE_TTL = Duration.ofMinutes(5);
    private static final int MAX_ENTRIES = 1_000;
    private static final int MAX_SELECTION_MEMOS = 4;

    private final Clock clock;
    private final Consumer<Generation> invalidationListener;
    private final ReentrantLock lock = new ReentrantLock();
    // Concurrent readers use only published state and immutable lookup lists.
    private final ConcurrentMap<OriginRouteKey, RouteState> routes = new ConcurrentHashMap<>();
    private final ConcurrentMap<ClientConnectionTarget.LookupKey, List<OriginRouteKey>> lookupIndex =
            new ConcurrentHashMap<>();
    // Mutated under lock. Add before publishing a route; remove after unpublishing it.
    private final ConcurrentMap<String, Integer> advertisedHostCounts = new ConcurrentHashMap<>();
    // Mutated only while holding lock.
    private final LinkedHashMap<OriginRouteKey, RouteState> insertionOrder = new LinkedHashMap<>();
    // Mutated only while holding lock. Active routes and tombstones share MAX_ENTRIES.
    private final LinkedHashMap<OriginRouteKey, Instant> tombstones = new LinkedHashMap<>();
    // Even values are stable; every locked mutation spans the adjacent odd value.
    private volatile long mutationVersion;
    // Mutated only while holding lock, then published through mutationVersion.
    private long nextMutationVersion;
    // Small raw-target memo. Common and alternating case variants avoid canonical-key allocation and map hashing.
    private volatile List<SelectionMemo> selectionMemos = List.of();
    private long nextGeneration;
    private long networkGeneration;
    private Instant networkChangedAt = Instant.MIN;
    private volatile boolean closed;

    private Http2AltSvcCache(Clock clock, Consumer<Generation> invalidationListener) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.invalidationListener = Objects.requireNonNull(invalidationListener, "invalidationListener");
    }

    static Http2AltSvcCache create(Consumer<Generation> invalidationListener) {
        return new Http2AltSvcCache(Clock.systemUTC(), invalidationListener);
    }

    static Http2AltSvcCache create(Clock clock, Consumer<Generation> invalidationListener) {
        return new Http2AltSvcCache(clock, invalidationListener);
    }

    boolean mayContain(String host) {
        return !advertisedHostCounts.isEmpty() && advertisedHostCounts.containsKey(normalizeHost(host));
    }

    boolean available(ClientConnectionTarget target,
                      boolean explicitConnection,
                      Predicate<Selection> established,
                      Predicate<Generation> establishedGeneration) {
        return select(target, explicitConnection, established, establishedGeneration) != null;
    }

    Candidate selectRoute(ClientConnectionTarget.LookupKey lookupKey,
                          boolean explicitConnection,
                          Predicate<Generation> established) {
        Objects.requireNonNull(lookupKey, "lookupKey");
        Objects.requireNonNull(established, "established");
        if (explicitConnection) {
            return null;
        }

        ClientConnectionTarget.LookupKey originKey = lookupKey.altSvcOriginKey();
        long version = mutationVersion;
        if ((version & 1) == 0 && !closed && originKey.currentTlsGeneration()) {
            List<OriginRouteKey> indexedRoutes = lookupIndex.get(originKey);
            if (indexedRoutes == null && stable(version)) {
                return null;
            }
            Instant now = clock.instant();
            if (indexedRoutes != null) {
                boolean slowPath = false;
                for (int index = indexedRoutes.size() - 1; index >= 0; index--) {
                    RouteState state = routes.get(indexedRoutes.get(index));
                    if (state == null || expiredNegative(state, now)) {
                        slowPath = true;
                        break;
                    }
                    if (negative(state, now)) {
                        continue;
                    }
                    Candidate candidate = state.candidate;
                    if (fresh(state, now)) {
                        if (stable(version) && current(candidate)) {
                            return candidate;
                        }
                        slowPath = true;
                        break;
                    }
                    boolean generationEstablished = established.test(state.generation);
                    if (stable(version)) {
                        if (generationEstablished && current(candidate)) {
                            return candidate;
                        }
                        slowPath = true;
                        break;
                    }
                }
                if (!slowPath && stable(version)) {
                    return null;
                }
            } else if (stable(version)) {
                return null;
            }
        }
        return selectRouteSlow(originKey, established);
    }

    Selection select(ClientConnectionTarget target,
                     boolean explicitConnection,
                     Predicate<Selection> established) {
        return select(target, explicitConnection, established, _ -> false);
    }

    Selection select(ClientConnectionTarget target,
                     boolean explicitConnection,
                     Predicate<Selection> established,
                     Predicate<Generation> establishedGeneration) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(established, "established");
        Objects.requireNonNull(establishedGeneration, "establishedGeneration");
        if (explicitConnection) {
            return null;
        }
        if (!target.currentTlsGeneration()) {
            removeStale(target);
            return null;
        }
        if (!eligible(target)) {
            return null;
        }
        if (routes.isEmpty()) {
            return null;
        }

        long version = mutationVersion;
        OriginRouteKey routeKey = null;
        if ((version & 1) == 0 && !closed) {
            SelectionMemo memo = selectionMemo(target);
            RouteState state;
            Selection selection = null;
            if (memo != null) {
                routeKey = memo.routeKey;
                state = memo.state;
                selection = memo.selection;
            } else {
                routeKey = originRouteKey(target);
                state = routes.get(routeKey);
            }
            if (state == null && stable(version)) {
                return null;
            }
            Instant now = clock.instant();
            if (state != null && negative(state, now)) {
                if (stable(version)) {
                    return null;
                }
            }
            if (state != null && !expiredNegative(state, now)) {
                if (selection == null) {
                    selection = state.selection(target);
                }
                if (selection != null) {
                    if (fresh(state, now)) {
                        if (stable(version) && current(selection)) {
                            return selection;
                        }
                    } else {
                        boolean exactEstablished = established.test(selection);
                        if (stable(version) && exactEstablished && current(selection)) {
                            return selection;
                        }
                        if (stable(version)) {
                            boolean anyEstablished = establishedGeneration.test(state.generation);
                            if (stable(version) && anyEstablished) {
                                return null;
                            }
                        }
                    }
                }
            }
        }
        return selectSlow(target, routeKey, established, establishedGeneration);
    }

    void record(ClientConnectionTarget target,
                AltSvcHeader header,
                boolean secureOrigin,
                boolean explicitConnection,
                Instant observedAt) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(header, "header");
        Objects.requireNonNull(observedAt, "observedAt");
        if (!target.currentTlsGeneration()) {
            removeStale(target);
            return;
        }
        if (!secureOrigin || explicitConnection || !eligible(target)) {
            return;
        }

        Instant now = clock.instant();
        List<Generation> invalidations = null;
        lock.lock();
        try {
            if (closed) {
                return;
            }
            SelectionMemo memo = selectionMemo(target);
            boolean memoized = memo != null && memo.state.generation.current;
            OriginRouteKey routeKey = memoized ? memo.routeKey : originRouteKey(target);
            RouteState previous = memoized ? memo.state : routes.get(routeKey);
            Alternative alternative = null;
            String originHost = null;
            if (!header.clear()) {
                originHost = memoized
                        ? previous.originHost
                        : normalizeHost(target.originAuthority().host().value());
                alternative = selectAlternative(originHost, header, now);
            }
            boolean withdrawal = alternative == null;
            Instant latestObservation = previous == null ? tombstones.get(routeKey) : previous.observedAt;
            // Equal-time withdrawals win; advertisements must be strictly newer than state and network barriers.
            if (observedAt.isBefore(networkChangedAt)
                    || (!withdrawal && observedAt.equals(networkChangedAt))
                    || (latestObservation != null
                            && (observedAt.isBefore(latestObservation)
                                    || (!withdrawal && observedAt.equals(latestObservation))))) {
                return;
            }
            if (alternative != null
                    && previous != null
                    && previous.alternative.sameAuthority(alternative)) {
                beginMutation();
                try {
                    previous.refresh(alternative, observedAt);
                    Selection selection = previous.selection(target);
                    if (selection == null) {
                        selection = previous.addSelection(clock,
                                                          target,
                                                          alternative,
                                                          networkGeneration,
                                                          MAX_SELECTION_MEMOS);
                    }
                    publishSelectionMemoLocked(target, routeKey, previous, selection);
                } finally {
                    endMutation();
                }
                return;
            }
            invalidations = new ArrayList<>();
            beginMutation();
            try {
                if (alternative == null) {
                    removeWithTombstone(routeKey, previous, observedAt, invalidations);
                } else {
                    Generation generation = nextGeneration(alternative.expirationTime);
                    RouteState updated = new RouteState(previous == null ? originHost : previous.originHost,
                                                        previous == null
                                                                ? normalizeHost(target.connectionKey().host())
                                                                : previous.advertisedHost,
                                                        routeKey,
                                                        alternative,
                                                        observedAt,
                                                        generation,
                                                        selection(target, routeKey, alternative, generation),
                                                        null);
                    put(routeKey, target, previous, updated, invalidations);
                }
            } finally {
                endMutation();
            }
        } finally {
            lock.unlock();
            notifyInvalidations(invalidations);
        }
    }

    boolean current(Selection selection) {
        Objects.requireNonNull(selection, "selection");
        return selection.generation.current && selection.originTarget.currentTlsGeneration();
    }

    void recordFailure(Selection selection) {
        Objects.requireNonNull(selection, "selection");
        if (!current(selection)) {
            return;
        }

        Instant observedAt = clock.instant();
        List<Generation> invalidations = new ArrayList<>();
        lock.lock();
        try {
            RouteState state = routes.get(selection.routeKey);
            if (!matches(selection, state)) {
                return;
            }
            beginMutation();
            try {
                Instant eventAt = latest(state.observedAt, observedAt);
                Generation generation = nextGeneration(state.alternative.expirationTime);
                RouteState updated = new RouteState(state.originHost,
                                                    state.advertisedHost,
                                                    selection.routeKey,
                                                    state.alternative,
                                                    eventAt,
                                                    generation,
                                                    selection(selection.originTarget,
                                                              selection.routeKey,
                                                              state.alternative,
                                                              generation),
                                                    eventAt.plus(NEGATIVE_CACHE_TTL));
                put(selection.routeKey, selection.originTarget, state, updated, invalidations);
            } finally {
                endMutation();
            }
        } finally {
            lock.unlock();
            notifyInvalidations(invalidations);
        }
    }

    void recordMisdirected(Selection selection) {
        Objects.requireNonNull(selection, "selection");
        Instant observedAt = clock.instant();
        List<Generation> invalidations = new ArrayList<>();
        lock.lock();
        try {
            RouteState state = routes.get(selection.routeKey);
            if (matches(selection, state)) {
                beginMutation();
                try {
                    removeWithTombstone(selection.routeKey,
                                        state,
                                        latest(state.observedAt, observedAt),
                                        invalidations);
                } finally {
                    endMutation();
                }
            }
        } finally {
            lock.unlock();
            notifyInvalidations(invalidations);
        }
    }

    void networkChanged() {
        Instant observedAt = clock.instant();
        List<Generation> invalidations = new ArrayList<>();
        lock.lock();
        try {
            if (closed) {
                return;
            }
            beginMutation();
            try {
                networkChangedAt = latest(networkChangedAt, observedAt);
                networkGeneration++;
                var iterator = insertionOrder.entrySet().iterator();
                while (iterator.hasNext()) {
                    Map.Entry<OriginRouteKey, RouteState> entry = iterator.next();
                    OriginRouteKey routeKey = entry.getKey();
                    RouteState state = entry.getValue();
                    invalidate(state, invalidations);
                    if (state.alternative.persist && routeKey.originKey.currentTlsGeneration()) {
                        RouteState updated = routeState(routeKey,
                                                        state.originHost,
                                                        state.advertisedHost,
                                                        state.alternative,
                                                        latest(state.observedAt, networkChangedAt),
                                                        nextGeneration(state.alternative.expirationTime),
                                                        null);
                        entry.setValue(updated);
                        routes.put(routeKey, updated);
                        clearSelectionMemosLocked(state);
                    } else {
                        iterator.remove();
                        routes.remove(routeKey, state);
                        removeIndexLocked(routeKey);
                        removeAdvertisedHost(state.advertisedHost);
                        clearSelectionMemosLocked(state);
                        putTombstoneLocked(routeKey, networkChangedAt, invalidations);
                    }
                }
            } finally {
                endMutation();
            }
        } finally {
            lock.unlock();
            notifyInvalidations(invalidations);
        }
    }

    @Override
    public void close() {
        List<Generation> invalidations = new ArrayList<>();
        lock.lock();
        try {
            if (closed) {
                return;
            }
            beginMutation();
            try {
                closed = true;
                insertionOrder.values().forEach(state -> invalidate(state, invalidations));
                routes.clear();
                lookupIndex.clear();
                insertionOrder.clear();
                tombstones.clear();
                advertisedHostCounts.clear();
                selectionMemos = List.of();
            } finally {
                endMutation();
            }
        } finally {
            lock.unlock();
            notifyInvalidations(invalidations);
        }
    }

    private static boolean negative(RouteState state, Instant now) {
        return state.negativeUntil != null && state.negativeUntil.isAfter(now);
    }

    private static boolean expiredNegative(RouteState state, Instant now) {
        return state.negativeUntil != null && !state.negativeUntil.isAfter(now);
    }

    private static boolean fresh(RouteState state, Instant now) {
        return state.alternative.expirationTime.isAfter(now);
    }

    private static Instant latest(Instant first, Instant second) {
        return first.isAfter(second) ? first : second;
    }

    private static boolean eligible(ClientConnectionTarget target) {
        return target.currentTlsGeneration()
                && "https".equals(target.scheme())
                && target.connectionKey().tls().enabled()
                && target.connectionKey().proxy().type() == Proxy.ProxyType.NONE
                && target.transportAddress().isEmpty()
                && target.proxyRoute().direct()
                && !target.proxyRoute().addressBound();
    }

    private static boolean matches(Selection selection, RouteState state) {
        return state != null
                && state.generation == selection.generation
                && state.routeKey.equals(selection.routeKey);
    }

    private static OriginRouteKey originRouteKey(ClientConnectionTarget target) {
        return new OriginRouteKey(target.lookupKey().altSvcOriginKey(), target.proxyRoute());
    }

    private static boolean sameHost(String first, String second) {
        return normalizeHost(first).equals(normalizeHost(second));
    }

    private static String normalizeHost(String host) {
        String normalized = Objects.requireNonNull(host, "host").trim();
        if (normalized.startsWith("[") && normalized.endsWith("]")) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        return normalized.toLowerCase(Locale.ROOT);
    }

    private Candidate selectRouteSlow(ClientConnectionTarget.LookupKey originKey,
                                      Predicate<Generation> established) {
        if (!originKey.currentTlsGeneration()) {
            removeStale(originKey);
            return null;
        }

        while (true) {
            List<OriginRouteKey> indexedRoutes;
            lock.lock();
            try {
                if (closed) {
                    return null;
                }
                indexedRoutes = lookupIndex.get(originKey);
                if (indexedRoutes == null) {
                    return null;
                }
            } finally {
                lock.unlock();
            }

            boolean retry = false;
            for (int index = indexedRoutes.size() - 1; index >= 0; index--) {
                OriginRouteKey routeKey = indexedRoutes.get(index);
                RouteState state;
                boolean freshAdvertisement;
                lock.lock();
                try {
                    if (closed) {
                        return null;
                    }
                    state = routes.get(routeKey);
                    if (state == null) {
                        beginMutation();
                        try {
                            removeIndexLocked(routeKey);
                        } finally {
                            endMutation();
                        }
                        continue;
                    }
                    Instant now = clock.instant();
                    if (expiredNegative(state, now)) {
                        beginMutation();
                        try {
                            state.negativeUntil = null;
                        } finally {
                            endMutation();
                        }
                    } else if (negative(state, now)) {
                        continue;
                    }
                    freshAdvertisement = fresh(state, now);
                } finally {
                    lock.unlock();
                }

                Candidate candidate = state.candidate;
                if (freshAdvertisement) {
                    return current(candidate) ? candidate : null;
                }
                boolean generationEstablished = established.test(state.generation);
                List<Generation> invalidations = new ArrayList<>();
                boolean stateChanged;
                lock.lock();
                try {
                    stateChanged = routes.get(routeKey) != state;
                    if (!stateChanged) {
                        Instant now = clock.instant();
                        stateChanged = negative(state, now) || fresh(state, now);
                        if (!stateChanged) {
                            if (generationEstablished && current(candidate)) {
                                return candidate;
                            }
                            beginMutation();
                            try {
                                removeWithTombstone(routeKey,
                                                    state,
                                                    state.observedAt,
                                                    invalidations);
                            } finally {
                                endMutation();
                            }
                        }
                    }
                } finally {
                    lock.unlock();
                    notifyInvalidations(invalidations);
                }
                if (stateChanged) {
                    retry = true;
                    break;
                }
            }
            if (retry) {
                continue;
            }
            return null;
        }
    }

    private Selection selectSlow(ClientConnectionTarget target,
                                 OriginRouteKey suppliedRouteKey,
                                 Predicate<Selection> established,
                                 Predicate<Generation> establishedGeneration) {
        OriginRouteKey routeKey = suppliedRouteKey == null ? originRouteKey(target) : suppliedRouteKey;
        while (true) {
            RouteState state;
            Selection selection;
            boolean freshAdvertisement;
            lock.lock();
            try {
                if (closed || !target.currentTlsGeneration() || !eligible(target)) {
                    return null;
                }
                state = routes.get(routeKey);
                if (state == null) {
                    return null;
                }
                Instant now = clock.instant();
                if (negative(state, now)) {
                    return null;
                }
                if (expiredNegative(state, now)) {
                    beginMutation();
                    try {
                        state.negativeUntil = null;
                    } finally {
                        endMutation();
                    }
                }
                selection = state.selection(target);
                if (selection == null) {
                    beginMutation();
                    try {
                        selection = state.addSelection(clock,
                                                       target,
                                                       state.alternative,
                                                       networkGeneration,
                                                       MAX_SELECTION_MEMOS);
                        publishSelectionMemoLocked(target, routeKey, state, selection);
                    } finally {
                        endMutation();
                    }
                } else {
                    publishSelectionMemoLocked(target, routeKey, state, selection);
                }
                freshAdvertisement = fresh(state, now);
            } finally {
                lock.unlock();
            }

            if (freshAdvertisement) {
                return current(selection) ? selection : null;
            }
            boolean exactEstablished = established.test(selection);
            boolean anyEstablished = !exactEstablished && establishedGeneration.test(state.generation);
            List<Generation> invalidations = new ArrayList<>();
            lock.lock();
            try {
                if (routes.get(routeKey) != state) {
                    continue;
                }
                Instant now = clock.instant();
                if (negative(state, now)) {
                    return null;
                }
                if (fresh(state, now)) {
                    continue;
                }
                if (exactEstablished && current(selection)) {
                    return selection;
                }
                if (anyEstablished) {
                    return null;
                }
                beginMutation();
                try {
                    removeWithTombstone(routeKey, state, state.observedAt, invalidations);
                } finally {
                    endMutation();
                }
                return null;
            } finally {
                lock.unlock();
                notifyInvalidations(invalidations);
            }
        }
    }

    private Alternative selectAlternative(String originHost, AltSvcHeader header, Instant now) {
        Alternative firstFresh = null;
        Alternative firstExpired = null;
        for (AltSvcHeader.Alternative candidate : header.alternatives()) {
            if (!"h2".equals(candidate.protocolId())) {
                continue;
            }
            String host = candidate.host().orElse(originHost);
            if (!sameHost(host, originHost)) {
                continue;
            }
            Alternative alternative = new Alternative(host,
                                                       candidate.port(),
                                                       candidate.expirationTime(),
                                                       candidate.persist());
            if (alternative.expirationTime.isAfter(now)) {
                if (firstFresh == null) {
                    firstFresh = alternative;
                }
            } else if (firstExpired == null) {
                firstExpired = alternative;
            }
        }
        return firstFresh == null ? firstExpired : firstFresh;
    }

    private void put(OriginRouteKey routeKey,
                     ClientConnectionTarget target,
                     RouteState previous,
                     RouteState updated,
                     List<Generation> invalidations) {
        if (previous != null && previous.generation != updated.generation) {
            invalidate(previous, invalidations);
            clearSelectionMemosLocked(previous);
        }
        if (previous == null) {
            addAdvertisedHost(updated.advertisedHost);
        }
        tombstones.remove(routeKey);
        routes.put(routeKey, updated);
        insertionOrder.put(routeKey, updated);
        addIndexLocked(routeKey);
        enforceCapacityLocked(invalidations, false);
        Selection selection = updated.selection(target);
        if (selection != null && routes.get(routeKey) == updated) {
            publishSelectionMemoLocked(target, routeKey, updated, selection);
        }
    }

    private void enforceCapacityLocked(List<Generation> invalidations, boolean evictRouteFirst) {
        while (insertionOrder.size() + tombstones.size() > MAX_ENTRIES) {
            if (!evictRouteFirst && removeOldestTombstoneLocked()) {
                continue;
            }
            if (removeOldestRouteLocked(invalidations)) {
                continue;
            }
            if (removeOldestTombstoneLocked()) {
                continue;
            }
            throw new IllegalStateException("HTTP/2 Alt-Svc indexes are empty above capacity");
        }
    }

    private boolean removeOldestTombstoneLocked() {
        Map.Entry<OriginRouteKey, Instant> tombstone = tombstones.firstEntry();
        if (tombstone != null) {
            tombstones.remove(tombstone.getKey(), tombstone.getValue());
            return true;
        }
        return false;
    }

    private boolean removeOldestRouteLocked(List<Generation> invalidations) {
        Map.Entry<OriginRouteKey, RouteState> removed = insertionOrder.firstEntry();
        if (removed == null) {
            return false;
        }
        invalidate(removed.getValue(), invalidations);
        insertionOrder.remove(removed.getKey(), removed.getValue());
        routes.remove(removed.getKey(), removed.getValue());
        removeIndexLocked(removed.getKey());
        removeAdvertisedHost(removed.getValue().advertisedHost);
        clearSelectionMemosLocked(removed.getValue());
        return true;
    }

    private void removeWithTombstone(OriginRouteKey routeKey,
                                     RouteState expected,
                                     Instant observedAt,
                                     List<Generation> invalidations) {
        removeDiscardingHistory(routeKey, expected, invalidations);
        if (expected == null || routes.get(routeKey) == null) {
            putTombstoneLocked(routeKey, observedAt, invalidations);
        }
    }

    private void removeDiscardingHistory(OriginRouteKey routeKey,
                                         RouteState expected,
                                         List<Generation> invalidations) {
        if (expected != null && routes.get(routeKey) == expected) {
            invalidate(expected, invalidations);
            routes.remove(routeKey, expected);
            insertionOrder.remove(routeKey, expected);
            removeIndexLocked(routeKey);
            removeAdvertisedHost(expected.advertisedHost);
            clearSelectionMemosLocked(expected);
        }
    }

    private void putTombstoneLocked(OriginRouteKey routeKey,
                                    Instant observedAt,
                                    List<Generation> invalidations) {
        Instant previous = tombstones.get(routeKey);
        if (previous != null && !observedAt.isAfter(previous)) {
            return;
        }
        tombstones.remove(routeKey);
        tombstones.put(routeKey, observedAt);
        enforceCapacityLocked(invalidations, true);
    }

    private void invalidate(RouteState state, List<Generation> invalidations) {
        state.generation.current = false;
        invalidations.add(state.generation);
    }

    private RouteState routeState(OriginRouteKey routeKey,
                                  String originHost,
                                  String advertisedHost,
                                  Alternative alternative,
                                  Instant observedAt,
                                  Generation generation,
                                  Instant negativeUntil) {
        return new RouteState(originHost,
                              advertisedHost,
                              routeKey,
                              alternative,
                              observedAt,
                              generation,
                              negativeUntil);
    }

    private Selection selection(ClientConnectionTarget target,
                                OriginRouteKey routeKey,
                                Alternative alternative,
                                Generation generation) {
        return new Selection(clock,
                             target,
                             routeKey,
                             alternative.host,
                             alternative.port,
                             generation,
                             networkGeneration);
    }

    private SelectionMemo selectionMemo(ClientConnectionTarget target) {
        for (SelectionMemo memo : selectionMemos) {
            if (memo.target.equals(target)) {
                return memo;
            }
        }
        return null;
    }

    private void publishSelectionMemoLocked(ClientConnectionTarget target,
                                            OriginRouteKey routeKey,
                                            RouteState state,
                                            Selection selection) {
        List<SelectionMemo> current = selectionMemos;
        if (!current.isEmpty()) {
            SelectionMemo first = current.getFirst();
            if (first.state == state && first.selection == selection && first.target.equals(target)) {
                return;
            }
        }
        var updated = new ArrayList<SelectionMemo>(Math.min(MAX_SELECTION_MEMOS, current.size() + 1));
        updated.add(new SelectionMemo(target, routeKey, state, selection));
        for (SelectionMemo memo : current) {
            if (updated.size() == MAX_SELECTION_MEMOS) {
                break;
            }
            if (!memo.target.equals(target) && memo.selection != selection) {
                updated.add(memo);
            }
        }
        selectionMemos = List.copyOf(updated);
    }

    private void clearSelectionMemosLocked(RouteState state) {
        List<SelectionMemo> current = selectionMemos;
        if (current.isEmpty()) {
            return;
        }
        var updated = new ArrayList<SelectionMemo>(current.size());
        for (SelectionMemo memo : current) {
            if (memo.state != state) {
                updated.add(memo);
            }
        }
        if (updated.size() != current.size()) {
            selectionMemos = updated.isEmpty() ? List.of() : List.copyOf(updated);
        }
    }

    private void addIndexLocked(OriginRouteKey routeKey) {
        List<OriginRouteKey> indexedRoutes = lookupIndex.get(routeKey.originKey);
        if (indexedRoutes == null) {
            lookupIndex.put(routeKey.originKey, List.of(routeKey));
            return;
        }
        if (indexedRoutes.getLast().equals(routeKey)) {
            return;
        }
        var updatedRoutes = new ArrayList<>(indexedRoutes);
        updatedRoutes.remove(routeKey);
        updatedRoutes.add(routeKey);
        lookupIndex.put(routeKey.originKey, List.copyOf(updatedRoutes));
    }

    private void removeIndexLocked(OriginRouteKey routeKey) {
        List<OriginRouteKey> indexedRoutes = lookupIndex.get(routeKey.originKey);
        if (indexedRoutes == null || !indexedRoutes.contains(routeKey)) {
            return;
        }
        if (indexedRoutes.size() == 1) {
            lookupIndex.remove(routeKey.originKey, indexedRoutes);
            return;
        }
        var updatedRoutes = new ArrayList<>(indexedRoutes);
        updatedRoutes.remove(routeKey);
        lookupIndex.replace(routeKey.originKey, indexedRoutes, List.copyOf(updatedRoutes));
    }

    private void addAdvertisedHost(String host) {
        Integer count = advertisedHostCounts.get(host);
        advertisedHostCounts.put(host, count == null ? 1 : count + 1);
    }

    private void removeAdvertisedHost(String host) {
        Integer count = advertisedHostCounts.get(host);
        if (count == null) {
            throw new IllegalStateException("HTTP/2 Alt-Svc host index does not contain " + host);
        }
        if (count == 1) {
            advertisedHostCounts.remove(host, count);
        } else {
            advertisedHostCounts.put(host, count - 1);
        }
    }

    private void notifyInvalidations(List<Generation> invalidations) {
        if (invalidations == null) {
            return;
        }
        for (Generation invalidation : invalidations) {
            try {
                invalidationListener.accept(invalidation);
            } catch (RuntimeException e) {
                LOGGER.log(System.Logger.Level.WARNING,
                           "Failed to retire invalidated HTTP/2 alternative " + invalidation,
                           e);
            }
        }
    }

    private void removeStale(ClientConnectionTarget target) {
        OriginRouteKey routeKey = originRouteKey(target);
        List<Generation> invalidations = new ArrayList<>();
        lock.lock();
        try {
            RouteState state = routes.get(routeKey);
            if (state != null) {
                beginMutation();
                try {
                    removeDiscardingHistory(routeKey, state, invalidations);
                } finally {
                    endMutation();
                }
            }
        } finally {
            lock.unlock();
            notifyInvalidations(invalidations);
        }
    }

    private void removeStale(ClientConnectionTarget.LookupKey lookupKey) {
        List<Generation> invalidations = new ArrayList<>();
        lock.lock();
        try {
            List<OriginRouteKey> routeKeys = lookupIndex.get(lookupKey);
            if (routeKeys != null) {
                beginMutation();
                try {
                    for (OriginRouteKey routeKey : routeKeys) {
                        RouteState state = routes.get(routeKey);
                        if (state != null) {
                            removeDiscardingHistory(routeKey, state, invalidations);
                        }
                    }
                } finally {
                    endMutation();
                }
            }
        } finally {
            lock.unlock();
            notifyInvalidations(invalidations);
        }
    }

    private Generation nextGeneration(Instant establishUntil) {
        return new Generation(++nextGeneration, establishUntil);
    }

    private void beginMutation() {
        nextMutationVersion++;
        mutationVersion = nextMutationVersion;
    }

    private void endMutation() {
        nextMutationVersion++;
        mutationVersion = nextMutationVersion;
    }

    private boolean stable(long version) {
        return version == mutationVersion;
    }

    private boolean current(Candidate candidate) {
        return candidate.generation.current && candidate.routeKey.originKey.currentTlsGeneration();
    }

    static final class Candidate {
        private final OriginRouteKey routeKey;
        private final Generation generation;

        private Candidate(OriginRouteKey routeKey, Generation generation) {
            this.routeKey = Objects.requireNonNull(routeKey, "routeKey");
            this.generation = Objects.requireNonNull(generation, "generation");
        }

        ProxyRoute proxyRoute() {
            return routeKey.proxyRoute;
        }

        @Override
        public String toString() {
            return "Http2AltSvcCache.Candidate[routeKey=" + routeKey
                    + ", generation=" + generation
                    + ']';
        }
    }

    static final class Selection {
        private final Clock clock;
        private final ClientConnectionTarget originTarget;
        private final OriginRouteKey routeKey;
        private final String host;
        private final int port;
        private final UriAuthority authority;
        private final Generation generation;
        private final long networkGeneration;

        private Selection(Clock clock,
                          ClientConnectionTarget originTarget,
                          OriginRouteKey routeKey,
                          String host,
                          int port,
                          Generation generation,
                          long networkGeneration) {
            this.clock = clock;
            this.originTarget = originTarget;
            this.routeKey = routeKey;
            this.host = host;
            this.port = port;
            this.authority = UriAuthority.create(UriHost.create(host), port);
            this.generation = generation;
            this.networkGeneration = networkGeneration;
        }

        ClientConnectionTarget originTarget() {
            return originTarget;
        }

        String host() {
            return host;
        }

        int port() {
            return port;
        }

        UriAuthority authority() {
            return authority;
        }

        long networkGeneration() {
            return networkGeneration;
        }

        boolean establishAllowed() {
            return generation.establishUntil.isAfter(clock.instant());
        }

        boolean sameRouteGeneration(Selection other) {
            return other != null
                    && generation == other.generation
                    && routeKey.equals(other.routeKey);
        }

        boolean sameGeneration(Generation other) {
            return generation == other;
        }

        @Override
        public String toString() {
            return "Http2AltSvcCache.Selection[originTarget=" + originTarget
                    + ", authority=" + host + ':' + port
                    + ", routeKey=" + routeKey
                    + ", generation=" + generation
                    + ", networkGeneration=" + networkGeneration
                    + ", establishUntil=" + generation.establishUntil
                    + ']';
        }
    }

    private record SelectionMemo(ClientConnectionTarget target,
                                 OriginRouteKey routeKey,
                                 RouteState state,
                                 Selection selection) {
        private SelectionMemo {
            Objects.requireNonNull(target, "target");
            Objects.requireNonNull(routeKey, "routeKey");
            Objects.requireNonNull(state, "state");
            Objects.requireNonNull(selection, "selection");
        }
    }

    private record Alternative(String host, int port, Instant expirationTime, boolean persist) {
        private Alternative {
            host = normalizeHost(host);
            if (port < 1 || port > 65_535) {
                throw new IllegalArgumentException("Invalid alternative port: " + port);
            }
            Objects.requireNonNull(expirationTime, "expirationTime");
        }

        private boolean sameAuthority(Alternative other) {
            return host.equals(other.host) && port == other.port;
        }
    }

    private record OriginRouteKey(ClientConnectionTarget.LookupKey originKey, ProxyRoute proxyRoute) {
        private OriginRouteKey {
            Objects.requireNonNull(originKey, "originKey");
            Objects.requireNonNull(proxyRoute, "proxyRoute");
        }
    }

    private static final class RouteState {
        private final String originHost;
        private final String advertisedHost;
        private final OriginRouteKey routeKey;
        private final Generation generation;
        private final Candidate candidate;
        private List<Selection> selections;
        private Alternative alternative;
        private Instant observedAt;
        private Instant negativeUntil;

        private RouteState(String originHost,
                           String advertisedHost,
                           OriginRouteKey routeKey,
                           Alternative alternative,
                           Instant observedAt,
                           Generation generation,
                           Selection selection,
                           Instant negativeUntil) {
            this.originHost = Objects.requireNonNull(originHost, "originHost");
            this.advertisedHost = Objects.requireNonNull(advertisedHost, "advertisedHost");
            this.routeKey = Objects.requireNonNull(routeKey, "routeKey");
            this.alternative = Objects.requireNonNull(alternative, "alternative");
            this.observedAt = Objects.requireNonNull(observedAt, "observedAt");
            this.generation = Objects.requireNonNull(generation, "generation");
            this.candidate = new Candidate(routeKey, generation);
            this.selections = List.of(Objects.requireNonNull(selection, "selection"));
            this.negativeUntil = negativeUntil;
        }

        private RouteState(String originHost,
                           String advertisedHost,
                           OriginRouteKey routeKey,
                           Alternative alternative,
                           Instant observedAt,
                           Generation generation,
                           Instant negativeUntil) {
            this.originHost = Objects.requireNonNull(originHost, "originHost");
            this.advertisedHost = Objects.requireNonNull(advertisedHost, "advertisedHost");
            this.routeKey = Objects.requireNonNull(routeKey, "routeKey");
            this.alternative = Objects.requireNonNull(alternative, "alternative");
            this.observedAt = Objects.requireNonNull(observedAt, "observedAt");
            this.generation = Objects.requireNonNull(generation, "generation");
            this.candidate = new Candidate(routeKey, generation);
            this.selections = List.of();
            this.negativeUntil = negativeUntil;
        }

        private Selection selection(ClientConnectionTarget target) {
            for (Selection selection : selections) {
                if (selection.originTarget.equals(target)) {
                    return selection;
                }
            }
            return null;
        }

        private Selection addSelection(Clock clock,
                                       ClientConnectionTarget target,
                                       Alternative alternative,
                                       long networkGeneration,
                                       int maximumSelections) {
            Selection selection = new Selection(clock,
                                                target,
                                                routeKey,
                                                alternative.host,
                                                alternative.port,
                                                generation,
                                                networkGeneration);
            var updated = new ArrayList<Selection>(Math.min(maximumSelections, selections.size() + 1));
            updated.add(selection);
            for (Selection existing : selections) {
                if (updated.size() == maximumSelections) {
                    break;
                }
                if (!existing.originTarget.equals(target)) {
                    updated.add(existing);
                }
            }
            selections = List.copyOf(updated);
            return selection;
        }

        private void refresh(Alternative alternative, Instant observedAt) {
            this.alternative = alternative;
            this.observedAt = observedAt;
            generation.establishUntil = alternative.expirationTime;
            negativeUntil = null;
        }
    }

    static final class Generation {
        private final long id;
        private volatile boolean current = true;
        private volatile Instant establishUntil;

        private Generation(long id, Instant establishUntil) {
            this.id = id;
            this.establishUntil = establishUntil;
        }

        @Override
        public String toString() {
            return Long.toString(id);
        }
    }
}
