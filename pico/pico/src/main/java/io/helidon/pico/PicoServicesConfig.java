/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Provides optional config by the provider implementation.
 */
@Contract
public interface PicoServicesConfig {

    /**
     * The short name for pico.
     */
    String NAME = "pico";

    /**
     * The fully qualified name for pico (used for system properties, etc).
     */
    String FQN = "io.helidon." + NAME;

    /**
     * The key association with the name of the provider implementation.
     */
    String KEY_PROVIDER = FQN + ".provider";
    /**
     * The key association with the version of the provider implementation.
     */
    String KEY_VERSION = FQN + ".version";

    /**
     * Applicable during activation, this is the key that controls the timeout before deadlock detection errors being thrown.
     */
    String KEY_DEADLOCK_TIMEOUT_IN_MILLIS = FQN + ".deadlock.timeout.millis";
    /**
     * The default deadlock detection timeout in millis.
     */
    Long DEFAULT_DEADLOCK_TIMEOUT_IN_MILLIS = 10000L;

    /**
     * Applicable for capturing activation logs.
     */
    String KEY_ACTIVATION_LOGS_ENABLED = FQN + ".activation.logs.enabled";
    /**
     * The default value for this is false, meaning that the activation logs will not be recorded or logged.
     */
    Boolean DEFAULT_ACTIVATION_LOGS_ENABLED = false;

    /**
     * The key that models the services registry, and whether the registry can expand dynamically after program startup.
     */
    String KEY_SUPPORTS_DYNAMIC = FQN + ".supports.dynamic";
    /**
     * The default value for this is false, meaning that the services registry cannot be changed during runtime.
     */
    Boolean DEFAULT_SUPPORTS_DYNAMIC = false;

    /**
     * The key that represents whether the provider support reflection, and reflection based activation/injection.
     */
    String KEY_SUPPORTS_REFLECTION = FQN + ".supports.reflection";
    /**
     * The default value for this is false, meaning no reflection is available or provided in the implementation.
     */
    Boolean DEFAULT_SUPPORTS_REFLECTION = false;

    /**
     * Can the provider support compile-time activation/injection (i.e., {@link Activator}'s)?
     */
    String KEY_SUPPORTS_COMPILE_TIME = FQN + ".supports.compiletime";
    /**
     * The default value is true, meaning injection points are evaluated at compile-time.
     */
    Boolean DEFAULT_SUPPORTS_COMPILE_TIME = true;

    /**
     * Can the services registry activate services in a thread-safe manner?
     */
    String KEY_SUPPORTS_THREAD_SAFE_ACTIVATION = FQN + ".supports.threadsafe.activation";
    /**
     * The default is true, meaning the implementation is (or should be) thread safe.
     */
    Boolean DEFAULT_SUPPORTS_THREAD_SAFE_ACTIVATION = true;

    /**
     * The key to represent whether the provider support and is compliant w/ Jsr-330.
     */
    String KEY_SUPPORTS_JSR330 = FQN + ".supports.jsr330";
    /**
     * The default value is true.
     */
    Boolean DEFAULT_SUPPORTS_JSR330 = true;

    /**
     * Can the injector / activator support static injection?  Note: this is optional in Jsr-330
     */
    String KEY_SUPPORTS_JSR330_STATIC = FQN + ".supports.jsr330.static";
    /**
     * The default value is false.
     */
    Boolean DEFAULT_SUPPORTS_STATIC = false;
    /**
     * Can the injector / activator support private injection?  Note: this is optional in Jsr-330
     */
    String KEY_SUPPORTS_JSR330_PRIVATE = FQN + ".supports.jsr330.private";
    /**
     * The default value is false.
     */
    Boolean DEFAULT_SUPPORTS_PRIVATE = false;

    /**
     * Indicates whether the {@link Module}(s) should be read at startup. The default value is true.
     */
    String KEY_BIND_MODULES = FQN + ".bind.modules";
    /**
     * The default value is true.
     */
    Boolean DEFAULT_BIND_MODULES = true;

    /**
     * Indicates whether the {@link Application}(s) should be used as an optimization at startup to
     * avoid lookups. The default value is true.
     */
    String KEY_BIND_APPLICATION = FQN + ".bind.application";
    /**
     * The default value is true.
     */
    Boolean DEFAULT_BIND_APPLICATION = true;

    /**
     * Supports a query mechanism to determine if properties are available/set.
     *
     * @param <T> the type for the config value
     * @param key the key to query for config
     * @param defaultValueSupplier the default value
     * @return the configuration value for the associated key, or else the default value supplied
     */
    <T> T value(String key, Supplier<T> defaultValueSupplier);

    /**
     * Return a string-type key value.
     *
     * @param key the key
     * @return the value, or null if no key is found
     */
    default String stringValue(String key) {
        Object val = value(key, null);
        return Objects.nonNull(val) ? val.toString() : null;
    }

    /**
     * Supports a properties-based mechanism to determine configuration.
     *
     * @return all properties in this configuration
     */
    default Optional<Map<String, Object>> properties() {
        return Optional.empty();
    }

    /**
     * Return a boolean-type key value.
     *
     * @param val the default value
     * @return the value supplier
     */
    static Supplier<Boolean> defaultValue(Boolean val) {
        return () -> val;
    }

    /**
     * Return a string-type key value.
     *
     * @param val the default value
     * @return the value supplier
     */
    static Supplier<String> defaultValue(String val) {
        return () -> val;
    }

    /**
     * Return a long-type key value.
     *
     * @param val the default value
     * @return the value supplier
     */
    static Supplier<Long> defaultValue(Long val) {
        return () -> val;
    }

}
