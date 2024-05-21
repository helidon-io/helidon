/*
 * Copyright (c) 2020, 2024 Oracle and/or its affiliates.
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

package io.helidon.config.mp;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import io.helidon.common.configurable.LruCache;
import io.helidon.config.PropertiesFilter;

import jakarta.annotation.Priority;
import org.eclipse.microprofile.config.spi.ConfigSource;

@Priority(MpEnvironmentVariablesSource.MY_DEFAULT_ORDINAL)
class MpEnvironmentVariablesSource implements ConfigSource {
    static final int MY_DEFAULT_ORDINAL = 300;
    private static final Pattern DISALLOWED_CHARS = Pattern.compile("[^a-zA-Z0-9_]");
    private static final String UNDERSCORE = "_";
    private static final int MAX_CACHE_SIZE = 10000;

    private final Map<String, String> env;
    private final LruCache<String, Cached> cache;

    MpEnvironmentVariablesSource() {
        this(MAX_CACHE_SIZE);
    }

    MpEnvironmentVariablesSource(int cacheSize) {
        this.env = Map.copyOf(PropertiesFilter.create(System.getProperties()).filter(System.getenv()));
        this.cache = LruCache.<String, Cached>builder().capacity(cacheSize).build();
    }

    /**
     * Access internal cache, used for testing.
     *
     * @return internal cache
     */
    LruCache<String, Cached> cache() {
        return cache;
    }

    @Override
    public Set<String> getPropertyNames() {
        return env.keySet();
    }

    @Override
    public Map<String, String> getProperties() {
        return env;
    }

    @Override
    public String getValue(String propertyName) {
        // environment variable config source is immutable - we can safely cache all requested keys, so we
        // do not execute the regular expression on every get
        return cache.computeValue(propertyName, () -> {
            // According to the spec, we have three ways of looking for a property
            // 1. Exact match
            String result = env.get(propertyName);
            if (null != result) {
                return Optional.of(new Cached(result));
            }
            // 2. replace non alphanumeric characters with _
            String rule2 = rule2(propertyName);
            result = env.get(rule2);
            if (null != result) {
                return Optional.of(new Cached(result));
            }
            // 3. replace same as above, but uppercase
            String rule3 = rule2.toUpperCase();
            result = env.get(rule3);
            return Optional.of(new Cached(result));
        }).map(cached -> cached.value).orElse(null);
    }

    @Override
    public int getOrdinal() {
        String configOrdinal = getValue(CONFIG_ORDINAL);
        if (configOrdinal == null) {
            return MY_DEFAULT_ORDINAL;
        } else {
            return ConfigSource.super.getOrdinal();
        }
    }

    @Override
    public String getName() {
        return "Environment Variables";
    }

    @Override
    public String toString() {
        return getName() + " (" + getOrdinal() + ")";
    }

    /**
     * Rule #2 states: Replace each character that is neither alphanumeric nor _ with _ (i.e. com_ACME_size).
     *
     * @param propertyName name of property as requested by user
     * @return name of environment variable we look for
     */
    private static String rule2(String propertyName) {
        return DISALLOWED_CHARS.matcher(propertyName).replaceAll(UNDERSCORE);
    }

    private static final class Cached {
        private final String value;

        private Cached(String value) {
            this.value = value;
        }
    }
}
