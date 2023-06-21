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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import io.helidon.common.LazyValue;
import io.helidon.common.Version;
import io.helidon.common.http.Http;
import io.helidon.common.uri.UriQueryWriteable;
import io.helidon.nima.http.media.ReadableEntity;
import io.helidon.nima.webclient.WebClient;
import io.helidon.nima.webclient.http1.Http1Client;
import io.helidon.nima.webclient.http1.Http1ClientRequest;
import io.helidon.nima.webclient.http1.Http1ClientResponse;

import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.core.Configuration;
import jakarta.ws.rs.core.Response;
import org.glassfish.jersey.client.ClientRequest;
import org.glassfish.jersey.client.ClientResponse;
import org.glassfish.jersey.client.spi.AsyncConnectorCallback;
import org.glassfish.jersey.client.spi.Connector;
import org.glassfish.jersey.internal.util.PropertiesHelper;

class HelidonConnector implements Connector {
    private static final String HELIDON_VERSION = "Helidon/" + Version.VERSION + " (java "
            + PropertiesHelper.getSystemProperty("java.runtime.version") + ")";

    private static LazyValue<ExecutorService> executorService =
            LazyValue.create(() -> Executors.newThreadPerTaskExecutor(
                    Thread.ofVirtual().name("helidon-connector-", 0).factory()));

    private final Client client;
    private final Configuration config;
    private final Http1Client http1Client;

    HelidonConnector(Client client, Configuration config) {
        this.client = client;
        this.config = config;
        this.http1Client = WebClient.builder().build();
    }

    /**
     * Map a Jersey request to a Helidon HTTP/1.1 request.
     *
     * @param request the request to map
     * @return the mapped request
     */
    private Http1ClientRequest mapRequest(ClientRequest request) {
        URI uri = request.getUri();
        Http1ClientRequest http1Request = http1Client
                .method(Http.Method.create(request.getMethod()))
                .uri(uri);

        // map query parameters
        String queryString = uri.getQuery();
        if (queryString != null) {
            UriQueryWriteable query = UriQueryWriteable.create();
            query.fromQueryString(queryString);
            query.names().forEach(name -> {
                String[] values = query.all(name).toArray(new String[0]);
                http1Request.queryParam(name, values);
            });
        }

        // map request headers
        request.getRequestHeaders().forEach((key, value) -> {
            String[] values = value.toArray(new String[0]);
            http1Request.header(Http.Header.create(key), values);
        });

        // TODO copy over properties
        // TODO timeouts redirects etc

        return http1Request;
    }

    /**
     * Map a Helidon HTTP/1.1 response to a Jersey response.
     *
     * @param http1Response the response to map
     * @param request the request
     * @return the mapped response
     */
    private ClientResponse mapResponse(Http1ClientResponse http1Response, ClientRequest request) {
        ClientResponse response = new ClientResponse(new Response.StatusType() {
            @Override
            public int getStatusCode() {
                return http1Response.status().code();
            }

            @Override
            public Response.Status.Family getFamily() {
                return Response.Status.Family.familyOf(getStatusCode());
            }

            @Override
            public String getReasonPhrase() {
                return http1Response.status().reasonPhrase();
            }
        }, request);

        for (Http.HeaderValue header : http1Response.headers()) {
            for (String v : header.allValues()) {
                response.getHeaders().add(header.name(), v);
            }
        }

        // TODO set URL of last redirection

        ReadableEntity entity = http1Response.entity();
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
        Http1ClientResponse http1Response;
        Http1ClientRequest http1Request = mapRequest(request);

        if (request.hasEntity()) {
            http1Response = http1Request.outputStream(os -> {
                request.setStreamProvider(length -> os);
                request.writeEntity();      // ask Jersey to write entity to WebClient stream
            });
        } else {
            http1Response = http1Request.request();
        }

        return mapResponse(http1Response, request);
    }

    /**
     * Asynchronously execute Jersey request using WebClient.
     *
     * @param request the Jersey request
     * @return a Jersey response
     */
    @Override
    public Future<?> apply(ClientRequest request, AsyncConnectorCallback callback) {
        return executorService.get().submit(() -> {
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
