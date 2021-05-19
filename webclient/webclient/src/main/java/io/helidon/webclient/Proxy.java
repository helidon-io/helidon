/*
 * Copyright (c) 2020, 2021 Oracle and/or its affiliates.
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
package io.helidon.webclient;

import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.helidon.common.configurable.LruCache;
import io.helidon.config.Config;

import io.netty.channel.ChannelHandler;
import io.netty.handler.proxy.HttpProxyHandler;
import io.netty.handler.proxy.Socks4ProxyHandler;
import io.netty.handler.proxy.Socks5ProxyHandler;

/**
 * A definition of a proxy server to use for outgoing requests.
 */
public class Proxy {

    private static final Logger LOGGER = Logger.getLogger(Proxy.class.getName());

    /**
     * No proxy instance.
     */
    private static final Proxy NO_PROXY = new Proxy(builder().type(ProxyType.NONE));

    private static final Pattern PORT_PATTERN = Pattern.compile(".*:(\\d+)");
    private static final Pattern IP_V4 = Pattern.compile("^(25[0-5]|2[0-4]\\d|[0-1]?\\d?\\d)(\\."
                                                                 + "(25[0-5]|2[0-4]\\d|[0-1]?\\d?\\d)){3}$");
    private static final Pattern IP_V6_IDENTIFIER = Pattern.compile("^\\[(?:[0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}]$");
    private static final Pattern IP_V6_HEX_IDENTIFIER = Pattern
            .compile("^\\[((?:[0-9A-Fa-f]{1,4}(?::[0-9A-Fa-f]{1,4})*)?)::((?:[0-9A-Fa-f]{1,4}(?::[0-9A-Fa-f]{1,4})*)?)]$");
    private static final Pattern IP_V6_HOST = Pattern.compile("^(?:[0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}$");
    private static final Pattern IP_V6_HEX_HOST = Pattern
            .compile("^((?:[0-9A-Fa-f]{1,4}(?::[0-9A-Fa-f]{1,4})*)?)::((?:[0-9A-Fa-f]{1,4}(?::[0-9A-Fa-f]{1,4})*)?)$");

    private static final LruCache<String, Boolean> IVP6_HOST_MATCH_RESULTS = LruCache.create();
    private static final LruCache<String, Boolean> IVP6_IDENTIFIER_MATCH_RESULTS = LruCache.create();

    private final ProxyType type;
    private final String host;
    private final int port;
    private final Function<URI, Boolean> noProxy;
    private final Optional<String> username;
    private final Optional<char[]> password;
    private final ProxySelector systemSelector;
    private final boolean useSystemSelector;

    private Proxy(Proxy.Builder builder) {
        this.type = builder.type();
        this.systemSelector = builder.systemSelector();
        this.host = builder.host();
        this.useSystemSelector = ((null == host) && (null != systemSelector));

        this.port = builder.port();
        this.username = builder.username();
        this.password = builder.password();

        if (useSystemSelector) {
            this.noProxy = inetSocketAddress -> true;
        } else {
            this.noProxy = prepareNoProxy(builder.noProxyHosts());
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
        return builder()
                .useSystemSelector(true)
                .build();
    }

    static Function<URI, Boolean> prepareNoProxy(Set<String> noProxyHosts) {
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
            return address -> noProxyHosts.contains(address.getHost()) || noProxyHosts
                    .contains(address.getHost() + ":" + address.getPort());
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
            String host = resolveHost(address.getHost());
            int port = address.getPort();

            // first need to make sure whether I have an IP address or a hostname
            if (isIpV4(host) || isIpV6Host(host)) {
                // we have an IP address
                for (BiFunction<String, Integer, Boolean> ipMatcher : ipMatchers) {
                    if (ipMatcher.apply(host, port)) {
                        LOGGER.finest(() -> "IP Address " + host + " bypasses proxy");
                        return true;
                    }
                }
                LOGGER.finest(() -> "IP Address " + host + " uses proxy");
            } else {
                // we have a host name
                for (BiFunction<String, Integer, Boolean> hostMatcher : hostMatchers) {
                    if (hostMatcher.apply(host, port)) {
                        LOGGER.finest(() -> "Host " + host + " bypasses proxy");
                        return true;
                    }
                }
                LOGGER.finest(() -> "Host " + host + " uses proxy");
            }

            return false;
        };
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

    /**
     * Creates new proxy channel handler.
     *
     * @param address request address
     * @return channel handler
     */
    public Optional<ChannelHandler> handler(URI address) {
        if (type == ProxyType.NONE) {
            return Optional.empty();
        }

        if (useSystemSelector) {
            return systemSelectorHandler(address);
        }

        if (noProxy.apply(address)) {
            return Optional.empty();
        }

        return Optional.of(handler());
    }

    private Optional<ChannelHandler> systemSelectorHandler(URI address) {
        // this is hardcoded to http protocol and no path - a bit limited use case, though better than none
        List<java.net.Proxy> selected = systemSelector
                .select(URI.create("http://" + address.getHost() + ":" + address.getPort()));

        if (selected.isEmpty()) {
            return Optional.empty();
        }

        java.net.Proxy systemProxy = selected.iterator().next();

        switch (systemProxy.type()) {
        case DIRECT:
            return Optional.empty();
        case HTTP:
            return Optional.of(httpProxy(systemProxy));
        case SOCKS:
            return Optional.of(socksProxy(systemProxy));
        default:
            throw new IllegalStateException("Unexpected proxy type: " + systemProxy.type());
        }
    }

    private ChannelHandler handler() {
        switch (type) {
        case HTTP:
            return httpProxy();
        case SOCKS_4:
            return socks4Proxy();
        case SOCKS_5:
            return socks5Proxy();
        default:
            throw new IllegalArgumentException("Unsupported proxy type: " + type);
        }
    }

    private ChannelHandler socks5Proxy() {
        return username.map(s -> new Socks5ProxyHandler(address(), s, password.map(String::new).orElse("")))
                .orElseGet(() -> new Socks5ProxyHandler(address()));
    }

    private ChannelHandler socks4Proxy() {
        return username.map(s -> new Socks4ProxyHandler(address(), s))
                .orElseGet(() -> new Socks4ProxyHandler(address()));
    }

    private ChannelHandler httpProxy() {
        return username.map(s -> new HttpProxyHandler(address(), s, password.map(String::new).orElse("")))
                .orElseGet(() -> new HttpProxyHandler(address()));

    }

    private ChannelHandler httpProxy(java.net.Proxy systemProxy) {
        return new HttpProxyHandler(systemProxy.address());
    }

    private ChannelHandler socksProxy(java.net.Proxy systemProxy) {
        return new Socks5ProxyHandler(systemProxy.address());
    }

    private InetSocketAddress address() {
        return new InetSocketAddress(host, port);
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
                && useSystemSelector == proxy.useSystemSelector
                && type == proxy.type
                && Objects.equals(host, proxy.host)
                && Objects.equals(noProxy, proxy.noProxy)
                && Objects.equals(username, proxy.username)
                && Objects.equals(password, proxy.password)
                && Objects.equals(systemSelector, proxy.systemSelector);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, host, port, noProxy, username, password, systemSelector, useSystemSelector);
    }

    /**
     * Fluent API builder for {@link Proxy}.
     */
    public static class Builder implements io.helidon.common.Builder<Proxy> {
        private final Set<String> noProxyHosts = new HashSet<>();

        private ProxyType type;
        private String host;
        private int port = 80;
        private String username;
        private char[] password;
        private ProxySelector systemSelector;

        private Builder() {
        }

        @Override
        public Proxy build() {
            if ((null == host) || (host.isEmpty() && (null == systemSelector))) {
                return NO_PROXY;
            }
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
         *     <td>use-system-selector</td>
         *     <td>{@code false}</td>
         *     <td>Whether system proxy selector should be used</td>
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
         *     <td>no-proxy</td>
         *     <td>{@code no default}</td>
         *     <td>Contains list of the hosts which should be excluded from using proxy</td>
         * </tr>
         * </table>
         *
         * @param config configuration to configure this proxy
         * @return updated builder instance
         */
        public Builder config(Config config) {
            config.get("use-system-selector").asBoolean().ifPresent(this::useSystemSelector);
            if (this.type != ProxyType.SYSTEM) {
                config.get("type").asString().map(ProxyType::valueOf).ifPresent(this::type);
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
         */
        public Builder type(ProxyType type) {
            this.type = type;
            return this;
        }

        /**
         * Sets a new host value.
         *
         * @param host host
         * @return updated builder instance
         */
        public Builder host(String host) {
            this.host = host;
            return this;
        }

        /**
         * Sets a port value.
         *
         * @param port port
         * @return updated builder instance
         */
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
         *
         * @param noProxyHost to exclude from proxying
         * @return updated builder instance
         */
        public Builder addNoProxy(String noProxyHost) {
            noProxyHosts.add(noProxyHost);
            return this;
        }

        /**
         * Configure proxy from environment variables and system properties.
         *
         * @param useIt use system selector
         * @return updated builder instance
         */
        public Builder useSystemSelector(boolean useIt) {
            if (useIt) {
                this.type = ProxyType.SYSTEM;
                this.systemSelector = ProxySelector.getDefault();
            } else {
                if (this.type == ProxyType.SYSTEM) {
                    this.type = ProxyType.NONE;
                }
                this.systemSelector = null;
            }

            return this;
        }

        ProxyType type() {
            return type;
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

        ProxySelector systemSelector() {
            return systemSelector;
        }
    }

    /**
     * Type of the proxy.
     */
    public enum ProxyType {

        /**
         * No proxy.
         */
        NONE,

        /**
         * Proxy obtained from system.
         */
        SYSTEM,

        /**
         * HTTP proxy.
         */
        HTTP,

        /**
         * SOCKS V4 proxy.
         */
        SOCKS_4,

        /**
         * SOCKS V5 proxy.
         */
        SOCKS_5
    }

}
