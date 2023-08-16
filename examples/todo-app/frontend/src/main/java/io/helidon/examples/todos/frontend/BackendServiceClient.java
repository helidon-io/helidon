/*
 * Copyright (c) 2017, 2023 Oracle and/or its affiliates.
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

package io.helidon.examples.todos.frontend;

import java.util.Optional;

import io.helidon.http.Http;
import io.helidon.tracing.Span;
import io.helidon.tracing.SpanContext;
import io.helidon.tracing.Tracer;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http1.Http1ClientResponse;
import io.helidon.webserver.http.ServerResponse;

import jakarta.json.JsonArray;
import jakarta.json.JsonObject;

/**
 * Client to invoke the TODO backend service.
 */
public final class BackendServiceClient {

    private final Http1Client client;
    private final Tracer tracer;

    /**
     * Create a new {@code BackendServiceClient} instance.
     *
     * @param client the backend HTTP client
     */
    public BackendServiceClient(Http1Client client) {
        this.client = client;
        this.tracer = Tracer.global();
    }

    /**
     * Retrieve all TODO entries from the backend.
     *
     * @param spanContext {@code SpanContext} to use
     * @return all records
     */
    public JsonArray getAll(SpanContext spanContext) {
        Span span = tracer.spanBuilder("todos.get-all")
                .parent(spanContext)
                .start();

        try {
            JsonArray jsonArray = client.get("/api/backend")
                    .requestEntity(JsonArray.class);
            span.end();
            return jsonArray;
        } catch (Throwable t) {
            span.end(t);
            throw t;
        }
    }

    /**
     * Retrieve the TODO entry identified by the given ID.
     *
     * @param id the ID identifying the entry to retrieve
     * @return retrieved entry as a {@code JsonObject}
     */
    public Optional<JsonObject> getSingle(String id) {
        return processSingleEntityResponse(client.get("/api/backend/" + id).request());
    }

    /**
     * Delete the TODO entry identified by the given ID.
     *
     * @param id the ID identifying the entry to delete
     * @return deleted entry as a {@code JsonObject}
     */
    public Optional<JsonObject> deleteSingle(String id) {
        return processSingleEntityResponse(client.delete("/api/backend/" + id).request());
    }

    /**
     * Create a new TODO entry.
     *
     * @param json the new entry value to create as {@code JsonObject}
     * @return created entry as {@code JsonObject}
     */
    public Optional<JsonObject> create(JsonObject json) {
        return processSingleEntityResponse(
                client.post("/api/backend/")
                        .submit(json));
    }

    /**
     * Update a TODO entry identifying by the given ID.
     *
     * @param id   the ID identifying the entry to update
     * @param json the update entry value as {@code JsonObject}
     * @param res  updated entry as {@code JsonObject}
     */
    public void update(String id, JsonObject json, ServerResponse res) {
        try (Http1ClientResponse response = client.put("/api/backend/" + id).submit(json)) {
            if (response.status().family() == Http.Status.Family.SUCCESSFUL) {
                res.send(response.entity().as(JsonObject.class));
            } else {
                res.status(response.status());
            }
        }
    }

    /**
     * Wrap the response entity in an {@code Optional}.
     *
     * @param response {@code Response} to process
     * @return empty optional if response status is {@code 404}, optional of
     * the response entity otherwise
     */
    private Optional<JsonObject> processSingleEntityResponse(Http1ClientResponse response) {
        try (response) {
            if (response.status() == Http.Status.NOT_FOUND_404) {
                return Optional.empty();
            }
            return Optional.of(response.entity().as(JsonObject.class));
        }
    }
}
