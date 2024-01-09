/*
 * Copyright (c) 2022, 2024 Oracle and/or its affiliates.
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

package io.helidon.inject;

import java.util.List;
import java.util.Optional;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.common.config.Config;
import io.helidon.inject.service.ServiceDescriptor;

/**
 * Helidon Inject configuration options.
 */
@Prototype.Blueprint
@Prototype.Configured("inject")
interface InjectionConfigBlueprint {
    /**
     * In certain conditions Injection services should be initialized but not started (i.e., avoiding calls to
     * {@code PostConstruct}
     * etc.). This can be used in special cases where the normal Injection startup should limit lifecycle up to a given phase.
     * Normally
     * one should not use this feature - it is mainly used in Injection tooling (e.g., the injection maven-plugin).
     *
     * @return the phase to stop at during lifecycle
     */
    @Option.Configured
    @Option.Default("ACTIVE")
    Phase limitRuntimePhase();

    /**
     * Flag indicating whether service lookups
     * (i.e., via {@link io.helidon.inject.Services#first(io.helidon.inject.service.Lookup)}) are cached.
     *
     * @return the flag indicating whether service lookups are cached, defaults to {@code false}
     */
    @Option.Configured
    @Option.DefaultBoolean(false)
    boolean serviceLookupCaching();

    /**
     * Flag indicating whether compile-time generated {@link io.helidon.inject.Application}'s
     * should be used at Injection's startup initialization.
     * Even if set to {@code true}, this is effective only if an Application was generated using Helidon Inject Maven Plugin.
     *
     * @return the flag indicating whether the provider is permitted to use Application generated code from compile-time,
     *         defaults to {@code true}
     * @see io.helidon.inject.Application
     */
    @Option.Configured
    @Option.DefaultBoolean(true)
    boolean useApplication();

    /**
     * Flag indicating whether {@link io.helidon.inject.service.ModuleComponent} is discovered from the
     * classpath (expected to be code generated, uses {@link java.util.ServiceLoader}).
     * Note that if this is disabled, the registry will be empty, and unless you add service descriptors by hand.
     *
     * @return the flag indicating whether the provider should use modules to bind services
     */
    @Option.Configured
    @Option.DefaultBoolean(true)
    boolean useModules();

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
     * Manually registered service descriptors to add to the registry.
     * This is useful when {@link #useModules()} is set to {@code false}, to register only hand-picked services
     * into the registry.
     * <p>
     * Even when modules are used, this can be used to add service descriptors that are not part of a module
     * available through service loader.
     *
     * @return services to register
     */
    @Option.Singular
    List<ServiceDescriptor<?>> serviceDescriptors();

    /**
     * Configuration to use to set up business application services.
     * When configured here, the registry should not use {@link io.helidon.common.config.GlobalConfig}.
     *
     * @return configuration (root) to use
     */
    Optional<Config> serviceConfig();
}
