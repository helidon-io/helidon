/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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

@Prototype.Blueprint
@Prototype.Configured(root = false, value = "config")
@Prototype.Provides(ObserveProvider.class)
interface ConfigObserverConfigBlueprint extends ObserverConfigBase, Prototype.Factory<ConfigObserver> {
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
     * <p>
     * Patterns always added:
     * <ul>
     *     <li>{@code .*password}</li>
     *     <li>{@code .*passphrase}</li>
     *     <li>{@code .*secret}</li>
     * </ul>
     *
     * @return set of regular expression patterns for keys, where values should be excluded from output
     */
    @Option.Configured
    @Option.Singular
    @Option.Default({".*password", ".*passphrase", ".*secret"})
    Set<String> secrets();
}
