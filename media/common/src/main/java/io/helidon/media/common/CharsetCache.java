/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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
package io.helidon.media.common;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Utility for caching per commonly used charsets.
 */
final class CharsetCache<T> {

    /**
     * Cache populator.
     * @param <T> cache item type
     */
    interface Populator<T> extends Function<Charset, T> {
    }

    /**
     * The charsets cached by default.
     */
    private static final Charset[] DEFAULT_CHARSETS = loadCharsets();

    /**
     * The cache.
     */
    private final Map<Charset, T> cache = new ConcurrentHashMap<>();

    /**
     * Create a new cache instance.
     * @param populator populator to use for caching the default charsets
     */
    CharsetCache(Populator<T> populator) {
        add(populator);
    }

    /**
     * Cache a new item for the defaults charset.
     * @param item item to cache
     */
    private void add(Populator<T> populator) {
        for (Charset charset : DEFAULT_CHARSETS) {
            cache.put(charset, populator.apply(charset));
        }
    }

    /**
     * Get, or create and cache an item from the charset cache.
     *
     * @param charset charset
     * @param populator function used to create the new item if not found in the
     * cache
     * @return cached item
     */
    T get(Charset charset, Populator<T> populator) {
        return cache.computeIfAbsent(charset, populator);
    }

    /**
     * Load the default charsets.
     * @return Charset[]
     */
    private static Charset[] loadCharsets() {
        ArrayList<Charset> charsets = new ArrayList<>();
        charsets.add(StandardCharsets.UTF_8);
        charsets.add(StandardCharsets.UTF_16);
        charsets.add(StandardCharsets.ISO_8859_1);
        charsets.add(StandardCharsets.US_ASCII);
        try {
            charsets.add(Charset.forName("cp1252"));
        } catch (Exception ignored) {
            // ignored
        }
        try {
            charsets.add(Charset.forName("cp1250"));
        } catch (Exception ignored) {
            // ignored
        }
        try {
            charsets.add(Charset.forName("ISO-8859-2"));
        } catch (Exception ignored) {
            // ignored
        }
        return charsets.toArray(new Charset[charsets.size()]);
    }
}
