/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.helidon.common.uri.UriInfo;
import io.helidon.common.uri.UriQuery;

/**
 * @deprecated Remove when
 *         {@link RequestedUriDiscoveryContext#uriInfo(String, String, String, ServerRequestHeaders,
 *         io.helidon.common.uri.UriQuery, boolean)} is removed.
 */
@Deprecated(forRemoval = true, since = "4.2.1")
final class UriInfoCompatibilityHelper {
    private static final Pattern ADDRESS_PATTERN = Pattern.compile("([^/]*)/(\\[[^]]+]|[^:]+|<unresolved>):?([0-9]*)");

    private UriInfoCompatibilityHelper() {
        //noop
    }

    static UriInfo uriInfo(
            RequestedUriDiscoveryContext ctx,
            String remoteAddress,
            String localAddress,
            String requestPath,
            ServerRequestHeaders headers,
            UriQuery query,
            boolean isSecure) {

        Matcher remoteMatcher = ADDRESS_PATTERN.matcher(remoteAddress);
        Matcher localMatcher = ADDRESS_PATTERN.matcher(localAddress);
        if (remoteMatcher.matches() && localMatcher.matches()) {
            String remotePort = remoteMatcher.group(3);
            String localPort = localMatcher.group(3);

            return ctx.uriInfo(InetSocketAddress.createUnresolved(remoteMatcher.group(1),
                                                                  remotePort.isEmpty() ? 0 : Integer.parseInt(remotePort)),
                               InetSocketAddress.createUnresolved(localMatcher.group(1),
                                                                  localPort.isEmpty() ? 0 : Integer.parseInt(remotePort)),
                               requestPath, headers, query, isSecure);
        }

        throw new IllegalArgumentException("Invalid remote: " + remoteAddress + " or local address: " + localAddress);
    }
}
