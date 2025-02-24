/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

package io.helidon.webclient.api;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

import io.helidon.common.tls.Tls;
import io.helidon.http.Method;

/**
 * Client request with getters for all configurable options, used for integration with HTTP version implementations.
 *
 * @param <T> type of the implementation, to support fluent API
 */
public interface FullClientRequest<T extends ClientRequest<T>> extends ClientRequest<T> {
    /**
     * Replace a placeholder in URI with an actual value.
     *
     * @return a map of path parameters
     */
    Map<String, String> pathParams();

    /**
     * HTTP method of this request.
     *
     * @return method
     */
    Method method();

    /**
     * URI of this request.
     *
     * @return client URI
     */
    ClientUri uri();

    /**
     * Configured properties.
     *
     * @return properties
     */
    Map<String, String> properties();

    /**
     * Request ID.
     *
     * @return id of this request
     */
    String requestId();

    /**
     * Possible explicit connection to use (such as when using a proxy).
     *
     * @return client connection if explicitly defined
     */
    Optional<ClientConnection> connection();

    /**
     * Read timeout.
     *
     * @return read timeout of this request
     */
    Duration readTimeout();

    /**
     * Read 100-Continue timeout.
     *
     * @return read 100-Continue timeout of this request
     */
    Duration readContinueTimeout();

    /**
     * TLS configuration (may be disabled - e.g. use plaintext).
     *
     * @return TLS configuration
     */
    Tls tls();

    /**
     * Proxy configuration (may be no-proxy).
     *
     * @return proxy
     */
    Proxy proxy();

    /**
     * Whether to use keep-alive connection (if relevant for the used HTTP version).
     *
     * @return whether to use keep alive
     */
    boolean keepAlive();

    /**
     * Whether to skip URI encoding.
     *
     * @return whether to skip encoding
     */
    boolean skipUriEncoding();

    /**
     * Whether Expect 100-Continue header is sent to verify server availability before sending
     * an entity. Overrides the setting from {@link HttpClientConfig#sendExpectContinue()}.
     *
     * @return Expect 100-Continue value if set
     */
    Optional<Boolean> sendExpectContinue();
}
