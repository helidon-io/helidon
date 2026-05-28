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
import java.util.List;
import java.util.Objects;

import javax.net.ssl.SNIServerName;

import io.helidon.common.Api;
import io.helidon.common.tls.Tls;
import io.helidon.http.ClientRequestHeaders;
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
    private final String tlsPeerHost;
    private final int tlsPeerPort;
    private final SniSupport.State sni;

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
        this(scheme,
             host,
             port,
             tls,
             dnsResolver,
             dnsAddressLookup,
             proxy,
             transport,
             SniSupport.tlsDefault(simpleUri(scheme, host, port), tls));
    }

    private ConnectionKey(String scheme,
                          String host,
                          int port,
                          Tls tls,
                          DnsResolver dnsResolver,
                          DnsAddressLookup dnsAddressLookup,
                          Proxy proxy,
                          Transport transport,
                          SniSupport.Selection sni) {
        this.scheme = scheme;
        this.host = host;
        this.port = port;
        this.tls = tls;
        this.dnsResolver = dnsResolver;
        this.dnsAddressLookup = dnsAddressLookup;
        this.proxy = proxy;
        this.transport = transport;
        this.tlsPeerHost = sni.tlsPeerHost();
        this.tlsPeerPort = sni.tlsPeerPort();
        this.sni = sni.state();
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
     * Create new instance of the {@link ConnectionKey}.
     *
     * @param uri              resolved URI
     * @param tls              TLS to be used in connection
     * @param dnsResolver      DNS resolver to be used
     * @param dnsAddressLookup DNS address lookup strategy
     * @param proxy            Proxy server to use for outgoing requests
     * @return new instance
     */
    @Api.Internal
    public static ConnectionKey create(ClientUri uri,
                                       Tls tls,
                                       DnsResolver dnsResolver,
                                       DnsAddressLookup dnsAddressLookup,
                                       Proxy proxy) {
        ClientUri checkedUri = Objects.requireNonNull(uri, "uri");
        Tls checkedTls = Objects.requireNonNull(tls, "tls");
        return new ConnectionKey(checkedUri.scheme(),
                                 checkedUri.host(),
                                 checkedUri.port(),
                                 checkedTls,
                                 Objects.requireNonNull(dnsResolver, "dnsResolver"),
                                 Objects.requireNonNull(dnsAddressLookup, "dnsAddressLookup"),
                                 Objects.requireNonNull(proxy, "proxy"),
                                 null,
                                 SniSupport.tlsDefault(checkedUri, checkedTls));
    }

    /**
     * Create new instance of the {@link ConnectionKey}.
     *
     * @param uri              resolved URI
     * @param sni              effective SNI configuration
     * @param tls              TLS to be used in connection
     * @param dnsResolver      DNS resolver to be used
     * @param dnsAddressLookup DNS address lookup strategy
     * @param proxy            Proxy server to use for outgoing requests
     * @param headers          final request headers available before connection acquisition
     * @return new instance
     */
    @Api.Internal
    public static ConnectionKey create(ClientUri uri,
                                       SniConfig sni,
                                       Tls tls,
                                       DnsResolver dnsResolver,
                                       DnsAddressLookup dnsAddressLookup,
                                       Proxy proxy,
                                       ClientRequestHeaders headers) {
        ClientUri checkedUri = Objects.requireNonNull(uri, "uri");
        Tls checkedTls = Objects.requireNonNull(tls, "tls");
        SniSupport.Selection selection = SniSupport.resolve(checkedUri,
                                                            Objects.requireNonNull(sni, "sni"),
                                                            checkedTls,
                                                            Objects.requireNonNull(headers, "headers"));
        return new ConnectionKey(checkedUri.scheme(),
                                 checkedUri.host(),
                                 checkedUri.port(),
                                 checkedTls,
                                 Objects.requireNonNull(dnsResolver, "dnsResolver"),
                                 Objects.requireNonNull(dnsAddressLookup, "dnsAddressLookup"),
                                 Objects.requireNonNull(proxy, "proxy"),
                                 null,
                                 selection);
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
        UnixDomainSocketAddress socketAddress = Objects.requireNonNull(address, "address");
        return new ConnectionKey(Objects.requireNonNull(scheme, "scheme"),
                                 Objects.requireNonNull(host, "host"),
                                 port,
                                 Objects.requireNonNull(tls, "tls"),
                                 Objects.requireNonNull(dnsResolver, "dnsResolver"),
                                 Objects.requireNonNull(dnsAddressLookup, "dnsAddressLookup"),
                                 Proxy.noProxy(),
                                 new Transport("unix", socketAddress.getPath().toString()));
    }

    /**
     * Create new instance of the {@link ConnectionKey} for a Unix domain socket transport.
     *
     * @param uri              resolved URI
     * @param tls              TLS to be used in connection
     * @param dnsResolver      DNS resolver to be used
     * @param dnsAddressLookup DNS address lookup strategy
     * @param address          Unix domain socket transport address
     * @return new instance
     */
    @Api.Internal
    public static ConnectionKey createUnixDomainSocket(ClientUri uri,
                                                       Tls tls,
                                                       DnsResolver dnsResolver,
                                                       DnsAddressLookup dnsAddressLookup,
                                                       UnixDomainSocketAddress address) {
        ClientUri checkedUri = Objects.requireNonNull(uri, "uri");
        Tls checkedTls = Objects.requireNonNull(tls, "tls");
        UnixDomainSocketAddress socketAddress = Objects.requireNonNull(address, "address");
        return new ConnectionKey(Objects.requireNonNull(checkedUri.scheme(), "scheme"),
                                 Objects.requireNonNull(checkedUri.host(), "host"),
                                 checkedUri.port(),
                                 checkedTls,
                                 Objects.requireNonNull(dnsResolver, "dnsResolver"),
                                 Objects.requireNonNull(dnsAddressLookup, "dnsAddressLookup"),
                                 Proxy.noProxy(),
                                 new Transport("unix", socketAddress.getPath().toString()),
                                 SniSupport.tlsDefault(checkedUri, checkedTls));
    }

    /**
     * Create new instance of the {@link ConnectionKey} for a Unix domain socket transport.
     *
     * @param uri              resolved URI
     * @param sni              effective SNI configuration
     * @param tls              TLS to be used in connection
     * @param dnsResolver      DNS resolver to be used
     * @param dnsAddressLookup DNS address lookup strategy
     * @param address          Unix domain socket transport address
     * @param headers          final request headers available before connection acquisition
     * @return new instance
     */
    @Api.Internal
    public static ConnectionKey createUnixDomainSocket(ClientUri uri,
                                                       SniConfig sni,
                                                       Tls tls,
                                                       DnsResolver dnsResolver,
                                                       DnsAddressLookup dnsAddressLookup,
                                                       UnixDomainSocketAddress address,
                                                       ClientRequestHeaders headers) {
        ClientUri checkedUri = Objects.requireNonNull(uri, "uri");
        Tls checkedTls = Objects.requireNonNull(tls, "tls");
        UnixDomainSocketAddress socketAddress = Objects.requireNonNull(address, "address");
        SniSupport.Selection selection = SniSupport.resolve(checkedUri,
                                                            Objects.requireNonNull(sni, "sni"),
                                                            checkedTls,
                                                            Objects.requireNonNull(headers, "headers"));
        return new ConnectionKey(Objects.requireNonNull(checkedUri.scheme(), "scheme"),
                                 Objects.requireNonNull(checkedUri.host(), "host"),
                                 checkedUri.port(),
                                 checkedTls,
                                 Objects.requireNonNull(dnsResolver, "dnsResolver"),
                                 Objects.requireNonNull(dnsAddressLookup, "dnsAddressLookup"),
                                 Proxy.noProxy(),
                                 new Transport("unix", socketAddress.getPath().toString()),
                                 selection);
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

    /**
     * Host used as the TLS peer host for SNI and endpoint identification.
     *
     * @return TLS peer host
     */
    @Api.Internal
    public String tlsPeerHost() {
        return tlsPeerHost;
    }

    /**
     * Port used as the TLS peer port for endpoint identification.
     *
     * @return TLS peer port
     */
    @Api.Internal
    public int tlsPeerPort() {
        return tlsPeerPort;
    }

    List<SNIServerName> serverNamesOverride() {
        return SniSupport.serverNamesOverride(sni);
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
                && Objects.equals(this.transport, that.transport)
                && Objects.equals(this.tlsPeerHost, that.tlsPeerHost)
                && this.tlsPeerPort == that.tlsPeerPort
                && Objects.equals(this.sni, that.sni);
    }

    @Override
    public int hashCode() {
        return Objects.hash(scheme, host, port, tls, dnsResolver, dnsAddressLookup, proxy, transport, tlsPeerHost,
                            tlsPeerPort, sni);
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
                + ", tlsPeerHost=" + tlsPeerHost + ", "
                + "tlsPeerPort=" + tlsPeerPort + ", "
                + "sni=" + sni
                + ']';
    }

    private static ClientUri simpleUri(String scheme, String host, int port) {
        return ClientUri.create()
                .scheme(scheme)
                .host(host)
                .port(port);
    }

    private record Transport(String type, String value) {
    }
}
