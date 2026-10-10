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

import java.util.Objects;

import io.helidon.config.Config;

/**
 * Runtime-owned factory for generation-specific configuration filters.
 * A factory belongs to one independently built configuration runtime and may own resources shared by its generations.
 * The config system serializes filter creation for a runtime, including manual and automatic reloads.
 * Monitoring callbacks may run concurrently with filter creation and shutdown. The factory must coordinate its
 * monitoring state accordingly; {@link #stopChangeSupport()} may overlap {@link #create(Config)} during manual reload.
 *
 * @see ConfigFilterProvider
 */
@FunctionalInterface
public interface ConfigFilterFactory {
    /**
     * Creates a fully initialized filter for the initial configuration or a subsequent reload.
     * Filter state must remain unchanged when later filters are created; old configuration snapshots keep their filters.
     * The config system does not invoke {@link ConfigFilter#init(Config)} on returned filters.
     * The supplied configuration is an unfiltered source view with inert context operations, as described by
     * {@link ConfigFilterProvider#create(Config)}.
     *
     * @param config unfiltered source configuration for this generation
     * @return immutable or otherwise generation-isolated filter
     */
    ConfigFilter create(Config config);

    /**
     * Starts optional independent change monitoring after the first configuration is built.
     * Called at most once. The callback requests an asynchronous, coalesced configuration rebuild and may be invoked
     * during this method. A callback need not wait for filter creation or configuration change delivery.
     * Callbacks may be invoked concurrently from monitoring threads; the config system serializes resulting rebuilds.
     * Stop change support must also clean up a partially failed start.
     *
     * @param requestReload callback to request rebuilding this configuration runtime
     * @return whether automatic change monitoring is active
     */
    default boolean startChangeSupport(Runnable requestReload) {
        Objects.requireNonNull(requestReload, "requestReload");
        return false;
    }

    /**
     * Stops monitoring and releases its resources, including after failed initial construction.
     * Must be safe before start and after a partial start. Already admitted callbacks may complete; subsequent callbacks
     * must not request new rebuilds. Filter creation remains usable for manual reload and must not restart monitoring.
     */
    default void stopChangeSupport() {
    }
}
