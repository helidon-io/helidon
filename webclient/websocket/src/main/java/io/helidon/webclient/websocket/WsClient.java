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

package io.helidon.webclient.websocket;

import java.net.URI;
import java.util.function.Consumer;

import io.helidon.builder.api.RuntimeType;
import io.helidon.webclient.api.WebClient;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.spi.Protocol;
import io.helidon.websocket.WsListener;

/**
 * WebSocket client.
 */
@RuntimeType.PrototypedBy(WsClientConfig.class)
public interface WsClient extends RuntimeType.Api<WsClientConfig> {
    /**
     * Protocol to use to obtain an instance of WebSocket specific client from
     * {@link io.helidon.webclient.api.WebClient#client(io.helidon.webclient.spi.Protocol)}.
     */
    Protocol<WsClient, WsClientProtocolConfig> PROTOCOL = WsProtocolProvider::new;

    /**
     * A new fluent API builder to create new instances of client.
     *
     * @return a new builder
     */
    static WsClientConfig.Builder builder() {
        return WsClientConfig.builder();
    }

    /**
     * Create a new WebSocket client with custom configuration.
     *
     * @param clientConfig websocket client configuration
     * @return a new WebSocket client
     */
    static WsClient create(WsClientConfig clientConfig) {
        WebClient webClient = WebClient.create(it -> it.from(clientConfig));
        return new WsClientImpl(webClient,
                                webClient.client(Http1Client.PROTOCOL),
                                clientConfig);
    }

    /**
     * Create a new WebSocket client customizing its configuration.
     *
     * @param consumer websocket client configuration consumer
     * @return a new WebSocket client
     */
    static WsClient create(Consumer<WsClientConfig.Builder> consumer) {
        return WsClientConfig.builder()
                .update(consumer)
                .build();
    }

    /**
     * Starts a new WebSocket connection and runs it in a new virtual thread.
     * This method returns when the connection is established and a new {@link io.helidon.websocket.WsSession} is
     * started.
     *
     * @param uri      URI to connect to
     * @param listener listener to handle WebSocket
     */
    void connect(URI uri, WsListener listener);

    /**
     * Starts a new WebSocket connection and runs it in a new virtual thread.
     * This method returns when the connection is established and a new {@link io.helidon.websocket.WsSession} is
     * started.
     *
     * @param path     path to connect to, if client uses a base URI, this is resolved against the base URI
     * @param listener listener to handle WebSocket
     */
    void connect(String path, WsListener listener);
}
