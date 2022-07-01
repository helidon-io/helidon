/*
 * Copyright (c) 2017, 2022 Oracle and/or its affiliates.
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

package io.helidon.demo.todos.frontend;

import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.helidon.common.http.Http;
import io.helidon.config.Config;
import io.helidon.security.SecurityContext;
import io.helidon.security.integration.jersey.client.ClientSecurity;
import io.helidon.tracing.Span;
import io.helidon.tracing.SpanContext;
import io.helidon.tracing.Tracer;
import io.helidon.webserver.ServerResponse;

import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.InvocationCallback;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;

import static io.helidon.tracing.jersey.client.ClientTracingFilter.CURRENT_SPAN_CONTEXT_PROPERTY_NAME;

/**
 * Client to invoke the TODO backend service.
 */
public final class BackendServiceClient {

    /**
     * Client logger.
     */
    private static final Logger LOGGER =
            Logger.getLogger(BackendServiceClient.class.getName());

    /**
     * JAXRS client.
     */
    private final Client client;

    /**
     * Configured endpoint for the backend service.
     */
    private final String serviceEndpoint;

    /**
     * Tracer instance.
     */
    private final Tracer tracer;

    /**
     * Create a new {@code BackendServiceClient} instance.
     *
     * @param restClient the JAXRS {@code Client} to use
     * @param config     the Helidon {@code Config} to use
     */
    public BackendServiceClient(final Client restClient, final Config config) {
        this.client = restClient;
        this.serviceEndpoint =
                config.get("services.backend.endpoint").asString().get();
        this.tracer = Tracer.global();
    }

    /**
     * Retrieve all TODO entries from the backend.
     *
     * @param spanContext {@code SpanContext} to use
     * @return future with all records
     */
    public CompletionStage<JsonArray> getAll(final SpanContext spanContext) {
        Span span = tracer.spanBuilder("todos.get-all")
                .parent(spanContext)
                .start();

        CompletionStage<JsonArray> result = client.target(serviceEndpoint + "/api/backend")
                .request()
                .property(CURRENT_SPAN_CONTEXT_PROPERTY_NAME, spanContext)
                .rx()
                .get(JsonArray.class);

        // I want to finish my span once the result is received, and report error if failed
        result.thenAccept(ignored -> span.end())
                .exceptionally(t -> {
                    span.end(t);
                    LOGGER.log(Level.WARNING,
                               "Failed to invoke getAll() on "
                                       + serviceEndpoint + "/api/backend", t);
                    return null;
                });

        return result;
    }

    /**
     * Retrieve the TODO entry identified by the given ID.
     *
     * @param id the ID identifying the entry to retrieve
     * @return retrieved entry as a {@code JsonObject}
     */
    public CompletionStage<Optional<JsonObject>> getSingle(final String id) {

        return client.target(serviceEndpoint + "/api/backend/" + id)
                .request()
                .rx()
                .get()
                .thenApply(this::processSingleEntityResponse);
    }

    /**
     * Delete the TODO entry identified by the given ID.
     *
     * @param id the ID identifying the entry to delete
     * @return deleted entry as a {@code JsonObject}
     */
    public CompletionStage<Optional<JsonObject>> deleteSingle(final String id) {

        return client
                .target(serviceEndpoint + "/api/backend/" + id)
                .request()
                .rx()
                .delete()
                .thenApply(this::processSingleEntityResponse);
    }

    /**
     * Create a new TODO entry.
     *
     * @param json the new entry value to create as {@code JsonObject}
     * @param sc   {@code SecurityContext} to use
     * @return created entry as {@code JsonObject}
     */
    public CompletionStage<Optional<JsonObject>> create(final JsonObject json, final SecurityContext sc) {

        return client
                .target(serviceEndpoint + "/api/backend/")
                .property(ClientSecurity.PROPERTY_CONTEXT, sc)
                .request()
                .rx()
                .post(Entity.json(json))
                .thenApply(this::processSingleEntityResponse);
    }

    /**
     * Update a TODO entry identifying by the given ID.
     *
     * @param sc   {@code SecurityContext} to use
     * @param id   the ID identifying the entry to update
     * @param json the update entry value as {@code JsonObject}
     * @param res  updated entry as {@code JsonObject}
     */
    public void update(final SecurityContext sc,
                       final String id,
                       final JsonObject json,
                       final ServerResponse res) {

        client.target(serviceEndpoint + "/api/backend/" + id)
                .property(ClientSecurity.PROPERTY_CONTEXT, sc)
                .request()
                .buildPut(Entity.json(json))
                .submit(new InvocationCallback<Response>() {
                    @Override
                    public void completed(final Response response) {
                        try (response) {
                            if (response.getStatusInfo().getFamily() == Status.Family.SUCCESSFUL) {
                                res.send(response.readEntity(JsonObject.class));
                            } else {
                                res.status(response.getStatus());
                            }
                        }
                    }

                    @Override
                    public void failed(final Throwable throwable) {
                        res.status(Http.Status.INTERNAL_SERVER_ERROR_500);
                        res.send();
                    }
                });

    }

    /**
     * Wrap the response entity in an {@code Optional}.
     *
     * @param response {@code Reponse} to process
     * @return empty optional if response status is {@code 404}, optional of
     *         the response entity otherwise
     */
    private Optional<JsonObject> processSingleEntityResponse(final Response response) {
        try (response) {
            if (response.getStatusInfo().toEnum() == Status.NOT_FOUND) {
                return Optional.empty();
            }
            return Optional.of(response.readEntity(JsonObject.class));
        }
    }
}
