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

package io.helidon.nima.http2.webclient;

import java.util.function.Consumer;

import io.helidon.builder.api.RuntimeType;
import io.helidon.common.config.Config;
import io.helidon.nima.webclient.api.HttpClient;
import io.helidon.nima.webclient.api.WebClient;

/**
 * HTTP2 client.
 */
@RuntimeType.PrototypedBy(Http2ClientConfig.class)
public interface Http2Client extends HttpClient<Http2ClientRequest>, RuntimeType.Api<Http2ClientConfig> {

    String PROTOCOL_ID = "h2";

    /**
     * A new fluent API builder to customize client setup.
     *
     * @return a new builder
     */
    static Http2ClientConfig.Builder builder() {
        return Http2ClientConfig.builder();
    }

    static Http2Client create(Http2ClientConfig clientConfig) {
        return new Http2ClientImpl(WebClient.create(it -> it.from(clientConfig)), clientConfig);
    }

    static Http2Client create(Consumer<Http2ClientConfig.Builder> consumer) {
        return create(Http2ClientConfig.builder()
                              .update(consumer)
                              .buildPrototype());
    }

    /**
     * Create a new instance.
     *
     * @return client
     */
    static Http2Client create() {
        return create(Http2ClientConfig.create());
    }

    /**
     * Create a new instance based on {@link io.helidon.common.config.Config}.
     *
     * @param config client config
     * @return client
     */
    static Http2Client create(Config config) {
        return create(it -> it.config(config));
    }
}
