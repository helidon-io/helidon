/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.webclient.http2;

import java.net.SocketAddress;
import java.net.UnixDomainSocketAddress;
import java.util.Optional;

import io.helidon.common.tls.Tls;
import io.helidon.webclient.api.ClientUri;
import io.helidon.webclient.api.ConnectionKey;
import io.helidon.webclient.api.FullClientRequest;
import io.helidon.webclient.api.HttpClientConfig;
import io.helidon.webclient.api.Proxy;

final class Http2ConnectionKeys {
    private static final Tls NO_TLS = Tls.builder().enabled(false).build();

    private Http2ConnectionKeys() {
    }

    static ConnectionKey create(ClientUri uri, FullClientRequest<?> request, HttpClientConfig clientConfig) {
        return create(uri, request.address(), request.tls(), request.proxy(), clientConfig);
    }

    static ConnectionKey create(ClientUri uri,
                                Optional<SocketAddress> address,
                                Tls tls,
                                Proxy proxy,
                                HttpClientConfig clientConfig) {
        Tls effectiveTls = "https".equals(uri.scheme()) ? tls : NO_TLS;
        return address
                .filter(UnixDomainSocketAddress.class::isInstance)
                .map(UnixDomainSocketAddress.class::cast)
                .map(udsAddress -> ConnectionKey.createUnixDomainSocket(uri.scheme(),
                                                                         uri.host(),
                                                                         uri.port(),
                                                                         effectiveTls,
                                                                         clientConfig.dnsResolver(),
                                                                         clientConfig.dnsAddressLookup(),
                                                                         udsAddress))
                .orElseGet(() -> ConnectionKey.create(uri.scheme(),
                                                      uri.host(),
                                                      uri.port(),
                                                      effectiveTls,
                                                      clientConfig.dnsResolver(),
                                                      clientConfig.dnsAddressLookup(),
                                                      proxy));
    }
}
