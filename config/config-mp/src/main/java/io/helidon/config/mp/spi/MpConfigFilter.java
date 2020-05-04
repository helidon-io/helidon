/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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

package io.helidon.config.mp.spi;

import org.eclipse.microprofile.config.Config;

/**
 * Filtering support for MicroProfile config implementation.
 * The current specification does not have a way to intercept values as they are
 * delivered.
 * As we want to support filtering capabilities (such as for configuration encryption),
 *  this is a temporary solution (or permanent if the MP spec does not add any similar feature).
 */
public interface MpConfigFilter {
    /**
     * Initialize this filter from configuration.
     * The config instance provided only has filters with higher priority than this filter.
     *
     * @param config configuration to set this filter up.
     */
    default void init(Config config) {
    }

    /**
     * Apply this filter on the provided value.
     *
     * @param propertyName name of the property (its key)
     * @param value the current value of the property as retrieved from the config source, or from previous
     *                 filters
     * @return value as processed by this filter
     */
    String apply(String propertyName, String value);
}
