/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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

package io.helidon.webserver.spi;

import java.util.Optional;

import io.helidon.config.Config;
import io.helidon.webserver.Service;

/**
 * Java service loader interface for server services.
 * <p>
 * Services provided by this interface will be registered with
 * the web server using {@link io.helidon.webserver.Routing.Builder#register(io.helidon.webserver.Service...)}
 * or {@link io.helidon.webserver.Routing.Builder#register(String, io.helidon.webserver.Service...)} depending
 * on the presence of {@code path-pattern} configuration option.
 * <p>
 * Services are configured in order defined by priority (lowest priority is registered first),
 *  {@code priority} key can be used to re-define service priority in
 *  configuration.
 * Default priority is defined through {@code @Priority} annotation on an
 * implementation of this service, or
 * by implementing the {@link io.helidon.common.Prioritized} interface.
 * <p>
 * You can control what configuration is given to this service by
 * configuring either property {@code config-key} - a reference to another
 * configuration key in the configuration root, or by configuring
 * {@code config} node with values instead.
 * If neither is present, empty configuration is provided to service.
 */
public interface WebServerServiceProvider {
    /**
     * Config key expected under {@code server.services}.
     *
     * @return name of the configuration node of this service
     */
    String configKey();

    /**
     * Path pattern to use if none is configured in config.
     *
     * @return default path pattern, or empty if none should be used
     */
    default Optional<String> defaultPathPattern() {
        return Optional.empty();
    }

    /**
     * Create new instance(s) based on configuration.
     *
     * @param config configuration of this service
     * @return a new server service instance
     */
    Iterable<Service> create(Config config);
}
