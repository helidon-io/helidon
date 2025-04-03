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
package io.helidon.http;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.helidon.common.config.Config;
import io.helidon.common.configurable.AllowList;
import io.helidon.common.uri.UriInfo;
import io.helidon.common.uri.UriQuery;
import io.helidon.config.metadata.Configured;
import io.helidon.config.metadata.ConfiguredOption;

/**
 * Requested URI discovery settings for a socket.
 */
public interface RequestedUriDiscoveryContext {

    /**
     * Creates a new builder for a {@code RequestedUriDiscoveryContext}.
     *
     * @return new builder
     */
    static Builder builder() {
        return new Builder();
    }

    /**
     * Creates a new builder for a {@code RequestedUriDiscoveryContext} using the provide discovery context config node.
     *
     * @param config discovery context config node
     * @return new builder
     */
    static Builder builder(Config config) {
        return builder().config(config);
    }

    /**
     * Creates a new {@code RequestedUriDiscoveryContext} from the provided discovery context config node.
     *
     * @param config node for the discovery context
     * @return new discovery context instance
     */
    static RequestedUriDiscoveryContext create(Config config) {
        return builder().config(config).build();
    }

    /**
     * Creates a {@link io.helidon.common.uri.UriInfo} object for a request based on the discovery settings in the
     * {@link RequestedUriDiscoveryContext} and the specified request-related information.
     *
     * @param remoteAddress remote address from the request
     * @param localAddress local address from the request
     * @param requestPath path from the request
     * @param headers request headers
     * @param query query information from the request
     * @param isSecure whether the request is secure
     * @return {@code UriInfo} which reconstructs, as well as possible, the requested URI from the originating client
     * @deprecated Use {@link RequestedUriDiscoveryContext#uriInfo(java.net.InetSocketAddress, java.net.InetSocketAddress, String, ServerRequestHeaders, io.helidon.common.uri.UriQuery, boolean)}
     */
    @Deprecated(since = "4.2.0")
    default UriInfo uriInfo(String remoteAddress,
                            String localAddress,
                            String requestPath,
                            ServerRequestHeaders headers,
                            UriQuery query,
                            boolean isSecure) {

        Pattern p = Pattern.compile("([^/]*)/(\\[[^]]+\\]|[^:]+|<unresolved>):?([0-9]*)");
        Matcher remoteMatcher = p.matcher(remoteAddress);
        Matcher localMatcher = p.matcher(localAddress);
        if (remoteMatcher.matches() && localMatcher.matches()) {
            String remotePort = remoteMatcher.group(3);
            String localPort = localMatcher.group(3);

            return uriInfo(InetSocketAddress.createUnresolved(remoteMatcher.group(1),
                                                              remotePort.isEmpty() ? 0 : Integer.parseInt(remotePort)),
                           InetSocketAddress.createUnresolved(localMatcher.group(1),
                                                              localPort.isEmpty() ? 0 : Integer.parseInt(remotePort)),
                           requestPath, headers, query, isSecure);
        }

        throw new IllegalArgumentException("Invalid remote: " + remoteAddress + " or local address: " + localAddress);
    }

    /**
     * Creates a {@link io.helidon.common.uri.UriInfo} object for a request based on the discovery settings in the
     * {@link RequestedUriDiscoveryContext} and the specified request-related information.
     *
     * @param remoteAddress remote address from the request
     * @param localAddress local address from the request
     * @param requestPath path from the request
     * @param headers request headers
     * @param query query information from the request
     * @param isSecure whether the request is secure
     * @return {@code UriInfo} which reconstructs, as well as possible, the requested URI from the originating client
     */
    UriInfo uriInfo(InetSocketAddress remoteAddress,
                    InetSocketAddress localAddress,
                    String requestPath,
                    ServerRequestHeaders headers,
                    UriQuery query,
                    boolean isSecure);

    /**
     * Builder for {@link RequestedUriDiscoveryContext}.
     */
    @Configured
    final class Builder implements io.helidon.common.Builder<RequestedUriDiscoveryContext.Builder, RequestedUriDiscoveryContext> {

        /**
         * Config key prefix for requested URI discovery settings.
         */
        public static final String REQUESTED_URI_DISCOVERY_CONFIG_KEY = "requested-uri-discovery";

        private static final System.Logger LOGGER = System.getLogger(Builder.class.getName());
        private Boolean enabled;
        private final List<RequestedUriDiscoveryType> discoveryTypes = new ArrayList<>();
        private AllowList trustedProxies;
        private String socketId = "@default";

        private Builder() {
        }

        @Override
        public RequestedUriDiscoveryContext build() {
            prepareAndCheckRequestedUriSettings();
            return new RequestedUriDiscoveryContextImpl(this);
        }

        /**
         * Update the settings from the {@value REQUESTED_URI_DISCOVERY_CONFIG_KEY}
         * {@link io.helidon.common.config.Config} node within the socket configuration.
         *
         * @param requestedUriDiscoveryConfig requested URI discovery configuration node
         * @return updated builder instance
         */
        public Builder config(Config requestedUriDiscoveryConfig) {
            requestedUriDiscoveryConfig.get("enabled")
                    .as(Boolean.class)
                    .ifPresent(this::enabled);
            // TODO - discoveryTypes as a key was never documented but was hand-coded this way. Keep for compatibility
            // in case existing apps happen to use it. Remove as soon as practical.
            requestedUriDiscoveryConfig.get("discoveryTypes")
                    .asList(RequestedUriDiscoveryType.class)
                    .ifPresent(this::discoveryTypes);
            requestedUriDiscoveryConfig.get("types")
                    .asList(RequestedUriDiscoveryType.class)
                    .ifPresent(this::types);
            requestedUriDiscoveryConfig.get("trusted-proxies")
                    .map(AllowList::create)
                    .ifPresent(this::trustedProxies);
            return this;
        }

        /**
         * Sets whether requested URI discovery is enabled for requestes arriving on the socket.
         *
         * @param value new enabled state
         * @return updated builder
         */
        @ConfiguredOption(value = "true if 'types' or 'trusted-proxies' is set; false otherwise")
        public Builder enabled(boolean value) {
            enabled = value;
            return this;
        }

        /**
         * Sets the trusted proxies for requested URI discovery for requests arriving on the socket.
         *
         * @param trustedProxies the {@link io.helidon.common.configurable.AllowList} represented trusted proxies
         * @return updated builder
         */
        @ConfiguredOption
        public Builder trustedProxies(AllowList trustedProxies) {
            this.trustedProxies = trustedProxies;
            return this;
        }

        /**
         * Sets the discovery types for requested URI discovery for requests arriving on the socket.
         *
         * @param discoveryTypes discovery types to use
         * @return updated builder
         */
        @ConfiguredOption()
        public Builder types(List<RequestedUriDiscoveryType> discoveryTypes) {
            this.discoveryTypes.clear();
            this.discoveryTypes.addAll(discoveryTypes);
            return this;
        }

        /**
         * Sets the discovery types for requested URI discovery for requests arriving on the socket.
         *
         * @param discoveryTypes discovery types to use
         * @return updated builder
         * @deprecated Use {@link #types(java.util.List)} instead
         */
        @Deprecated(since = "4.0.6", forRemoval = true)
        public Builder discoveryTypes(List<RequestedUriDiscoveryType> discoveryTypes) {
            return types(discoveryTypes);
        }

        /**
         * Adds a discovery type for requested URI discovery for requests arriving on the socket.
         *
         * @param discoveryType the {@link RequestedUriDiscoveryContext.RequestedUriDiscoveryType} to add
         * @return updated builder
         */
        public Builder addDiscoveryType(RequestedUriDiscoveryType discoveryType) {
            discoveryTypes.add(discoveryType);
            return this;
        }

        /**
         * Sets the socket identifier to which the discovery context applies.
         *
         * @param socketId socket identifier (used in logging)
         * @return updated builder
         */
        public Builder socketId(String socketId) {
            this.socketId = socketId;
            return this;
        }

        /**
         * Checks validity of requested URI settings and supplies defaults for omitted settings.
         * <p>The behavior of `requested-uri-discovery` config or builder settings can be summarized as follows:
         *     <ul>
         *     <li>The `requested-uri-discovery` settings are optional.</li>
         *     <li>If `requested-uri-discovery` is absent or is present with `enabled` explicitly set to `false`, Helidon
         *     ignores any {@code Forwarded} or {@code X-Forwarded-*} headers and adopts the
         *     {@code HOST} discovery type. That is, Helidon uses the {@code Host} header for the host
         *     and the request's scheme and port.</li>
         *     <li>If `requested-uri-discovery` is present and enabled, either because {@code enabled} is set to {@code true}
         *     or {@code discoveryTypes} or {@code trusted-proxies} has been set, then Helidon performs a simple validity
         *     check before adopting the selected discovery behavior: If {@code discoveryTypes} is specified and includes
         *     either {@code FORWARDED} or {@code X_FORWARDED} then {@code trusted-proxies} must also be set to at least
         *     one value. Put another way, if requested URI discovery is enabled then {@code trusted-proxies} can be unspecified
         *     only if {@code discoveryTypes} contains only {@code HOST}.</li>
         *     </ul>
         * </p>
         */
        private void prepareAndCheckRequestedUriSettings() {
            boolean isDiscoveryEnabledDefaulted = (enabled == null);
            if (enabled == null) {
                enabled = !discoveryTypes.isEmpty() || trustedProxies != null;
            }

            boolean areDiscoveryTypesDefaulted = false;

            if (enabled) {
                // Configure a default type if discovery is enabled and no explicit discoveryTypes are configured.
                if (this.discoveryTypes.isEmpty()) {
                    areDiscoveryTypesDefaulted = true;
                    this.discoveryTypes.add(RequestedUriDiscoveryType.FORWARDED);
                }

                // Require _some_ settings for trusted proxies (except for HOST discovery) so the socket does not start unsafely
                // by accident. The user _can_ set allow.all to run the socket unsafely but at least that way it was
                // an explicit choice.
                if (trustedProxies == null && !isDiscoveryTypesOnlyHost()) {
                    throw new UnsafeRequestedUriSettingsException(this, areDiscoveryTypesDefaulted);
                }
            } else {
                // Discovery is disabled so ignore any explicit settings of discovery type and use HOST discovery.
                if (!discoveryTypes.isEmpty()) {
                    LOGGER.log(System.Logger.Level.INFO, """
                            Ignoring explicit settings of requested-uri-discovery types and trusted-proxies because
                            requested-uri-discovery.enabled {0} to false
                            """, isDiscoveryEnabledDefaulted ? " defaulted" : "was set");
                }
                discoveryTypes.clear();
                discoveryTypes.add(RequestedUriDiscoveryType.HOST);
            }
            if (trustedProxies == null) {
                trustedProxies = AllowList.builder()
                        .addDenied(s -> true)
                        .build();
            }
        }

        private boolean isDiscoveryTypesOnlyHost() {
            return discoveryTypes.size() == 1
                    && discoveryTypes.contains(RequestedUriDiscoveryType.HOST);
        }

        private static class RequestedUriDiscoveryContextImpl implements RequestedUriDiscoveryContext {

            private final boolean enabled;
            private final List<RequestedUriDiscoveryType> discoveryTypes;
            private final AllowList trustedProxies;

            private RequestedUriDiscoveryContextImpl(RequestedUriDiscoveryContext.Builder builder) {
                this.enabled = builder.enabled;
                this.discoveryTypes = builder.discoveryTypes;
                this.trustedProxies = builder.trustedProxies;
            }

            @Override
            public UriInfo uriInfo(InetSocketAddress remoteAddress,
                                   InetSocketAddress localAddress,
                                   String requestPath,
                                   ServerRequestHeaders headers,
                                   UriQuery query,
                                   boolean isSecure) {
                String scheme = null;
                String authority = null;
                String host = null;
                int port = -1;
                String path = null;

                // Note: enabled() returns true if discovery is explicitly enabled or if either
                // requestedUriDiscoveryTypes or trustedProxies is set.
                if (enabled) {
                    // Host with ip address fallback or ip address only
                    if (trustedProxies.test(remoteAddress.getHostString())
                            || (remoteAddress.getAddress() != null
                                    && trustedProxies.test(remoteAddress.getAddress().getHostAddress()))) {

                        // Once we discover trusted information using one of the discovery discoveryTypes, we do not mix in
                        // information from other discoveryTypes.

                        nextDiscoveryType:
                        for (var type : discoveryTypes) {
                            switch (type) {
                            case FORWARDED -> {
                                ForwardedDiscovery discovery = discoverUsingForwarded(headers);
                                if (discovery != null) {
                                    authority = discovery.authority();
                                    scheme = discovery.scheme();

                                    break nextDiscoveryType;
                                }
                            }
                            case X_FORWARDED -> {
                                XForwardedDiscovery discovery = discoverUsingXForwarded(headers, requestPath);
                                if (discovery != null) {
                                    scheme = discovery.scheme();
                                    host = discovery.host();
                                    port = discovery.port();
                                    path = discovery.path();

                                    break nextDiscoveryType;
                                }
                            }
                            case HOST -> {
                                authority = headers.first(HeaderNames.HOST).orElse(null);
                                break nextDiscoveryType;
                            }

                            default -> {
                                authority = headers.first(HeaderNames.HOST).orElse(null);
                                break nextDiscoveryType;
                            }
                            }
                        }
                    }
                }

                UriInfo.Builder uriInfo = UriInfo.builder();

                // now we must fill values that were not discovered (to have a valid URI information)
                if (host == null && authority == null) {
                    authority = headers.first(HeaderNames.HOST).orElse(null);
                }

                uriInfo.path(path == null ? requestPath : path);
                uriInfo.host(localAddress.getHostString()); // this is the fallback
                if (authority != null) {
                    uriInfo.authority(authority); // this is the second possibility
                }
                if (host != null) {
                    uriInfo.host(host); // and this one has priority
                }
                if (port != -1) {
                    uriInfo.port(port);
                }

                /*
                Discover final values to be used
                */
                if (scheme == null) {
                    if (port == 80) {
                        scheme = "http";
                    } else if (port == 443) {
                        scheme = "https";
                    } else {
                        scheme = isSecure ? "https" : "http";
                    }
                }
                uriInfo.scheme(scheme);
                uriInfo.query(query);

                return uriInfo.build();
            }

            private ForwardedDiscovery discoverUsingForwarded(ServerRequestHeaders headers) {
                String scheme = null;
                String authority = null;
                List<Forwarded> forwardedList = Forwarded.create(headers);
                if (!forwardedList.isEmpty()) {
                    for (int i = forwardedList.size() - 1; i >= 0; i--) {
                        Forwarded f = forwardedList.get(i);

                        // Because we remained in the loop, the Forwarded entry we are looking at is trustworthy.
                        if (scheme == null && f.proto().isPresent()) {
                            scheme = f.proto().get();
                        }
                        if (authority == null && f.host().isPresent()) {
                            authority = f.host().get();
                        }
                        if (f.forClient().isPresent() && !trustedProxies.test(f.forClient().get())
                                || scheme != null && authority != null) {
                            // This is the first Forwarded entry we've found for which the "for" value is untrusted (and
                            // therefore the proxy which created this Forwarded entry is the most remote trusted one)
                            //   OR
                            // we have already harvested the values we need from trusted proxies.
                            // Either way, we do not need to look at further Forwarded entries.
                            break;
                        }
                    }
                }
                return authority != null ? new ForwardedDiscovery(authority, scheme) : null;
            }

            private XForwardedDiscovery discoverUsingXForwarded(ServerRequestHeaders headers,
                                                                String requestPath) {
                // With X-Forwarded-* headers, the X-Forwarded-Host and X-Forwarded-Proto headers appear only once, indicating
                // the host and protocol supposedly requested by the original client as seen by the proxy which received the
                // original request. To trust those single values, we need to trust all the X-Forwarded-For instances except
                // the very first one (the original client itself).
                boolean discovered = false;
                String scheme = null;
                String host = null;
                int port = -1;
                String path = null;

                List<String> xForwardedFors = headers.values(HeaderNames.X_FORWARDED_FOR);
                boolean areProxiesTrusted = true;
                if (!xForwardedFors.isEmpty()) {
                    // Intentionally skip the first X-Forwarded-For value. That is the originating client, and as such it
                    // is not a proxy and we do not need to check its trustworthiness.
                    for (int i = 1; i < xForwardedFors.size(); i++) {
                        areProxiesTrusted &= trustedProxies.test(xForwardedFors.get(i));
                    }
                }
                if (areProxiesTrusted) {
                    scheme = headers.first(HeaderNames.X_FORWARDED_PROTO).orElse(null);
                    host = headers.first(HeaderNames.X_FORWARDED_HOST).orElse(null);
                    port = headers.first(HeaderNames.X_FORWARDED_PORT).map(Integer::parseInt).orElse(-1);
                    path = headers.first(HeaderNames.X_FORWARDED_PREFIX)
                            .map(prefix -> {
                                String absolute = requestPath;
                                return prefix + (absolute.startsWith("/") ? "" : "/") + absolute;
                            })
                            .orElse(null);
                    // at least one header was present
                    discovered = scheme != null || host != null || port != -1 || path != null;
                }
                return discovered ? new XForwardedDiscovery(scheme, host, port, path) : null;
            }

            private record ForwardedDiscovery(String authority, String scheme) {}
            private record XForwardedDiscovery(String scheme, String host, int port, String path) {}

        }
    }

    /**
     * Types of discovery of frontend URI. Defaults to {@link #HOST} when frontend URI discovery is disabled (uses only Host
     * header and information about current request to determine scheme, host, port, and path).
     * Defaults to {@link #FORWARDED} when discovery is enabled. Can be explicitly configured on socket configuration builder.
     */
    enum RequestedUriDiscoveryType {
        /**
         * The {@code io.helidon.http.Header#FORWARDED} header is used to discover the original requested URI.
         */
        FORWARDED,
        /**
         * The
         * {@code io.helidon.http.Header#X_FORWARDED_PROTO},
         * {@code io.helidon.http.Header#X_FORWARDED_HOST},
         * {@code io.helidon.http.Header#X_FORWARDED_PORT},
         * {@code io.helidon.http.Header#X_FORWARDED_PREFIX}
         * headers are used to discover the original requested URI.
         */
        X_FORWARDED,
        /**
         * This is the default, only the {@code io.helidon.http.Header#HOST} header is used to discover
         * requested URI.
         */
        HOST
    }

    /**
     * Indicates unsafe settings for a socket's request URI discovery.
     * <p>
     *     This exception typically results when the user has enabled requested URI discovery or selected a discovery type
     *     but has not assigned the trusted proxy {@link AllowList}.
     * </p>
     */
    class UnsafeRequestedUriSettingsException extends RuntimeException {

        /**
         * Creates a new exception instance.
         *
         * @param requestedUriDiscoveryContextBuilder builder for the socket config
         * @param areDiscoveryTypesDefaulted whether discovery discoveryTypes were defaulted (as opposed to set explicitly)
         */
        UnsafeRequestedUriSettingsException(RequestedUriDiscoveryContext.Builder requestedUriDiscoveryContextBuilder,
                                            boolean areDiscoveryTypesDefaulted) {

            super(String.format("""
                Settings which control requested URI discovery for socket %s are unsafe: \
                discovery is enabled with types %s to %s but no trusted proxies were set to protect against forgery of headers. \
                Server start-up will not continue. \
                Please prepare the trusted-proxies allow-list for this socket using 'allow' and/or 'deny' settings. \
                If you choose to start unsafely (not recommended), set trusted-proxies.allow.all to 'true'. \
                """,
                                requestedUriDiscoveryContextBuilder.socketId,
                                areDiscoveryTypesDefaulted ? "defaulted" : "set",
                                requestedUriDiscoveryContextBuilder.discoveryTypes));
        }
    }
}
