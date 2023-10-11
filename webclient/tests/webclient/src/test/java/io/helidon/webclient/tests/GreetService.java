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

package io.helidon.webclient.tests;

import java.lang.System.Logger.Level;
import java.security.Principal;
import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.common.context.Context;
import io.helidon.common.context.Contexts;
import io.helidon.common.parameters.Parameters;
import io.helidon.common.uri.UriFragment;
import io.helidon.http.HeaderNames;
import io.helidon.http.Status;
import io.helidon.security.SecurityContext;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http1.Http1ClientResponse;
import io.helidon.webclient.security.WebClientSecurity;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http.HttpService;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

import jakarta.json.Json;
import jakarta.json.JsonBuilderFactory;
import jakarta.json.JsonException;
import jakarta.json.JsonObject;

/**
 * A simple service to greet you. Examples:
 * <p>
 * Get default greeting message:
 * curl -X GET http://localhost:8080/greet
 * <p>
 * Get greeting message for Joe:
 * curl -X GET http://localhost:8080/greet/Joe
 * <p>
 * Change greeting
 * curl -X PUT -H "Content-Type: application/json" -d '{"greeting" : "Howdy"}' http://localhost:8080/greet/greeting
 * <p>
 * The message is returned as a JSON object
 */

public class GreetService implements HttpService {

    private static final JsonBuilderFactory JSON = Json.createBuilderFactory(Collections.emptyMap());
    private static final System.Logger LOGGER = System.getLogger(GreetService.class.getName());

    /**
     * The config value for the key {@code greeting}.
     */
    private final AtomicReference<String> greeting = new AtomicReference<>();
    private final Http1Client outboundClient = Http1Client.builder()
            .servicesDiscoverServices(false)
            .addService(WebClientSecurity.create())
            .build();

    GreetService() {
        greeting.set("Hello");
    }

    /**
     * A service registers itself by updating the routing rules.
     *
     * @param rules the routing rules.
     */
    @Override
    public void routing(HttpRules rules) {
        rules
                .get("/", this::getDefaultMessageHandler)
                .get("/redirect", this::redirect)
                .get("/redirectPath", this::redirectPath)
                .get("/redirect/infinite", this::redirectInfinite)
                .post("/form", this::form)
                .post("/form/content", this::formContent)
                .post("/contentLength", this::contentLength)
                .put("/contentLength", this::contentLength)
                .get("/contentLength", this::contentLength)
                .get("/secure/basic", this::basicAuth)
                .get("/secure/basic/outbound", this::basicAuthOutbound)
                .get("/valuesPropagated", this::valuesPropagated)
                .get("/obtainedQuery", this::obtainedQuery)
                .get("/pattern with space", this::getDefaultMessageHandler)
                .put("/greeting", this::updateGreetingHandler)
                .get("/contextCheck", this::contextCheck);
    }

    private void contentLength(ServerRequest serverRequest, ServerResponse serverResponse) {
        serverRequest.headers().contentLength()
                .ifPresentOrElse(value -> serverResponse.send(HeaderNames.CONTENT_LENGTH + " is " + value),
                        () -> serverResponse.send("No " + HeaderNames.CONTENT_LENGTH + " has been set"));
    }

    private void basicAuthOutbound(ServerRequest request, ServerResponse response) {

        try (Http1ClientResponse clientResponse = outboundClient.get("http://localhost:"
                                                                             + request.requestedUri().port()
                                                                             + "/greet/secure/basic")
                .request()) {
            response.status(clientResponse.status());
            if (clientResponse.status() == Status.OK_200) {
                response.send(clientResponse.entity().as(String.class));
            } else {
                response.send();
            }
        }
    }

    private void valuesPropagated(ServerRequest serverRequest, ServerResponse serverResponse) {
        String queryParam = serverRequest.query().first("param").orElse("Query param not present");
        String fragment = serverRequest.prologue().fragment().value();
        serverResponse.status(Status.OK_200);
        serverResponse.send(queryParam + " " + fragment);
    }

    private void obtainedQuery(ServerRequest serverRequest, ServerResponse serverResponse) {
        String queryParam = serverRequest.query().first("param").orElse("Query param not present");
        String queryValue = serverRequest.query().first(queryParam).orElse("Query " + queryParam + " param not present");
        UriFragment uriFragment = serverRequest.prologue().fragment();
        String fragment = uriFragment.hasValue() ? uriFragment.value() : null;
        serverResponse.status(Status.OK_200);
        serverResponse.send(queryValue + " " + (fragment == null ? "" : fragment));
    }

    private void basicAuth(ServerRequest serverRequest, ServerResponse response) {
        String name = serverRequest.context()
                .get(SecurityContext.class)
                .flatMap(SecurityContext::userPrincipal)
                .map(Principal::getName)
                .orElse("Anonymous");

        sendResponse(response, name);
    }

    /**
     * Return a worldly greeting message.
     *
     * @param request  the server request
     * @param response the server response
     */
    private void getDefaultMessageHandler(ServerRequest request,
                                          ServerResponse response) {
        sendResponse(response, "World");
    }

    /**
     * Return a greeting message using the name that was provided.
     *
     * @param request  the server request
     * @param response the server response
     */
    private void redirect(ServerRequest request, ServerResponse response) {
        response.headers().add(HeaderNames.LOCATION, "http://localhost:" + request.requestedUri().port() + "/greet");
        response.status(Status.MOVED_PERMANENTLY_301).send();
    }

    private void redirectPath(ServerRequest request, ServerResponse response) {
        response.headers().add(HeaderNames.LOCATION, "/greet");
        response.status(Status.MOVED_PERMANENTLY_301).send();
    }

    private void redirectInfinite(ServerRequest request, ServerResponse response) {
        response.headers().add(HeaderNames.LOCATION, "http://localhost:" + request.requestedUri().port() + "/greet/redirect/infinite");
        response.status(Status.MOVED_PERMANENTLY_301).send();
    }

    private void form(ServerRequest req, ServerResponse res) {
        Parameters form = req.content().as(Parameters.class);
        res.send("Hi " + form.first("name").orElse("unknown"));
    }

    private void formContent(ServerRequest req, ServerResponse res) {
        Parameters form = req.content().as(Parameters.class);
        res.send(form);
    }

    /**
     * Set the greeting to use in future messages.
     *
     * @param request  the server request
     * @param response the server response
     */
    private void updateGreetingHandler(ServerRequest request,
                                       ServerResponse response) {
        try {
            JsonObject jsonObject = request.content().as(JsonObject.class);
            updateGreetingFromJson(jsonObject, response);
        } catch (JsonException ex) {
            LOGGER.log(Level.DEBUG, "Invalid JSON", ex);
            JsonObject jsonErrorObject = JSON.createObjectBuilder()
                    .add("error", "Invalid JSON")
                    .build();
            response.status(Status.BAD_REQUEST_400).send(jsonErrorObject);
        }
    }

    private void sendResponse(ServerResponse response, String name) {
        String msg = String.format("%s %s!", greeting.get(), name);

        JsonObject returnObject = JSON.createObjectBuilder()
                .add("message", msg)
                .build();
        response.send(returnObject);
    }

    private void updateGreetingFromJson(JsonObject jo, ServerResponse response) {

        if (!jo.containsKey("greeting")) {
            JsonObject jsonErrorObject = JSON.createObjectBuilder()
                    .add("error", "No greeting provided")
                    .build();
            response.status(Status.BAD_REQUEST_400)
                    .send(jsonErrorObject);
            return;
        }

        greeting.set(jo.getString("greeting"));
        response.status(Status.NO_CONTENT_204).send();
    }

    /**
     * Checks the existence of a {@code Context} object in a WebClient thread.
     *
     * @param request  the request
     * @param response the response
     */
    private void contextCheck(ServerRequest request, ServerResponse response) {
        Http1Client webClient = Http1Client.builder()
                .baseUri("http://localhost:" + request.requestedUri().port() + "/")
                .build();

        Optional<Context> context = Contexts.context();

        // Verify that context was propagated with auth enabled
        if (context.isEmpty()) {
            response.status(Status.INTERNAL_SERVER_ERROR_500).send();
            return;
        }

        // Register instance in context
        context.get().register(this);

        // Ensure context is available in webclient threads
        try (Http1ClientResponse ignored = webClient.get().request()) {
            Context singleContext = Contexts.context().orElseThrow();
            Objects.requireNonNull(singleContext.get(GreetService.class));
            response.status(Status.OK_200);
            response.send();
        }
    }
}
