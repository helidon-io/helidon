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
package io.helidon.common.http;

import java.util.List;
import java.util.Optional;

import io.helidon.common.configurable.RequestedUriDiscoveryContext;
import io.helidon.common.uri.UriInfo;
import io.helidon.common.uri.UriQuery;

/**
 * Utility helper class for requested URI discovery.
 */
public class RequestedUriDiscovery {

    private RequestedUriDiscovery() {
    }

    /**
     * Creates a {@link io.helidon.common.uri.UriInfo} object for a request based on the discovery settings in the
     * {@link RequestedUriDiscoveryContext} and the specified request-related information.
     *
     * @param discoveryContext requested URI discovery settings
     * @param remoteAddress remote address from the request
     * @param localAddress local address from the request
     * @param requestPath path from the request
     * @param headers request headers
     * @param query query information from the request
     * @param isSecure whether the request is secure
     * @return {@code UriInfo} which reconstructs, as well as possible, the requested URI from the originating client
     */

    public static UriInfo uriInfo(RequestedUriDiscoveryContext discoveryContext,
                                  String remoteAddress,
                                  String localAddress,
                                  String requestPath,
                                  ServerRequestHeaders headers,
                                  UriQuery query,
                                  boolean isSecure) {
        String scheme = null;
        String authority = null;
        String host = null;
        int port = -1;
        String path = null;

        // Note: requestedUriDiscoveryEnabled() returns true if discovery is explicitly enabled or if either
        // requestedUriDiscoveryTypes or trustedProxies is set.
        if (discoveryContext.enabled()) {
            if (discoveryContext.trustedProxies().test(hostPart(remoteAddress))) {
                // Once we discover trusted information using one of the discovery discoveryTypes, we do not mix in
                // information from other discoveryTypes.

                nextDiscoveryType:
                for (var type : discoveryContext.discoveryTypes()) {
                    switch (type) {
                    case FORWARDED -> {
                        ForwardedDiscovery discovery = discoverUsingForwarded(discoveryContext, headers);
                        if (discovery != null) {
                            authority = discovery.authority();
                            scheme = discovery.scheme();

                            break nextDiscoveryType;
                        }
                    }
                    case X_FORWARDED -> {
                        XForwardedDiscovery discovery = discoverUsingXForwarded(discoveryContext, headers, requestPath);
                        if (discovery != null) {
                            scheme = discovery.scheme();
                            host = discovery.host();
                            port = discovery.port();
                            path = discovery.path();

                            break nextDiscoveryType;
                        }
                    }
                    default -> {
                        authority = headers.first(Http.Header.HOST).orElse(null);

                        break nextDiscoveryType;
                    }
                    }
                }
            }
        }

        // now we must fill values that were not discovered (to have a valid URI information)
        if (host == null && authority == null) {
            authority = headers.first(Http.Header.HOST).orElse(null);
        }

        if (path == null) {
            path = requestPath;
        }

        if (host == null && authority != null) {
            Authority a;
            if (scheme == null) {
                a = Authority.create(authority);
            } else {
                a = Authority.create(scheme, authority);
            }
            if (a.host() != null) {
                host = a.host();
            }
            if (port == -1) {
                port = a.port();
            }
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

        if (host == null) {
            host = localAddress;
        }

        // we may still have -1, if port was not explicitly defined by a header - use default port of protocol
        if (port == -1) {
            if ("https".equals(scheme)) {
                port = 443;
            } else {
                port = 80;
            }
        }
        if (query == null || query.isEmpty()) {
            query = null;
        }
        return new UriInfo(scheme, host, port, path, Optional.ofNullable(query));
    }

    private static String hostPart(String address) {
        int colon = address.indexOf(':');
        return colon == -1 ? address : address.substring(0, colon);
    }

    private static ForwardedDiscovery discoverUsingForwarded(RequestedUriDiscoveryContext discoveryContext,
                                                      ServerRequestHeaders headers) {
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
                if (f.forClient().isPresent() && !discoveryContext.trustedProxies().test(f.forClient().get())
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

    private static XForwardedDiscovery discoverUsingXForwarded(RequestedUriDiscoveryContext discoveryContext,
                                                        ServerRequestHeaders headers,
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

        List<String> xForwardedFors = headers.values(Http.Header.X_FORWARDED_FOR);
        boolean areProxiesTrusted = true;
        if (xForwardedFors.size() > 0) {
            // Intentionally skip the first X-Forwarded-For value. That is the originating client, and as such it
            // is not a proxy and we do not need to check its trustworthiness.
            for (int i = 1; i < xForwardedFors.size(); i++) {
                areProxiesTrusted &= discoveryContext.trustedProxies().test(xForwardedFors.get(i));
            }
        }
        if (areProxiesTrusted) {
            scheme = headers.first(Http.Header.X_FORWARDED_PROTO).orElse(null);
            host = headers.first(Http.Header.X_FORWARDED_HOST).orElse(null);
            port = headers.first(Http.Header.X_FORWARDED_PORT).map(Integer::parseInt).orElse(-1);
            path = headers.first(Http.Header.X_FORWARDED_PREFIX)
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

    private record Authority(String host, int port) {
        static Authority create(String hostHeader) {
            int colon = hostHeader.indexOf(':');
            if (colon == -1) {
                // we do not know the protocol, and there is no port defined
                return new Authority(hostHeader, -1);
            }
            String hostString = hostHeader.substring(0, colon);
            String portString = hostHeader.substring(colon + 1);
            return new Authority(hostString, Integer.parseInt(portString));
        }
        static Authority create(String scheme, String hostHeader) {
            int colon = hostHeader.indexOf(':');
            if (colon == -1) {
                // define port by protocol
                return new Authority(hostHeader, "https".equals(scheme) ? 443 : 80);
            }
            String hostString = hostHeader.substring(0, colon);
            String portString = hostHeader.substring(colon + 1);
            return new Authority(hostString, Integer.parseInt(portString));
        }
    }

    private record ForwardedDiscovery(String authority, String scheme) {}
    private record XForwardedDiscovery(String scheme, String host, int port, String path) {}

}
