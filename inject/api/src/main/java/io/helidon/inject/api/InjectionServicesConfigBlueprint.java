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

package io.helidon.inject.api;

import java.time.Duration;
import java.util.Optional;

import io.helidon.builder.api.Prototype;
import io.helidon.config.metadata.Configured;
import io.helidon.config.metadata.ConfiguredOption;

/**
 * This is the configuration that the Injection service provider uses internally.
 * <p>
 * If left as-is a default configuration instance will be used with default values provided herein. Callers can
 * optionally configure values by providing a {@link Bootstrap#config()} prior to Injection startup. The configuration provided
 * will be used, and tunable configuration must be located under the key {@code inject} within the provided configuration
 * element.
 * @deprecated Helidon inject is deprecated and will be replaced in a future version
 */
@Deprecated(forRemoval = true, since = "4.0.8")
@Prototype.Blueprint
@Configured(root = true, prefix = "inject")
interface InjectionServicesConfigBlueprint {
    /**
     * The provider implementation name.
     *
     * @return the provider implementation name
     */
    @ConfiguredOption
    Optional<String> providerName();

    /**
     * The provider implementation version.
     *
     * @return the provider implementation version
     */
    @ConfiguredOption
    Optional<String> providerVersion();

    /**
     * The deadlock detection timeout in millis.
     *
     * @return the deadlock detection timeout
     */
    @ConfiguredOption("PT10S")
    Duration activationDeadlockDetectionTimeout();

    /**
     * Shutdown timeout.
     *
     * @return shutdown timeout
     */
    @ConfiguredOption("PT10S")
    Duration shutdownTimeout();

    /**
     * Flag indicating whether activation logs are captured, recorded, and retained.
     *
     * @return the flag indicating whether activation logs are captured and retained
     */
    @ConfiguredOption("false")
    boolean activationLogs();

    /**
     * Flag indicating whether service lookups (i.e., via {@link Services#lookup}) are cached.
     *
     * @return the flag indicating whether service lookups are cached
     */
    @ConfiguredOption("false")
    boolean serviceLookupCaching();

    /**
     * Flag indicating whether the services registry permits dynamic behavior. The default
     * implementation of Injection supports dynamic (see {@link #supportsDynamic()}), but does not permit it by default.
     *
     * @return the flag indicating whether the services registry supports dynamic updates of the service registry
     */
    @ConfiguredOption("false")
    boolean permitsDynamic();

    /**
     * Flag indicating whether the services registry supports dynamic behavior. Note that
     * if the provider does not support this flag then permitting it via {@link #permitsDynamic()} will have no affect. The
     * default implementation of Injection supports dynamic, but does not permit it by default.
     *
     * @return the flag indicating whether the services registry supports dynamic updates of the service registry post
     * startup
     */
    @ConfiguredOption("true")
    boolean supportsDynamic();

    /**
     * Flag indicating whether reflection is permitted. The default implementation of Injection
     * supports reflection at compile-time only, and is not controlled by this flag directly.
     *
     * @return the flag indicating whether the provider is permitted to use reflection for normal runtime usage
     */
    @ConfiguredOption("false")
    boolean permitsReflection();

    /**
     * Flag indicating whether the reflection is supported. Note that if the provider does not support this
     * flag then permitting it via {@link #permitsReflection()} will have no affect. The default implementation of Injection supports
     * reflection only during compile-time operations using the Injection <i>maven-plugin</i>.
     *
     * @return the flag indicating whether reflection is supported during runtime operations
     */
    @ConfiguredOption("false")
    boolean supportsReflection();

    /**
     * Flag indicating whether compile-time generated {@link Application}'s should be used at Injection's startup initialization.
     * Setting
     * this value to false will have no affect if the underlying provider does not support compile-time generation via
     * {@link #supportsCompileTime()}.
     *
     * @return the flag indicating whether the provider is permitted to use Application generated code from compile-time
     * @see Application
     * @see Activator
     */
    @ConfiguredOption("true")
    boolean usesCompileTimeApplications();

    /**
     * Flag indicating whether compile-time generated {@link ModuleComponent}'s should be used at Injection's startup
     * initialization. Setting this value to false will have no affect if the underlying provider does not support compile-time
     * generation via {@link #supportsCompileTime()}.
     *
     * @return the flag indicating whether the provider is permitted to use Application generated code from compile-time
     * @see ModuleComponent
     * @see Activator
     */
    @ConfiguredOption("true")
    boolean usesCompileTimeModules();

    /**
     * Flag indicating whether the dependency injection model for the {@link Application} and
     * {@link Activator} is capable for being produced at compile-time, and therefore used/loaded during runtime operations.
     *
     * @return the flag indicating whether the provider supports compile-time code generation of DI artifacts
     */
    @ConfiguredOption("true")
    boolean supportsCompileTime();

    /**
     * Flag indicating whether jsr330 specification will be used and enforced.
     *
     * @return the flag indicating whether strict jsr330 specification will be enforced
     */
    @ConfiguredOption("false")
    boolean usesJsr330();

    /**
     * Flag indicating whether jsr330 is supported by the provider implementation.
     *
     * @return the flag indicating whether the provider supports the jsr330 specification
     */
    @ConfiguredOption("true")
    boolean supportsJsr330();

    /**
     * Flag indicating whether jsr330 is supported by the provider implementation for the use on static injection points.
     *
     * @return the flag indicating whether the provider supports the jsr330 specification for the use of static injection points
     */
    @ConfiguredOption("false")
    boolean supportsJsr330Statics();

    /**
     * Flag indicating whether jsr330 is supported by the provider implementation for the use on private injection points.
     *
     * @return the flag indicating whether the provider supports the jsr330 specification for the use of private injection points
     */
    @ConfiguredOption("false")
    boolean supportsJsr330Privates();

    /**
     * Flag indicating whether contextual lookup is supported via {@link Services#contextualServices(InjectionPointInfo)}.
     *
     * @return the flag indicating whether the provider supports contextual lookup
     */
    @ConfiguredOption("false")
    boolean supportsContextualLookup();

    /**
     * Whether debug is enabled.
     * Defaults to false, can be overridden using system property {@link InjectionServices#TAG_DEBUG}.
     *
     * @return if debug should be enabled
     */
    @ConfiguredOption
    Optional<Boolean> debug();

    /**
     * Uses configured {@link #debug()}, or attempts to discover if debug should be used if not configured.
     *
     * @return whether to debug
     */
    default boolean shouldDebug() {
        return debug().orElseGet(() -> Boolean.getBoolean(InjectionServices.TAG_DEBUG));
    }
}
