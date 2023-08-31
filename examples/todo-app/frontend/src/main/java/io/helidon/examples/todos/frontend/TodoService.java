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
package io.helidon.examples.todos.frontend;

import io.helidon.http.Http;
import io.helidon.metrics.api.Counter;
import io.helidon.metrics.api.Meter;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.Metrics;
import io.helidon.tracing.Span;
import io.helidon.tracing.Tracer;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http.HttpService;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

import jakarta.json.JsonObject;

/**
 * TODO service.
 * <p>
 * An entry is structured as follows:
 * <code>{ 'title': string, 'completed': boolean, 'id': string }</code>
 * <p>
 * The IDs are server generated on the initial POST operation (so they are not
 * included in that case).
 * <p>
 * Here is a summary of the operations:
 * <code>GET /api/todo</code>: Get all entries
 * <code>GET /api/todo/{id}</code>: Get an entry by ID
 * <code>POST /api/todo</code>: Create a new entry, created entry is returned
 * <code>DELETE /api/todo/{id}</code>: Delete an entry, deleted entry is returned
 * <code>PUT /api/todo/{id}</code>: Update an entry,  updated entry is returned
 */
public final class TodoService implements HttpService {

    private final BackendServiceClient bsc;
    private final Counter createCounter;
    private final Counter updateCounter;
    private final Counter deleteCounter;

    /**
     * Create a new {@code TodosHandler} instance.
     *
     * @param bsc the {@code BackendServiceClient} to use
     */
    TodoService(BackendServiceClient bsc) {
        MeterRegistry registry = Metrics.globalRegistry();

        this.bsc = bsc;
        this.createCounter = registry.getOrCreate(Counter.builder("created"));
        this.updateCounter = registry.getOrCreate(Counter.builder("updates"));
        this.deleteCounter = registry.getOrCreate(counterMetadata("deletes", "Number of deleted todos"));
    }

    @Override
    public void routing(HttpRules rules) {
        rules.get("/todo/{id}", this::get)
                .delete("/todo/{id}", this::delete)
                .put("/todo/{id}", this::update)
                .get("/todo", this::list)
                .post("/todo", this::create);
    }

    private Counter.Builder counterMetadata(String name, String description) {
        return Counter.builder(name)
                .description(description)
                .baseUnit(Meter.BaseUnits.NONE);
    }

    /**
     * Handler for {@code POST /todo}.
     *
     * @param req the server request
     * @param res the server response
     */
    private void create(ServerRequest req, ServerResponse res) {
        JsonObject jsonObject = bsc.create(req.content().as(JsonObject.class));
        createCounter.increment();
        res.status(Http.Status.CREATED_201);
        res.send(jsonObject);
    }

    /**
     * Handler for {@code GET /todo}.
     *
     * @param req the server request
     * @param res the server response
     */
    private void list(ServerRequest req, ServerResponse res) {
        res.send(bsc.list());
    }

    /**
     * Handler for {@code PUT /todo/id}.
     *
     * @param req the server request
     * @param res the server response
     */
    private void update(ServerRequest req, ServerResponse res) {
        String id = req.path().pathParameters().value("id");
        JsonObject jsonObject = bsc.update(id, req.content().as(JsonObject.class));
        updateCounter.increment();
        res.send(jsonObject);
    }

    /**
     * Handler for {@code DELETE /todo/id}.
     *
     * @param req the server request
     * @param res the server response
     */
    private void delete(ServerRequest req, ServerResponse res) {
        String id = req.path().pathParameters().value("id");
        JsonObject jsonObject = bsc.deleteSingle(id);
        deleteCounter.increment();
        res.send(jsonObject);
    }

    /**
     * Handler for {@code GET /todo/id}.
     *
     * @param req the server request
     * @param res the server response
     */
    private void get(ServerRequest req, ServerResponse res) {
        String id = req.path().pathParameters().value("id");
        res.send(bsc.get(id));
    }
}
