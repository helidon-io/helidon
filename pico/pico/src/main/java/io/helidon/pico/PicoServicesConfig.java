/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

package io.helidon.pico;

import java.util.Optional;
import java.util.function.Supplier;

import io.helidon.builder.Builder;
import io.helidon.common.config.Config;
import io.helidon.config.metadata.ConfiguredOption;

/**
 * This is the configuration that the Pico service provider uses internally.
 * <p>
 * If left as-is a default configuration instance will be used with default values provided herein. Callers can
 * optionally configure values by providing a {@link Bootstrap#config()} prior to Pico startup. The configuration provided
 * will be used, and tunable configuration must be located under the key {@link #NAME} within the provided configuration
 * element.
 */
@Builder
public abstract class PicoServicesConfig {

    /**
     * Default Constructor.
     */
    protected PicoServicesConfig() {
    }

    /**
     * The short name for pico.
     */
    public static final String NAME = "pico";

    /**
     * The fully qualified name for pico.
     */
    public static final String FQN = "io.helidon." + NAME;

    /**
     * The key association with the name of the provider implementation.
     */
    public static final String KEY_PROVIDER_NAME = "provider-name";

    /**
     * The provider implementation name.
     *
     * @return the provider implementation name
     */
    @ConfiguredOption(key = KEY_PROVIDER_NAME)
    public abstract String providerName();


    /**
     * The key association with the version of the provider implementation.
     */
    public static final String KEY_PROVIDER_VERSION = "provider-version";

    /**
     * The provider implementation version.
     *
     * @return the provider implementation version
     */
    @ConfiguredOption(key = KEY_PROVIDER_VERSION)
    public abstract String providerVersion();


    /**
     * Applicable during activation, this is the key that controls the timeout before deadlock detection exceptions are thrown.
     * <p>
     * Deadlock can occur in situations where there are cyclic, non-{@code Provider<>} type dependencies between two services, e.g.,
     * A -> B and B -> A. Obviously this example is the simplest of cases. More often cyclic dependencies are nested N levels deep.
     * <p>
     * Pico may attempt to resolve cyclic dependencies, but this timeout will govern how long Pico will wait before giving up
     * and instead will result in an exception being thrown.
     * <p>
     * There are two best practices recommended:
     * <ol>
     *     <li>Use {@code Provider<>} as often as possible. If a service activation does not a dependency during
     *     {@code PostConstruct} then there is really no need to have a direct dependency to that service.
     *     <li>Use compile-time {@link Application} generation. See the Pico <i>maven-plugin</i> module for details. Use of this
     *     feature will detect all cyclic dependencies at compile-time and will result in faster startup times.
     * </ol>
     */
    public static final String KEY_ACTIVATION_DEADLOCK_TIMEOUT_IN_MILLIS = "activation-deadlock-timeout-millis";
    /**
     * The default deadlock detection timeout in millis.
     */
    public static final String DEFAULT_ACTIVATION_DEADLOCK_TIMEOUT_IN_MILLIS = "10000";

    /**
     * The deadlock detection timeout in millis.
     *
     * @return the deadlock detection timeout in mills
     */
    @ConfiguredOption(key = KEY_ACTIVATION_DEADLOCK_TIMEOUT_IN_MILLIS, value = DEFAULT_ACTIVATION_DEADLOCK_TIMEOUT_IN_MILLIS)
    public long activationDeadlockDetectionTimeoutMillis() {
        return asLong(KEY_ACTIVATION_DEADLOCK_TIMEOUT_IN_MILLIS,
                      () -> Long.valueOf(DEFAULT_ACTIVATION_DEADLOCK_TIMEOUT_IN_MILLIS));
    }


    /**
     * Applicable for enabling the capture of activation logs at Pico startup.
     */
    public static final String KEY_ACTIVATION_LOGS = "activation-logs";
    /**
     * The default value for this is false, meaning that the activation logs will not be captured or recorded or logged.
     */
    public static final String DEFAULT_ACTIVATION_LOGS = "false";

    /**
     * Flag indicating whether activation logs are captured, recorded, and retained.
     *
     * @return the flag indicating whether activation logs are captured and retained
     */
    @ConfiguredOption(key = KEY_ACTIVATION_LOGS, value = DEFAULT_ACTIVATION_LOGS)
    public boolean activationLogs() {
        return asBoolean(KEY_ACTIVATION_LOGS, () -> Boolean.valueOf(DEFAULT_ACTIVATION_LOGS));
    }


    /**
     * Applicable for enabling service lookup caching.
     */
    public static final String KEY_SERVICE_LOOKUP_CACHING = "service-lookup-caching";
    /**
     * The default value for this is false, meaning that no caching will occur.
     */
    public static final String DEFAULT_SERVICE_LOOKUP_CACHING = "false";

    /**
     * Flag indicating whether service lookups (i.e., via {@link Services#lookup} are cached.
     *
     * @return the flag indicating whether service lookups are cached
     */
    @ConfiguredOption(key = KEY_SERVICE_LOOKUP_CACHING, value = DEFAULT_SERVICE_LOOKUP_CACHING)
    public boolean serviceLookupCaching() {
        return asBoolean(KEY_SERVICE_LOOKUP_CACHING, () -> Boolean.valueOf(DEFAULT_SERVICE_LOOKUP_CACHING));
    }


    /**
     * The key that controls whether the {@link Services} registry is permitted to expand or be dynamically altered after JVM
     * startup.
     */
    public static final String KEY_PERMITS_DYNAMIC = "permits-dynamic";
    /**
     * The default value for this is false, meaning that the services registry cannot be changed during runtime post Pico startup.
     */
    public static final String DEFAULT_PERMITS_DYNAMIC = "false";

    /**
     * Flag indicating whether the services registry permits dynamic behavior (key is {@link #KEY_PERMITS_DYNAMIC}). The default
     * implementation of Pico supports dynamic (see {@link #supportsDynamic()}, but does not permit it by default.
     *
     * @return the flag indicating whether the services registry supports dynamic updates of the service registry
     */
    @ConfiguredOption(key = KEY_PERMITS_DYNAMIC, value = DEFAULT_PERMITS_DYNAMIC)
    public boolean permitsDynamic() {
        return asBoolean(KEY_PERMITS_DYNAMIC, () -> Boolean.valueOf(DEFAULT_PERMITS_DYNAMIC));
    }


    /**
     * The key that indicates whether the {@link Services} registry is capable of expanding or being dynamically altered after JVM
     * startup. This is referred to as the service registry being dynamic in nature.
     */
    public static final String KEY_SUPPORTS_DYNAMIC = "supports-dynamic";
    /**
     * The default value for this is false, meaning that the services registry supports dynamic behavior post Pico startup.
     */
    public static final String DEFAULT_SUPPORTS_DYNAMIC = "true";

    /**
     * Flag indicating whether the services registry supports dynamic behavior (key is {@link #KEY_SUPPORTS_DYNAMIC}). Note that
     * if the provider does not support this flag then permitting it via {@link #permitsDynamic()} will have no affect. The default
     * implementation of Pico supports dynamic, but does not permit it by default.
     *
     * @return the flag indicating whether the services registry supports dynamic updates of the service registry post Pico startup
     */
    @ConfiguredOption(key = KEY_SUPPORTS_DYNAMIC, value = DEFAULT_SUPPORTS_DYNAMIC)
    public abstract boolean supportsDynamic();


    /**
     * The key that controls whether reflection is permitted to be used during Pico runtime operations. The default implementation
     * of Pico does not support runtime reflection usage; it is only supported via the Pico <i>maven-plugin</i> and
     * supporting tooling, which typically occurs during compile-time operations but not during normal runtime operations.
     */
    public static final String KEY_PERMITS_REFLECTION = "permits-reflection";
    /**
     * The default value for this is false, meaning that the Pico will make not attempt to use reflection during runtime operations.
     */
    public static final String DEFAULT_PERMITS_REFLECTION = "false";

    /**
     * Flag indicating whether reflection is permitted (key is {@link #KEY_PERMITS_DYNAMIC}). The default implementation of Pico
     * supports reflection at compile-time only, and is not controlled by this flag directly.
     *
     * @return the flag indicating whether the provider is permitted to use reflection for normal runtime usage
     */
    @ConfiguredOption(key = KEY_PERMITS_REFLECTION, value = DEFAULT_PERMITS_REFLECTION)
    public boolean permitsReflection() {
        return asBoolean(KEY_PERMITS_REFLECTION, () -> Boolean.valueOf(DEFAULT_PERMITS_REFLECTION));
    }


    /**
     * The key that indicates whether the reflection is supported in normal runtime behavior.
     */
    public static final String KEY_SUPPORTS_REFLECTION = "supports-reflection";
    /**
     * The default value for this is false, meaning that the reflection is not supported by the provider.
     */
    public static final String DEFAULT_SUPPORTS_REFLECTION = "false";

    /**
     * Flag indicating whether the reflection is supported. Note that if the provider does not support this
     * flag then permitting it via {@link #permitsReflection()} will have no affect. The default implementation of Pico supports
     * reflection only during compile-time operations using the Pico <i>maven-plugin</i>.
     *
     * @return the flag indicating whether reflection is supported during runtime operations
     */
    @ConfiguredOption(key = KEY_SUPPORTS_REFLECTION, value = DEFAULT_SUPPORTS_REFLECTION)
    public abstract boolean supportsReflection();


    /**
     * The key that controls whether any {@link Application} (typically produced at compile-time by Pico tooling)
     * can be discovered and is used during Pico startup processing. It is strongly suggested for developers to adopt
     * a compile-time strategy for producing the dependency/injection model as it will lead to faster startup times as well as be
     * deterministic and validated during compile-time instead of at runtime.
     */
    public static final String KEY_USES_COMPILE_TIME = "uses-compile-time";
    /**
     * The default value for this is true, meaning that the Pico will attempt to find and use {@link Application} code generated
     * during compile-time (see Pico's APT <i>processor</i> and <i>maven-plugin</i> modules for usage).
     */
    public static final String DEFAULT_USES_COMPILE_TIME = "true";

    /**
     * Flag indicating whether compile-time generated {@link Application}'s should be used at Pico's startup initialization. Setting
     * this value to false will have no affect if the underlying provider does not support compile-time generation.
     *
     * @return the flag indicating whether the provider is permitted to use Application generated code from compile-time
     * @see io.helidon.pico.Application
     * @see io.helidon.pico.Activator
     */
    @ConfiguredOption(key = KEY_USES_COMPILE_TIME, value = DEFAULT_USES_COMPILE_TIME)
    public boolean usesCompileTime() {
        return asBoolean(KEY_USES_COMPILE_TIME, () -> Boolean.valueOf(DEFAULT_USES_COMPILE_TIME));
    }


    /**
     * The key that represents whether the provider supports compile-time code generation of DI artifacts.
     *
     * @see io.helidon.pico.Application
     * @see io.helidon.pico.Activator
     */
    public static final String KEY_SUPPORTS_COMPILE_TIME = "supports-compile-time";
    /**
     * The default value is true, meaning that the provider supports compile-time code generation of DI artifacts.
     */
    public static final String DEFAULT_SUPPORTS_COMPILE_TIME = "true";

    /**
     * Flag indicating whether the dependency injection model for the {@link Application} and
     * {@link Activator} is capable for being produced at compile-time, and therefore used/loaded during runtime operations.
     *
     * @return the flag indicating whether the provider supports compile-time code generation of DI artifacts
     */
    @ConfiguredOption(key = KEY_SUPPORTS_COMPILE_TIME, value = DEFAULT_SUPPORTS_COMPILE_TIME)
    public abstract boolean supportsCompileTime();


    /**
     * The key that controls whether strict jsr330 specification interpretation is used. See the README for additional details.
     */
    public static final String KEY_USES_JSR330 = "uses-jsr330";
    /**
     * The default value for this is false, meaning that the Pico implementation will not follow a strict jsr330 interpretation
     * of the specification. See the README for additional details.
     */
    public static final String DEFAULT_USES_JSR330 = "false";

    /**
     * Flag indicating whether jsr330 specification will be used and enforced.
     *
     * @return the flag indicating whether strict jsr330 specification will be enforced
     */
    @ConfiguredOption(key = KEY_USES_JSR330, value = DEFAULT_USES_JSR330)
    public boolean usesJsr330() {
        return asBoolean(KEY_USES_JSR330, () -> Boolean.valueOf(DEFAULT_USES_JSR330));
    }


    /**
     * The key to represent whether the provider supports the jsr330 specification.
     */
    public static final String KEY_SUPPORTS_JSR330 = "supports-jsr330";
    /**
     * The default value is true, meaning that the default Pico implementation supports the jsr330 specification (i.e.,
     * one that passes the jsr330 TCK).
     */
    public static final String DEFAULT_SUPPORTS_JSR330 = "true";

    /**
     * Flag indicating whether jsr330 is supported by the provider implementation.
     *
     * @return the flag indicating whether the provider supports the jsr330 specification
     */
    @ConfiguredOption(key = KEY_SUPPORTS_JSR330, value = DEFAULT_SUPPORTS_JSR330)
    public abstract boolean supportsJsr330();


    /**
     * Key indicating support for static injection points.  Note: this is optional in jsr330.
     */
    public static final String KEY_SUPPORTS_JSR330_STATICS = KEY_SUPPORTS_JSR330 + "-statics";
    /**
     * The default value is false, meaning that the default provider implementation does not support static injection points.
     */
    public static final String DEFAULT_SUPPORTS_JSR330_STATICS = "false";

    /**
     * Flag indicating whether jsr330 is supported by the provider implementation for the use on static injection points.
     *
     * @return the flag indicating whether the provider supports the jsr330 specification for the use of static injection points
     */
    @ConfiguredOption(key = KEY_SUPPORTS_JSR330_STATICS, value = DEFAULT_SUPPORTS_JSR330_STATICS)
    public abstract boolean supportsJsr330Statics();


    /**
     * Key indicating support for private injection points.  Note: this is optional in jsr330.
     */
    public static final String KEY_SUPPORTS_JSR330_PRIVATES = KEY_SUPPORTS_JSR330 + "-privates";
    /**
     * The default value is false, meaning that the default provider implementation does not support private injection points.
     */
    public static final String DEFAULT_SUPPORTS_JSR330_PRIVATES = "false";

    /**
     * Flag indicating whether jsr330 is supported by the provider implementation for the use on private injection points.
     *
     * @return the flag indicating whether the provider supports the jsr330 specification for the use of private injection points
     */
    @ConfiguredOption(key = KEY_SUPPORTS_JSR330_PRIVATES, value = DEFAULT_SUPPORTS_JSR330_PRIVATES)
    public abstract boolean supportsJsr330Privates();


    /**
     * Key indicating support for contextual lookup via {@link Services#contextualServices(InjectionPointInfo)}.
     */
    public static final String KEY_SUPPORTS_CONTEXTUAL_LOOKUP = "supports-contextual-lookup";
    /**
     * The default value is false, meaning that contextual lookup is not supported.
     */
    public static final String DEFAULT_SUPPORTS_CONTEXTUAL_LOOKUP = "false";

    /**
     * Flag indicating whether contextual lookup is supported via {@link Services#contextualServices(InjectionPointInfo)}.
     *
     * @return the flag indicating whether the provider supports contextual lookup
     */
    @ConfiguredOption(key = KEY_SUPPORTS_CONTEXTUAL_LOOKUP, value = DEFAULT_SUPPORTS_CONTEXTUAL_LOOKUP)
    public abstract boolean supportsContextualLookup();


    /**
     * Shortcut method to obtain a String with a default value supplier.
     *
     * @param key configuration key
     * @param defaultValueSupplier supplier of default value
     * @return value
     */
    protected String asString(
            String key,
            Supplier<String> defaultValueSupplier) {
        Optional<Config> cfg = get(key);
        if (cfg.isEmpty()
                || !cfg.get().hasValue()) {
            return defaultValueSupplier.get();
        }
        return cfg.get().asString().orElseGet(defaultValueSupplier);
    }

    /**
     * Shortcut method to obtain a Boolean with a default value supplier.
     *
     * @param key configuration key
     * @param defaultValueSupplier supplier of default value
     * @return value
     */
    protected Boolean asBoolean(
            String key,
            Supplier<Boolean> defaultValueSupplier) {
        Optional<Config> cfg = get(key);
        if (cfg.isEmpty()
                || !cfg.get().hasValue()) {
            return defaultValueSupplier.get();
        }
        return cfg.get().asBoolean().orElseGet(defaultValueSupplier);
    }

    /**
     * Shortcut method to obtain a Long with a default value supplier.
     *
     * @param key configuration key
     * @param defaultValueSupplier supplier of default value
     * @return value
     */
    protected Long asLong(
            String key,
            Supplier<Long> defaultValueSupplier) {
        Optional<Config> cfg = get(key);
        if (cfg.isEmpty()
                || !cfg.get().hasValue()) {
            return defaultValueSupplier.get();
        }
        return cfg.get().asLong().orElseGet(defaultValueSupplier);
    }

    /**
     * Retrieves an arbitrary {@link Config} given a key. The physical configuration will be based upon any
     * {@link Bootstrap#config()} that was previously established using {@link PicoServices#globalBootstrap()}. If the bootstrap
     * configuration has not been established then empty is returned.
     *
     * @param key the config key relative to the parent global bootstrap configuration
     * @return the configuration for the key
     */
    protected Optional<Config> get(
            String key) {
        Optional<Bootstrap> bootstrap = PicoServicesHolder.bootstrap(false);
        if (bootstrap.isEmpty()
                || bootstrap.get().config().isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(bootstrap.get().config().get().get(NAME).get(key));
    }

}
