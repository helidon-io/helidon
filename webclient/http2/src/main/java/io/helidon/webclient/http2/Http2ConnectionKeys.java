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

import io.helidon.common.tls.Tls;
import io.helidon.http.ClientRequestHeaders;
import io.helidon.http.WritableHeaders;
import io.helidon.webclient.api.ClientUri;
import io.helidon.webclient.api.ConnectionKey;
import io.helidon.webclient.api.FullClientRequest;
import io.helidon.webclient.api.HttpClientConfig;
import io.helidon.webclient.api.Proxy;
import io.helidon.webclient.api.SniConfig;

final class Http2ConnectionKeys {
    private static final Tls NO_TLS = Tls.builder().enabled(false).build();
    private static final ClientRequestHeaders EMPTY_HEADERS = ClientRequestHeaders.create(WritableHeaders.create());

    private Http2ConnectionKeys() {
    }

    static ConnectionKey create(ClientUri uri, FullClientRequest<?> request, HttpClientConfig clientConfig) {
        return create(uri, request, clientConfig, EMPTY_HEADERS);
    }

    static ConnectionKey create(ClientUri uri,
                                FullClientRequest<?> request,
                                HttpClientConfig clientConfig,
                                ClientRequestHeaders headers) {
        return create(uri,
                      request.address().orElse(null),
                      request.sni().or(clientConfig::sni).orElse(null),
                      request.tls(),
                      request.proxy(),
                      clientConfig,
                      headers);
    }

    static ConnectionKey create(ClientUri uri,
                                SocketAddress address,
                                SniConfig sni,
                                Tls tls,
                                Proxy proxy,
                                HttpClientConfig clientConfig,
                                ClientRequestHeaders headers) {
        Tls effectiveTls = "https".equals(uri.scheme()) ? tls : NO_TLS;
        if (address instanceof UnixDomainSocketAddress udsAddress) {
            if (sni == null) {
                return ConnectionKey.createUnixDomainSocket(uri,
                                                            effectiveTls,
                                                            clientConfig.dnsResolver(),
                                                            clientConfig.dnsAddressLookup(),
                                                            udsAddress);
            }
            return ConnectionKey.createUnixDomainSocket(uri,
                                                        sni,
                                                        effectiveTls,
                                                        clientConfig.dnsResolver(),
                                                        clientConfig.dnsAddressLookup(),
                                                        udsAddress,
                                                        headers);
        }
        if (sni == null) {
            return ConnectionKey.create(uri,
                                        effectiveTls,
                                        clientConfig.dnsResolver(),
                                        clientConfig.dnsAddressLookup(),
                                        proxy);
        }
        return ConnectionKey.create(uri,
                                    sni,
                                    effectiveTls,
                                    clientConfig.dnsResolver(),
                                    clientConfig.dnsAddressLookup(),
                                    proxy,
                                    headers);
    }
}
