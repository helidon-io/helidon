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

package io.helidon.nima.observe.spi;

import io.helidon.config.Config;
import io.helidon.nima.webserver.http.HttpRouting;

/**
 * {@link java.util.ServiceLoader} provider interface for observability services.
 */
public interface ObserveProvider {
    /**
     * Configuration key of this provider.
     * The following keys are reserved by Observe support:
     * <ul>
     *     <li>{@code enabled} - enable/disable the service</li>
     *     <li>{@code endpoint} - endpoint, if starts with {@code /} then absolute, otherwise relative to observe endpoint</li>
     * </ul>
     *
     * @return configuration key of this provider (such as {@code health})
     */
    String configKey();

    /**
     * Default endpoint of this provider. To define a relative path, do not include forward slash (such as {@code health}
     * would resolve into {@code /observe/health}).
     *
     * @return default endpoint under {@code /observe}
     */
    String defaultEndpoint();

    /**
     * Register the provider's services and handlers to the routing builder.
     * The component MUST honor the provided component path.
     *
     * @param config        configuration of this provider
     * @param componentPath component path to register under (such as {@code /observe/health}, based on configured
     *                      endpoint and {@link #defaultEndpoint()})
     * @param routing       routing builder
     */
    void register(Config config, String componentPath, HttpRouting.Builder routing);
}
