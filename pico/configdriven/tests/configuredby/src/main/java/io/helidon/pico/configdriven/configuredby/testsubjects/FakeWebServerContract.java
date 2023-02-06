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

package io.helidon.pico.configdriven.configuredby.testsubjects;

import java.util.Objects;

import io.helidon.builder.config.testsubjects.fakes.FakeServerConfig;
import io.helidon.builder.config.testsubjects.fakes.FakeSocketConfig;
import io.helidon.pico.Contract;

/**
 * For Testing.
 */
@Contract
public interface FakeWebServerContract {

    /**
     * Gets effective server configuration.
     *
     * @return Server configuration
     */
    FakeServerConfig configuration();

    /**
     * Returns {@code true} if the server is currently running. Running server in stopping phase returns {@code true} until it
     * is not fully stopped.
     *
     * @return {@code true} if server is running
     */
    boolean isRunning();

    /**
     * Returns {@code true} if TLS is configured for the named socket.
     *
     * @param socketName the name of a socket
     * @return whether TLS is enabled for the socket, returns {@code false} if the socket does not exists
     */
    default boolean hasTls(String socketName) {
        FakeSocketConfig cfg = configuration().sockets().get(socketName);
        return !Objects.isNull(cfg) && cfg.tls().isPresent();
    }

}
