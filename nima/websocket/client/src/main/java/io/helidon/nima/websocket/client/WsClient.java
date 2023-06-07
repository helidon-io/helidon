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

package io.helidon.nima.websocket.client;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import io.helidon.common.http.Http;
import io.helidon.nima.webclient.DefaultDnsResolverProvider;
import io.helidon.nima.webclient.DnsAddressLookup;
import io.helidon.nima.webclient.WebClient;
import io.helidon.nima.websocket.WsListener;

/**
 * WebSocket client.
 */
public interface WsClient extends WebClient {
    /**
     * A new fluent API builder to create new instances of client.
     *
     * @return a new builder
     */
    static Builder builder() {
        return new Builder();
    }

    /**
     * Starts a new WebSocket connection and runs it in a new virtual thread.
     * This method returns when the connection is established and a new {@link io.helidon.nima.websocket.WsSession} is
     * started.
     *
     * @param uri URI to connect to
     * @param listener listener to handle WebSocket
     */
    void connect(URI uri, WsListener listener);

    /**
     * Starts a new WebSocket connection and runs it in a new virtual thread.
     * This method returns when the connection is established and a new {@link io.helidon.nima.websocket.WsSession} is
     * started.
     *
     * @param path path to connect to, if client uses a base URI, this is resolved against the base URI
     * @param listener listener to handle WebSocket
     */
    void connect(String path, WsListener listener);

    /**
     * Fluent API builder for {@link io.helidon.nima.websocket.client.WsClient}.
     */
    class Builder extends WebClient.Builder<Builder, WsClient> {
        /**
         * Supported WebSocket version.
         */
        static final String SUPPORTED_VERSION = "13";
        static final Http.HeaderValue HEADER_UPGRADE_WS = Http.Header.createCached(Http.Header.UPGRADE, "websocket");
        static final Http.HeaderName HEADER_WS_PROTOCOL = Http.Header.create("Sec-WebSocket-Protocol");
        private static final Http.HeaderValue HEADER_WS_VERSION = Http.Header.createCached(Http.Header.create(
                "Sec-WebSocket-Version"), SUPPORTED_VERSION);
        private final List<String> subprotocols = new ArrayList<>();

        private Builder() {
            // until we use the same parent for HTTP/1 and websocket, we need to have these defined as defaults
            super.dnsResolver(new DefaultDnsResolverProvider().createDnsResolver());
            super.dnsAddressLookup(DnsAddressLookup.defaultLookup());
        }

        @Override
        public WsClient build() {
            // these headers cannot be modified by user
            header(HEADER_UPGRADE_WS);
            header(HEADER_WS_VERSION);
            header(Http.HeaderValues.CONTENT_LENGTH_ZERO);
            if (subprotocols.isEmpty()) {
                removeHeader(HEADER_WS_PROTOCOL);
            } else {
                header(HEADER_WS_PROTOCOL, subprotocols);
            }

            return new WsClientImpl(this);
        }

        /**
         * Add sub-protocol. A list of preferred sub-protocols is sent to server, and it chooses zero or one of them.
         *
         * @param preferred preferred sub-protocol to use, first one added is considered to be the most desired one
         * @return updated builder instance
         */
        public Builder addSubProtocol(String preferred) {
            Objects.requireNonNull(preferred);
            this.subprotocols.add(preferred);
            return this;
        }

        /**
         * Configure sub-protocols.
         * A list of preferred sub-protocols is sent to server, and it chooses zero or one of them.
         *
         * @param preferred preferred sub-protocols to use
         * @return updated builder instance
         */
        public Builder subProtocols(String... preferred) {
            Objects.requireNonNull(preferred);
            subprotocols.clear();
            Collections.addAll(subprotocols, preferred);
            return this;
        }
    }
}
