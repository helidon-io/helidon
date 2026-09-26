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

package io.helidon.webclient.api;

import java.lang.System.Logger.Level;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

import io.helidon.common.Api;
import io.helidon.http.HttpTransportObserver;
import io.helidon.webclient.api.HttpTransportObserverSupport.ObserverLifecycle;
import io.helidon.webclient.api.HttpTransportObserverSupport.ObserverProvider;
import io.helidon.webclient.spi.ClientConnectionCache;
import io.helidon.webclient.spi.WebClientService;

/**
 * Ownership of a protocol connection cache and its transport observers.
 *
 * <p>Observed shared caches are partitioned by protocol cache type, observer provider type, and scope identities.
 * An unused handle does not resolve observer scopes or acquire a cache or observer lifecycle. The last owner closes
 * the cache and stops observation; physical connections which are still active retain their observer lifecycle leases
 * until closed.
 *
 * @param <T> protocol connection cache type
 */
@Api.Internal
public final class HttpTransportConnectionCache<T extends ClientConnectionCache> implements ReleasableResource {
    private static final System.Logger LOGGER = System.getLogger(HttpTransportConnectionCache.class.getName());
    private static final ReentrantLock PARTITIONS_LOCK = new ReentrantLock();
    private static final Map<CacheKey, Partition> PARTITIONS = new HashMap<>();

    private final Class<T> cacheType;
    private final boolean shared;
    private final ReentrantLock lock = new ReentrantLock();
    private final CompletableFuture<Void> completion = new CompletableFuture<>();
    private final CompletionStage<Void> completionView = completion.minimalCompletionStage();

    private List<ObserverProvider> providers;
    private Supplier<T> factory;
    private Partition partition;
    private volatile Resources resources;
    private volatile boolean closed;

    private HttpTransportConnectionCache(Class<T> cacheType,
                                         boolean shared,
                                         List<ObserverProvider> providers,
                                         Supplier<T> factory) {
        this.cacheType = cacheType;
        this.shared = shared;
        this.providers = providers;
        this.factory = factory;
    }

    /**
     * Creates lazy ownership of an observed connection cache, if observation is enabled.
     *
     * <p>The supplied factory must create an independently owned cache without registering a JVM shutdown hook.
     * Disabled providers do not resolve their scopes or create observer lifecycles.
     *
     * @param cacheType protocol cache type
     * @param config client configuration
     * @param factory factory for independently owned caches
     * @param <T> protocol connection cache type
     * @return lazy cache ownership, or empty if there are no enabled observer providers
     */
    public static <T extends ClientConnectionCache> Optional<HttpTransportConnectionCache<T>> create(Class<T> cacheType,
                                                                                                   HttpClientConfig config,
                                                                                                   Supplier<T> factory) {
        Objects.requireNonNull(cacheType);
        Objects.requireNonNull(config);
        Objects.requireNonNull(factory);
        List<ObserverProvider> providers = null;
        for (WebClientService service : config.services()) {
            if (service instanceof ObserverProvider provider) {
                try {
                    if (provider.enabled()) {
                        if (providers == null) {
                            providers = new ArrayList<>();
                        }
                        providers.add(provider);
                    }
                } catch (RuntimeException e) {
                    LOGGER.log(Level.WARNING, "Failed to check client transport observation configuration.", e);
                }
            }
        }
        if (providers == null) {
            return Optional.empty();
        }
        return Optional.of(new HttpTransportConnectionCache<>(cacheType, config.shareConnectionCache(), providers, factory));
    }

    /**
     * Obtains the protocol cache, acquiring ownership on first use.
     *
     * @return protocol cache
     * @throws IllegalStateException if this handle is closed
     */
    public T cache() {
        return cacheType.cast(resources().cache());
    }

    /**
     * Obtains the observer associated with this cache, acquiring ownership on first use.
     *
     * @return failure-isolating transport observer
     * @throws IllegalStateException if this handle is closed
     */
    public HttpTransportObserver observer() {
        return resources().observer();
    }

    @Override
    public void closeResource() {
        Partition toRelease;
        lock.lock();
        try {
            if (closed) {
                return;
            }
            closed = true;
            toRelease = partition;
            partition = null;
            resources = null;
            providers = List.of();
            factory = null;
        } finally {
            lock.unlock();
        }
        if (toRelease == null) {
            completion.complete(null);
        } else {
            release(toRelease);
        }
    }

    /**
     * Completion of observer cleanup after all owners release this partition.
     *
     * <p>Closing this handle does not wait for other owners or active physical connections. Their lifetimes may delay
     * completion. An unused handle completes immediately when closed.
     *
     * @return completion stage which cannot complete or cancel the underlying cleanup
     */
    public CompletionStage<Void> completion() {
        return completionView;
    }

    @Override
    public CompletionStage<Void> closeResourceAsync() {
        closeResource();
        return completionView;
    }

    private static void release(Partition partition) {
        boolean last;
        PARTITIONS_LOCK.lock();
        try {
            last = --partition.owners == 0;
            if (last && partition.key != null) {
                PARTITIONS.remove(partition.key, partition);
            }
        } finally {
            PARTITIONS_LOCK.unlock();
        }
        if (last) {
            partition.closeResource();
        }
    }

    private static CompletableFuture<Void> stop(ObserverLifecycle lifecycle) {
        try {
            return Objects.requireNonNull(lifecycle.stop(), "Observer stop stage").toCompletableFuture();
        } catch (RuntimeException e) {
            LOGGER.log(Level.WARNING, "Failed to stop client transport observation.", e);
            return CompletableFuture.failedFuture(e);
        }
    }

    private Resources resources() {
        if (closed) {
            throw new IllegalStateException("Observed connection cache is closed");
        }
        Resources current = resources;
        if (current != null) {
            return current;
        }
        lock.lock();
        try {
            if (closed) {
                throw new IllegalStateException("Observed connection cache is closed");
            }
            if (resources == null) {
                resources = acquire();
                providers = List.of();
                factory = null;
            }
            return resources;
        } finally {
            lock.unlock();
        }
    }

    private Resources acquire() {
        var scopes = new HashSet<Scope>();
        var scopedProviders = new ArrayList<ObserverProvider>();
        for (ObserverProvider provider : providers) {
            try {
                if (scopes.add(new Scope(provider.getClass(), Objects.requireNonNull(provider.scope(), "Observer scope")))) {
                    scopedProviders.add(provider);
                }
            } catch (RuntimeException e) {
                LOGGER.log(Level.WARNING, "Failed to resolve client transport observation scope.", e);
            }
        }
        CacheKey key = shared ? new CacheKey(cacheType, Set.copyOf(scopes)) : null;
        boolean initialize = false;
        PARTITIONS_LOCK.lock();
        try {
            partition = key == null ? null : PARTITIONS.get(key);
            if (partition == null) {
                partition = new Partition(key);
                if (key != null) {
                    PARTITIONS.put(key, partition);
                }
                initialize = true;
            }
            partition.owners++;
        } finally {
            PARTITIONS_LOCK.unlock();
        }
        CompletableFuture<Void> handleCompletion = completion;
        partition.completion.whenComplete((_, failure) -> {
            if (failure == null) {
                handleCompletion.complete(null);
            } else {
                handleCompletion.completeExceptionally(failure);
            }
        });
        if (initialize) {
            partition.initialize(scopedProviders, factory);
        }
        try {
            return partition.ready.join();
        } catch (CompletionException e) {
            Partition failed = partition;
            closed = true;
            partition = null;
            providers = List.of();
            factory = null;
            release(failed);
            if (e.getCause() instanceof RuntimeException cause) {
                throw cause;
            }
            throw e;
        }
    }

    private record CacheKey(Class<?> cacheType, Set<Scope> scopes) {
    }

    private record Resources(ClientConnectionCache cache, HttpTransportObserver observer) {
    }

    private static final class Scope {
        private final Class<?> providerType;
        private final Object identity;

        private Scope(Class<?> providerType, Object identity) {
            this.providerType = providerType;
            this.identity = identity;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Scope scope && providerType == scope.providerType && identity == scope.identity;
        }

        @Override
        public int hashCode() {
            return 31 * System.identityHashCode(providerType) + System.identityHashCode(identity);
        }
    }

    private static final class Partition {
        private final CacheKey key;
        private final CompletableFuture<Resources> ready = new CompletableFuture<>();
        private final CompletableFuture<Void> completion = new CompletableFuture<>();
        private final List<ObserverLifecycle> lifecycles = new ArrayList<>();
        private final List<CompletableFuture<Void>> stops = new ArrayList<>();

        private ClientConnectionCache cache;
        private RuntimeException initializationFailure;
        private int owners;

        private Partition(CacheKey key) {
            this.key = key;
        }

        private void initialize(List<ObserverProvider> providers, Supplier<? extends ClientConnectionCache> factory) {
            try {
                cache = Objects.requireNonNull(factory.get(), "Protocol connection cache");
                var observers = new ArrayList<HttpTransportObserver>(providers.size());
                for (ObserverProvider provider : providers) {
                    ObserverLifecycle lifecycle = null;
                    try {
                        lifecycle = Objects.requireNonNull(provider.createObserver(), "Observer lifecycle");
                        observers.add(Objects.requireNonNull(lifecycle.start(), "Transport observer"));
                        lifecycles.add(lifecycle);
                    } catch (RuntimeException e) {
                        LOGGER.log(Level.WARNING, "Failed to start client transport observation.", e);
                        if (lifecycle != null) {
                            stops.add(stop(lifecycle));
                        }
                    }
                }
                ready.complete(new Resources(cache, HttpTransportObserver.compose(observers)));
            } catch (RuntimeException e) {
                initializationFailure = e;
                ready.completeExceptionally(e);
            }
        }

        private void closeResource() {
            RuntimeException closeFailure = initializationFailure;
            if (cache != null) {
                try {
                    cache.closeResource();
                } catch (RuntimeException e) {
                    LOGGER.log(Level.WARNING, "Failed to close an observed client connection cache.", e);
                    closeFailure = e;
                }
            }
            for (ObserverLifecycle lifecycle : lifecycles) {
                stops.add(stop(lifecycle));
            }
            lifecycles.clear();
            RuntimeException failure = closeFailure;
            CompletableFuture.allOf(stops.toArray(CompletableFuture<?>[]::new)).whenComplete((_, stopFailure) -> {
                if (failure != null) {
                    completion.completeExceptionally(failure);
                } else if (stopFailure != null) {
                    completion.completeExceptionally(stopFailure);
                } else {
                    completion.complete(null);
                }
            });
            stops.clear();
        }
    }
}
