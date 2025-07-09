/*
 * Copyright (c) 2023, 2025 Oracle and/or its affiliates.
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

    private Duration readTimeout;

    /**
     * @param scheme           uri address scheme
     * @param host             uri address host
     * @param port             uri address port
     * @param readTimeout      SO read timeout
     * @param tls              TLS to be used in connection
     * @param dnsResolver      DNS resolver to be used
     * @param dnsAddressLookup DNS address lookup strategy
     * @param proxy            Proxy server to use for outgoing requests
     * @deprecated readTimeout is deprecated to be part of the connection key.
     * Use {@link #ConnectionKey(String, String, int, Tls, DnsResolver, DnsAddressLookup, Proxy)} instead.
     */
    @Deprecated(forRemoval = true, since = "4.2.4")
    public ConnectionKey(String scheme,
                         String host,
                         int port,
                         Duration readTimeout,
                         Tls tls,
                         DnsResolver dnsResolver,
                         DnsAddressLookup dnsAddressLookup,
                         Proxy proxy) {
        this.scheme = scheme;
        this.host = host;
        this.port = port;
        this.readTimeout = readTimeout;
        this.tls = tls;
        this.dnsResolver = dnsResolver;
        this.dnsAddressLookup = dnsAddressLookup;
        this.proxy = proxy;
    }

    /**
     * @param scheme           uri address scheme
     * @param host             uri address host
     * @param port             uri address port
     * @param tls              TLS to be used in connection
     * @param dnsResolver      DNS resolver to be used
     * @param dnsAddressLookup DNS address lookup strategy
     * @param proxy            Proxy server to use for outgoing requests
     */
    public ConnectionKey(String scheme,
                         String host,
                         int port,
                         Tls tls,
                         DnsResolver dnsResolver,
                         DnsAddressLookup dnsAddressLookup,
                         Proxy proxy) {
        this.scheme = scheme;
        this.host = host;
        this.port = port;
        this.tls = tls;
        this.dnsResolver = dnsResolver;
        this.dnsAddressLookup = dnsAddressLookup;
        this.proxy = proxy;
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
     * Socket read timeout.
     *
     * @return socket read timeout
     */
    @Deprecated(forRemoval = true, since = "4.2.4")
    public Duration readTimeout() {
        return readTimeout;
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
                && Objects.equals(this.readTimeout, that.readTimeout)
                && Objects.equals(this.tls, that.tls)
                && Objects.equals(this.dnsResolver, that.dnsResolver)
                && Objects.equals(this.dnsAddressLookup, that.dnsAddressLookup)
                && Objects.equals(this.proxy, that.proxy);
    }

    @Override
    public int hashCode() {
        return Objects.hash(scheme, host, port, readTimeout, tls, dnsResolver, dnsAddressLookup, proxy);
    }

    @Override
    public String toString() {
        return "ConnectionKey["
                + "scheme=" + scheme + ", "
                + "host=" + host + ", "
                + "port=" + port + ", "
                + "readTimeout=" + readTimeout + ", "
                + "tls=" + tls + ", "
                + "dnsResolver=" + dnsResolver + ", "
                + "dnsAddressLookup=" + dnsAddressLookup + ", "
                + "proxy=" + proxy + ']';
    }

}
