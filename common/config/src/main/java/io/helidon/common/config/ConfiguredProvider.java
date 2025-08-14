/*
 * Copyright (c) 2023, 2025 Oracle and/or its affiliates.
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

package io.helidon.common.config;

/**
 * Providers that can be loaded from configuration should implement this interface.
 * The configuration is expected to be similar to this (using YAML as an example):
 * <pre>
 *   configured-option:
 *     discover-services: true # this is the default
 *     providers:
 *       provider1-config-key:
 *         provider1-config: value
 *       provider2-config-key:
 *         provider2-config: value
 * </pre>
 *
 * @param <T> type of the service this provider provides
 *
 * @deprecated this class will be moved to {@code helidon-config} module in Helidon 5
 */
@Deprecated(since = "4.3.0")
public interface ConfiguredProvider<T extends NamedService> {
    /**
     * Key this service implementation is stored under. This is also considered the service "type" when used
     * in a list in configuration, to allow the same service defined more than once.
     *
     * @return key of this implementation
     */
    String configKey();

    /**
     * Create a new instance from the configuration located
     * on the provided node.
     *
     * @param config located at {@link #configKey()} node
     * @param name name of the configured implementation
     *
     * @return a new instance created from this config node
     */
    T create(Config config, String name);
}
