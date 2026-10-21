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
 * Context for config filter factories belonging to one configuration runtime.
 *
 * @see ConfigFilterProvider
 */
public interface ConfigFilterContext {
    /**
     * Returns the initial unfiltered source configuration for this runtime.
     * The configuration contains merged source values before value filtering.
     * {@link Config#get(String)} and {@link Config#detach()} return nodes in this unfiltered view.
     * {@link Config.Context#last()} and {@link Config.Context#reload()} return the same unfiltered node;
     * {@link Config.Context#stopChangeSupport()} has no effect.
     * This snapshot does not emit changes, but {@link Config#onChange(java.util.function.Consumer)} registers listeners
     * with the owning runtime. They receive published filtered configurations; a {@link Config.ChangeListener} also
     * receives stop notification, including if initial construction fails. Register listeners once during
     * {@link ConfigFilterProvider#create(ConfigFilterContext) provider creation}.
     * <p>
     * Values from lazy sources are read on demand. Capture the values needed by a filter during its creation so later
     * source changes cannot affect that filter.
     *
     * @return initial unfiltered source configuration
     */
    Config initialConfig();

    /**
     * Requests an asynchronous configuration rebuild using the latest known source data.
     */
    void reload();
}
