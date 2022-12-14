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

package io.helidon.nima.webserver.spi;

/**
 * Server connection abstraction, used by any provider to handle a socket connection.
 */
public interface ServerConnection {
    /**
     * Start handling the connection. Data is provided through
     * {@link io.helidon.nima.webserver.ServerConnectionSelector#connection(io.helidon.nima.webserver.ConnectionContext)}.
     *
     * @throws InterruptedException to interrupt any waiting state and terminate this connection
     */
    void handle() throws InterruptedException;
}
