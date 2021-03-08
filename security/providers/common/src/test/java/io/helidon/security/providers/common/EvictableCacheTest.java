/*
 * Copyright (c) 2018, 2021 Oracle and/or its affiliates.
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

package io.helidon.security.providers.common;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Unit test for {@link io.helidon.security.providers.common.EvictableCache}.
 */
class EvictableCacheTest {
    private static final Optional<String> EMPTY = Optional.empty();

    @Test
    void testCaching() {
        EvictableCache<String, String> cache = EvictableCache.create();
        assertThat(cache.computeValue("one", () -> Optional.of("1")), is(Optional.of("1")));
        assertThat(cache.computeValue("two", () -> Optional.of("2")), is(Optional.of("2")));
        assertThat(cache.computeValue("none", () -> EMPTY), is(EMPTY));
        // already cached
        assertThat(cache.computeValue("two", () -> EMPTY), is(Optional.of("2")));

        assertThat(cache.remove("two"), is(Optional.of("2")));
        assertThat(cache.remove("two"), is(Optional.empty()));

        assertThat(cache.computeValue("two", () -> EMPTY), is(EMPTY));
        assertThat(cache.get("one"), is(Optional.of("1")));

        cache.close();
    }

    @Test
    void testEviction() throws InterruptedException {
        EvictableCache<String, String> cache = EvictableCache.<String, String>builder()
                .evictSchedule(100, 100, TimeUnit.MILLISECONDS)
                .timeout(50, TimeUnit.MILLISECONDS)
                .build();

        assertThat(cache.computeValue("one", () -> Optional.of("1")), is(Optional.of("1")));
        assertThat(cache.get("one"), is(Optional.of("1")));
        TimeUnit.MILLISECONDS.sleep(200);
        assertThat(cache.get("one"), is(EMPTY));

        cache.close();
    }

    @Test
    void testOverallTimeout() throws InterruptedException {
        EvictableCache<String, String> cache = EvictableCache.<String, String>builder()
                .timeout(10, TimeUnit.MINUTES)
                .overallTimeout(50, TimeUnit.MILLISECONDS)
                .build();

        assertThat(cache.computeValue("one", () -> Optional.of("1")), is(Optional.of("1")));
        assertThat(cache.get("one"), is(Optional.of("1")));
        TimeUnit.MILLISECONDS.sleep(200);
        assertThat(cache.get("one"), is(EMPTY));

        cache.close();
    }

    @Test
    void testEvictor() {
        EvictableCache<String, String> cache = EvictableCache.<String, String>builder()
                // always evict ones
                .evictor((key, value) -> "one".equals(key))
                .build();

        assertThat(cache.computeValue("one", () -> Optional.of("1")), is(Optional.of("1")));
        assertThat(cache.computeValue("two", () -> Optional.of("2")), is(Optional.of("2")));
        assertThat(cache.get("one"), is(EMPTY));
        assertThat(cache.get("two"), is(Optional.of("2")));

        cache.close();
    }

    @Test
    void testMaxSize() {
        EvictableCache<String, String> cache = EvictableCache.<String, String>builder()
                .maxSize(2)
                .build();

        assertThat(cache.computeValue("one", () -> Optional.of("1")), is(Optional.of("1")));
        assertThat(cache.computeValue("two", () -> Optional.of("2")), is(Optional.of("2")));
        assertThat(cache.computeValue("three", () -> Optional.of("3")), is(Optional.of("3")));

        assertThat(cache.get("one"), is(Optional.of("1")));
        assertThat(cache.get("two"), is(Optional.of("2")));
        assertThat(cache.get("three"), is(EMPTY));

        cache.close();
    }

    @Test
    void testNoCache() {
        EvictableCache<String, String> cache = EvictableCache.noCache();

        assertThat(cache.computeValue("one", () -> Optional.of("1")), is(Optional.of("1")));
        assertThat(cache.computeValue("two", () -> Optional.of("2")), is(Optional.of("2")));
        assertThat(cache.computeValue("three", () -> Optional.of("3")), is(Optional.of("3")));

        assertThat(cache.get("one"), is(EMPTY));
        assertThat(cache.get("two"), is(EMPTY));
        assertThat(cache.get("three"), is(EMPTY));

        assertThat(cache.computeValue("one", () -> Optional.of("2")), is(Optional.of("2")));
    }
}
