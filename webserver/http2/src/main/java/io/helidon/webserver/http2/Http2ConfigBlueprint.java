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

package io.helidon.webserver.http2;

import java.time.Duration;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.http.RequestedUriDiscoveryContext;
import io.helidon.webserver.spi.ProtocolConfig;

/**
 * HTTP/2 server configuration.
 */
@Prototype.Blueprint(decorator = Http2ConfigBlueprint.Http2ConfigDecorator.class)
@Prototype.Configured
@Prototype.Provides(ProtocolConfig.class)
interface Http2ConfigBlueprint extends ProtocolConfig {
    /**
     * The size of the largest frame payload that the sender is willing to receive in bytes.
     * Default value is {@code 16384} and maximum value is 2<sup>24</sup>-1 = 16777215 bytes.
     * See RFC 9113 section 6.5.2 for details.
     *
     * @return maximal frame size
     */
    @Option.Configured
    @Option.DefaultInt(16384)
    int maxFrameSize();

    /**
     * The maximum field section size that the sender is prepared to accept in bytes.
     * See RFC 9113 section 6.5.2 for details.
     * Default is 8192.
     *
     * @return maximal header list size in bytes
     */
    @Option.Configured
    @Option.DefaultInt(8192)
    long maxHeaderListSize();

    /**
     * Maximum number of concurrent streams that the server will allow.
     * Defaults to {@code 8192}. This limit is directional: it applies to the number of streams that the sender
     * permits the receiver to create.
     * It is recommended that this value be no smaller than 100 to not unnecessarily limit parallelism
     * See RFC 9113 section 6.5.2 for details.
     *
     * @return maximal number of concurrent streams
     */
    @Option.Configured
    @Option.DefaultLong(8192)
    long maxConcurrentStreams();

    /**
     * This setting indicates the sender's maximum window size in bytes for stream-level flow control.
     * Default and maximum value is 2<sup>31</sup>-1 = 2147483647 bytes. This setting affects the window size
     * of HTTP/2 connection.
     * Any value greater than 2147483647 causes an error. Any value smaller than initial window size causes an error.
     * See RFC 9113 section 6.9.1 for details.
     *
     * @return maximum window size in bytes
     */
    @Option.Configured
    @Option.DefaultInt(1048576)
    int initialWindowSize();

    /**
     * Outbound flow control blocking timeout configured as {@link java.time.Duration}
     * or text in ISO-8601 format.
     * Blocking timeout defines an interval to wait for the outbound window size changes(incoming window updates)
     * before the next blocking iteration.
     * Default value is {@code PT0.1S}.
     *
     * <table>
     *     <caption><b>ISO_8601 format examples:</b></caption>
     *     <tr><th>PT0.1S</th><th>100 milliseconds</th></tr>
     *     <tr><th>PT0.5S</th><th>500 milliseconds</th></tr>
     *     <tr><th>PT2S</th><th>2 seconds</th></tr>
     * </table>
     *
     * @return duration
     * @see <a href="https://en.wikipedia.org/wiki/ISO_8601#Durations">ISO_8601 Durations</a>
     */
    @Option.Configured
    @Option.Default("PT0.1S")
    Duration flowControlTimeout();

    /**
     * Whether to send error message over HTTP to client.
     * Defaults to {@code false}, as exception message may contain internal information that could be used as an
     * attack vector. Use with care and in cases where both server and clients are under your full control (such as for
     * testing).
     *
     * @return whether to send error messages over the network
     */
    @Option.Configured
    @Option.DefaultBoolean(false)
    boolean sendErrorDetails();

    /**
     * Period for counting rapid resets(stream RST sent by client before any data have been sent by server).
     * Default value is {@code PT10S}.
     *
     * @return duration
     * @see <a href="https://nvd.nist.gov/vuln/detail/CVE-2023-44487">CVE-2023-44487</a>
     * @see <a href="https://en.wikipedia.org/wiki/ISO_8601#Durations">ISO_8601 Durations</a>
     */
    @Option.Configured
    @Option.Default("PT10S")
    Duration rapidResetCheckPeriod();

    /**
     * Maximum number of rapid resets(stream RST sent by client before any data have been sent by server).
     * When reached within {@link #rapidResetCheckPeriod()}, GOAWAY is sent to client and connection is closed.
     * Default value is {@code 100}.
     *
     * @return maximum number of rapid resets
     * @see <a href="https://nvd.nist.gov/vuln/detail/CVE-2023-44487">CVE-2023-44487</a>
     */
    @Option.Configured
    @Option.DefaultInt(100)
    int maxRapidResets();

    /**
     * Maximum number of consecutive empty frames allowed on connection.
     *
     * @return max number of consecutive empty frames
     */
    @Option.Configured
    @Option.DefaultInt(10)
    int maxEmptyFrames();

    /**
     * If set to false, any path is accepted (even containing illegal characters).
     *
     * @return whether to validate path
     */
    @Option.Configured
    @Option.DefaultBoolean(true)
    boolean validatePath();

    /**
     * Requested URI discovery settings.
     *
     * @return settings for computing the requested URI
     */
    @Option.Configured
    RequestedUriDiscoveryContext requestedUriDiscovery();

    /**
     * Protocol configuration type.
     *
     * @return type of this configuration
     */
    default String type() {
        return Http2ConnectionProvider.CONFIG_NAME;
    }

    class Http2ConfigDecorator implements Prototype.BuilderDecorator<Http2Config.BuilderBase<?, ?>> {
        @Override
        public void decorate(Http2Config.BuilderBase<?, ?> target) {
            if (target.name().isEmpty()) {
                target.name("@default");
            }

            if (target.requestedUriDiscovery().isEmpty()) {
                target.requestedUriDiscovery(RequestedUriDiscoveryContext.builder()
                                                     .socketId(target.name().orElse("@default"))
                                                     .build());
            }
        }
    }
}
