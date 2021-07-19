/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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

package io.helidon.caching.coherence;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.logging.Logger;

import io.helidon.caching.Cache;
import io.helidon.caching.CacheConfig;
import io.helidon.caching.CacheLoader;
import io.helidon.caching.LoaderBackedCache;
import io.helidon.caching.spi.CacheProviderManager;
import io.helidon.common.configurable.ThreadPoolSupplier;
import io.helidon.common.reactive.Single;
import io.helidon.config.Config;

import com.tangosol.net.CacheFactory;
import com.tangosol.net.Coherence;
import com.tangosol.net.NamedCache;
import com.tangosol.net.cache.TypeAssertion;

public class CoherenceManager implements CacheProviderManager {
    private static final Logger LOGGER = Logger.getLogger(CoherenceManager.class.getName());

    private final Coherence coherence;
    private final ExecutorService executor;

    private CoherenceManager(Builder builder) {
        this.coherence = builder.coherence;
        this.executor = builder.executor;
        LOGGER.info("Started Coherence Cache Manager for: " + coherence.getCluster());
    }

    public static Builder builder() {
        return new Builder();
    }

    @SuppressWarnings("unchecked")
    @Override
    public <K, V> Single<Cache<K, V>> createCache(String name, CacheConfig<K, V> configuration) {
        CompletableFuture<Cache<K, V>> cf = new CompletableFuture<>();

        executor.submit(() -> {
            try {
                NamedCache<K, V> namedCache = CacheFactory
                        .getTypedCache(name, (TypeAssertion<K, V>) TypeAssertion.withTypes(configuration.keyType().rawType(),
                                                                                           configuration.valueType().rawType()));
                Cache<K, V> helidonCache = new CoherenceCache<>(namedCache, namedCache.async(), executor);
                Optional<CacheLoader<K, V>> loader = configuration.loader();
                if (loader.isPresent()) {
                    helidonCache = LoaderBackedCache.create(loader.get(), helidonCache);
                }
                cf.complete(helidonCache);
            } catch (Exception e) {
                cf.completeExceptionally(e);
            }
        });

        return Single.create(cf);
    }

    @Override
    public Single<Void> close() {
        CompletableFuture<Void> cf = new CompletableFuture<>();
        executor.submit(() -> {
            try {
                coherence.close();
                cf.complete(null);
            } catch (Exception e) {
                cf.completeExceptionally(e);
            }
        });
        return Single.create(cf, true);
    }

    public static class Builder extends CacheProviderManager.Builder<Builder, CoherenceConfig, CoherenceManager> {
        private final ThreadPoolSupplier.Builder threadPoolBuilder = ThreadPoolSupplier.builder();
        private ExecutorService executor;
        private Coherence coherence;
        private CoherenceConfig coherenceConfig;

        private Builder() {
        }

        @Override
        public Builder config(Config config) {
            return this;
        }

        @Override
        public Builder cacheConfig(String name, Config config) {
            return this;
        }

        @Override
        public Builder config(CoherenceConfig providerConfig) {
            this.coherenceConfig = providerConfig;
            return this;
        }

        @Override
        public Single<CoherenceManager> build() {
            if (executor == null) {
                executor = threadPoolBuilder.build().get();
            }

            if (coherence != null && coherence.isStarted()) {
                return Single.just(new CoherenceManager(this));
            }

            if (coherence == null) {
                if (coherenceConfig == null) {
                    coherence = Coherence.clusterMember();
                } else {
                    coherence = Coherence.clusterMember(coherenceConfig);
                }
            }

            return Single.create(coherence.start()
                                         .thenApply(nothing -> new CoherenceManager(this)));
        }
    }
}
