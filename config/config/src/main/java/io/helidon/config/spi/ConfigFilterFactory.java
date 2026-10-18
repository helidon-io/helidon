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
package io.helidon.config.spi;

import io.helidon.config.Config;

/**
 * {@link ConfigFilter} factory.
 * A factory is created for each independently built configuration and reused when that configuration is reloaded.
 *
 * @see ConfigFilterProvider
 */
@FunctionalInterface
public interface ConfigFilterFactory {
    /**
     * Creates a new config filter.
     * <p>
     * This method is invoked during configuration initialization and subsequent reloads.
     * The returned filter is scoped to that configuration snapshot. Creating a filter must not change the behavior of
     * filters created for earlier snapshots.
     * The supplied configuration is an unfiltered source view, as described by
     * {@link ConfigFilterContext#initialConfig()}.
     *
     * @param config unfiltered source configuration for this snapshot
     * @return fully initialized filter scoped to this configuration snapshot
     */
    ConfigFilter create(Config config);
}
