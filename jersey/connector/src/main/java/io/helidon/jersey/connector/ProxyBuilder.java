/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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

import java.net.URI;
import java.util.Locale;
import java.util.Optional;

import io.helidon.webclient.api.Proxy;

import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.core.Configuration;
import org.glassfish.jersey.client.ClientProperties;
import org.glassfish.jersey.client.ClientRequest;

import static org.glassfish.jersey.client.ClientProperties.PROXY_PASSWORD;
import static org.glassfish.jersey.client.ClientProperties.PROXY_URI;
import static org.glassfish.jersey.client.ClientProperties.PROXY_USERNAME;

class ProxyBuilder {

    static Optional<Proxy> createProxy(Configuration config) {
        Object proxyUri = config.getProperty(PROXY_URI);
        String userName = ClientProperties.getValue(config.getProperties(), PROXY_USERNAME, String.class);
        String password = ClientProperties.getValue(config.getProperties(), PROXY_PASSWORD, String.class);
        return createProxy(proxyUri, userName, password);
    }

    static Optional<Proxy> createProxy(ClientRequest clientRequest) {
        Object proxyUri = clientRequest.resolveProperty(PROXY_URI, Object.class);
        String userName = clientRequest.resolveProperty(PROXY_USERNAME, String.class);
        String password = clientRequest.resolveProperty(PROXY_PASSWORD, String.class);
        return createProxy(proxyUri, userName, password);
    }

    private ProxyBuilder() {
    }

    private static Optional<Proxy> createProxy(Object proxyUri, String userName, String password) {
        if (proxyUri != null) {
            URI u = getProxyUri(proxyUri);
            Proxy.Builder builder = Proxy.builder();
            if (u.getScheme().toUpperCase(Locale.ROOT).equals("DIRECT")) {
                builder.type(Proxy.ProxyType.NONE);
            } else {
                builder.host(u.getHost()).port(u.getPort());
                if ("HTTP".equals(u.getScheme().toUpperCase(Locale.ROOT))) {
                    builder.type(Proxy.ProxyType.HTTP);
                } else {
                    HelidonConnector.LOGGER.log(System.Logger.Level.WARNING,
                            String.format("Proxy schema %s not supported.", u.getScheme()));
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

    private static URI getProxyUri(Object proxy) {
        if (proxy instanceof URI) {
            return (URI) proxy;
        } else if (proxy instanceof String) {
            return URI.create((String) proxy);
        } else {
            throw new ProcessingException("The proxy URI '" + proxy + "' MUST be String or URI");
        }
    }
}
