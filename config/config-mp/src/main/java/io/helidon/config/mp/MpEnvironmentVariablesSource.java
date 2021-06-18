/*
 * Copyright (c) 2020, 2021 Oracle and/or its affiliates.
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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import javax.annotation.Priority;

import org.eclipse.microprofile.config.spi.ConfigSource;

@Priority(300)
class MpEnvironmentVariablesSource implements ConfigSource {
    private static final Pattern DISALLOWED_CHARS = Pattern.compile("[^a-zA-Z0-9_]");
    private static final String UNDERSCORE = "_";

    private final Map<String, String> env;
    private final Map<String, Cached> cache = new ConcurrentHashMap<>();

    MpEnvironmentVariablesSource() {
        this.env = Map.copyOf(System.getenv());
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
        return cache.computeIfAbsent(propertyName, theKey -> {
            // According to the spec, we have three ways of looking for a property
            // 1. Exact match
            String result = env.get(propertyName);
            if (null != result) {
                return new Cached(result);
            }
            // 2. replace non alphanumeric characters with _
            String rule2 = rule2(propertyName);
            result = env.get(rule2);
            if (null != result) {
                return new Cached(result);
            }
            // 3. replace same as above, but uppercase
            String rule3 = rule2.toUpperCase();
            result = env.get(rule3);
            return new Cached(result);
        }).value;
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
