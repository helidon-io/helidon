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

import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.helidon.common.configurable.LruCache;
import io.helidon.common.socket.HelidonSocket;
import io.helidon.http.Method;
import io.helidon.webclient.spi.HttpClientSpi;

/**
 * Client request of any client that support HTTP protocol.
 * This allows configuration of a request, such as setting headers, query parameters etc.
 * Terminating methods are {@link #submit(Object)} (and its variants), and {@link #request()} (and its variants).
 */
public class HttpClientRequest extends ClientRequestBase<HttpClientRequest, HttpClientResponse> {
    private static final System.Logger LOGGER = System.getLogger(HttpClientRequest.class.getName());

    private final LruCache<LoomClient.EndpointKey, HttpClientSpi> clientSpiCache;
    private final WebClient webClient;
    private final Map<String, LoomClient.ProtocolSpi> clients;
    private final List<LoomClient.ProtocolSpi> tcpProtocols;
    private final List<String> tcpProtocolIds;
    private final List<LoomClient.ProtocolSpi> protocols;
    private String preferredProtocolId;

    HttpClientRequest(WebClient webClient,
                      WebClientConfig clientConfig,
                      Method method,
                      ClientUri clientUri,
                      Map<String, LoomClient.ProtocolSpi> protocolsToClients,
                      List<LoomClient.ProtocolSpi> protocols,
                      List<LoomClient.ProtocolSpi> tcpProtocols,
                      List<String> tcpProtocolIds,
                      LruCache<LoomClient.EndpointKey, HttpClientSpi> clientSpiCache) {
        this(webClient, clientConfig, method, clientUri, protocolsToClients, protocols, tcpProtocols,
             tcpProtocolIds, null, clientSpiCache);
    }

    HttpClientRequest(WebClient webClient,
                      WebClientConfig clientConfig,
                      Method method,
                      ClientUri clientUri,
                      Map<String, LoomClient.ProtocolSpi> protocolsToClients,
                      List<LoomClient.ProtocolSpi> protocols,
                      List<LoomClient.ProtocolSpi> tcpProtocols,
                      List<String> tcpProtocolIds,
                      Boolean send100Continue,
                      LruCache<LoomClient.EndpointKey, HttpClientSpi> clientSpiCache) {
        super(clientConfig, webClient.cookieManager(), "any", method, clientUri,
              send100Continue, clientConfig.properties());
        this.webClient = webClient;
        this.clients = protocolsToClients;
        this.protocols = protocols;
        this.tcpProtocols = tcpProtocols;
        this.tcpProtocolIds = tcpProtocolIds;
        this.clientSpiCache = clientSpiCache;
    }

    /**
     * Use an explicit version of HTTP by defining its ALPN protocol ID.
     * Constants are defined on each version specific client.
     * For example to use HTTP/2, {@code h2} is the protocol ID. For TLS, we will attempt only {@code h2} negotiation,
     * for plaintext requests, we will either attempt an upgrade from HTTP/1.1, or use prior knowledge according to HTTP/2
     * protocol configuration. This method is a no-op when called on a version specific HTTP client.
     *
     * @param protocol HTTP protocol ID to use
     * @return updated request
     */
    public HttpClientRequest protocolId(String protocol) {
        this.preferredProtocolId = protocol;
        return this;
    }

    @Override
    protected HttpClientResponse doSubmit(Object entity) {
        return discoverHttpImplementation().submit(entity);
    }

    @Override
    protected HttpClientResponse doOutputStream(OutputStreamHandler outputStreamConsumer) {
        return discoverHttpImplementation().outputStream(outputStreamConsumer);
    }

    private ClientRequest<?> discoverHttpImplementation() {
        ClientUri resolvedUri = resolvedUri();

        if (preferredProtocolId != null) {
            /*
            Explicit protocol id specified for this very request - we must honor it
             */
            LoomClient.ProtocolSpi httpClientSpi = clients.get(preferredProtocolId);
            if (httpClientSpi == null) {
                throw new IllegalArgumentException("Requested protocol with id \"" + preferredProtocolId + "\", which is not "
                                                           + "available on classpath. Available protocols: " + clients.keySet());
            }
            return httpClientSpi.spi().clientRequest(this, resolvedUri);
        }

        LoomClient.EndpointKey endpointKey = new LoomClient.EndpointKey(resolvedUri.scheme(),
                                                                        resolvedUri.authority(),
                                                                        tls(),
                                                                        proxy());
        Optional<HttpClientSpi> spi = clientSpiCache.get(endpointKey);
        if (spi.isPresent()) {
            /*
            We already know this is handled by a specific protocol version, handle it again
             */
            return spi.get().clientRequest(this, resolvedUri);
        }

        // now use the first protocol that supports the request without condition, store first compatible
        HttpClientSpi compatible = null;
        HttpClientSpi unknown = null;
        for (LoomClient.ProtocolSpi protocol : protocols) {
            // must iterate through list, to maintain weighted ordering

            HttpClientSpi client = protocol.spi();
            HttpClientSpi.SupportLevel supports = client.supports(this, resolvedUri);
            if (supports == HttpClientSpi.SupportLevel.SUPPORTED) {
                clientSpiCache.put(endpointKey, client);
                return client.clientRequest(this, resolvedUri);
            }
            if (supports == HttpClientSpi.SupportLevel.COMPATIBLE && compatible == null) {
                compatible = client;
            }
            if (supports == HttpClientSpi.SupportLevel.UNKNOWN && unknown == null) {
                unknown = client;
            }
        }

        if ("https".equals(resolvedUri.scheme()) && tls().enabled() && !tcpProtocols.isEmpty()) {
            // use ALPN
            ConnectionKey connectionKey = ConnectionKey.create(resolvedUri.scheme(),
                                                               resolvedUri.host(),
                                                               resolvedUri.port(),
                                                               tls(),
                                                               clientConfig().dnsResolver(),
                                                               clientConfig().dnsAddressLookup(),
                                                               proxy());

            // this is a temporary connection, used to determine which protocol is supported, next
            // call to the same remote location will be obtained from cache
            TcpClientConnection connection = TcpClientConnection.create(webClient,
                                                                        connectionKey,
                                                                        tcpProtocolIds,
                                                                        conn -> true,
                                                                        conn -> {});
            connection.connect();
            HelidonSocket socket = connection.helidonSocket();
            if (socket.protocolNegotiated()) {
                String negotiatedProtocol = socket.protocol();
                LoomClient.ProtocolSpi protocolSpi = clients.get(negotiatedProtocol);
                if (protocolSpi == null) {
                    if (LOGGER.isLoggable(System.Logger.Level.TRACE)) {
                        LOGGER.log(System.Logger.Level.TRACE, "Attempted to negotiate a protocol (" + tcpProtocolIds + "), "
                                + "but got an unsupported protocol back: " + negotiatedProtocol);
                    }
                    // we have negotiated protocol we do not support? this is strange
                    connection.closeResource();
                } else {
                    clientSpiCache.put(endpointKey, protocolSpi.spi());
                    connection(connection);
                    return protocolSpi.spi().clientRequest(this, resolvedUri);
                }
            } else {
                if (LOGGER.isLoggable(System.Logger.Level.TRACE)) {
                    LOGGER.log(System.Logger.Level.TRACE, "Attempted to negotiate a protocol (" + tcpProtocolIds + "), "
                            + "but did not get a negotiated protocol back, ignoring.");
                }
                connection.closeResource();
            }
        }

        if (compatible != null) {
            clientSpiCache.put(endpointKey, compatible);
            return compatible.clientRequest(this, resolvedUri);
        }

        if (unknown != null) {
            // do not cache
            return unknown.clientRequest(this, resolvedUri);
        }

        throw new IllegalArgumentException("Cannot handle request to " + resolvedUri + ", did not discover any HTTP version "
                                                   + "willing to handle it. HTTP versions supported: " + clients.keySet());
    }
}
