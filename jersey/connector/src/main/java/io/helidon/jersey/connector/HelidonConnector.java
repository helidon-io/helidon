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
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.logging.Logger;

import io.helidon.common.LazyValue;
import io.helidon.common.Version;
import io.helidon.common.tls.Tls;
import io.helidon.config.Config;
import io.helidon.http.Header;
import io.helidon.http.HeaderNames;
import io.helidon.http.Method;
import io.helidon.http.media.ReadableEntity;
import io.helidon.webclient.api.HttpClientRequest;
import io.helidon.webclient.api.HttpClientResponse;
import io.helidon.webclient.api.Proxy;
import io.helidon.webclient.api.WebClient;
import io.helidon.webclient.api.WebClientConfig;
import io.helidon.webclient.spi.ProtocolConfig;

import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.core.Configuration;
import jakarta.ws.rs.core.Response;
import org.glassfish.jersey.client.ClientRequest;
import org.glassfish.jersey.client.ClientResponse;
import org.glassfish.jersey.client.spi.AsyncConnectorCallback;
import org.glassfish.jersey.client.spi.Connector;
import org.glassfish.jersey.internal.util.PropertiesHelper;

import static io.helidon.jersey.connector.HelidonProperties.DEFAULT_HEADERS;
import static io.helidon.jersey.connector.HelidonProperties.PROTOCOL_CONFIGS;
import static io.helidon.jersey.connector.HelidonProperties.PROTOCOL_ID;
import static io.helidon.jersey.connector.HelidonProperties.SHARE_CONNECTION_CACHE;
import static io.helidon.jersey.connector.HelidonProperties.TLS;
import static org.glassfish.jersey.client.ClientProperties.CONNECT_TIMEOUT;
import static org.glassfish.jersey.client.ClientProperties.FOLLOW_REDIRECTS;
import static org.glassfish.jersey.client.ClientProperties.READ_TIMEOUT;
import static org.glassfish.jersey.client.ClientProperties.getValue;

class HelidonConnector implements Connector {
    static final Logger LOGGER = Logger.getLogger(HelidonConnector.class.getName());

    private static final int DEFAULT_TIMEOUT = 10000;
    private static final Map<String, String> EMPTY_MAP_LIST = Map.of("", "");

    private static final String HELIDON_VERSION = "Helidon/" + Version.VERSION + " (java "
            + PropertiesHelper.getSystemProperty("java.runtime.version") + ")";

    private static final LazyValue<ExecutorService> EXECUTOR_SERVICE =
            LazyValue.create(() -> Executors.newThreadPerTaskExecutor(
                    Thread.ofVirtual().name("helidon-connector-", 0).factory()));

    private final WebClient webClient;
    private final Proxy proxy;

    HelidonConnector(Client client, Configuration config) {
        // create underlying HTTP client
        Map<String, Object> properties = config.getProperties();
        var builder = WebClientConfig.builder();

        // use config for client
        builder.config(helidonConfig(config).orElse(Config.empty()));

        // proxy support
        proxy = ProxyBuilder.createProxy(config).orElse(Proxy.create());

        // possibly override config with properties
        if (properties.containsKey(CONNECT_TIMEOUT)) {
            builder.connectTimeout(Duration.ofMillis(getValue(properties, CONNECT_TIMEOUT, DEFAULT_TIMEOUT)));
        }
        if (properties.containsKey(READ_TIMEOUT)) {
            builder.readTimeout(Duration.ofMillis(getValue(properties, READ_TIMEOUT, DEFAULT_TIMEOUT)));
        }
        if (properties.containsKey(FOLLOW_REDIRECTS)) {
            builder.followRedirects(getValue(properties, FOLLOW_REDIRECTS, true));
        }

        // prefer Tls over SSLContext
        if (properties.containsKey(TLS)) {
            builder.tls(getValue(properties, TLS, Tls.class));
        } else if (client.getSslContext() != null) {
            builder.tls(Tls.builder().sslContext(client.getSslContext()).build());
        }

        // protocol configs
        if (properties.containsKey(PROTOCOL_CONFIGS)) {
            List<? extends ProtocolConfig> protocolConfigs =
                    (List<? extends ProtocolConfig>) properties.get(PROTOCOL_CONFIGS);
            if (protocolConfigs != null) {
                builder.addProtocolConfigs(protocolConfigs);
            }
        }

        // default headers
        if (properties.containsKey(DEFAULT_HEADERS)) {
            Map<String, String> headers = getValue(properties, DEFAULT_HEADERS, EMPTY_MAP_LIST);
            headers.forEach(builder::addHeader);
        }

        // connection sharing defaults to false in this connector
        if (properties.containsKey(SHARE_CONNECTION_CACHE)) {
            builder.shareConnectionCache(getValue(properties, SHARE_CONNECTION_CACHE, false));
        }

        webClient = builder.build();
    }

    /**
     * Map a Jersey request to a Helidon HTTP request.
     *
     * @param request the request to map
     * @return the mapped request
     */
    private HttpClientRequest mapRequest(ClientRequest request) {
        // possibly override proxy in request
        Proxy requestProxy = ProxyBuilder.createProxy(request).orElse(proxy);

        // create WebClient request
        URI uri = request.getUri();
        HttpClientRequest httpRequest = webClient
                .method(Method.create(request.getMethod()))
                .proxy(requestProxy)
                .skipUriEncoding(true)      // already encoded by Jersey
                .uri(uri);

        // map request headers
        request.getRequestHeaders().forEach((key, value) -> {
            String[] values = value.toArray(new String[0]);
            httpRequest.header(HeaderNames.create(key), values);
        });

        // request config
        Boolean followRedirects = request.resolveProperty(FOLLOW_REDIRECTS, Boolean.class);
        if (followRedirects != null) {
            httpRequest.followRedirects(followRedirects);
        }
        Integer readTimeout = request.resolveProperty(READ_TIMEOUT, Integer.class);
        if (readTimeout != null) {
            httpRequest.readTimeout(Duration.ofMillis(readTimeout));
        }
        String protocolId = request.resolveProperty(PROTOCOL_ID, String.class);
        if (protocolId != null) {
            httpRequest.protocolId(protocolId);
        }

        // copy properties
        for (String name : request.getConfiguration().getPropertyNames()) {
            Object value = request.getConfiguration().getProperty(name);
            if (!name.startsWith("jersey") && value instanceof String stringValue) {
                httpRequest.property(name, stringValue);
            }
        }
        for (String propertyName : request.getPropertyNames()) {
            Object value = request.resolveProperty(propertyName, Object.class);
            if (!propertyName.startsWith("jersey") && value instanceof String stringValue) {
                httpRequest.property(propertyName, stringValue);
            }
        }

        return httpRequest;
    }

    /**
     * Map a Helidon HTTP/1.1 response to a Jersey response.
     *
     * @param httpResponse the response to map
     * @param request the request
     * @return the mapped response
     */
    private ClientResponse mapResponse(HttpClientResponse httpResponse, ClientRequest request) {
        Response.StatusType statusType = new Response.StatusType() {
            @Override
            public int getStatusCode() {
                return httpResponse.status().code();
            }

            @Override
            public Response.Status.Family getFamily() {
                return Response.Status.Family.familyOf(getStatusCode());
            }

            @Override
            public String getReasonPhrase() {
                return httpResponse.status().reasonPhrase();
            }
        };
        ClientResponse response = new ClientResponse(statusType, request) {
            @Override
            public void close() {
                super.close();
                httpResponse.close();       // closes WebClient's response
            }
        };

        // copy headers
        for (Header header : httpResponse.headers()) {
            for (String v : header.allValues()) {
                response.getHeaders().add(header.name(), v);
            }
        }

        // last URI, possibly after redirections
        response.setResolvedRequestUri(httpResponse.lastEndpointUri().toUri());

        // handle entity
        ReadableEntity entity = httpResponse.entity();
        if (entity.hasEntity()) {
            response.setEntityStream(entity.inputStream());
        }
        return response;
    }

    /**
     * Execute Jersey request using WebClient.
     *
     * @param request the Jersey request
     * @return a Jersey response
     */
    @Override
    public ClientResponse apply(ClientRequest request) {
        HttpClientResponse httpResponse;
        HttpClientRequest httpRequest = mapRequest(request);

        if (request.hasEntity()) {
            httpResponse = httpRequest.outputStream(os -> {
                request.setStreamProvider(length -> os);
                request.writeEntity();
            });
        } else {
            httpResponse = httpRequest.request();
        }

        return mapResponse(httpResponse, request);
    }

    /**
     * Asynchronously execute Jersey request using WebClient.
     *
     * @param request the Jersey request
     * @return a Jersey response
     */
    @Override
    public Future<?> apply(ClientRequest request, AsyncConnectorCallback callback) {
        return EXECUTOR_SERVICE.get().submit(() -> {
            try {
                ClientResponse response = apply(request);
                callback.response(response);
            } catch (Throwable t) {
                callback.failure(t);
            }
        });
    }

    @Override
    public String getName() {
        return HELIDON_VERSION;
    }

    @Override
    public void close() {
    }

    WebClient client() {
        return webClient;
    }

    Proxy proxy() {
        return proxy;
    }

    /**
     * Returns the Helidon Connector configuration, if available.
     *
     * @param configuration from Jakarta REST
     * @return an optional config
     */
    static Optional<Config> helidonConfig(Configuration configuration) {
        Object helidonConfig = configuration.getProperty(HelidonProperties.CONFIG);
        if (helidonConfig != null) {
            if (!(helidonConfig instanceof Config)) {
                LOGGER.warning(String.format("Ignoring Helidon Connector config at '%s'",
                        HelidonProperties.CONFIG));
            } else {
                return Optional.of((Config) helidonConfig);
            }
        }
        return Optional.empty();
    }
}
