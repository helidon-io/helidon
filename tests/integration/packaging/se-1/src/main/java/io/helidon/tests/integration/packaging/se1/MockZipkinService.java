/*
 * Copyright (c) 2020, 2024 Oracle and/or its affiliates.
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

package io.helidon.tests.integration.packaging.se1;

import java.lang.System.Logger.Level;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.common.reactive.Single;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http.HttpService;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;

class MockZipkinService implements HttpService {

    private static final System.Logger LOGGER = System.getLogger(MockZipkinService.class.getName());

    private final Set<String> components;
    private final AtomicReference<CompletableFuture<JsonValue>> next = new AtomicReference<>(new CompletableFuture<>());

    /**
     * Create mock of the Zipkin listening on /api/v2/spans.
     *
     * @param components listen only for traces with component tag having one of specified values
     */
    MockZipkinService(Set<String> components) {
        this.components = components;
    }

    @Override
    public void routing(HttpRules rules) {
        rules.post("/api/v2/spans", this::mockZipkin);
    }

    /**
     * Return completion being completed when next trace call arrives.
     *
     * @return completion being completed when next trace call arrives
     */
    Single<JsonValue> next() {
        return Single.create(next.get());
    }

    private void mockZipkin(ServerRequest request, ServerResponse response) {
        JsonArray entity = request.content().as(JsonArray.class);
        List<JsonObject> spans = entity.stream()
                .map(JsonValue::asJsonObject)
                .filter(o -> o.containsKey("tags"))
                .filter(o -> components.contains(o.getJsonObject("tags").getString("component")))
                .toList();

        for (JsonObject span : spans) {
            LOGGER.log(Level.INFO, span.toString());
            next.getAndSet(new CompletableFuture<>()).complete(span);
        }

        response.send();
    }
}
