/*
 * Copyright (c) 2020 Oracle and/or its affiliates. All rights reserved.
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


package io.helidon.jersey.connector;

import java.io.InputStream;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import javax.net.ssl.SSLContext;
import javax.ws.rs.ProcessingException;
import javax.ws.rs.core.Configuration;

import io.helidon.common.http.Headers;
import io.helidon.common.http.Http;
import io.helidon.common.http.ReadOnlyParameters;
import io.helidon.config.Config;
import io.helidon.media.common.DefaultMediaSupport;
import io.helidon.media.common.MessageBodyReader;
import io.helidon.webclient.Proxy;
import io.helidon.webclient.Ssl;
import io.helidon.webclient.WebClientResponse;

import io.netty.handler.codec.http.HttpHeaderValues;
import org.glassfish.jersey.client.ClientProperties;
import org.glassfish.jersey.client.ClientRequest;

/**
 * Helidon specific classes and implementations.
 */
class HelidonStructures {

    private HelidonStructures() {
    }

    static Headers createHeaders(Map<String, List<String>> data) {
        return new ReadOnlyHeaders(data);
    }

    static MessageBodyReader<InputStream> createInputStreamBodyReader() {
        return DefaultMediaSupport.inputStreamReader();
    }

    static Optional<Config> helidonConfig(Configuration configuration) {
        final Object helidonConfig = configuration.getProperty(HelidonProperties.CONFIG);
        if (helidonConfig != null) {
            if (!Config.class.isInstance(helidonConfig)) {
                HelidonConnector.LOGGER.warning(
                        String.format("Given instance of %s is not Helidon config. Provided HelidonProperties.CONFIG is ignored.",
                            helidonConfig.getClass().getName())
                );
                return Optional.empty();
            } else {
                return Optional.of((Config) helidonConfig);
            }
        }
        return Optional.empty();
    }

    static Optional<Proxy> createProxy(Configuration config) {
        return ProxyBuilder.createProxy(config);
    }

    static Optional<Proxy> createProxy(ClientRequest request) {
        return ProxyBuilder.createProxy(request);
    }

    static Optional<Ssl> createSSL(SSLContext context) {
        return context == null ? Optional.empty() : Optional.of(Ssl.builder().sslContext(context).build());
    }

    static boolean hasEntity(WebClientResponse webClientResponse) {
        final ReadOnlyParameters headers = webClientResponse.content().readerContext().headers();
        final Optional<String> contentLenth = headers.first(Http.Header.CONTENT_LENGTH);
        final Optional<String> encoding = headers.first(Http.Header.TRANSFER_ENCODING);

        return ((contentLenth.isPresent() && !contentLenth.get().equals("0"))
                || (encoding.isPresent() && encoding.get().equals(HttpHeaderValues.CHUNKED.toString())));
    }

    private static class ReadOnlyHeaders extends ReadOnlyParameters implements Headers {
        ReadOnlyHeaders(Map<String, List<String>> data) {
            super(data);
        }
    }

    private static class ProxyBuilder {
        private static Optional<Proxy> createProxy(Configuration config) {
            final Object proxyUri = config.getProperty(ClientProperties.PROXY_URI);
            final String userName
                    = ClientProperties.getValue(config.getProperties(), ClientProperties.PROXY_USERNAME, String.class);
            final String password
                    = ClientProperties.getValue(config.getProperties(), ClientProperties.PROXY_PASSWORD, String.class);
            return createProxy(proxyUri, userName, password);
        }

        private static Optional<Proxy> createProxy(ClientRequest clientRequest) {
            final Object proxyUri = clientRequest.resolveProperty(ClientProperties.PROXY_URI, Object.class);
            final String userName = clientRequest.resolveProperty(ClientProperties.PROXY_USERNAME, String.class);
            final String password = clientRequest.resolveProperty(ClientProperties.PROXY_PASSWORD, String.class);
            return createProxy(proxyUri, userName, password);
        }

        private static Optional<Proxy> createProxy(Object proxyUri, String userName, String password) {
            if (proxyUri != null) {
                final URI u = getProxyUri(proxyUri);
                final Proxy.Builder builder = Proxy.builder();
                if (u.getScheme().toUpperCase(Locale.ROOT).equals("DIRECT")) {
                    builder.type(Proxy.ProxyType.NONE);
                } else {
                    builder.host(u.getHost()).port(u.getPort());
                    switch (u.getScheme().toUpperCase(Locale.ROOT)) {
                        case "HTTP":
                            builder.type(Proxy.ProxyType.HTTP);
                            break;
                        case "SOCKS":
                            builder.type(Proxy.ProxyType.SOCKS_4);
                            break;
                        case "SOCKS5":
                            builder.type(Proxy.ProxyType.SOCKS_5);
                            break;
                        default:
                            HelidonConnector.LOGGER.warning(String.format("Proxy schema %s not supported.", u.getScheme()));
                            return Optional.empty();
                    }
                }
                if (userName != null) {
                    builder.username(userName);

                    if (password != null) {
                        builder.password(password.toCharArray());
                    }
                }
                return Optional.of(builder.build());
            } else {
                return Optional.empty();
            }
        }

        private static URI getProxyUri(final Object proxy) {
            if (proxy instanceof URI) {
                return (URI) proxy;
            } else if (proxy instanceof String) {
                return URI.create((String) proxy);
            } else {
                throw new ProcessingException("The proxy URI (" + proxy + ") property MUST be an instance of String or URI");
            }
        }
    }
}
