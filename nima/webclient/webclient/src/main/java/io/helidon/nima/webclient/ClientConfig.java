/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.nima.webclient;

import java.net.URI;
import java.util.Optional;

import io.helidon.common.socket.SocketOptions;
import io.helidon.config.metadata.ConfiguredOption;
import io.helidon.nima.common.tls.Tls;
import io.helidon.nima.webclient.spi.DnsResolver;

/**
 * Configuration of Web Client.
 */
public interface ClientConfig {
    /**
     * Base uri used by the client in all requests, unless defined as an absolute URI.
     *
     * @return base uri of the client requests
     */
    @ConfiguredOption
    Optional<URI> baseUri();

    /**
     * Socket options for connections opened by this client.
     *
     * @return options
     */
    SocketOptions socketOptions();

    /**
     * TLS configuration for any TLS request from this client.
     * TLS can also be configured per request.
     * TLS is used when the protocol is set to {@code https} (or other secure protocols that web client supports)
     *
     * @return TLS configuration to use
     */
    Optional<Tls> tls();

    /**
     * DNS resolver to be used by this client.
     *
     * @return dns resolver
     */
    DnsResolver dnsResolver();

    /**
     * DNS address lookup preferences to be used by this client.
     *
     * @return dns address lookup strategy
     */
    DnsAddressLookup dnsAddressLookup();

    /**
     * Whether to follow redirects.
     *
     * @return follow redirects
     */
    @ConfiguredOption("true")
    boolean followRedirects();

    /**
     * Maximum number of redirects allowed.
     *
     * @return allowed number of redirects
     */
    @ConfiguredOption("5")
    int maxRedirects();

}
