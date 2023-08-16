/*
 * Copyright (c) 2020, 2023 Oracle and/or its affiliates.
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

package io.helidon.tests.integration.nativeimage.se1;

import java.lang.System.Logger.Level;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.GZIPInputStream;

import io.helidon.common.reactive.Single;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http.HttpService;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

import jakarta.json.Json;
import jakarta.json.JsonPointer;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;

class MockZipkinService implements HttpService {

    private static final System.Logger LOGGER = System.getLogger(MockZipkinService.class.getName());

    final static JsonPointer TAGS_POINTER = Json.createPointer("/tags");
    final static JsonPointer COMPONENT_POINTER = Json.createPointer("/tags/component");

    private final Set<String> filteredComponents;
    private final AtomicReference<CompletableFuture<JsonValue>> next = new AtomicReference<>(new CompletableFuture<>());

    /**
     * Create mock of the Zipkin listening on /api/v2/spans.
     *
     * @param filteredComponents listen only for traces with component tag having one of specified values
     */
    MockZipkinService(Set<String> filteredComponents) {
        this.filteredComponents = filteredComponents;
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
        try {
            request.query().all("serviceName").forEach(s -> System.out.println(">>>" + s));

            GZIPInputStream is = new GZIPInputStream(request.content()
                                                             .inputStream());
            Json.createReader(is).readArray()
                    .stream()
                    .map(JsonValue::asJsonObject)
                    .filter(json ->
                                    TAGS_POINTER.containsValue(json)
                                            && COMPONENT_POINTER.containsValue(json)
                                            && filteredComponents.stream()
                                            .anyMatch(s -> s.equals(((JsonString) COMPONENT_POINTER.getValue(json)).getString()))
                    )
                    .peek(json -> LOGGER.log(Level.INFO, json.toString()))
                    .forEach(e -> next.getAndSet(new CompletableFuture<>()).complete(e));

            response.send();
        } catch (Exception e) {
            response.send(e);
        }
    }
}
