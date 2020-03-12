/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.netty.channel.ChannelHandler;
import io.netty.handler.proxy.HttpProxyHandler;
import io.netty.handler.proxy.Socks4ProxyHandler;
import io.netty.handler.proxy.Socks5ProxyHandler;

import static io.helidon.webclient.Proxy.ProxyType.NONE;

/**
 * Proxy implementation.
 */
class ProxyImpl implements Proxy {
    private static final Logger LOGGER = Logger.getLogger(ProxyImpl.class.getName());
    private static final Pattern PORT_PATTERN = Pattern.compile(".*:(\\d+)");
    private static final Pattern IP_V4 = Pattern.compile("^(25[0-5]|2[0-4]\\d|[0-1]?\\d?\\d)(\\."
                                                                 + "(25[0-5]|2[0-4]\\d|[0-1]?\\d?\\d)){3}$");
    private static final Pattern IP_V6_IDENTIFIER = Pattern.compile("^\\[(?:[0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}]$");
    private static final Pattern IP_V6_HEX_IDENTIFIER = Pattern
            .compile("^\\[((?:[0-9A-Fa-f]{1,4}(?::[0-9A-Fa-f]{1,4})*)?)::((?:[0-9A-Fa-f]{1,4}(?::[0-9A-Fa-f]{1,4})*)?)]$");
    private static final Pattern IP_V6_HOST = Pattern.compile("^(?:[0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}$");
    private static final Pattern IP_V6_HEX_HOST = Pattern
            .compile("^((?:[0-9A-Fa-f]{1,4}(?::[0-9A-Fa-f]{1,4})*)?)::((?:[0-9A-Fa-f]{1,4}(?::[0-9A-Fa-f]{1,4})*)?)$");
    private final ProxyType type;
    private final String host;
    private final int port;
    private final Function<URI, Boolean> noProxy;
    private final Optional<String> username;
    private final Optional<char[]> password;
    private final ProxySelector systemSelector;
    private final boolean useSystemSelector;

    ProxyImpl(Proxy.Builder builder) {
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

    @Override
    public Optional<ChannelHandler> handler(URI address) {
        if (type == NONE) {
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
            String host = address.getHost();
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
        return IP_V6_IDENTIFIER.matcher(host).matches()
                || IP_V6_HEX_IDENTIFIER.matcher(host).matches();
    }

    private static boolean isIpV6Host(String host) {
        return IP_V6_HOST.matcher(host).matches()
                || IP_V6_HEX_HOST.matcher(host).matches();
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
        if (username.isPresent()) {
            return new Socks5ProxyHandler(address(), username.get(), password.map(String::new).orElse(""));
        }
        return new Socks5ProxyHandler(address());
    }

    private ChannelHandler socks4Proxy() {
        if (username.isPresent()) {
            return new Socks4ProxyHandler(address(), username.get());
        }
        return new Socks4ProxyHandler(address());
    }

    private ChannelHandler httpProxy() {
        if (username.isPresent()) {
            return new HttpProxyHandler(address(), username.get(), password.map(String::new).orElse(""));
        }

        return new HttpProxyHandler(address());
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
}
