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

import java.util.Map;

import javax.json.Json;
import javax.json.JsonBuilderFactory;

import io.helidon.webclient.WebClient;
import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;
import io.helidon.webserver.Service;

/**
 * The usual reactive Helidon Greet service.
 */
class ReactiveService implements Service {
    private static final JsonBuilderFactory JSON = Json.createBuilderFactory(Map.of());

    private final WebClient webClient;

    ReactiveService(WebClient webClient) {
        this.webClient = webClient;
    }

    /**
     * A service registers itself by updating the routing rules.
     * @param rules the routing rules.
     */
    @Override
    public void update(Routing.Rules rules) {
        rules.get("/call", this::call);
    }

    /**
     * OK or FAILED depending on response from backend service.
     * @param req the server request
     * @param res the server response
     */
    private void call(ServerRequest req, ServerResponse res) {
        webClient.get()
                .request(String.class)
                .thenAccept(it -> {
                    res.send(JSON.createObjectBuilder().add("status", it).build());
                })
                .exceptionally(res::send);
    }
}
