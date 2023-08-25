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
import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;
import io.helidon.webserver.Service;

import jakarta.json.JsonObject;
import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.MetricType;
import org.eclipse.microprofile.metrics.MetricUnits;

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
public final class TodoService implements Service {

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
        MetricRegistry registry = RegistryFactory.getInstance().getRegistry(MetricRegistry.Type.APPLICATION);
        this.bsc = bsc;
        this.createCounter = registry.counter("created");
        this.updateCounter = registry.counter("updates");
        this.deleteCounter = registry.counter(Metadata.builder()
                                                      .withName("deletes")
                                                      .withDisplayName("deletes")
                                                      .withDescription("Number of deleted todos")
                                                      .withType(MetricType.COUNTER)
                                                      .withUnit(MetricUnits.NONE)
                                                      .build());
    }

    @Override
    public void update(Routing.Rules rules) {
        rules.get("/todo/{id}", this::get)
                .delete("/todo/{id}", this::delete)
                .put("/todo/{id}", this::update)
                .get("/todo", this::list)
                .post("/todo", this::create);
    }

    /**
     * Handler for {@code POST /todo}.
     *
     * @param req the server request
     * @param res the server response
     */
    private void create(ServerRequest req, ServerResponse res) {
        req.content()
                .as(JsonObject.class)
                .flatMapSingle(bsc::create)
                .peek(ignored -> createCounter.inc())
                .onError(res::send)
                .forSingle(json -> {
                    res.status(Http.Status.CREATED_201);
                    res.send(json);
                });
    }

    /**
     * Handler for {@code GET /todo}.
     *
     * @param req the server request
     * @param res the server response
     */
    private void list(ServerRequest req, ServerResponse res) {
        bsc.list()
                .onError(res::send)
                .forSingle(res::send);
    }

    /**
     * Handler for {@code PUT /todo/id}.
     *
     * @param req the server request
     * @param res the server response
     */
    private void update(ServerRequest req, ServerResponse res) {
        req.content()
                .as(JsonObject.class)
                .flatMapSingle(json -> bsc.update(req.path().param("id"), json))
                .peek(ignored -> updateCounter.inc())
                .onError(res::send)
                .forSingle(res::send);
    }

    /**
     * Handler for {@code DELETE /todo/id}.
     *
     * @param req the server request
     * @param res the server response
     */
    private void delete(ServerRequest req, ServerResponse res) {
        bsc.deleteSingle(req.path().param("id"))
                .peek(ignored -> deleteCounter.inc())
                .onError(res::send)
                .forSingle(res::send);
    }

    /**
     * Handler for {@code GET /todo/id}.
     *
     * @param req the server request
     * @param res the server response
     */
    private void get(ServerRequest req, ServerResponse res) {
        bsc.get(req.path().param("id"))
                .onError(res::send)
                .forSingle(res::send);
    }
}
