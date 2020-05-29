/*
 * Copyright (c) 2018, 2020 Oracle and/or its affiliates.
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
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import javax.json.JsonObject;

import io.helidon.common.http.Http;
import io.helidon.metrics.RegistryFactory;
import io.helidon.security.SecurityContext;
import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;
import io.helidon.webserver.Service;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.MetricType;
import org.eclipse.microprofile.metrics.MetricUnits;

/**
 * Handler of requests to process TODOs.
 *
 * A TODO is structured as follows:
 * <code>{ 'title': string, 'completed': boolean, 'id': string }</code>
 *
 * The IDs are server generated on the initial POST operation (so they are not
 * included in that case).
 *
 * Here is a summary of the operations:
 * <code>GET /api/todo</code>: Get all TODOs
 * <code>GET /api/todo/{id}</code>: Get a TODO
 * <code>POST /api/todo</code>: Create a new TODO, TODO with generated ID
 * is returned
 * <code>DELETE /api/todo/{id}</code>: Delete a TODO, deleted TODO is returned
 * <code>PUT /api/todo/{id}</code>: Update a TODO,  updated TODO is returned
 */
public final class TodosHandler implements Service {

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
     * Create a new {@code TodosHandler} instance.
     *
     * @param bsc the {@code BackendServiceClient} to use
     */
    public TodosHandler(BackendServiceClient bsc) {
        MetricRegistry registry = RegistryFactory.getInstance().getRegistry(MetricRegistry.Type.APPLICATION);

        this.bsc = bsc;
        this.createCounter = registry.counter("created");

        this.updateCounter = registry.counter("updates");

        this.deleteCounter = registry.counter(counterMetadata("deletes",
                                                              "Number of deleted todos"));
    }

    private Metadata counterMetadata(String name, String description) {
        return Metadata.builder()
                .withName(name)
                .withDisplayName(name)
                .withDescription(description)
                .withType(MetricType.COUNTER)
                .withUnit(MetricUnits.NONE)
                .build();
    }

    @Override
    public void update(final Routing.Rules rules) {
        rules.get("/todo/{id}", this::getSingle)
                .delete("/todo/{id}", this::delete)
                .put("/todo/{id}", this::update)
                .get("/todo", this::getAll)
                .post("/todo", this::create);
    }

    /**
     * Handler for {@code POST /todo}.
     *
     * @param req the server request
     * @param res the server response
     */
    private void create(final ServerRequest req, final ServerResponse res) {
        secure(req, res, sc -> json(req, res, json -> {
            createCounter.inc();
            bsc.create(json, sc)
                    .thenAccept(created -> sendResponse(res, created, Http.Status.INTERNAL_SERVER_ERROR_500))
                    .exceptionally(t -> sendError(t, res));
        }));
    }

    /**
     * Handler for {@code GET /todo}.
     *
     * @param req the server request
     * @param res the server response
     */
    private void getAll(final ServerRequest req, final ServerResponse res) {
        AtomicReference<Span> createdSpan = new AtomicReference<>();

        SpanContext spanContext = req.spanContext().orElseGet(() -> {
           Span mySpan = req.tracer().buildSpan("getAll").start();
           createdSpan.set(mySpan);
           return mySpan.context();
        });
        secure(req, res, sc -> {
            bsc.getAll(spanContext)
                    .thenAccept(res::send)
                    .exceptionally(t -> sendError(t, res))
                    .whenComplete((noting, throwable) -> {
                       Span mySpan = createdSpan.get();
                       if (null != mySpan) {
                           mySpan.finish();
                       }
                    });
        });
    }

    /**
     * Handler for {@code PUT /todo/id}.
     *
     * @param req the server request
     * @param res the server response
     */
    private void update(final ServerRequest req, final ServerResponse res) {
        secure(req, res, sc -> json(req, res, json -> {
            updateCounter.inc();
            // example of asynchronous processing
            bsc.update(sc,
                    req.path().param("id"), json, res);
        }));
    }

    /**
     * Handler for {@code DELETE /todo/id}.
     *
     * @param req the server request
     * @param res the server response
     */
    private void delete(final ServerRequest req, final ServerResponse res) {
        secure(req, res, sc -> {
            deleteCounter.inc();
            bsc.deleteSingle(req.path().param("id"))
                    .thenAccept(json -> sendResponse(res, json, Http.Status.NOT_FOUND_404))
                    .exceptionally(throwable -> sendError(throwable, res));
        });
    }

    /**
     * Handler for {@code GET /todo/id}.
     *
     * @param req the server request
     * @param res the server response
     */
    private void getSingle(final ServerRequest req, final ServerResponse res) {
        secure(req, res, sc -> {
            bsc.getSingle(req.path().param("id"))
                    .thenAccept(found -> sendResponse(res, found, Http.Status.NOT_FOUND_404))
                    .exceptionally(throwable -> sendError(throwable, res));
        });
    }

    /**
     * Send a response with a {@code 500} status code.
     *
     * @param res the server response
     */
    private void noSecurityContext(final ServerResponse res) {
        res.status(Http.Status.INTERNAL_SERVER_ERROR_500);
        res.send("Security context not present");
    }

    /**
     * Send the response entity if {@code jsonResponse} has a value, otherwise
     * sets the status to {@code failureStatus}.
     *
     * @param res           the server response
     * @param jsonResponse  the response entity
     * @param failureStatus the status to use if {@code jsonResponse} is empty
     */
    private void sendResponse(final ServerResponse res,
                              final Optional<? extends JsonObject> jsonResponse,
                              final Http.Status failureStatus) {

        jsonResponse
                .ifPresentOrElse(res::send, () -> res.status(failureStatus));
    }

    /**
     * Reads a request entity as {@JsonObject}, and if successful invoke the
     * given consumer, otherwise terminate the request with a {@code 500}
     * status code.
     *
     * @param req  the server request
     * @param res  the server response
     * @param json the {@code JsonObject} consumer
     */
    private void json(final ServerRequest req,
                      final ServerResponse res,
                      final Consumer<JsonObject> json) {

        req.content()
                .as(JsonObject.class)
                .thenAccept(json)
                .exceptionally(throwable -> sendError(throwable, res));
    }

    /**
     * Reads the request security context, and if successful invoke the given
     * consumer, otherwise terminate the request with a {@code 500}
     * status code.
     *
     * @param req the server request
     * @param res the server response
     * @param ctx the {@code SecurityContext} consumer
     */
    private void secure(final ServerRequest req,
                        final ServerResponse res,
                        final Consumer<SecurityContext> ctx) {

        req.context()
                .get(SecurityContext.class)
                .ifPresentOrElse(ctx, () -> noSecurityContext(res));
    }

    private static Void sendError(Throwable throwable, ServerResponse res) {
        res.status(Http.Status.INTERNAL_SERVER_ERROR_500);
        res.send(throwable.getClass().getName()
                         + ": " + throwable.getMessage());
        return null;
    }
}
