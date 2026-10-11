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
 * {@link ConfigFilter} factory.
 * A factory is created for each independently built configuration and reused when that configuration is reloaded.
 * Calls to {@link #create(Config)} on the same factory do not overlap, including during manual and automatic reloads.
 * <p>
 * The reload callback passed to {@link #startChangeSupport(Runnable)} may be invoked from monitoring threads while
 * {@link #create(Config)} or {@link #stopChangeSupport()} is running.
 * Calls to {@link #stopChangeSupport()} may overlap {@link #create(Config)} during manual reload.
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
     * {@link ConfigFilterProvider#create(Config)}.
     *
     * @param config unfiltered source configuration for this snapshot
     * @return fully initialized filter scoped to this configuration snapshot
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
