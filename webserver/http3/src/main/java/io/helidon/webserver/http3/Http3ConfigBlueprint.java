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

package io.helidon.webserver.http3;

import java.util.List;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.common.Api;
import io.helidon.http.HttpConfig;
import io.helidon.webserver.http.spi.SinkProvider;
import io.helidon.webserver.quic.spi.QuicSubProtocolConfig;
import io.helidon.webserver.spi.ProtocolConfigProvider;

/**
 * HTTP/3 listener protocol configuration.
 */
@Api.Incubating
@Prototype.Blueprint(decorator = Http3BlueprintSupport.Decorator.class)
@Prototype.Configured(root = false, value = Http3BlueprintSupport.CONFIG_NAME)
@Prototype.Provides(ProtocolConfigProvider.class)
@Prototype.IncludeDefaultMethods({"maxBufferedEntitySize", "log"})
interface Http3ConfigBlueprint extends QuicSubProtocolConfig, HttpConfig {
    @Override
    default String type() {
        return Http3BlueprintSupport.CONFIG_NAME;
    }

    /**
     * Name of this HTTP/3 protocol configuration.
     *
     * @return config name
     */
    @Option.Default(Http3BlueprintSupport.CONFIG_NAME)
    @Override
    String name();

    /**
     * Common HTTP request-header validation setting, ignored because HTTP/3 always validates request field names and values.
     *
     * @return configured common HTTP validation setting
     */
    @Option.Configured
    @Option.DefaultBoolean(true)
    @Override
    boolean validateRequestHeaders();

    /**
     * Common HTTP response-header validation setting, ignored because HTTP/3 always validates response field names and values.
     *
     * @return configured common HTTP validation setting
     */
    @Option.Configured
    @Option.DefaultBoolean(true)
    @Override
    boolean validateResponseHeaders();

    /**
     * Whether HTTP/3 is enabled on this listener.
     *
     * @return whether HTTP/3 is enabled
     */
    @Option.Configured
    @Option.DefaultBoolean(true)
    @Override
    boolean enabled();

    /**
     * Maximum field section size the server advertises to clients in HTTP/3 SETTINGS.
     * The hard local request-header limit is configured by {@link #maxHeadersSize()}.
     * See RFC 9114 section 7.2.4.1 for details.
     * The value must be {@code -1}, which omits the setting, or a QUIC variable-length integer from {@code 0} through
     * {@code 2^62 - 1}, inclusive. Default is {@code 8192}.
     *
     * @return advertised field section size in octets
     */
    @Option.Configured
    @Option.DefaultInt(8192)
    long maxFieldSectionSize();

    /**
     * Maximum QPACK dynamic table capacity announced by the server; {@code -1} uses the protocol default of {@code 0}.
     * The value must be {@code -1} or a QUIC variable-length integer from {@code 0} through {@code 2^62 - 1}, inclusive.
     * Default is {@code -1}.
     *
     * @return capacity in octets
     */
    @Option.Configured
    @Option.DefaultLong(-1L)
    long qpackMaxTableCapacity();

    /**
     * Maximum number of QPACK blocked streams announced by the server; {@code -1} uses the protocol default of {@code 0}.
     * The value must be {@code -1} or a QUIC variable-length integer from {@code 0} through {@code 2^62 - 1}, inclusive.
     * Default is {@code -1}.
     *
     * @return number of blocked streams
     */
    @Option.Configured
    @Option.DefaultLong(-1L)
    long qpackBlockedStreams();

    /**
     * Maximum number of response entity bytes that may be submitted on one HTTP/3 stream without waiting for those
     * bytes to be handed to the QUIC packet path. HTTP/3 frame overhead, headers, trailers, and QUIC retransmission
     * state are not included. The value must be greater than {@code 0}. Default is {@code 65536}.
     *
     * @return response dispatch window size in bytes
     */
    @Option.Configured
    @Option.DefaultInt(Http3BlueprintSupport.DEFAULT_RESPONSE_DISPATCH_WINDOW_SIZE)
    int responseDispatchWindowSize();

    /**
     * Whether to perform additional URI path validation beyond mandatory HTTP/3 request-target checks.
     *
     * @return whether to validate request paths
     */
    @Option.Configured
    @Option.DefaultBoolean(true)
    boolean validatePath();

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
     * Response sink providers available to HTTP/3 requests.
     *
     * @return discovered and explicitly configured response sink providers
     */
    @Api.Internal
    @Option.Provider(SinkProvider.class)
    @Option.Singular
    @SuppressWarnings("rawtypes")
    List<SinkProvider> sinkProviders();
}
