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

package io.helidon.webclient.api;

import java.net.UnixDomainSocketAddress;
import java.util.Objects;

import io.helidon.common.tls.Tls;
import io.helidon.webclient.spi.DnsResolver;

/**
 * Connection key instance contains all needed connection related information.
 */
public final class ConnectionKey {
    private final String scheme;
    private final String host;
    private final int port;
    private final Tls tls;
    private final DnsResolver dnsResolver;
    private final DnsAddressLookup dnsAddressLookup;
    private final Proxy proxy;
    private final Transport transport;

    private ConnectionKey(String scheme,
                          String host,
                          int port,
                          Tls tls,
                          DnsResolver dnsResolver,
                          DnsAddressLookup dnsAddressLookup,
                          Proxy proxy) {
        this(scheme, host, port, tls, dnsResolver, dnsAddressLookup, proxy, null);
    }

    private ConnectionKey(String scheme,
                          String host,
                          int port,
                          Tls tls,
                          DnsResolver dnsResolver,
                          DnsAddressLookup dnsAddressLookup,
                          Proxy proxy,
                          Transport transport) {
        this.scheme = scheme;
        this.host = host;
        this.port = port;
        this.tls = tls;
        this.dnsResolver = dnsResolver;
        this.dnsAddressLookup = dnsAddressLookup;
        this.proxy = proxy;
        this.transport = transport;
    }

    /**
     * Create new instance of the {@link ConnectionKey}.
     *
     * @param scheme           uri address scheme
     * @param host             uri address host
     * @param port             uri address port
     * @param tls              TLS to be used in connection
     * @param dnsResolver      DNS resolver to be used
     * @param dnsAddressLookup DNS address lookup strategy
     * @param proxy            Proxy server to use for outgoing requests
     * @return new instance
     */
    public static ConnectionKey create(String scheme,
                                       String host,
                                       int port,
                                       Tls tls,
                                       DnsResolver dnsResolver,
                                       DnsAddressLookup dnsAddressLookup,
                                       Proxy proxy) {
        return new ConnectionKey(scheme, host, port, tls, dnsResolver, dnsAddressLookup, proxy);
    }

    /**
     * Create new instance of the {@link ConnectionKey} for a Unix domain socket transport.
     *
     * @param scheme           uri address scheme
     * @param host             uri address host
     * @param port             uri address port
     * @param tls              TLS to be used in connection
     * @param dnsResolver      DNS resolver to be used
     * @param dnsAddressLookup DNS address lookup strategy
     * @param address          Unix domain socket transport address
     * @return new instance
     */
    public static ConnectionKey createUnixDomainSocket(String scheme,
                                                       String host,
                                                       int port,
                                                       Tls tls,
                                                       DnsResolver dnsResolver,
                                                       DnsAddressLookup dnsAddressLookup,
                                                       UnixDomainSocketAddress address) {
        return new ConnectionKey(Objects.requireNonNull(scheme, "scheme"),
                                 Objects.requireNonNull(host, "host"),
                                 port,
                                 Objects.requireNonNull(tls, "tls"),
                                 Objects.requireNonNull(dnsResolver, "dnsResolver"),
                                 Objects.requireNonNull(dnsAddressLookup, "dnsAddressLookup"),
                                 Proxy.noProxy(),
                                 Transport.unixDomainSocket(Objects.requireNonNull(address, "address")));
    }

    /**
     * Uri address scheme.
     *
     * @return uri address scheme
     */
    public String scheme() {
        return scheme;
    }

    /**
     * Uri address host.
     *
     * @return uri address host
     */
    public String host() {
        return host;
    }

    /**
     * Uri address port.
     *
     * @return uri address port
     */
    public int port() {
        return port;
    }

    /**
     * Configured {@link Tls}.
     *
     * @return configured tls
     */
    public Tls tls() {
        return tls;
    }

    /**
     * Configured {@link DnsResolver}.
     *
     * @return configured dns resolver
     */
    public DnsResolver dnsResolver() {
        return dnsResolver;
    }

    /**
     * Configured {@link DnsAddressLookup}.
     *
     * @return configured dns address lookup
     */
    public DnsAddressLookup dnsAddressLookup() {
        return dnsAddressLookup;
    }

    /**
     * Configured {@link Proxy}.
     *
     * @return configured proxy
     */
    public Proxy proxy() {
        return proxy;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }
        var that = (ConnectionKey) obj;
        return Objects.equals(this.scheme, that.scheme)
                && Objects.equals(this.host, that.host)
                && this.port == that.port
                && Objects.equals(this.tls, that.tls)
                && Objects.equals(this.dnsResolver, that.dnsResolver)
                && Objects.equals(this.dnsAddressLookup, that.dnsAddressLookup)
                && Objects.equals(this.proxy, that.proxy)
                && Objects.equals(this.transport, that.transport);
    }

    @Override
    public int hashCode() {
        return Objects.hash(scheme, host, port, tls, dnsResolver, dnsAddressLookup, proxy, transport);
    }

    @Override
    public String toString() {
        return "ConnectionKey["
                + "scheme=" + scheme + ", "
                + "host=" + host + ", "
                + "port=" + port + ", "
                + "tls=" + tls + ", "
                + "dnsResolver=" + dnsResolver + ", "
                + "dnsAddressLookup=" + dnsAddressLookup + ", "
                + "proxy=" + proxy
                + (transport == null ? "" : ", transport=" + transport)
                + ']';
    }

    private static final class Transport {
        private final String type;
        private final String value;

        private Transport(String type, String value) {
            this.type = type;
            this.value = value;
        }

        static Transport unixDomainSocket(UnixDomainSocketAddress address) {
            return new Transport("unix", address.getPath().toString());
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj == null || obj.getClass() != this.getClass()) {
                return false;
            }
            Transport that = (Transport) obj;
            return Objects.equals(this.type, that.type)
                    && Objects.equals(this.value, that.value);
        }

        @Override
        public int hashCode() {
            return Objects.hash(type, value);
        }

        @Override
        public String toString() {
            return "Transport[type=" + type + ", value=" + value + "]";
        }
    }
}
