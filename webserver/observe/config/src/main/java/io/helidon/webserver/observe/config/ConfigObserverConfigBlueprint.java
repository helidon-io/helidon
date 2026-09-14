/*
 * Copyright (c) 2023, 2026 Oracle and/or its affiliates.
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

package io.helidon.webserver.observe.config;

import java.util.Set;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.webserver.observe.ObserverConfigBase;
import io.helidon.webserver.observe.spi.ObserveProvider;

/**
 * Configuration of Config Observer.
 */
@Prototype.Blueprint
@Prototype.Configured(root = false, value = "config")
@Prototype.Provides(ObserveProvider.class)
@Prototype.IncludeDefaultMethods
interface ConfigObserverConfigBlueprint extends ObserverConfigBase, Prototype.Factory<ConfigObserver> {
    /**
     * Endpoint this observer is available on.
     *
     * @return the observer endpoint
     */
    @Option.Configured
    @Option.Default("config")
    String endpoint();

    @Override
    @Option.Default("config")
    String name();

    /**
     * Permit all access, even when not authorized.
     *
     * @return whether to permit access for anybody
     */
    @Option.Configured
    boolean permitAll();

    /**
     * Secret patterns (regular expressions) to exclude from output.
     * Any pattern that matches a key will cause the output to be obfuscated and not contain the value.
     * Patterns are matched case-insensitively.
     * <p>
     * Built-in default patterns always added:
     * <ul>
     *     <li>{@code .*password.*}</li>
     *     <li>{@code .*passphrase.*}</li>
     *     <li>{@code .*pwd.*}</li>
     *     <li>{@code .*secret.*}</li>
     *     <li>{@code .*credential.*}</li>
     *     <li>{@code .*token.*}</li>
     *     <li>{@code .*api[-_.]?key.*}</li>
     *     <li>{@code .*access[-_.]?key.*}</li>
     *     <li>{@code .*private[-_.]?key.*}</li>
     *     <li>{@code .*connection[-_.]?url.*}</li>
     * </ul>
     *
     * @return set of regular expression patterns for keys, where values should be excluded from output
     */
    @Option.Configured
    @Option.Singular
    @Option.Default({
            ConfigObserverConfigDefaults.SECRET_PASSWORD,
            ConfigObserverConfigDefaults.SECRET_PASSPHRASE,
            ConfigObserverConfigDefaults.SECRET_PWD,
            ConfigObserverConfigDefaults.SECRET_SECRET,
            ConfigObserverConfigDefaults.SECRET_CREDENTIAL,
            ConfigObserverConfigDefaults.SECRET_TOKEN,
            ConfigObserverConfigDefaults.SECRET_API_KEY,
            ConfigObserverConfigDefaults.SECRET_ACCESS_KEY,
            ConfigObserverConfigDefaults.SECRET_PRIVATE_KEY,
            ConfigObserverConfigDefaults.SECRET_CONNECTION_URL
    })
    Set<String> secrets();

    /**
     * Safe key patterns (regular expressions) to include in output.
     * Any pattern that matches a key will cause the output to contain the value, unless it also matches a
     * {@link #secrets()} pattern.
     * Patterns are matched case-insensitively.
     *
     * @return set of regular expression patterns for keys, where values can be included in output
     */
    @Option.Configured
    @Option.Singular
    @Option.Default({
            ConfigObserverConfigDefaults.SAFE_KEY_SERVER_HOST,
            ConfigObserverConfigDefaults.SAFE_KEY_SERVER_PORT,
            ConfigObserverConfigDefaults.SAFE_KEY_SERVER_SOCKET_HOST,
            ConfigObserverConfigDefaults.SAFE_KEY_SERVER_SOCKET_PORT,
            ConfigObserverConfigDefaults.SAFE_KEY_OBSERVE_ENABLED,
            ConfigObserverConfigDefaults.SAFE_KEY_OBSERVE_ENDPOINT,
            ConfigObserverConfigDefaults.SAFE_KEY_OBSERVE_SOCKETS,
            ConfigObserverConfigDefaults.SAFE_KEY_OBSERVE_WEIGHT,
            ConfigObserverConfigDefaults.SAFE_KEY_OBSERVER_ENABLED,
            ConfigObserverConfigDefaults.SAFE_KEY_OBSERVER_ENDPOINT,
            ConfigObserverConfigDefaults.SAFE_KEY_OBSERVER_NAME
    })
    default Set<String> safeKeys() {
        return ConfigObserverConfigDefaults.SAFE_KEYS;
    }

    /**
     * Whether to include values that do not match configured {@code safe-keys} patterns; values whose keys match
     * configured {@code secrets} patterns are still obfuscated.
     * If enabled, the observer uses the previous behavior and only obfuscates values whose keys match
     * {@link #secrets()}.
     *
     * @return whether to include unsafe values in output
     */
    @Option.Configured
    @Option.DefaultBoolean(false)
    default boolean unsafeValues() {
        return false;
    }
}
