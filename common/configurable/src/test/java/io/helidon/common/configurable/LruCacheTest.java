/*
 * Copyright (c) 2019, 2021 Oracle and/or its affiliates.
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

package io.helidon.common.configurable;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Unit test for {@link LruCache}.
 */
class LruCacheTest {
    @Test
    void testCache() {
        LruCache<String, String> theCache = LruCache.create();
        String value = "cached";
        String key = "theKey";
        String newValue = "not-cached";

        Optional<String> res = theCache.put(key, value);
        assertThat(res, is(Optional.empty()));
        res = theCache.get(key);
        assertThat(res, is(Optional.of(value)));
        res = theCache.computeValue(key, () -> Optional.of(newValue));
        assertThat(res, is(Optional.of(value)));
        res = theCache.remove(key);
        assertThat(res, is(Optional.of(value)));
        res = theCache.get(key);
        assertThat(res, is(Optional.empty()));
    }

    @Test
    void testCacheComputeValue() {
        LruCache<String, String> theCache = LruCache.create();
        String value = "cached";
        String key = "theKey";
        String newValue = "not-cached";

        Optional<String> res = theCache.computeValue(key, () -> Optional.of(value));
        assertThat(res, is(Optional.of(value)));
        res = theCache.get(key);
        assertThat(res, is(Optional.of(value)));
        res = theCache.computeValue(key, () -> Optional.of(newValue));
        assertThat(res, is(Optional.of(value)));
        res = theCache.remove(key);
        assertThat(res, is(Optional.of(value)));
        res = theCache.get(key);
        assertThat(res, is(Optional.empty()));
    }

    @Test
    void testMaxCapacity() {
        LruCache<Integer, Integer> theCache = LruCache.<Integer, Integer>builder().capacity(10).build();
        for (int i = 0; i < 10; i++) {
            theCache.put(i, i);
        }
        for (int i = 0; i < 10; i++) {
            Optional<Integer> integer = theCache.get(i);
            assertThat(integer, is(Optional.of(i)));
        }
        theCache.put(10, 10);
        Optional<Integer> res = theCache.get(0);
        assertThat(res, is(Optional.empty()));
        res = theCache.get(10);
        assertThat(res, is(Optional.of(10)));
    }

    @Test
    void testLruBehavior() {
        LruCache<Integer, Integer> theCache =  LruCache.<Integer, Integer>builder().capacity(10).build();
        for (int i = 0; i < 10; i++) {
            // insert all
            theCache.put(i, i);
        }
        for (int i = 0; i < 10; i++) {
            // use them in ascending order
            Optional<Integer> integer = theCache.get(i);
            assertThat(integer, is(Optional.of(i)));
        }
        // now use 0
        Optional<Integer> value = theCache.get(0);
        assertThat(value, is(Optional.of(0)));

        theCache.put(10, 10);

        // 0 should be in
        value = theCache.get(0);
        assertThat(value, is(Optional.of(0)));

        // 1 should not
        value = theCache.get(1);
        assertThat(value, is(Optional.empty()));

    }
}
