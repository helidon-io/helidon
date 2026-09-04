/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.webclient.http3;

import java.time.Duration;
import java.util.Optional;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.common.Api;
import io.helidon.http.HttpConfig;
import io.helidon.quic.QuicConfig;
import io.helidon.webclient.spi.ProtocolConfig;

/**
 * Configuration of the HTTP/3 client protocol.
 */
@Prototype.Blueprint(decorator = Http3ClientConfigSupport.ProtocolConfigDecorator.class)
@Prototype.Configured
@Prototype.IncludeDefaultMethods("maxBufferedEntitySize")
@Api.Incubating
interface Http3ClientProtocolConfigBlueprint extends ProtocolConfig, HttpConfig {
    @Override
    default String type() {
        return Http3ProtocolProvider.CONFIG_KEY;
    }

    /**
     * Name of this HTTP/3 protocol configuration.
     *
     * @return protocol configuration name
     */
    @Option.Configured
    @Option.Default(Http3ProtocolProvider.CONFIG_KEY)
    @Override
    String name();

    /**
     * Prior knowledge of HTTP/3 capabilities of the server. If server we are connecting to does not support HTTP/3 and
     * prior knowledge is set to {@code true}, the request fails instead of falling back to a configured TCP protocol.
     *
     * @return whether to use prior knowledge of HTTP/3
     */
    @Option.Configured
    @Option.DefaultBoolean(false)
    boolean priorKnowledge();

    /**
     * Maximum field section size the client advertises to servers in HTTP/3 SETTINGS.
     * The hard local response-header limit is configured by {@link #maxHeadersSize()}.
     * The value must be {@code -1}, which omits the setting, or a QUIC variable-length integer from {@code 0} through
     * {@code 2^62 - 1}, inclusive. Default is {@code -1}.
     *
     * @return advertised field section size in octets
     */
    @Option.Configured
    @Option.DefaultLong(-1L)
    long maxFieldSectionSize();

    /**
     * Maximum QPACK dynamic table capacity announced by the client.
     * The value must be {@code -1}, which uses the protocol default of {@code 0}, or a QUIC variable-length integer from
     * {@code 0} through {@code 2^62 - 1}, inclusive. Default is {@code -1}.
     *
     * @return capacity in octets
     */
    @Option.Configured
    @Option.DefaultLong(-1L)
    long qpackMaxTableCapacity();

    /**
     * Maximum number of QPACK blocked streams announced by the client.
     * The value must be {@code -1}, which uses the protocol default of {@code 0}, or a QUIC variable-length integer from
     * {@code 0} through {@code 2^62 - 1}, inclusive. Default is {@code -1}.
     *
     * @return number of blocked streams
     */
    @Option.Configured
    @Option.DefaultLong(-1L)
    long qpackBlockedStreams();

    /**
     * Whether exception-message text may be sent in HTTP/3 connection-close frames after control and formatting characters
     * are replaced with spaces and the payload is truncated to 256 UTF-8 bytes; sensitive content is not redacted.
     * Defaults to {@code false}, because exception messages can contain internal information or secrets.
     *
     * @return whether to disclose bounded exception-message text
     */
    @Option.Configured
    @Option.DefaultBoolean(false)
    boolean sendErrorDetails();

    /**
     * Maximum duration to wait after sending the first client QUIC Initial packet until receiving the first peer Initial
     * packet. This phase is nested within the complete QUIC and TLS handshake bounded by {@link #handshakeTimeout()}, so this
     * duration must be positive, fit in signed 64-bit nanoseconds, and be no greater than the handshake timeout.
     *
     * @return initial-response timeout
     */
    @Option.Configured
    @Option.Default("PT10S")
    Duration initialResponseTimeout();

    /**
     * Handshake timeout for a complete new HTTP/3 connection attempt. The duration must be positive, fit in signed 64-bit
     * nanoseconds, and be no less than {@link #initialResponseTimeout()}.
     *
     * @return handshake timeout
     */
    @Option.Configured
    @Option.Default("PT10S")
    Duration handshakeTimeout();

    /**
     * Maximum duration to wait for peer QUIC stream credit when opening local HTTP/3 request and critical streams.
     * The duration must be positive and fit in signed 64-bit nanoseconds. This timeout is independent of the connection
     * handshake timeouts.
     *
     * @return stream-open timeout
     */
    @Option.Configured
    @Option.Default("PT10S")
    Duration streamOpenTimeout();

    /**
     * QUIC protocol and transport configuration used for HTTP/3 connections created from this protocol configuration.
     * This includes the QUIC connection idle timeout. The configured
     * {@link io.helidon.quic.QuicConfig#maxUniStreams()} value must be at least {@code 3}, as required by HTTP/3.
     *
     * @return optional QUIC protocol and transport configuration
     */
    @Option.Configured
    Optional<QuicConfig> quic();

}
