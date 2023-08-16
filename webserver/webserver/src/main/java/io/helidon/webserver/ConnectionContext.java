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

package io.helidon.webserver;

import java.util.concurrent.ExecutorService;

import io.helidon.common.buffers.DataReader;
import io.helidon.common.buffers.DataWriter;
import io.helidon.common.socket.SocketContext;

/**
 * Server connection context.
 */
public interface ConnectionContext extends SocketContext {
    /**
     * Context of the listener. Configuration specific to a single listener.
     *
     * @return listener specific context
     */
    ListenerContext listenerContext();

    /**
     * Executor service to submit asynchronous tasks.
     *
     * @return executor service
     */
    ExecutorService executor();

    /**
     * Data writer to write response bytes.
     *
     * @return data writer
     */
    DataWriter dataWriter();

    /**
     * Data reader to read request bytes.
     *
     * @return data reader
     */
    DataReader dataReader();

    /**
     * Router that may contain routings of different types (HTTP, WebSocket, grpc).
     *
     * @return rouer
     */
    Router router();
}
