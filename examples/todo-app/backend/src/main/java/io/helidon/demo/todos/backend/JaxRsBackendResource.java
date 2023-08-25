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

package io.helidon.demo.todos.backend;

import java.util.Collections;
import java.util.UUID;

import io.helidon.security.Principal;
import io.helidon.security.SecurityContext;
import io.helidon.security.Subject;
import io.helidon.security.annotations.Authenticated;
import io.helidon.security.annotations.Authorized;

import io.opentracing.Tracer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonBuilderFactory;
import jakarta.json.JsonObject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.opentracing.Traced;

/**
 * The TODO backend REST service.
 */
@Path("/api/backend")
@Authenticated
@Authorized
@ApplicationScoped
public class JaxRsBackendResource {

    private static final JsonBuilderFactory JSON = Json.createBuilderFactory(Collections.emptyMap());

    private final DbService backendService;
    private final Tracer tracer;

    /**
     * Create new {@code JaxRsBackendResource} instance.
     * @param dbs the database service facade to use
     * @param tracer tracer to use
     */
    @Inject
    public JaxRsBackendResource(DbService dbs, Tracer tracer) {
        this.backendService = dbs;
        this.tracer = tracer;
    }

    /**
     * Retrieve all TODO entries.
     *
     * @param context security context to map the user
     * @return the response with the retrieved entries as entity
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Traced(operationName = "jaxrs:list")
    public Response list(@Context SecurityContext context) {
        JsonArrayBuilder builder = JSON.createArrayBuilder();
        backendService.list(tracer.activeSpan().context(), getUserId(context))
                      .forEach(data -> builder.add(data.forRest()));
        return Response.ok(builder.build()).build();
    }

    /**
     * Get the TODO entry identified by the given ID.
     * @param id the ID of the entry to retrieve
     * @param context security context to map the user
     * @return the response with the retrieved entry as entity
     */
    @GET
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response get(@PathParam("id") String id, @Context SecurityContext context) {

        return backendService
                .get(tracer.activeSpan().context(), id, getUserId(context))
                .map(Todo::forRest)
                .map(Response::ok)
                .orElse(Response.status(Response.Status.NOT_FOUND))
                .build();
    }

    /**
     * Delete the TODO entry identified by the given ID.
     * @param id the id of the entry to delete
     * @param context security context to map the user
     * @return the response with the deleted entry as entity
     */
    @DELETE
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response delete(@PathParam("id") String id, @Context SecurityContext context) {

        return backendService
                .delete(tracer.activeSpan().context(), id, getUserId(context))
                .map(Todo::forRest)
                .map(Response::ok)
                .orElse(Response.status(Response.Status.NOT_FOUND))
                .build();
    }

    /**
     * Create a new TODO entry.
     * @param jsonObject the value of the new entry
     * @param context security context to map the user
     * @return the response ({@code 200} status if successful
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response createIt(JsonObject jsonObject, @Context SecurityContext context) {

        String newId = UUID.randomUUID().toString();
        String userId = getUserId(context);
        Todo newBackend = Todo.newTodoFromRest(jsonObject, userId, newId);

        backendService.insert(tracer.activeSpan().context(), newBackend);

        return Response.ok(newBackend.forRest()).build();
    }

    /**
     * Update the TODO entry identified by the given ID.
     * @param id the ID of the entry to update
     * @param jsonObject the updated value of the entry
     * @param context security context to map the user
     * @return the response with the updated entry as entity
     */
    @PUT
    @Path("/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response update(@PathParam("id") String id, JsonObject jsonObject, @Context SecurityContext context) {
        return backendService
                .update(tracer.activeSpan().context(), Todo.fromRest(jsonObject, getUserId(context), id))
                .map(Todo::forRest)
                .map(Response::ok)
                .orElse(Response.status(Response.Status.NOT_FOUND))
                .build();
    }

    /**
     * Get the user id from the security context.
     * @param context the security context
     * @return user id found in the context or {@code <ANONYMOUS>} otherwise
     */
    private String getUserId(SecurityContext context) {
        return context.user()
                .map(Subject::principal)
                .map(Principal::id)
                .orElse("<ANONYMOUS>");
    }
}
