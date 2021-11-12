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
package io.helidon.metrics.api;

import io.helidon.config.Config;
import io.helidon.config.metadata.Configured;
import io.helidon.config.metadata.ConfiguredOption;

/**
 * Filter settings for registries.
 *
 * <p>
 *     Each filter settings instance contains two optional regular expression patterns, one indicating metric names to include
 *     and one indicating metric names to exclude. The patterns work together like this:
 *     <ul>
 *         <li>If you assign neither pattern, all metric names pass the filter and are accepted.</li>
 *         <li>If you assign the exclude pattern, the filter rejects any candidate metric name that matches it,
 *         regardless of the include pattern.</li>
 *         <li>If you assign the include pattern, the filter accepts only those candidate metric names which match the include
 *         pattern and do not match the exclude pattern (if you assigned one).</li>
 *     </ul>
 */
public interface RegistryFilterSettings {

    /**
     * Creates a new default builder for {@code RegistryFilterSettings}.
     *
     * @return new builder
     */
    static Builder builder() {
        return RegistryFilterSettingsImpl.builder();
    }

    /**
     * Creates a new default instance of {@code RegistryFilterSettings}.
     * @return new settings instance
     */
    static RegistryFilterSettings create() {
        return builder().build();
    }

    /**
     * Reports whether a given metric name passes the filter.
     *
     * @param metricName name of the metric to check
     * @return true if the name passes the filter; false otherwise
     */
    boolean passes(String metricName);

    /**
     * Builder for a new {@code RegistryFilterSettings} instance.
     */
    @Configured
    interface Builder extends io.helidon.common.Builder<Builder, RegistryFilterSettings> {

        /**
         * Returns a new builder initialized according to the specific configuration.
         *
         * @param config the registry settings config node
         * @return newly-initialized builder
         */
        static Builder create(Config config) {
            return builder().config(config);
        }

        /**
         * Config key within the {@code filter} section for the regex for names to exclude.
         */
        String EXCLUDE_CONFIG_KEY = "exclude";

        /**
         * Config key within the {@code filter} section for the regex for names to include.
         */
        String INCLUDE_CONFIG_KEY = "include";

        /**
         * Sets the regex for names to exclude.
         *
         * @param excludeFilter filter for names to exclude
         * @return updated builder
         */
        @ConfiguredOption(key = EXCLUDE_CONFIG_KEY,
                          description = "Regular expression matching metric names to exclude")
        Builder exclude(String excludeFilter);

        /**
         * Sets the refex for names to include.
         *
         * @param includeFilter filter for names to include
         * @return updated builder
         */
        @ConfiguredOption(key = INCLUDE_CONFIG_KEY,
                          description = "Regular expression matching metrics names to include")
        Builder include(String includeFilter);

        /**
         * Sets whichever values are specified by the provided {@code filter} config node.
         *
         * @param config the filter config node
         * @return updated builder
         */
        Builder config(Config config);
    }
}
