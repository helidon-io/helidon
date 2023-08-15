/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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
package io.helidon.metrics.api;

import java.util.Optional;
import java.util.regex.Pattern;

import io.helidon.builder.api.Prototype;

class MetricsConfigSupport {

    /**
     * Looks up a single config value within the metrics configuration by config key.
     *
     * @param metricsConfig the {@link io.helidon.common.config.Config} node containing the metrics configuration
     * @param key config key to fetch
     * @return config value
     */
    @Prototype.PrototypeMethod
    static Optional<String> lookupConfig(MetricsConfig metricsConfig, String key) {
        return metricsConfig.config()
                .get(key)
                .asString()
                .asOptional();
    }

    // Pattern of a single tag assignment (tag=value):
    //   - capture reluctant match of anything
    //   - non-capturing match of an unescaped =
    //   - capture the rest.
    static final Pattern TAG_ASSIGNMENT_PATTERN = Pattern.compile("(.*?)(?<!\\\\)=(.*)");

    private MetricsConfigSupport() {
    }
}
