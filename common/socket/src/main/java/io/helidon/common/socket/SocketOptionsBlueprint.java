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

package io.helidon.common.socket;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.Socket;
import java.net.SocketOption;
import java.net.StandardSocketOptions;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.config.metadata.Configured;
import io.helidon.config.metadata.ConfiguredOption;

/**
 * Socket options.
 */
@Configured
@Prototype.Blueprint(decorator = SocketOptionsBlueprint.BuilderDecorator.class)
interface SocketOptionsBlueprint {
    /**
     * Arbitrary socket options. Socket options that have dedicated methods
     * in this type will be ignored if configured within the map.
     *
     * @return custom socket options
     */
    @Option.Singular
    @Option.SameGeneric
    Map<SocketOption<?>, Object> socketOptions();

    /**
     * Socket connect timeout. Default is 10 seconds.
     *
     * @return connect timeout duration
     */
    @ConfiguredOption("PT10S")
    Duration connectTimeout();

    /**
     * Socket read timeout. Default is 30 seconds.
     *
     * @return read timeout duration
     */
    @ConfiguredOption("PT30S")
    Duration readTimeout();

    /**
     * Socket receive buffer size.
     *
     * @return buffer size, in bytes
     * @see java.net.StandardSocketOptions#SO_RCVBUF
     */
    @ConfiguredOption
    Optional<Integer> socketReceiveBufferSize();

    /**
     * Socket send buffer size.
     *
     * @return buffer size, in bytes
     * @see java.net.StandardSocketOptions#SO_SNDBUF
     */
    @ConfiguredOption
    Optional<Integer> socketSendBufferSize();

    /**
     * Socket reuse address.
     * Default is {@code true}.
     *
     * @return whether to reuse address
     * @see java.net.StandardSocketOptions#SO_REUSEADDR
     */
    @ConfiguredOption("true")
    boolean socketReuseAddress();

    /**
     * Configure socket keep alive.
     * Default is {@code true}.
     *
     * @return keep alive
     * @see java.net.StandardSocketOptions#SO_KEEPALIVE
     */
    @ConfiguredOption("true")
    boolean socketKeepAlive();

    /**
     * This option may improve performance on some systems.
     * Default is {@code false}.
     *
     * @return whether to use TCP_NODELAY, defaults to {@code false}
     * @see java.net.StandardSocketOptions#TCP_NODELAY
     */
    @ConfiguredOption("false")
    boolean tcpNoDelay();

    /**
     * Configure socket with defined socket options.
     *
     * @param socket socket to update
     */
    @SuppressWarnings("unchecked")
    default void configureSocket(Socket socket) {
        for (Map.Entry<SocketOption<?>, Object> entry : socketOptions().entrySet()) {
            try {
                SocketOption<Object> opt = (SocketOption<Object>) entry.getKey();
                socket.setOption(opt, entry.getValue());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    class BuilderDecorator implements Prototype.BuilderDecorator<SocketOptions.BuilderBase<?, ?>> {
        @Override
        public void decorate(SocketOptions.BuilderBase<?, ?> target) {
            target.socketReceiveBufferSize().ifPresent(i -> target.putSocketOption(StandardSocketOptions.SO_RCVBUF, i));
            target.socketSendBufferSize().ifPresent(i -> target.putSocketOption(StandardSocketOptions.SO_SNDBUF, i));
            target.putSocketOption(StandardSocketOptions.SO_REUSEADDR, target.socketReuseAddress());
            target.putSocketOption(StandardSocketOptions.SO_KEEPALIVE, target.socketKeepAlive());
            target.putSocketOption(StandardSocketOptions.TCP_NODELAY, target.tcpNoDelay());
        }
    }
}
