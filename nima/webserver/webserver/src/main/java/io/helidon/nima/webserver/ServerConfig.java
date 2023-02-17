/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.nima.webserver;

import io.helidon.builder.config.ConfigBean;
import io.helidon.config.metadata.ConfiguredOption;

/**
 * Server configuration bean.
 * There is a generated {@link io.helidon.nima.webserver.DefaultServerConfig} implementing this type, that can be used
 * to create an instance manually through a builder, or using configuration.
 */
@ConfigBean("server")
public interface ServerConfig {
    /**
     * Host of the default socket. Defaults to all host addresses ({@code 0.0.0.0}).
     *
     * @return host address to listen on (for the default socket)
     */
    @ConfiguredOption("0.0.0.0")
    String host();

    /**
     * Port of the default socket.
     * If configured to {@code 0} (the default), server starts on a random port.
     *
     * @return port to listen on (for the default socket)
     */
    @ConfiguredOption("0")
    int port();
}
