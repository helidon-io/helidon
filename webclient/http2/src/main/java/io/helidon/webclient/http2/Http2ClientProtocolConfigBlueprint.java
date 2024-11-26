/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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

import java.time.Duration;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.webclient.spi.ProtocolConfig;

@Prototype.Blueprint(decorator = Http2ClientConfigSupport.ProtocolConfigDecorator.class)
@Prototype.Configured
interface Http2ClientProtocolConfigBlueprint extends ProtocolConfig {
    @Override
    default String type() {
        return Http2ProtocolProvider.CONFIG_KEY;
    }

    @Option.Configured
    @Option.Default(Http2ProtocolProvider.CONFIG_KEY)
    @Override
    String name();

    /**
     * Prior knowledge of HTTP/2 capabilities of the server. If server we are connecting to does not
     * support HTTP/2 and prior knowledge is set to {@code false}, only features supported by HTTP/1 will be available
     * and attempts to use HTTP/2 specific will throw an {@link UnsupportedOperationException}.
     * <h4>Plain text connection</h4>
     * If prior knowledge is set to {@code true}, we will not attempt an upgrade of connection and use prior knowledge.
     * If prior knowledge is set to {@code false}, we will initiate an HTTP/1 connection and upgrade it to HTTP/2,
     * if supported by the server.
     * plaintext connection ({@code h2c}).
     * <h4>TLS protected connection</h4>
     * If prior knowledge is set to {@code true}, we will negotiate protocol using HTTP/2 only, failing if not supported.
     * if prior knowledge is set to {@code false}, we will negotiate protocol using both HTTP/2 and HTTP/1, using the protocol
     * supported by server.
     *
     * @return whether to use prior knowledge of HTTP/2
     */
    @Option.Configured
    @Option.DefaultBoolean(false)
    boolean priorKnowledge();

    /**
     * Configure initial MAX_FRAME_SIZE setting for new HTTP/2 connections.
     * Maximum size of data frames in bytes the client is prepared to accept from the server.
     * Default value is 2^14(16_384).
     *
     * @return data frame size in bytes between 2^14(16_384) and 2^24-1(16_777_215)
     */
    @Option.Configured
    @Option.DefaultInt(16384)
    int maxFrameSize();

    /**
     * Configure initial MAX_HEADER_LIST_SIZE setting for new HTTP/2 connections.
     * Sends to the server the maximum header field section size client is prepared to accept.
     * Defaults to {@code -1}, which means "unconfigured".
     *
     * @return units of octets
     */
    @Option.Configured
    @Option.DefaultLong(-1L)
    long maxHeaderListSize();

    /**
     * Configure INITIAL_WINDOW_SIZE setting for new HTTP/2 connections.
     * Sends to the server the size of the largest frame payload client is willing to receive.
     * Defaults to {@value io.helidon.http.http2.WindowSize#DEFAULT_WIN_SIZE}.
     *
     * @return units of octets
     */
    @Option.Configured
    @Option.DefaultInt(65535)
    int initialWindowSize();

    /**
     * Timeout for blocking while waiting for window update when window is depleted.
     *
     * @return timeout
     */
    @Option.Configured
    @Option.Default("PT15S")
    Duration flowControlBlockTimeout();

    /**
     * Check healthiness of cached connections with HTTP/2.0 ping frame.
     * Defaults to {@code false}.
     *
     * @return use ping if true
     */
    @Option.Configured
    @Option.DefaultBoolean(false)
    boolean ping();

    /**
     * Timeout for ping probe used for checking healthiness of cached connections.
     * Defaults to {@code PT0.5S}, which means 500 milliseconds.
     *
     * @return timeout
     */
    @Option.Configured
    @Option.Default("PT0.5S")
    Duration pingTimeout();
}
