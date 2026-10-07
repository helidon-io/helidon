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

package io.helidon.config;

import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.common.Weight;
import io.helidon.config.spi.ConfigFilterFactory;
import io.helidon.config.spi.ConfigFilterProvider;

public class AutoLoadedConfigFilterProvider implements ConfigFilterProvider {
    static final String VALUE_KEY = "auto-provider-value";
    static final String REFERENCE_KEY = "auto-provider-reference";
    static final String PRIORITY_KEY = "auto-provider-priority";

    @Override
    public ConfigFilterFactory create(Config initialConfig) {
        var generations = new AtomicInteger();
        return _ -> {
            int generation = generations.incrementAndGet();
            return (key, value) -> {
                if (VALUE_KEY.equals(key.toString())) {
                    return value + ":" + generation;
                }
                if (REFERENCE_KEY.equals(key.toString())) {
                    return "${auto-provider-target}";
                }
                return value;
            };
        };
    }

    @Weight(200)
    public static class HighPriority implements ConfigFilterProvider {
        @Override
        public ConfigFilterFactory create(Config initialConfig) {
            return _ -> (key, value) -> PRIORITY_KEY.equals(key.toString()) ? value + ":high" : value;
        }
    }

    @Weight(50)
    public static class LowPriority implements ConfigFilterProvider {
        @Override
        public ConfigFilterFactory create(Config initialConfig) {
            return _ -> (key, value) -> PRIORITY_KEY.equals(key.toString()) ? value + ":low" : value;
        }
    }
}
