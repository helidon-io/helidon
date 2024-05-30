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

package io.helidon.webclient.http2;

import java.util.function.Consumer;

import io.helidon.builder.api.RuntimeType;
import io.helidon.common.config.Config;
import io.helidon.webclient.api.HttpClient;
import io.helidon.webclient.api.WebClient;
import io.helidon.webclient.spi.Protocol;

/**
 * HTTP2 client.
 */
@RuntimeType.PrototypedBy(Http2ClientConfig.class)
public interface Http2Client extends HttpClient<Http2ClientRequest>, RuntimeType.Api<Http2ClientConfig> {
    /**
     * HTTP/2 protocol ID, as used by ALPN.
     */
    String PROTOCOL_ID = "h2";
    /**
     * Protocol to use to obtain an instance of http/2 specific client from
     * {@link io.helidon.webclient.api.WebClient#client(io.helidon.webclient.spi.Protocol)}.
     */
    Protocol<Http2Client, Http2ClientProtocolConfig> PROTOCOL = Http2ProtocolProvider::new;

    /**
     * A new fluent API builder to customize client setup.
     *
     * @return a new builder
     */
    static Http2ClientConfig.Builder builder() {
        return Http2ClientConfig.builder();
    }

    /**
     * Create a new instance with custom configuration.
     *
     * @param clientConfig HTTP/2 client configuration
     * @return a new HTTP/2 client
     */
    static Http2Client create(Http2ClientConfig clientConfig) {
        return new Http2ClientImpl(WebClient.create(it -> it.from(clientConfig)), clientConfig);
    }

    /**
     * Create a new instance customizing its configuration.
     *
     * @param consumer HTTP/2 client configuration
     * @return a new HTTP/2 client
     */
    static Http2Client create(Consumer<Http2ClientConfig.Builder> consumer) {
        return create(Http2ClientConfig.builder()
                              .update(consumer)
                              .buildPrototype());
    }

    /**
     * Create a new instance with default configuration.
     *
     * @return a new HTTP/2 client
     */
    static Http2Client create() {
        return create(Http2ClientConfig.create());
    }

    /**
     * Create a new instance based on {@link io.helidon.common.config.Config}.
     *
     * @param config client config
     * @return a new HTTP/2 client
     */
    static Http2Client create(Config config) {
        return create(it -> it.config(config));
    }
}
