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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.System.Logger.Level;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.helidon.common.Api;
import io.helidon.common.LruCache;
import io.helidon.common.media.type.MediaTypes;
import io.helidon.common.socket.SocketOptions;
import io.helidon.common.tls.Tls;
import io.helidon.common.uri.UriAuthority;
import io.helidon.common.uri.UriHost;
import io.helidon.config.Config;
import io.helidon.config.metadata.Configured;
import io.helidon.config.metadata.ConfiguredOption;
import io.helidon.http.Header;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.Method;
import io.helidon.http.Status;
import io.helidon.webclient.spi.DnsResolver;

/**
 * A definition of a proxy server to use for outgoing requests.
 */
public class Proxy {
    private static final System.Logger LOGGER = System.getLogger(Proxy.class.getName());
    private static final Tls NO_TLS = Tls.builder().enabled(false).build();

    private static final Pattern PORT_PATTERN = Pattern.compile(".*:(\\d+)");
    private static final Pattern IP_V4 = Pattern.compile("^(25[0-5]|2[0-4]\\d|[0-1]?\\d?\\d)(\\."
                                                                 + "(25[0-5]|2[0-4]\\d|[0-1]?\\d?\\d)){3}$");
    private static final Pattern IP_V6_IDENTIFIER = Pattern.compile("^\\[(?:[0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}]$");
    private static final Pattern IP_V6_HEX_IDENTIFIER = Pattern
            .compile("^\\[((?:[0-9A-Fa-f]{1,4}(?::[0-9A-Fa-f]{1,4})*)?)::((?:[0-9A-Fa-f]{1,4}(?::[0-9A-Fa-f]{1,4})*)?)]$");
    private static final Pattern IP_V6_HOST = Pattern.compile("^(?:[0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}$");
    private static final Pattern IP_V6_HEX_HOST = Pattern
            .compile("^((?:[0-9A-Fa-f]{1,4}(?::[0-9A-Fa-f]{1,4})*)?)::((?:[0-9A-Fa-f]{1,4}(?::[0-9A-Fa-f]{1,4})*)?)$");

    private static final LruCache<String, Boolean> IVP6_HOST_MATCH_RESULTS = LruCache.create(100);
    private static final LruCache<String, Boolean> IVP6_IDENTIFIER_MATCH_RESULTS = LruCache.create(100);
    /**
     * No proxy instance.
     */
    private static final Proxy NO_PROXY = new Proxy(builder().type(ProxyType.NONE));

    private final ProxyType type;
    private final String host;
    private final int port;
    private final Function<InetSocketAddress, Boolean> noProxy;
    private final Optional<String> username;
    private final Optional<char[]> password;
    private final ProxySelector systemProxySelector;
    private final Optional<Header> proxyAuthHeader;
    private final boolean forceHttpConnect;
    private final boolean ipNoProxyConfigured;

    private Proxy(Proxy.Builder builder) {
        this.host = builder.host();
        if (this.host != null) {
            this.type = ProxyType.HTTP;
        } else {
            this.type = builder.type();
        }

        this.port = builder.port();
        this.username = builder.username();
        this.password = builder.password();
        this.forceHttpConnect = builder.forceHttpConnect();

        if (type == ProxyType.SYSTEM) {
            this.noProxy = inetSocketAddress -> true;
            this.systemProxySelector = ProxySelector.getDefault();
            this.ipNoProxyConfigured = false;
        } else {
            Set<String> noProxyHosts = builder.noProxyHosts();
            this.noProxy = prepareNoProxy(noProxyHosts);
            this.systemProxySelector = null;
            boolean ipNoProxyConfigured = false;
            for (String noProxyHost : noProxyHosts) {
                String hostPart = noProxyHost;
                Matcher portMatcher = PORT_PATTERN.matcher(noProxyHost);
                if (portMatcher.matches()) {
                    hostPart = noProxyHost.substring(0, noProxyHost.lastIndexOf(':'));
                }
                if (isIpV4(hostPart) || isIpV6Identifier(hostPart)) {
                    ipNoProxyConfigured = true;
                    break;
                }
            }
            this.ipNoProxyConfigured = ipNoProxyConfigured;
        }

        if (username.isPresent()) {
            char[] pass = password.orElse(new char[0]);
            // Making the password char[] to String looks not correct, but it is done in the same way in HttpBasicAuthProvider
            String b64 = Base64.getEncoder().encodeToString((username.get() + ":" + new String(pass))
                    .getBytes(StandardCharsets.UTF_8));
            this.proxyAuthHeader = Optional.of(HeaderValues.create(HeaderNames.PROXY_AUTHORIZATION, "Basic " + b64));
        } else {
            this.proxyAuthHeader = Optional.empty();
        }
    }

    /**
     * Fluent API builder for new instances.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * A Proxy instance that does not proxy requests.
     *
     * @return a new instance with no proxy definition
     */
    public static Proxy noProxy() {
        return NO_PROXY;
    }

    /**
     * Create a new proxy instance from configuration.
     * {@code
     * proxy:
     * http:
     * uri: https://www.example.org
     * https:
     * uri: https://www.example.org
     * no-proxy: ["*.example.org", "localhost"]
     * }
     *
     * @param config configuration, should be located on a key that has proxy as a subkey
     * @return proxy instance
     */
    public static Proxy create(Config config) {
        return builder()
                .config(config)
                .build();
    }

    /**
     * Create from environment and system properties.
     *
     * @return a proxy instance configured based on this system settings
     */
    public static Proxy create() {
        // we must create a new instance, as the system proxy may be reset
        return builder().type(ProxyType.SYSTEM).build();
    }

    static Function<InetSocketAddress, Boolean> prepareNoProxy(Set<String> noProxyHosts) {
        if (noProxyHosts.isEmpty()) {
            // if no exceptions, then simple
            return address -> false;
        }

        boolean simple = true;
        for (String noProxyHost : noProxyHosts) {
            // go through all - if none start with *. then simple contains is sufficient
            if (noProxyHost.startsWith(".")) {
                simple = false;
                break;
            }
        }

        if (simple) {
            return address -> {
                String host = address.getHostString();
                if (noProxyHosts.contains(host) || noProxyHosts.contains(host + ":" + address.getPort())) {
                    return true;
                }
                InetAddress inetAddress = address.getAddress();
                if (inetAddress == null) {
                    return false;
                }
                String ipAddress = resolveHost(inetAddress.getHostAddress());
                return noProxyHosts.contains(ipAddress)
                        || noProxyHosts.contains(ipAddress + ":" + address.getPort());
            };
        }

        List<BiFunction<String, Integer, Boolean>> hostMatchers = new LinkedList<>();
        List<BiFunction<String, Integer, Boolean>> ipMatchers = new LinkedList<>();

        for (String noProxyHost : noProxyHosts) {
            String hostPart = noProxyHost;
            Integer portPart = null;
            Matcher portMatcher = PORT_PATTERN.matcher(noProxyHost);
            if (portMatcher.matches()) {
                // we have a port
                portPart = Integer.parseInt(portMatcher.group(1));
                int index = noProxyHost.lastIndexOf(':');
                hostPart = noProxyHost.substring(0, index);
            }

            if (isIpV4(hostPart)) {
                //this is going to be an IP matcher - IP matchers only support full IP addresses
                exactMatch(ipMatchers, hostPart, portPart);
            } else if (isIpV6Identifier(hostPart)) {
                if ("[::1]".equals(hostPart)) {
                    exactMatch(ipMatchers, "0:0:0:0:0:0:0:1", portPart);
                }

                exactMatch(ipMatchers, hostPart.substring(1, hostPart.length() - 1), portPart);
            } else {
                // for host names, we must honor . prefix to handle all sub-domains
                if (hostPart.charAt(0) == '.') {
                    prefixedMatch(hostMatchers, hostPart, portPart);
                } else {
                    // exact match
                    exactMatch(hostMatchers, hostPart, portPart);
                }
            }
        }

        // complicated - must check for . prefixes
        return address -> {
            InetAddress inetAddress = address.getAddress();
            Set<String> toCheck;
            if (inetAddress == null) {
                toCheck = Set.of(address.getHostString());
            } else {
                toCheck = new HashSet<>();
                // if the address was created with an IP address, both may be the same
                toCheck.add(resolveHost(inetAddress.getHostName()));
                toCheck.add(resolveHost(inetAddress.getHostAddress()));
            }

            int port = address.getPort();

            // we need to check both IP address and host name (if set)
            for (String host : toCheck) {
                // first need to make sure whether I have an IP address or a hostname
                if (isIpV4(host) || isIpV6Host(host)) {
                    // we have an IP address
                    for (BiFunction<String, Integer, Boolean> ipMatcher : ipMatchers) {
                        if (ipMatcher.apply(host, port)) {
                            LOGGER.log(Level.TRACE, () -> "IP Address " + host + " bypasses proxy");
                            return true;
                        }
                    }
                    LOGGER.log(Level.TRACE, () -> "IP Address " + host + " uses proxy");
                } else {
                    // we have a host name
                    for (BiFunction<String, Integer, Boolean> hostMatcher : hostMatchers) {
                        if (hostMatcher.apply(host, port)) {
                            LOGGER.log(Level.TRACE, () -> "Host " + host + " bypasses proxy");
                            return true;
                        }
                    }
                    LOGGER.log(Level.TRACE, () -> "Host " + host + " uses proxy");
                }
            }

            return false;
        };
    }

    /**
     * Create a socket for TCP, connected through the proxy.
     *
     * @param webClient web client to use if HTTP requests must be done
     * @param inetSocketAddress target address of the request (proxy address is configured as part Proxy instance)
     * @param socketOptions options for creating sockets
     * @param tls whether to use TLS
     * @return a new connected socket
     */
    public Socket tcpSocket(WebClient webClient,
                            InetSocketAddress inetSocketAddress,
                            SocketOptions socketOptions,
                            boolean tls) {
        return type.connect(webClient, this, inetSocketAddress, socketOptions, tls);
    }

    /**
     * Create a TCP socket for one preselected and resolved route.
     *
     * @param webClient web client used for an HTTP CONNECT exchange
     * @param target resolved physical target
     * @param socketOptions socket options
     * @return connected socket
     */
    @Api.Internal
    public Socket tcpSocket(WebClient webClient,
                            ResolvedClientTarget target,
                            SocketOptions socketOptions) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(socketOptions, "socketOptions");
        ProxyRoute route = target.proxyRoute();
        if (!route.belongsTo(this)) {
            throw new IllegalArgumentException("Resolved target belongs to a different proxy policy");
        }
        if (route.direct()) {
            return connect(new Socket(), target.localAddress(), target.peerAddress(), socketOptions);
        }
        if (route.systemProxyType() != null) {
            Socket socket = new Socket(new java.net.Proxy(route.systemProxyType(), target.peerAddress()));
            return connect(socket, target.localAddress(), target.destinationAddress(), socketOptions);
        }
        return connectToProxy(webClient, target, this);
    }

    /**
     * Get proxy type.
     *
     * @return the proxy type
     */
    public ProxyType type() {
        return type;
    }

    /**
     * Verifies whether the current host is inside noHosts.
     *
     * @param uri the uri
     * @return true if it is in no hosts, otherwise false
     */
    public boolean isNoHosts(InetSocketAddress uri) {
        return noProxy.apply(uri);
    }

    /**
     * Verifies whether the specified Uri is using system proxy.
     *
     * @param uri the uri
     * @return true if the uri resource will be proxied
     */
    public boolean isUsingSystemProxy(String uri) {
        if (systemProxySelector != null) {
            List<java.net.Proxy> proxies = systemProxySelector
                    .select(URI.create(uri));
            return !proxies.isEmpty() && !proxies.get(0).equals(java.net.Proxy.NO_PROXY);
        }
        return false;
    }

    /**
     * Resolve configured proxy policy to one effective route for a logical request target.
     * A system selector is invoked exactly once and its selected route is retained by the returned value.
     * Configured {@code no-proxy} rules that contain only host names are evaluated without DNS lookup. IP-based rules are
     * evaluated against the supplied host string by this overload; WebClient's connection-target resolution also evaluates
     * them against the concrete DNS address used for the connection.
     *
     * @param scheme logical request scheme
     * @param host logical request host
     * @param port logical request port
     * @param tls whether the application connection uses TLS
     * @return effective route
     */
    @Api.Internal
    public ProxyRoute effectiveRoute(String scheme, String host, int port, boolean tls) {
        return effectiveRoute(scheme, host, port, tls, null, null);
    }

    ProxyRoute effectiveRoute(String scheme,
                              String host,
                              int port,
                              boolean tls,
                              DnsResolver dnsResolver,
                              DnsAddressLookup dnsAddressLookup) {
        Objects.requireNonNull(scheme, "scheme");
        Objects.requireNonNull(host, "host");
        if (type == null || type == ProxyType.NONE) {
            return ProxyRoute.DIRECT;
        }
        if (type == ProxyType.HTTP) {
            if (isNoHosts(InetSocketAddress.createUnresolved(host, port))) {
                if (isIpV4(host) || isIpV6Host(host) || isIpV6Identifier(host)) {
                    return ProxyRoute.direct(this, scheme, host, port, tls, InetAddress.ofLiteral(resolveHost(host)));
                }
                return ProxyRoute.direct(this, scheme, host, port, tls);
            }
            if (ipNoProxyConfigured && dnsResolver != null) {
                InetAddress address = dnsResolver.resolveAddress(host,
                                                                 Objects.requireNonNull(dnsAddressLookup,
                                                                                        "dnsAddressLookup"));
                if (isNoHosts(new InetSocketAddress(address, port))) {
                    return ProxyRoute.direct(this, scheme, host, port, tls, address);
                }
            }
            ProxyRoute.Kind routeKind = forceHttpConnect || tls || username.isPresent()
                    ? ProxyRoute.Kind.HTTP_TUNNEL
                    : ProxyRoute.Kind.HTTP_FORWARD;
            return ProxyRoute.configuredHttp(this,
                                             routeKind,
                                             InetSocketAddress.createUnresolved(this.host, this.port),
                                             scheme,
                                             host,
                                             port,
                                             tls);
        }
        if (systemProxySelector == null) {
            return ProxyRoute.direct(this, scheme, host, port, tls);
        }
        UriAuthority authority = UriAuthority.create(UriHost.create(host), port);
        List<java.net.Proxy> selected = systemProxySelector.select(URI.create(scheme + "://" + authority));
        if (selected == null || selected.isEmpty()) {
            return ProxyRoute.direct(this, scheme, host, port, tls);
        }
        java.net.Proxy systemProxy = selected.get(0);
        if (systemProxy == null
                || systemProxy == java.net.Proxy.NO_PROXY
                || systemProxy.type() == java.net.Proxy.Type.DIRECT) {
            return ProxyRoute.direct(this, scheme, host, port, tls);
        }
        if (!(systemProxy.address() instanceof InetSocketAddress)) {
            throw new IllegalArgumentException("System proxy address must be an InetSocketAddress");
        }
        return switch (systemProxy.type()) {
            case HTTP -> ProxyRoute.system(this,
                                           ProxyRoute.Kind.HTTP_TUNNEL,
                                           systemProxy,
                                           scheme,
                                           host,
                                           port,
                                           tls);
            case SOCKS -> ProxyRoute.system(this,
                                            ProxyRoute.Kind.SOCKS,
                                            systemProxy,
                                            scheme,
                                            host,
                                            port,
                                            tls);
            case DIRECT -> ProxyRoute.direct(this, scheme, host, port, tls);
        };
    }

    /**
     * Creates an Optional with the InetSocketAddress of the server proxy for the specified uri.
     *
     * @param uri the uri
     * @return the InetSocketAddress
     */
    private Optional<InetSocketAddress> address(InetSocketAddress uri) {
        if (type == null || type == ProxyType.NONE || type == ProxyType.SYSTEM) {
            return Optional.empty();
        }

        if (isNoHosts(uri)) {
            return Optional.empty();
        }
        return Optional.of(new InetSocketAddress(host, port));
    }

    /**
     * Returns the port.
     *
     * @return proxy port
     */
    public int port() {
        return port;
    }

    /**
     * Returns the host.
     *
     * @return proxy host
     */
    public String host() {
        return host;
    }

    /**
     * Returns an Optional with the username.
     *
     * @return the username
     */
    public Optional<String> username() {
        return username;
    }

    /**
     * Returns an Optional with the password.
     *
     * @return the password
     */
    public Optional<char[]> password() {
        return password;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Proxy proxy = (Proxy) o;
        return port == proxy.port
                && type == proxy.type
                && forceHttpConnect == proxy.forceHttpConnect
                && Objects.equals(systemProxySelector, proxy.systemProxySelector)
                && Objects.equals(host, proxy.host)
                && Objects.equals(noProxy, proxy.noProxy)
                && Objects.equals(username, proxy.username)
                && Objects.equals(password, proxy.password);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, host, port, noProxy, username, password, forceHttpConnect);
    }

    private static Socket connect(Socket socket,
                                  Optional<InetSocketAddress> localAddress,
                                  InetSocketAddress destination,
                                  SocketOptions socketOptions) {
        try {
            socketOptions.configureSocket(socket);
            if (localAddress.isPresent()) {
                socket.bind(localAddress.get());
            }
            socket.connect(destination, (int) socketOptions.connectTimeout().toMillis());
            return socket;
        } catch (IOException e) {
            try {
                socket.close();
            } catch (IOException closeFailure) {
                e.addSuppressed(closeFailure);
            }
            throw new UncheckedIOException(e);
        } catch (RuntimeException | Error e) {
            try {
                socket.close();
            } catch (IOException closeFailure) {
                e.addSuppressed(closeFailure);
            }
            throw e;
        }
    }

    private static String resolveHost(String host) {
        if (host != null && isIpV6Identifier(host)) {
            return host.substring(1, host.length() - 1);
        }
        return host;
    }

    private static void prefixedMatch(List<BiFunction<String, Integer, Boolean>> matchers,
                                      String hostPart,
                                      Integer portPart) {
        if (null == portPart) {
            matchers.add((host, port) -> prefixHostMatch(hostPart, host));
        } else {
            matchers.add((host, port) -> portPart.equals(port) && prefixHostMatch(hostPart, host));
        }
    }

    private static boolean prefixHostMatch(String hostPart, String host) {
        if (host.endsWith(hostPart)) {
            return true;
        }
        return host.equals(hostPart.substring(1));
    }

    private static void exactMatch(List<BiFunction<String, Integer, Boolean>> matchers,
                                   String hostPart,
                                   Integer portPart) {
        if (null == portPart) {
            matchers.add((host, port) -> hostPart.equals(host));
        } else {
            matchers.add((host, port) -> portPart.equals(port) && hostPart.equals(host));
        }
    }

    private static boolean isIpV4(String host) {
        return IP_V4.matcher(host).matches();

    }

    private static boolean isIpV6Identifier(String host) {
        return IVP6_IDENTIFIER_MATCH_RESULTS.computeValue(host, () -> isIpV6IdentifierRegExp(host)).orElse(false);
    }

    private static Optional<Boolean> isIpV6IdentifierRegExp(String host) {
        return Optional.of(IP_V6_IDENTIFIER.matcher(host).matches() || IP_V6_HEX_IDENTIFIER.matcher(host).matches());
    }

    private static boolean isIpV6Host(String host) {
        return IVP6_HOST_MATCH_RESULTS.computeValue(host, () -> isIpV6HostRegExp(host)).orElse(false);
    }

    private static Optional<Boolean> isIpV6HostRegExp(String host) {
        return Optional.of(IP_V6_HOST.matcher(host).matches() || IP_V6_HEX_HOST.matcher(host).matches());
    }

    private static Socket connectToProxy(WebClient webClient,
                                         InetSocketAddress proxyAddress,
                                         InetSocketAddress targetAddress,
                                         Proxy proxy,
                                         boolean tls) {

        WebClientConfig clientConfig = webClient.prototype();
        TcpClientConnection connection = TcpClientConnection.create(webClient,
                                                                    ConnectionKey.create("http",
                                                                                         proxyAddress.getHostName(),
                                                                                         proxyAddress.getPort(),
                                                                                         NO_TLS,
                                                                                         clientConfig.dnsResolver(),
                                                                                         clientConfig.dnsAddressLookup(),
                                                                                         NO_PROXY),
                                                                    List.of(),
                                                                    it -> false,
                                                                    it -> {
                                                                    })
                .connect();
        if (proxy.forceHttpConnect || tls || proxy.username.isPresent()) {
            HttpClientRequest request = webClient.method(Method.CONNECT)
                    .followRedirects(false) // do not follow redirects for proxy connect itself
                    .connection(connection)
                    .uri("http://" + proxyAddress.getHostName() + ":" + proxyAddress.getPort())
                    .protocolId("http/1.1") // MUST be 1.1, if not available, proxy connection will fail
                    .header(HeaderNames.HOST, targetAddress.getHostName() + ":" + targetAddress.getPort())
                    .accept(MediaTypes.WILDCARD);
            if (clientConfig.keepAlive()) {
                request.header(HeaderValues.CONNECTION_KEEP_ALIVE)
                    .header(ClientRequestBase.PROXY_CONNECTION);
            }
            proxy.proxyAuthHeader.ifPresent(request::header);
            // we cannot close the response, as that would close the connection
            HttpClientResponse response = request.request();
            if (response.status().family() != Status.Family.SUCCESSFUL) {
                response.close();
                throw new IllegalStateException("Proxy sent wrong HTTP response code: " + response.status());
            }
        }
        return connection.socket();
    }

    private static Socket connectToProxy(WebClient webClient,
                                         ResolvedClientTarget target,
                                         Proxy proxy) {
        WebClientConfig clientConfig = webClient.prototype();
        InetSocketAddress configuredProxy = target.proxyRoute().proxyAddress().orElseThrow();
        ConnectionKey proxyConnectionKey = ConnectionKey.create("http",
                                                                configuredProxy.getHostString(),
                                                                configuredProxy.getPort(),
                                                                NO_TLS,
                                                                clientConfig.dnsResolver(),
                                                                clientConfig.dnsAddressLookup(),
                                                                NO_PROXY);
        UriAuthority proxyAuthority = UriAuthority.create(UriHost.create(proxyConnectionKey.host()),
                                                          configuredProxy.getPort());
        ProxyRoute directRoute = proxyConnectionKey.proxy()
                .effectiveRoute("http", configuredProxy.getHostString(), configuredProxy.getPort(), false);
        ClientConnectionTarget proxyTarget = target.localAddress()
                .map(localAddress -> ClientConnectionTarget.create(proxyConnectionKey,
                                                                   "http",
                                                                   proxyAuthority,
                                                                   directRoute,
                                                                   localAddress,
                                                                   proxyConnectionKey.tls().generation()))
                .orElseGet(() -> ClientConnectionTarget.create(proxyConnectionKey,
                                                               "http",
                                                               proxyAuthority,
                                                               directRoute,
                                                               proxyConnectionKey.tls().generation()));
        ResolvedClientTarget resolvedProxy = ResolvedClientTarget.direct(proxyTarget,
                                                                         proxyAuthority,
                                                                         target.peerAddress(),
                                                                         target.networkGeneration());
        TcpClientConnection connection = TcpClientConnection.create(webClient,
                                                                    resolvedProxy,
                                                                    List.of(),
                                                                    it -> false,
                                                                    it -> {
                                                                    })
                .connect();
        if (target.proxyRoute().kind() == ProxyRoute.Kind.HTTP_TUNNEL) {
            HttpClientRequest request = webClient.method(Method.CONNECT)
                    .followRedirects(false)
                    .connection(connection)
                    .uri("http://" + proxyAuthority)
                    .protocolId("http/1.1")
                    .header(HeaderNames.HOST, target.routeAuthority().toString())
                    .accept(MediaTypes.WILDCARD);
            if (clientConfig.keepAlive()) {
                request.header(HeaderValues.CONNECTION_KEEP_ALIVE)
                    .header(ClientRequestBase.PROXY_CONNECTION);
            }
            proxy.proxyAuthHeader.ifPresent(request::header);
            HttpClientResponse response = request.request();
            if (response.status().family() != Status.Family.SUCCESSFUL) {
                response.close();
                throw new IllegalStateException("Proxy sent wrong HTTP response code: " + response.status());
            }
        }
        return connection.socket();
    }

    /**
     * Type of the proxy.
     */
    public enum ProxyType {

        /**
         * No proxy.
         */
        NONE {
            @Override
            Socket connect(WebClient webClient,
                           Proxy proxy,
                           InetSocketAddress targetAddress,
                           SocketOptions socketOptions,
                           boolean tls) {
                try {
                    Socket socket = new Socket();
                    socketOptions.configureSocket(socket);
                    socket.connect(targetAddress, (int) socketOptions.connectTimeout().toMillis());
                    return socket;
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        },

        /**
         * Proxy obtained from system.
         */
        SYSTEM {
            @Override
            Socket connect(WebClient webClient,
                           Proxy proxy,
                           InetSocketAddress targetAddress,
                           SocketOptions socketOptions,
                           boolean tls) {
                String scheme = tls ? "https" : "http";
                if (proxy.systemProxySelector == null) {
                    return NONE.connect(webClient, proxy, targetAddress, socketOptions, tls);
                }
                List<java.net.Proxy> proxies = proxy.systemProxySelector
                        .select(URI.create(scheme + "://" + targetAddress.getHostName() + ":" + targetAddress.getPort()));
                if (proxies.isEmpty()) {
                    return NONE.connect(webClient, proxy, targetAddress, socketOptions, tls);
                }
                try {
                    Socket socket = new Socket(proxies.get(0));
                    socketOptions.configureSocket(socket);
                    socket.connect(targetAddress, (int) socketOptions.connectTimeout().toMillis());
                    return socket;
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        },

        /**
         * HTTP proxy.
         */
        HTTP {
            @Override
            Socket connect(WebClient webClient,
                           Proxy proxy,
                           InetSocketAddress targetAddress,
                           SocketOptions socketOptions,
                           boolean tls) {
                return proxy.address(targetAddress)
                        .map(proxyAddress -> connectToProxy(webClient,
                                                            proxyAddress,
                                                            targetAddress,
                                                            proxy,
                                                            tls))
                        .orElseGet(() -> NONE.connect(webClient, proxy, targetAddress, socketOptions, tls));
            }
        };

        abstract Socket connect(WebClient webClient,
                                Proxy proxy,
                                InetSocketAddress targetAddress,
                                SocketOptions socketOptions,
                                boolean tls);
    }

    /**
     * Fluent API builder for {@link Proxy}.
     */
    @Configured
    public static class Builder implements io.helidon.common.Builder<Builder, Proxy> {
        private final Set<String> noProxyHosts = new HashSet<>();

        // Defaults to system
        private ProxyType type = ProxyType.SYSTEM;
        private String host;
        private int port = 80;
        private String username;
        private char[] password;
        private boolean forceHttpConnect = false;

        private Builder() {
        }

        @Override
        public Proxy build() {
            return new Proxy(this);
        }

        /**
         * Configure a metric from configuration.
         * The following configuration key are used:
         * <table>
         * <caption>Client Metric configuration options</caption>
         * <tr>
         *     <th>key</th>
         *     <th>default</th>
         *     <th>description</th>
         * </tr>
         * <tr>
         *     <td>type</td>
         *     <td>{@code no default}</td>
         *     <td>Sets which type is this proxy. See {@link Proxy.ProxyType}</td>
         * </tr>
         * <tr>
         *     <td>host</td>
         *     <td>{@code no default}</td>
         *     <td>Host of the proxy</td>
         * </tr>
         * <tr>
         *     <td>port</td>
         *     <td>{@code 80}</td>
         *     <td>Port of the proxy</td>
         * </tr>
         * <tr>
         *     <td>username</td>
         *     <td>{@code no default}</td>
         *     <td>Proxy username</td>
         * </tr>
         * <tr>
         *     <td>password</td>
         *     <td>{@code no default}</td>
         *     <td>Proxy password</td>
         * </tr>
         * <tr>
         *     <td>httpConnect</td>
         *     <td>{@code false}</td>
         *     <td>Specify whether the HTTP client will always execute HTTP CONNECT.</td>
         * </tr>
         * <tr>
         *     <td>no-proxy</td>
         *     <td>{@code no default}</td>
         *     <td>Contains host or IP patterns which should be excluded from using proxy. IP patterns require local DNS
         *     resolution of host names and bind a direct route to the matching address.</td>
         * </tr>
         * </table>
         *
         * @param config configuration to configure this proxy
         * @return updated builder instance
         */
        public Builder config(Config config) {
            config.get("type").asString().map(ProxyType::valueOf).ifPresent(this::type);

            if (this.type != ProxyType.SYSTEM && this.type != ProxyType.NONE) {
                config.get("host").asString().ifPresent(this::host);
                config.get("port").asInt().ifPresent(this::port);
                config.get("username").asString().ifPresent(this::username);
                config.get("password").asString().map(String::toCharArray).ifPresent(this::password);
                config.get("no-proxy").asList(String.class).ifPresent(hosts -> hosts.forEach(this::addNoProxy));
            }
            return this;
        }

        /**
         * Sets a new proxy type.
         *
         * @param type proxy type
         * @return updated builder instance
         * @throws NullPointerException when type is null
         */
        @ConfiguredOption("HTTP")
        public Builder type(ProxyType type) {
            this.type = Objects.requireNonNull(type);
            return this;
        }

        /**
         * Forces HTTP CONNECT with the proxy server.
         * Otherwise it will not execute HTTP CONNECT when the request is
         * plain HTTP with no authentication.
         *
         * @param forceHttpConnect HTTP CONNECT
         * @return updated builder instance
         */
        @ConfiguredOption
        public Builder forceHttpConnect(boolean forceHttpConnect) {
            this.forceHttpConnect = forceHttpConnect;
            return this;
        }

        /**
         * Sets a new host value.
         *
         * @param host host
         * @return updated builder instance
         * @throws NullPointerException when host is null
         */
        @ConfiguredOption
        public Builder host(String host) {
            this.host = Objects.requireNonNull(host);
            return this;
        }

        /**
         * Sets a port value.
         *
         * @param port port
         * @return updated builder instance
         */
        @ConfiguredOption
        public Builder port(int port) {
            this.port = port;
            return this;
        }

        /**
         * Sets a new username for the proxy.
         *
         * @param username proxy username
         * @return updated builder instance
         */
        @ConfiguredOption
        public Builder username(String username) {
            this.username = username;
            return this;
        }

        /**
         * Sets a new password for the proxy.
         *
         * @param password proxy password
         * @return updated builder instance
         */
        @ConfiguredOption(type = String.class)
        public Builder password(char[] password) {
            this.password = Arrays.copyOf(password, password.length);
            return this;
        }

        /**
         * Configure a host pattern that is not going through a proxy.
         * <p>
         * Options are:
         * <ul>
         *     <li>IP Address, such as {@code 192.168.1.1}</li>
         *     <li>IP V6 Address, such as {@code [2001:db8:85a3:8d3:1319:8a2e:370:7348]}</li>
         *     <li>Hostname, such as {@code localhost}</li>
         *     <li>Domain name, such as {@code helidon.io}</li>
         *     <li>Domain name and all sub-domains, such as {@code .helidon.io} (leading dot)</li>
         *     <li>Combination of all options from above with a port, such as {@code .helidon.io:80}</li>
         * </ul>
         * An IP address pattern requires local DNS resolution of host-name targets. When the resolved address matches,
         * WebClient selects a direct route and retains that address for the connection attempt.
         *
         * @param noProxyHost to exclude from proxying
         * @return updated builder instance
         */
        @ConfiguredOption(key = "no-proxy", kind = ConfiguredOption.Kind.LIST)
        public Builder addNoProxy(String noProxyHost) {
            noProxyHosts.add(noProxyHost);
            return this;
        }

        ProxyType type() {
            return type;
        }

        boolean forceHttpConnect() {
            return forceHttpConnect;
        }

        String host() {
            return host;
        }

        int port() {
            return port;
        }

        Set<String> noProxyHosts() {
            return new HashSet<>(noProxyHosts);
        }

        Optional<String> username() {
            return Optional.ofNullable(username);
        }

        Optional<char[]> password() {
            return Optional.ofNullable(password);
        }
    }
}
