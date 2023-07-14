/*
 * Copyright (c) 2018, 2023 Oracle and/or its affiliates.
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

import io.helidon.common.http.Http;
import io.helidon.metrics.api.RegistryFactory;
import io.helidon.nima.webserver.http.HttpRules;
import io.helidon.nima.webserver.http.HttpService;
import io.helidon.nima.webserver.http.ServerRequest;
import io.helidon.nima.webserver.http.ServerResponse;
import io.helidon.tracing.Span;
import io.helidon.tracing.Tracer;

import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.MetricUnits;

/**
 * Handler of requests to process TODOs.
 * <p>
 * A TODO is structured as follows:
 * <code>{ 'title': string, 'completed': boolean, 'id': string }</code>
 * <p>
 * The IDs are server generated on the initial POST operation (so they are not
 * included in that case).
 * <p>
 * Here is a summary of the operations:
 * <code>GET /api/todo</code>: Get all TODOs
 * <code>GET /api/todo/{id}</code>: Get a TODO
 * <code>POST /api/todo</code>: Create a new TODO, TODO with generated ID
 * is returned
 * <code>DELETE /api/todo/{id}</code>: Delete a TODO, deleted TODO is returned
 * <code>PUT /api/todo/{id}</code>: Update a TODO,  updated TODO is returned
 */
public final class TodosHandler implements HttpService {

    /**
     * The backend service client.
     */
    private final BackendServiceClient bsc;

    /**
     * Create metric counter.
     */
    private final Counter createCounter;

    /**
     * Update metric counter.
     */
    private final Counter updateCounter;

    /**
     * Delete metric counter.
     */
    private final Counter deleteCounter;

    /**
     * Tracer.
     */
    private final Tracer tracer;

    /**
     * Create a new {@code TodosHandler} instance.
     *
     * @param bsc    the {@code BackendServiceClient} to use
     * @param tracer tracer
     */
    public TodosHandler(BackendServiceClient bsc, Tracer tracer) {
        MetricRegistry registry = RegistryFactory.getInstance().getRegistry(MetricRegistry.APPLICATION_SCOPE);
        this.tracer = tracer;
        this.bsc = bsc;
        this.createCounter = registry.counter("created");
        this.updateCounter = registry.counter("updates");
        this.deleteCounter = registry.counter(counterMetadata("deletes", "Number of deleted todos"));
    }

    @Override
    public void routing(final HttpRules rules) {
        rules.get("/todo/{id}", this::getSingle)
             .delete("/todo/{id}", this::delete)
             .put("/todo/{id}", this::update)
             .get("/todo", this::getAll)
             .post("/todo", this::create);
    }

    private Metadata counterMetadata(String name, String description) {
        return Metadata.builder()
                       .withName(name)
                       .withDescription(description)
                       .withUnit(MetricUnits.NONE)
                       .build();
    }

    /**
     * Handler for {@code POST /todo}.
     *
     * @param req the server request
     * @param res the server response
     */
    private void create(final ServerRequest req, final ServerResponse res) {
        createCounter.inc();
        JsonObject json = req.content().as(JsonObject.class);
        bsc.create(json)
           .ifPresentOrElse(res::send, () -> res.status(Http.Status.INTERNAL_SERVER_ERROR_500));
    }

    /**
     * Handler for {@code GET /todo}.
     *
     * @param req the server request
     * @param res the server response
     */
    private void getAll(ServerRequest req, ServerResponse res) {
        Span span = Span.current()
                        .map(sp -> tracer.spanBuilder("getAll0").parent(sp.context()).start())
                        .orElseGet(() -> tracer.spanBuilder("getAll").start());

        try {
            JsonArray todos = bsc.getAll(span.context());
            res.send(todos);
        } catch (Throwable th) {
            span.end();
            throw th;
        }
        span.end();
    }

    /**
     * Handler for {@code PUT /todo/id}.
     *
     * @param req the server request
     * @param res the server response
     */
    private void update(final ServerRequest req, final ServerResponse res) {
        updateCounter.inc();
        JsonObject json = req.content().as(JsonObject.class);
        String id = req.path().pathParameters().value("id");
        bsc.update(id, json, res);
    }

    /**
     * Handler for {@code DELETE /todo/id}.
     *
     * @param req the server request
     * @param res the server response
     */
    private void delete(final ServerRequest req, final ServerResponse res) {
        deleteCounter.inc();
        String id = req.path().pathParameters().value("id");
        bsc.deleteSingle(id)
           .ifPresentOrElse(res::send, () -> res.status(Http.Status.NOT_FOUND_404));
    }

    /**
     * Handler for {@code GET /todo/id}.
     *
     * @param req the server request
     * @param res the server response
     */
    private void getSingle(final ServerRequest req, final ServerResponse res) {
        String id = req.path().pathParameters().value("id");
        bsc.getSingle(id)
           .ifPresentOrElse(res::send, () -> res.status(Http.Status.NOT_FOUND_404));
    }
}
