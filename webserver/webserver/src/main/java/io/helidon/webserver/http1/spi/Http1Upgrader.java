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

package io.helidon.webserver.http1.spi;

import io.helidon.http.HttpPrologue;
import io.helidon.http.WritableHeaders;
import io.helidon.webserver.ConnectionContext;
import io.helidon.webserver.spi.ServerConnection;

/**
 * HTTP/1.1 connection upgrader.
 */
public interface Http1Upgrader {
    /**
     * Expected value of the protocol upgrade, such as {@code h2c}, or {@code websocket}.
     * If an implementation supports multiple protocols, please implement this selector for each protocol
     *
     * @return supported protocol
     */
    String supportedProtocol();

    /**
     * Upgrade connection.
     *
     * @param ctx      connection context
     * @param prologue http prologue of the upgrade request
     * @param headers  http headers of the upgrade request
     * @return a new connection to use instead of the original {@link io.helidon.webserver.http1.Http1Connection},
     *           or {@code null} if the connection cannot be upgraded
     */
    ServerConnection upgrade(ConnectionContext ctx, HttpPrologue prologue, WritableHeaders<?> headers);

}
