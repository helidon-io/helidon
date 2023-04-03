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

package io.helidon.nima.http2.webserver;

import java.time.Duration;

import io.helidon.builder.Builder;
import io.helidon.builder.config.ConfigBean;
import io.helidon.common.http.RequestedUriDiscoveryContext;
import io.helidon.config.metadata.ConfiguredOption;

/**
 * HTTP/2 server configuration.
 */
@Builder
@ConfigBean("server.connection-providers.http_2")
public interface Http2Config {
    /**
     * The size of the largest frame payload that the sender is willing to receive in bytes.
     * Default value is {@code 16384} and maximum value is 2<sup>24</sup>-1 = 16777215 bytes.
     * See RFC 9113 section 6.5.2 for details.
     *
     * @return maximal frame size
     */
    @ConfiguredOption("16384")
    int maxFrameSize();

    /**
     * The maximum field section size that the sender is prepared to accept in bytes.
     * See RFC 9113 section 6.5.2 for details.
     * Default is maximal unsigned int.
     *
     * @return maximal header list size in bytes
     */
    @ConfiguredOption("0xFFFFFFFFL")
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
    @ConfiguredOption("8192")
    long maxConcurrentStreams();

    /**
     * This setting indicates the sender's maximum window size in bytes for connection-level flow control.
     * Default and maximum value is 2<sup>31</sup>-1 = 2147483647 bytes. This setting affects the window size
     * of HTTP/2 connection.
     * Any value greater than 2147483647 causes an error. Any value smaller than initial window size causes an error.
     * See RFC 9113 section 6.9.1 for details.
     *
     * @return maximum window size in bytes
     */
    @ConfiguredOption("65635")
    int initialWindowSize();

    /**
     * Outbound flow control blocking timeout.
     *
     * @return duration
     */
    @ConfiguredOption("java.time.Duration.ofMillis(100)")
    Duration flowControlTimeout();

    /**
     * Whether to send error message over HTTP to client.
     * Defaults to {@code false}, as exception message may contain internal information that could be used as an
     * attack vector. Use with care and in cases where both server and clients are under your full control (such as for
     * testing).
     *
     * @return whether to send error messages over the network
     */
    @ConfiguredOption("false")
    boolean sendErrorDetails();

    /**
     * If set to false, any path is accepted (even containing illegal characters).
     *
     * @return whether to validate path
     */
    @ConfiguredOption("true")
    boolean validatePath();

    /**
     * Requested URI discovery settings.
     *
     * @return settings for computing the requested URI
     */
    @ConfiguredOption(key = "requested-uri-discovery")
    RequestedUriDiscoveryContext requestedUriDiscovery();
}
