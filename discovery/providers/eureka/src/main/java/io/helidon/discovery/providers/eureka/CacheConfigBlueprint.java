/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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
package io.helidon.discovery.providers.eureka;

import java.time.Duration;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.builder.api.Prototype.Blueprint;

/**
 * Prototypical state for the portion of Eureka Discovery configuration related to a local cache of Eureka server
 * information.
 */
@Blueprint
@Prototype.Configured
interface CacheConfigBlueprint {

    /**
     * Whether the state of the cache should be computed from changes reported by Eureka, or replaced in full; {@code true} by default.
     *
     * @return whether the state of the cache should be computed
     */
    @Option.Configured("compute-changes")
    @Option.DefaultBoolean(true)
    boolean computeChanges();

    /**
     * Whether to defer immediate cache synchronization; {@code false} by default.
     *
     * @return {@code true} if cache synchronization should be deferred until it is actually needed
     */
    @Option.Configured("defer-sync")
    @Option.DefaultBoolean(false)
    boolean deferSync();

    /**
     * Whether a local cache of Eureka information is used or not; {@code true} by default.
     *
     * @return {@code true} if the cache should be used
     */
    @Option.Configured("enabled")
    @Option.DefaultBoolean(true)
    boolean enabled();

    /**
     * The name of the {@link Thread} used to retrieve service information from the Eureka server; "Eureka registry
     * fetch thread" by default.
     *
     * @return the name of the {@link Thread} used to retrieve service information from the Eureka server
     *
     * @see Thread.Builder#name(String)
     */
    @Option.Configured("fetch-thread-name")
    @Option.Default("Eureka registry fetch thread")
    String fetchThreadName();

    /**
     * The time between retrievals of service information from the Eureka server; 30 seconds by default.
     *
     * @return the time between retrievals of service information from the Eureka server
     *
     * @see Duration#parse(CharSequence)
     */
    @Option.Configured("sync-interval")
    @Option.Default("PT30S")
    Duration syncInterval();

}
