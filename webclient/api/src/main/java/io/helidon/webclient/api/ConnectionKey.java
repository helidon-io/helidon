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

package io.helidon.webclient.api;

import java.time.Duration;

import io.helidon.common.tls.Tls;
import io.helidon.webclient.spi.DnsResolver;

/**
 * Connection key instance contains all needed connection related information.
 *
 * @param scheme           uri address scheme
 * @param host             uri address host
 * @param port             uri address port
 * @param readTimeout      SO read timeout
 * @param tls              TLS to be used in connection
 * @param dnsResolver      DNS resolver to be used
 * @param dnsAddressLookup DNS address lookup strategy
 * @param proxy            Proxy server to use for outgoing requests
 */
public record ConnectionKey(String scheme,
                            String host,
                            int port,
                            Duration readTimeout,
                            Tls tls,
                            DnsResolver dnsResolver,
                            DnsAddressLookup dnsAddressLookup,
                            Proxy proxy) {
}
