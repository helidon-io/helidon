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

package io.helidon.service.test.registry;

import java.util.List;

import io.helidon.service.registry.Lookup;
import io.helidon.service.registry.Service;
import io.helidon.service.registry.ServiceInfo;
import io.helidon.service.registry.ServiceRegistry;
import io.helidon.service.registry.ServiceRegistryConfig;
import io.helidon.service.registry.ServiceRegistryManager;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;

class LookupCacheTest {
    private ServiceRegistryManager registryManager;
    private ServiceRegistry registry;

    @BeforeEach
    void init() {
        registryManager = ServiceRegistryManager.create(ServiceRegistryConfig.builder()
                .lookupCacheEnabled(true)
                .build());
        registry = registryManager.registry();
    }

    @AfterEach
    void shutdown() {
        if (registryManager != null) {
            registryManager.shutdown();
        }
    }

    @Test
    void indexedLookupServicesResultMutationDoesNotPoisonCache() {
        Lookup lookup = Lookup.create(CacheContract.class);
        List<ServiceInfo> firstLookup = registry.lookupServices(lookup);

        assertThat(firstLookup, hasSize(2));
        assertLookupMutationDoesNotPoisonCache(lookup, firstLookup);
    }

    @Test
    void fullScanLookupServicesResultMutationDoesNotPoisonCache() {
        Lookup lookup = Lookup.EMPTY;
        List<ServiceInfo> firstLookup = registry.lookupServices(lookup);

        assertThat(firstLookup, not(empty()));
        assertLookupMutationDoesNotPoisonCache(lookup, firstLookup);
    }

    private void assertLookupMutationDoesNotPoisonCache(Lookup lookup, List<ServiceInfo> firstLookup) {
        List<ServiceInfo> expectedLookup = List.copyOf(firstLookup);
        try {
            firstLookup.clear();
        } catch (UnsupportedOperationException ignored) {
            // Immutable lookup results cannot poison the cache.
        }

        int cacheHits = registry.metrics().cacheHitCount();
        assertThat(registry.lookupServices(lookup), is(expectedLookup));
        assertThat(registry.metrics().cacheHitCount(), is(cacheHits + 1));
    }

    interface CacheContract {
    }

    @Service.Singleton
    static class FirstCacheService implements CacheContract {
    }

    @Service.Singleton
    static class SecondCacheService implements CacheContract {
    }
}
