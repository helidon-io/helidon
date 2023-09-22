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

package io.helidon.webserver.observe.spi;

import io.helidon.common.config.Config;
import io.helidon.common.config.ConfiguredProvider;

/**
 * {@link java.util.ServiceLoader} provider interface for observability services.
 */
public interface ObserveProvider extends ConfiguredProvider<Observer> {
    /**
     * Configuration key of this provider.
     * The following keys must be honored by Observe support:
     * <ul>
     *     <li>{@code enabled} - enable/disable the service</li>
     *     <li>{@code endpoint} - endpoint, if starts with {@code /} then absolute, otherwise relative to observe endpoint</li>
     *     <li>{@code cors} - CORS setup for this endpoint</li>
     * </ul>
     *
     * @return configuration key of this provider (such as {@code health})
     */
    @Override
    String configKey();

    /**
     * Type of this observe provider, to map to {@link io.helidon.webserver.observe.spi.Observer} when explicitly configured
     * by user (so we do not duplicate observers).
     *
     * @return type of this observer, defaults to {@link #configKey()}
     */
    default String type() {
        return configKey();
    }

    /**
     * Create a new observer from the provided configuration.
     *
     * @param config configuration of this provider
     * @param name name of the instance
     * @return a new observer to be registered with routing
     */
    @Override
    Observer create(Config config, String name);
}
