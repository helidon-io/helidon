/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

package io.helidon.service.inject;

import java.util.List;
import java.util.Optional;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.common.configurable.LruCache;
import io.helidon.service.inject.api.Activator;
import io.helidon.service.inject.api.InjectServiceInfo;
import io.helidon.service.inject.api.Lookup;
import io.helidon.service.registry.ServiceRegistryConfig;

/**
 * Helidon Inject configuration options.
 */
@Prototype.Blueprint
@Prototype.Configured("registry")
interface InjectConfigBlueprint extends ServiceRegistryConfig {
    /**
     * LRU cache to use for caching lookup. Only needed if {@link #lookupCacheEnabled()} is set to {@code true}.
     * This allows customization of the LRU cache.
     *
     * @return LRU cache to use for lookup caching, or empty to use the default instance (or when not used at all)
     */
    @Option.Configured
    Optional<LruCache<Lookup, List<InjectServiceInfo>>> lookupCache();

    /**
     * Flag indicating whether service lookups
     * (i.e., via {@link io.helidon.service.inject.api.InjectRegistry#first(io.helidon.service.inject.api.Lookup)}) are cached.
     *
     * @return the flag indicating whether service lookups are cached, defaults to {@code false}
     */
    @Option.Configured
    boolean lookupCacheEnabled();

    /**
     * Flag indicating whether runtime interception is enabled.
     * If set to {@code false}, methods will be invoked without any interceptors, even if interceptors are available.
     *
     * @return whether to intercept calls at runtime, defaults to {@code true}
     */
    @Option.Configured
    @Option.DefaultBoolean(true)
    boolean interceptionEnabled();

    /**
     * In certain conditions Injection services should be initialized but not started (i.e., avoiding calls to
     * {@code PostConstruct}
     * etc.). This can be used in special cases where the normal Injection startup should limit lifecycle up to a given phase.
     * Normally one should not use this feature - it is mainly used in Injection tooling (e.g., the service maven-plugin).
     *
     * @return the phase to stop at during lifecycle
     */
    @Option.Configured
    @Option.Default("ACTIVE")
    Activator.Phase limitRuntimePhase();

    /**
     * Flag indicating whether compile-time generated {@link io.helidon.service.inject.Application}'s
     * should be used at Injection's startup initialization.
     * Even if set to {@code true}, this is effective only if an Application was generated using Helidon Service Maven Plugin.
     *
     * @return the flag indicating whether the provider is permitted to use Application generated code from compile-time,
     *         defaults to {@code true}
     * @see io.helidon.service.inject.Application
     */
    @Option.Configured
    @Option.DefaultBoolean(true)
    boolean useApplication();
}
