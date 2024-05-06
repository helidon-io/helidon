/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

package io.helidon.webclient.grpc;

import java.time.Duration;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.webclient.spi.ProtocolConfig;

/**
 * Configuration of a gRPC client.
 */
@Prototype.Blueprint
@Prototype.Configured
interface GrpcClientProtocolConfigBlueprint extends ProtocolConfig {

    /**
     * Type identifying this protocol.
     *
     * @return protocol type
     */
    @Override
    default String type() {
        return GrpcProtocolProvider.CONFIG_KEY;
    }

    /**
     * Name identifying this client protocol. Defaults to type.
     *
     * @return name of client protocol
     */
    @Option.Configured
    @Option.Default(GrpcProtocolProvider.CONFIG_KEY)
    @Override
    String name();

    /**
     * How long to wait for the next HTTP/2 data frame to arrive in underlying stream.
     * Whether this is a fatal error or not is controlled by {@link #abortPollTimeExpired()}.
     *
     * @return poll time as a duration
     * @see io.helidon.common.socket.SocketOptions#readTimeout()
     */
    @Option.Configured
    @Option.Default("PT10S")
    Duration pollWaitTime();

    /**
     * Whether to continue retrying after a poll wait timeout expired or not. If a read
     * operation timeouts out and this flag is set to {@code false}, the event is logged
     * and the client will retry. Otherwise, an exception is thrown.
     *
     * @return abort timeout flag
     */
    @Option.Configured
    @Option.Default("false")
    boolean abortPollTimeExpired();

    /**
     * Initial buffer size used to serialize gRPC request payloads. Buffers shall grow
     * according to the payload size, but setting this initial buffer size to a larger value
     * may improve performance for certain applications.
     *
     * @return initial buffer size
     */
    @Option.Configured
    @Option.Default("2048")
    int initBufferSize();
}
