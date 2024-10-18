/*
 * Copyright (c) 2022, 2024 Oracle and/or its affiliates.
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

package io.helidon.webclient.http1;

import java.util.function.Consumer;

import io.helidon.builder.api.RuntimeType;
import io.helidon.common.config.Config;
import io.helidon.webclient.api.HttpClient;
import io.helidon.webclient.api.WebClient;
import io.helidon.webclient.spi.Protocol;

/**
 * HTTP/1.1 client.
 */
@RuntimeType.PrototypedBy(Http1ClientConfig.class)
public interface Http1Client extends HttpClient<Http1ClientRequest>, RuntimeType.Api<Http1ClientConfig> {
    /**
     * ID of HTTP/1.1 protocol, as used for example in ALPN.
     */
    String PROTOCOL_ID = "http/1.1";
    /**
     * HTTP/1.1 protocol to use to obtain an instance of HTTP/1.1 specific client from
     * {@link io.helidon.webclient.api.WebClient#client(io.helidon.webclient.spi.Protocol)}.
     */
    Protocol<Http1Client, Http1ClientProtocolConfig> PROTOCOL = Http1ProtocolProvider::new;

    /**
     * Create a new builder to construct an HTTP/1.1 client.
     *
     * @return fluent API builder
     */
    static Http1ClientConfig.Builder builder() {
        return Http1ClientConfig.builder()
                .update(it -> it.from(Http1ClientImpl.globalConfig()));
    }

    /**
     * Create a new HTTP/1.1 client with custom configuration.
     *
     * @param clientConfig client configuration
     * @return a new client
     */
    static Http1Client create(Http1ClientConfig clientConfig) {
        return new Http1ClientImpl(WebClient.create(it -> it.from(clientConfig)), clientConfig);
    }

    /**
     * Create a new HTTP/1.1 client customizing configuration.
     *
     * @param consumer client configuration
     * @return a new client
     */
    static Http1Client create(Consumer<Http1ClientConfig.Builder> consumer) {
        return builder().update(consumer)
                .build();
    }

    /**
     * Create a new instance with default configuration.
     *
     * @return client
     */
    static Http1Client create() {
        return create(Http1ClientConfig.create());
    }

    /**
     * Create a new instance based on {@link Config}.
     *
     * @param config client config
     * @return client
     */
    static Http1Client create(Config config) {
        return create(it -> it.config(config));
    }

    /**
     * Configure the default Http1 client configuration.
     * Note: This method needs to be used before Helidon is started to have the full effect.
     *
     * @param clientConfig global client config
     */
    static void configureDefaults(Http1ClientConfig clientConfig) {
        Http1ClientImpl.GLOBAL_CONFIG.compareAndSet(null, clientConfig);
    }

}
