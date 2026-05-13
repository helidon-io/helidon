/*
 * Copyright (c) 2023, 2026 Oracle and/or its affiliates.
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

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.http.HttpConfig;
import io.helidon.webclient.spi.ProtocolConfig;

/**
 * Configuration of an HTTP/1.1 client.
 */
@Prototype.Blueprint
@Prototype.Configured
@Prototype.IncludeDefaultMethods("maxBufferedEntitySize")
interface Http1ClientProtocolConfigBlueprint extends ProtocolConfig, HttpConfig {
    @Override
    default String type() {
        return Http1ProtocolProvider.CONFIG_KEY;
    }

    /**
     * Name of this protocol configuration.
     *
     * @return protocol configuration name
     */
    @Option.Configured
    @Option.Default(Http1ProtocolProvider.CONFIG_KEY)
    @Override
    String name();

    /**
     * Whether to use keep alive by default.
     *
     * @return {@code true} for keeping connections alive and re-using them for multiple requests (default), {@code false}
     *  to create a new connection for each request
     */
    @Option.Configured
    @Option.DefaultBoolean(true)
    boolean defaultKeepAlive();

    /**
     * Configure the maximum allowed header size of the response.
     *
     * @return  maximum header size
     */
    @Option.Configured
    @Option.DefaultInt(16384)
    int maxHeaderSize();

    /**
     * Configure the maximum allowed length of the status line from the response.
     *
     * @return maximum status line length
     */
    @Option.Configured
    @Option.DefaultInt(256)
    int maxStatusLineLength();
}
