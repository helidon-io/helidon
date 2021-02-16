/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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

package io.helidon.webserver.examples.blocking;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

import javax.json.Json;
import javax.json.JsonBuilderFactory;
import javax.json.JsonObject;

import io.helidon.common.http.Http;
import io.helidon.webserver.HttpException;
import io.helidon.webserver.Routing;
import io.helidon.webserver.Service;
import io.helidon.webserver.blocking.BlockingHandler;

/**
 * An example of a WebServer service using blocking APIs.
 */
class BlockingService implements Service {
    private static final JsonBuilderFactory JSON = Json.createBuilderFactory(Map.of());
    private final HttpClient httpClient;
    private final int port;

    BlockingService(HttpClient httpClient, int port) {
        this.httpClient = httpClient;
        this.port = port;
    }

    /**
     * A service registers itself by updating the routing rules.
     * @param rules the routing rules.
     */
    @Override
    public void update(Routing.Rules rules) {
        rules.get("/call", BlockingHandler.create(this::call));
    }

    /**
     * OK or FAILED depending on response from backend service.
     * @return proper hello world message
     */
    private JsonObject call() {

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/sleep"))
                .GET()
                .build();

        try {
            int responseCode = httpClient.send(request, HttpResponse.BodyHandlers.discarding())
                    .statusCode();
            if (responseCode == Http.Status.OK_200.code()) {
                return JSON.createObjectBuilder().add("status", "OK").build();
            }

            throw new HttpException("FAILED", Http.Status.find(responseCode).orElse(Http.Status.INTERNAL_SERVER_ERROR_500));
        } catch (Exception e) {
            throw new HttpException("FAILED", e);
        }
    }
}
