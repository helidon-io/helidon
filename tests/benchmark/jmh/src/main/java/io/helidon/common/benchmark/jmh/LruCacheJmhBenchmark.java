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

package io.helidon.common.benchmark.jmh;

import java.util.Optional;

import io.helidon.common.LruCache;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

@State(Scope.Thread)
public class LruCacheJmhBenchmark {
    private static final int CACHE_SIZE = 128;

    private LruCache<String, String> cache;
    private String hitKey;
    private String missKey;
    private String defaultValue;

    @Setup
    public void setup() {
        cache = LruCache.create(CACHE_SIZE);
        for (int i = 0; i < CACHE_SIZE; i++) {
            cache.put("key-" + i, "value-" + i);
        }
        hitKey = "key-64";
        missKey = "missing";
        defaultValue = "default";
    }

    @Benchmark
    public String peekHit() {
        return cache.peek(hitKey, defaultValue);
    }

    @Benchmark
    public String peekMiss() {
        return cache.peek(missKey, defaultValue);
    }

    @Benchmark
    public Optional<String> getHit() {
        return cache.get(hitKey);
    }
}
