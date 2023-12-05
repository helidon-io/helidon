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

package io.helidon.webclient.spi;

import io.helidon.webclient.api.ClientRequest;
import io.helidon.webclient.api.ClientUri;
import io.helidon.webclient.api.FullClientRequest;
import io.helidon.webclient.api.ReleasableResource;

/**
 * Integration for HTTP versions to provide a single API.
 */
public interface HttpClientSpi extends ReleasableResource {
    /**
     * Return whether this HTTP version can handle the provided request.
     * <p>
     * Examples:
     * <ul>
     *     <li>HTTP/1.1 always returns true, as it is the fallback protocol of all other protocols</li>
     *     <li>HTTP/2 returns true when upgrade is enabled, false when prior-knowledge is specified (as we can always
     *     attempt to upgrade and fallback to 1.1 in the first case, but we would fail with prior-knowledge, unless the endpoint
     *     is known to be HTTP/2</li>
     *     <li>HTTP/3 would always return true, as it cannot fallback once a connection is attempted</li>
     * </ul>
     *
     * So how can we get to HTTP/2 with prior knowledge, or HTTP/3? There are the following options:
     * <ul>
     *     <li>An explicit protocol id is configured for the request -
     *     {@link io.helidon.webclient.api.HttpClientRequest#protocolId(String)}</li>
     *     <li>There is no fallback protocol enabled, in such a case, we use the one with highest priority - see
     *     {@link io.helidon.webclient.api.WebClientConfig.Builder#addProtocolPreference(String)}</li>
     *     <li>We get an {@code Alt-Svc} header from a response that points us to a protocol version (we only support permanent
     *     protocol changes, {@code Alt-Svc} with timeout will be ignored</li>
     *     <li>This protocol version already handled a request to such an endpoint and has the connection available</li>
     * </ul>
     *
     * Note that this response is cached.
     *
     * @param clientRequest HTTP request
     * @param clientUri     URI to invoke
     * @return {@code true} if we are sure we can handle this request with this protocol version
     */
    SupportLevel supports(FullClientRequest<?> clientRequest, ClientUri clientUri);

    /**
     * Create a client request based on the provided HTTP request that is for the version of this client.
     *
     * @param clientRequest request configuration
     * @param clientUri     URI to invoke (resolved)
     * @return a new request
     */
    ClientRequest<?> clientRequest(FullClientRequest<?> clientRequest,
                                   ClientUri clientUri);

    /**
     * For TCP based protocols, we can do ALPN negotiation, obtain a connection, and then let the client handle the protocol.
     * Similar for proxies - we can establish a proxy connection, and then let the client handle the protocol.
     * For UDP based protocols, we need to wait for {@code Alt-Svc} headers before attempting anything, or have an explicit
     * version configured.
     *
     * @return whether this is a TCP based HTTP protocol, defaults to {@code true}
     */
    default boolean isTcp() {
        return true;
    }

    /**
     * How does the provider support the request.
     */
    enum SupportLevel {
        /**
         * This request can never be supported by this client.
         */
        NOT_SUPPORTED,
        /**
         * We may support this, but not sure until we try.
         */
        UNKNOWN,
        /**
         * This request is compatible by this client, but we have not yet done it (for example always returned by HTTP/1.1).
         */
        COMPATIBLE,
        /**
         * This request is supported by this client, as we have already tried it (for example we have a cached connection).
         */
        SUPPORTED
    }
}
