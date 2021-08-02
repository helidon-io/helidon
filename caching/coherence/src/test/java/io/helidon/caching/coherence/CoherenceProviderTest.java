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

import java.math.BigDecimal;
import java.util.Optional;

import io.helidon.caching.Cache;
import io.helidon.caching.CacheConfig;

import com.tangosol.net.AsyncNamedCache;
import com.tangosol.net.NamedCache;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

class CoherenceProviderTest {
    private static CoherenceManager coherenceManager;
    private static Cache<Integer, BigDecimal> cache;

    @BeforeAll
    static void createCache() {
        CoherenceProvider provider = new CoherenceProvider();
        assertThat(provider.type(), is("coherence"));
        CoherenceManager.Builder builder = provider.cacheManagerBuilder();
        assertThat(builder, notNullValue());

        coherenceManager = builder.build().await();
        assertThat(coherenceManager, notNullValue());

        cache = coherenceManager
                .createCache("test", CacheConfig.create(Integer.class, BigDecimal.class))
                .await();
    }

    @AfterAll
    static void closeCache() throws Throwable {
        Throwable ce = null;
        try {
            if (cache != null) {
                cache.close().await();
            }
        } catch (Throwable e) {
            ce = e;
        }
        try {
            if (coherenceManager != null) {
                coherenceManager.close().await();
            }
        } catch (Throwable me) {
            if (ce != null) {
                me.addSuppressed(ce);
            }
            throw me;
        }
        if (ce != null) {
            throw ce;
        }
        if (cache != null) {
            assertThat(cache.isClosed(), is(true));
        }
    }

    @Test
    void testCrud() {
        BigDecimal firstValue = new BigDecimal("14.8");
        BigDecimal secondValue = new BigDecimal("15.9");

        cache.put(14, firstValue);
        assertThat(cache.get(14).await().get(), is(firstValue));

        cache.put(14, secondValue).await();
        assertThat(cache.get(14).await().get(), is(secondValue));

        cache.remove(14).await();
        assertThat(cache.get(14).await(), is(Optional.empty()));
    }

    @Test
    void testClear() {
        for (int i = 0; i < 100; i++) {
             cache.put(i, new BigDecimal(i));
        }

        for (int i = 0; i < 100; i++) {
            assertThat(cache.get(i).await().get(), is(new BigDecimal(i)));
        }

        cache.clear().await();

        for (int i = 0; i < 100; i++) {
            assertThat(cache.get(i).await(), is(Optional.empty()));
        }
    }

    @Test
    void testRemove() {
        // make sure we can remove a non-existent value
        cache.remove(14).await();
    }

    @Test
    void testUnwrapNamed() {
        NamedCache<?, ?> coherenceCache = cache.unwrap(NamedCache.class);
        assertThat(coherenceCache, notNullValue());
        assertThat(coherenceCache.getCacheName(), is("test"));
    }

    @Test
    void testUnwrapAsync() {
        AsyncNamedCache<?, ?> coherenceCache = cache.unwrap(AsyncNamedCache.class);
        assertThat(coherenceCache, notNullValue());
    }

    @Test
    void testName() {
        assertThat(cache.name(), is("test"));
    }
}