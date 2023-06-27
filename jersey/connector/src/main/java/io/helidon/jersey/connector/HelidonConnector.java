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

package io.helidon.jersey.connector;

import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import javax.net.ssl.SSLContext;

import io.helidon.common.LazyValue;
import io.helidon.common.Version;
import io.helidon.common.http.Http;
import io.helidon.common.uri.UriQueryWriteable;
import io.helidon.nima.common.tls.Tls;
import io.helidon.nima.http.media.ReadableEntity;
import io.helidon.nima.webclient.WebClient;
import io.helidon.nima.webclient.http1.Http1;
import io.helidon.nima.webclient.http1.Http1Client;
import io.helidon.nima.webclient.http1.Http1ClientRequest;
import io.helidon.nima.webclient.http1.Http1ClientResponse;

import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.core.Configuration;
import jakarta.ws.rs.core.Response;
import org.glassfish.jersey.client.ClientProperties;
import org.glassfish.jersey.client.ClientRequest;
import org.glassfish.jersey.client.ClientResponse;
import org.glassfish.jersey.client.spi.AsyncConnectorCallback;
import org.glassfish.jersey.client.spi.Connector;
import org.glassfish.jersey.internal.util.PropertiesHelper;

class HelidonConnector implements Connector {
    private static final String HELIDON_VERSION = "Helidon/" + Version.VERSION + " (java "
            + PropertiesHelper.getSystemProperty("java.runtime.version") + ")";

    private static final LazyValue<ExecutorService> EXECUTOR_SERVICE =
            LazyValue.create(() -> Executors.newThreadPerTaskExecutor(
                    Thread.ofVirtual().name("helidon-connector-", 0).factory()));

    private final Client client;
    private final Http1Client httpClient;

    HelidonConnector(Client client, Configuration config) {
        this.client = client;
        Map<String, Object> properties = config.getProperties();
        httpClient = WebClient.builder(Http1.PROTOCOL)
                .connectTimeout(Duration.ofMillis(
                        ClientProperties.getValue(properties, ClientProperties.CONNECT_TIMEOUT, 10000)))
                .readTimeout(Duration.ofMillis(
                        ClientProperties.getValue(properties, ClientProperties.READ_TIMEOUT, 10000)))
                .followRedirect(
                        ClientProperties.getValue(properties, ClientProperties.FOLLOW_REDIRECTS, true))
                .build();
    }

    /**
     * Map a Jersey request to a Helidon HTTP/1.1 request.
     *
     * @param request the request to map
     * @return the mapped request
     */
    private Http1ClientRequest mapRequest(ClientRequest request) {
        URI uri = request.getUri();
        Http1ClientRequest httpRequest = httpClient
                .method(Http.Method.create(request.getMethod()))
                .uri(uri);

        // map query parameters
        String queryString = uri.getQuery();
        if (queryString != null) {
            UriQueryWriteable query = UriQueryWriteable.create();
            query.fromQueryString(queryString);
            query.names().forEach(name -> {
                String[] values = query.all(name).toArray(new String[0]);
                httpRequest.queryParam(name, values);
            });
        }

        // map request headers
        request.getRequestHeaders().forEach((key, value) -> {
            String[] values = value.toArray(new String[0]);
            httpRequest.header(Http.Header.create(key), values);
        });

        // SSL context
        SSLContext sslContext = client.getSslContext();
        httpRequest.tls(Tls.builder().sslContext(sslContext).build());

        // redirects
        httpRequest.followRedirects(request.resolveProperty(ClientProperties.FOLLOW_REDIRECTS, true));

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
    private ClientResponse mapResponse(Http1ClientResponse httpResponse, ClientRequest request) {
        ClientResponse response = new ClientResponse(new Response.StatusType() {
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
        }, request);

        // copy headers
        for (Http.HeaderValue header : httpResponse.headers()) {
            for (String v : header.allValues()) {
                response.getHeaders().add(header.name(), v);
            }
        }

        // last URI, possibly after redirections
        response.setResolvedRequestUri(httpResponse.lastEndpointUri());

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
        Http1ClientResponse httpResponse;
        Http1ClientRequest httpRequest = mapRequest(request);

        if (request.hasEntity()) {
            httpResponse = httpRequest.outputStream(os -> {
                request.setStreamProvider(length -> os);
                request.writeEntity();      // ask Jersey to write entity to WebClient stream
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
}
