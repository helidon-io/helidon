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

import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

import io.helidon.builder.api.RuntimeType;
import io.helidon.common.http.Headers;
import io.helidon.nima.webclient.spi.Protocol;
import io.helidon.nima.webclient.spi.ProtocolConfig;

/**
 * HTTP client.
 */
@RuntimeType.PrototypedBy(WebClientConfig.class)
public interface WebClient extends RuntimeType.Api<WebClientConfig>, HttpClient<HttpClientRequest> {

    /**
     * Create a new builder of the HTTP protocol, that can be used with any supported version.
     * Which versions are supported is determined by the classpath, HTTP/1.1 is always supported.
     * To support a specific HTTP version only,
     *  you can use {@link WebClientConfig.Builder#addProtocolPreference(java.util.List)}.
     *
     * @return new HTTP client builder
     */
    static WebClientConfig.Builder builder() {
        return WebClientConfig.builder();
    }

    static WebClient create() {
        return create(WebClientConfig.create());
    }

    static WebClient create(WebClientConfig config) {
        return new LoomClient(config);
    }

    static WebClient create(Consumer<WebClientConfig.Builder> consumer) {
        return builder()
                .update(consumer)
                .build();
    }

    /**
     * To switch to a non-HTTP protocol client (or a client of a specific HTTP version).
     * The resulting protocol will use this client as a base, so it will share all configuration that is
     * relevant for the protocol.
     *
     * @param protocol protocol instance, usually defined as a constant on the protocol interface
     * @param protocolConfig configuration of the protocol to be used (if customization is required)
     * @return a new protocol client instance
     * @param <T> type of the protocol client
     * @param <C> type of the protocol config
     */
    <T, C extends ProtocolConfig> T client(Protocol<T, C> protocol, C protocolConfig);

    /**
     * To switch to a non-HTTP protocol client (or a client of a specific HTTP version) using its config configured
     * when creating the client, or default config if none configured.
     * The resulting protocol will use this client as a base, so it will share all configuration that is
     * relevant for the protocol.
     *
     * @param protocol protocol instance, usually defined as a constant on the protocol interface
     * @return a new protocol client instance
     * @param <T> type of the protocol client
     * @param <C> type of the protocol config
     */
    <T, C extends ProtocolConfig> T client(Protocol<T, C> protocol);

    /**
     * Executor services, uses virtual threads.
     *
     * @return executor service
     */
    ExecutorService executor();
}
