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

package io.helidon.nima.webclient.api;

import java.time.Duration;

import io.helidon.common.buffers.DataReader;
import io.helidon.common.buffers.DataWriter;
import io.helidon.common.socket.HelidonSocket;

/**
 * Client connection.
 * This allows usage of a custom connection for testing - see {@code DirectClient} class.
 */
public interface ClientConnection extends ReleasableResource {
    /**
     * Data reader providing response bytes.
     *
     * @return reader to read from this connection
     */
    DataReader reader();

    /**
     * Data writer the client request writes to.
     *
     * @return writer to write to this connection
     */
    DataWriter writer();

    /**
     * Channel id, mostly used in logs.
     *
     * @return id of this channel (connection)
     */
    String channelId();

    /**
     * Associated {@link io.helidon.common.socket.HelidonSocket}.
     *
     * @return socket of this connection
     */
    HelidonSocket helidonSocket();

    /**
     * Read timeout for this connection.
     *
     * @param readTimeout connection read timeout
     */
    void readTimeout(Duration readTimeout);
}
