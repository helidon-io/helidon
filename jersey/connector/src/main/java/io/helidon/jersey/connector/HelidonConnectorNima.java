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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

import io.helidon.common.Version;
import io.helidon.common.http.Http;
import io.helidon.nima.faulttolerance.Async;
import io.helidon.nima.webclient.WebClient;
import io.helidon.nima.webclient.http1.Http1Client;
import io.helidon.nima.webclient.http1.Http1ClientRequest;
import io.helidon.nima.webclient.http1.Http1ClientResponse;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.core.Configuration;
import org.glassfish.jersey.client.ClientRequest;
import org.glassfish.jersey.client.ClientResponse;
import org.glassfish.jersey.client.spi.AsyncConnectorCallback;
import org.glassfish.jersey.client.spi.Connector;
import org.glassfish.jersey.internal.util.PropertiesHelper;

class HelidonConnectorNima implements Connector {
    private static final String HELIDON_VERSION = "Helidon/" + Version.VERSION + " (java "
            + PropertiesHelper.getSystemProperty("java.runtime.version") + ")";

    private final Client client;
    private final Configuration config;
    private final Http1Client http1Client;

    HelidonConnectorNima(Client client, Configuration config) {
        this.client = client;
        this.config = config;
        this.http1Client = WebClient.builder().build();
    }

    @Override
    public ClientResponse apply(ClientRequest request) {
        // map ClientRequest to Http1ClientRequest
        Http1ClientRequest req = http1Client
                .method(Http.Method.create(request.getMethod()))
                .uri(request.getUri());

        request.getRequestHeaders().forEach((key, value) -> {
            String[] values = value.toArray(new String[0]);
            req.header(Http.Header.create(key), values);
        });

        // TODO copy over properties
        // TODO timeouts redirects etc

        Http1ClientResponse res = null;
        if (request.hasEntity()) {
            // TODO
        } else {
            res = req.request();
        }

        // map Http1ClientResponse to ClientResponse

        return null;
    }

    @Override
    public Future<?> apply(ClientRequest request, AsyncConnectorCallback callback) {
        CompletableFuture<ClientResponse> cf = Async.invokeStatic(() -> apply(request));
        cf.whenComplete((res, t) -> {
            if (t != null) {
                callback.failure(t);
            } else {
                callback.response(res);
            }
        })
        return cf;
    }

    @Override
    public String getName() {
        return HELIDON_VERSION;
    }

    @Override
    public void close() {
    }
}
