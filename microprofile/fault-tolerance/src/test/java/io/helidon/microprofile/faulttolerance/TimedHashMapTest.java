/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.microprofile.faulttolerance;

import java.util.Map;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/**
 * Class TimedHashMapTest.
 */
public class TimedHashMapTest extends TimedTest {

    private final long TTL = 500;

    private final Map<String, String> cache = new TimedHashMap<>(TTL);

    @Test
    public void testExpiration() throws Exception {
        assertThat(cache.size(), is(0));
        IntStream.range(0, 10).forEach(
            i -> cache.put(String.valueOf(i), String.valueOf(i))
        );
        assertThat(cache.size(), is(10));
        Thread.sleep(2 * TTL);
        assertEventually(() -> assertThat(cache.size(), is(0)));
    }

    @Test
    public void testExpirationBatch() throws Exception {
        assertThat(cache.size(), is(0));

        // First batch
        IntStream.range(0, 10).forEach(
            i -> cache.put(String.valueOf(i), String.valueOf(i))
        );
        assertThat(cache.size(), is(10));
        Thread.sleep(TTL / 2);

        // Second batch
        IntStream.range(10, 20).forEach(
            i -> cache.put(String.valueOf(i), String.valueOf(i))
        );
        assertThat(cache.size(), is(20));
        Thread.sleep(TTL);

        assertEventually(() -> assertThat(cache.size(), is(0)));
    }
}
