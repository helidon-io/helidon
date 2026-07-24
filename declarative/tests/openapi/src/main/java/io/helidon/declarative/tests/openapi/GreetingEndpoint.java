/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.declarative.tests.openapi;

import java.util.List;
import java.util.Optional;

import io.helidon.common.media.type.MediaTypes;
import io.helidon.http.Http;
import io.helidon.http.Status;
import io.helidon.openapi.OpenApi;
import io.helidon.webserver.http.RestServer;

/**
 * Greeting endpoint used by OpenAPI generation tests.
 */
@RestServer.Endpoint
@Http.Path("/greetings")
@OpenApi.SecuritySchemeRequirement("bearerAuth")
class GreetingEndpoint {
    private static final String ACCEPTED_MEDIA_TYPE = "application/vnd.greeting+json";

    @Http.GET
    @Http.Path("/{name}")
    @Http.Produces(MediaTypes.APPLICATION_JSON_VALUE)
    Message find(@Http.PathParam("name") String name,
                 @Http.QueryParam("language") Optional<String> language,
                 @Http.QueryParam("include") List<String> include) {
        return new Message(message("Hello", name, language));
    }

    @Http.GET
    @Http.Path("/documented/{name}")
    @Http.Produces(MediaTypes.APPLICATION_JSON_VALUE)
    @OpenApi.Operation(value = "Find a greeting",
                       operationId = "findDocumentedGreeting",
                       description = "Returns a documented greeting.",
                       tags = {"greeting", "documented"},
                       deprecated = true)
    @OpenApi.Server(value = "https://{region}.api.example.com/greetings",
                    description = "Operation server",
                    variables = @OpenApi.ServerVariable(name = "region",
                                                        defaultValue = "us",
                                                        enumeration = {"us", "eu"}))
    @OpenApi.ExternalDocs(value = "https://helidon.io/docs/openapi", description = "Operation documentation")
    @OpenApi.Extension(name = "x-test-operation", value = "documented-greeting")
    @OpenApi.SecurityRequirement({
            @OpenApi.SecuritySchemeRequirement("bearerAuth"),
            @OpenApi.SecuritySchemeRequirement(value = "oauth2", scopes = "greeting:read")
    })
    @OpenApi.Response(status = Status.OK_200_CODE,
                      description = "Greeting found",
                      content = @OpenApi.Content)
    Message documented(@OpenApi.Parameter(value = "Greeting recipient", example = "Tomas")
                       @Http.PathParam("name") String name) {
        return new Message("Hello " + name);
    }

    @Http.GET
    @Http.Path("/public")
    @Http.Produces(MediaTypes.APPLICATION_JSON_VALUE)
    @OpenApi.SecurityRequirements({})
    Message publicGreeting() {
        return new Message("Hello everybody");
    }

    @Http.GET
    @Http.Path("/responses")
    @Http.Produces(MediaTypes.APPLICATION_JSON_VALUE)
    @RestServer.Status(Status.ACCEPTED_202_CODE)
    @RestServer.Header(name = "Content-Type", value = ACCEPTED_MEDIA_TYPE)
    @RestServer.Header(name = "X-Static", value = "static")
    @RestServer.ComputedHeader(name = "Content-Type", function = ResponseHeaderFunction.SERVICE_NAME)
    @RestServer.ComputedHeader(name = ResponseHeaderFunction.HEADER_NAME, function = ResponseHeaderFunction.SERVICE_NAME)
    @OpenApi.Response(status = Status.ACCEPTED_202_CODE,
                      description = "Accepted greeting",
                      summary = "Accepted response",
                      headers = @OpenApi.Header(name = "X-Documented",
                                                value = "Documented response header",
                                                required = OpenApi.Required.TRUE,
                                                deprecated = true),
                      links = @OpenApi.Link(name = "documentedGreeting",
                                            operationId = "findDocumentedGreeting",
                                            parameters = @OpenApi.LinkParameter(name = "name",
                                                                                value = "$response.body#/message"),
                                            requestBody = "$response.body",
                                            description = "Follow the documented greeting"),
                      content = @OpenApi.Content(value = ACCEPTED_MEDIA_TYPE,
                                                 schema = Message.class,
                                                 examples = @OpenApi.Example(name = "accepted-response",
                                                                             summary = "Accepted example",
                                                                             value = "{\"message\":\"Accepted\"}")))
    Message responses() {
        return new Message("Accepted");
    }

    @Http.GET
    @Http.Path("/parameters/{id}")
    @Http.Produces(MediaTypes.APPLICATION_JSON_VALUE)
    @OpenApi.Parameter(name = "search",
                       in = "query",
                       value = "Search text",
                       required = OpenApi.Required.FALSE,
                       style = OpenApi.Style.FORM,
                       explode = OpenApi.Explode.FALSE,
                       allowReserved = true,
                       deprecated = true,
                       examples = @OpenApi.Example(name = "search-example",
                                                   summary = "Search example",
                                                   value = "hi"))
    @OpenApi.Parameter(name = "filter",
                       in = "query",
                       style = OpenApi.Style.PIPE_DELIMITED,
                       explode = OpenApi.Explode.FALSE)
    @OpenApi.Parameter(name = "packed",
                       in = "query",
                       value = "Packed JSON filter",
                       content = @OpenApi.Content(value = MediaTypes.APPLICATION_JSON_VALUE,
                                                  schema = MessageRequest.class,
                                                  examples = @OpenApi.Example(name = "packed-example",
                                                                              value = "{\"prefix\":\"Hello\","
                                                                                      + "\"name\":\"Ada\"}")))
    Message parameters(@OpenApi.Parameter(value = "Greeting identifier", example = "42")
                       @Http.PathParam("id") String id,
                       @Http.QueryParam("search") Optional<String> search,
                       @Http.QueryParam("filter") List<String> filter,
                       @Http.QueryParam("packed") String packed,
                       @OpenApi.Parameter(value = "Trace header",
                                          required = OpenApi.Required.FALSE,
                                          style = OpenApi.Style.SIMPLE,
                                          explode = OpenApi.Explode.FALSE,
                                          examples = @OpenApi.Example(name = "trace-example", value = "abc-123"))
                       @Http.HeaderParam("X-Trace") Optional<String> trace,
                       @Http.HeaderParam("X-Modes") List<String> modes) {
        return new Message("Hello " + id);
    }

    @Http.POST
    @Http.Consumes(MediaTypes.APPLICATION_JSON_VALUE)
    @Http.Produces(MediaTypes.APPLICATION_JSON_VALUE)
    @RestServer.Status(Status.CREATED_201_CODE)
    @OpenApi.RequestBody(value = "Greeting payload",
                         required = OpenApi.Required.TRUE,
                         content = @OpenApi.Content(value = MediaTypes.APPLICATION_JSON_VALUE,
                                                    examples = @OpenApi.Example(name = "create-request",
                                                                                value = "{\"prefix\":\"Hello\","
                                                                                        + "\"name\":\"Ada\"}")))
    Message create(@Http.Entity MessageRequest request) {
        return new Message(request.prefix() + " " + request.name());
    }

    @Http.PUT
    @Http.Path("/inferred-body")
    @Http.Consumes(MediaTypes.APPLICATION_JSON_VALUE)
    @Http.Produces(MediaTypes.APPLICATION_JSON_VALUE)
    @OpenApi.RequestBody(value = "Inferred greeting payload", required = OpenApi.Required.FALSE)
    Message inferredBody(@Http.Entity MessageRequest request) {
        return new Message(request.prefix() + " " + request.name());
    }

    @Http.POST
    @Http.Path("/explicit-request-schema")
    @Http.Consumes(MediaTypes.APPLICATION_JSON_VALUE)
    @Http.Produces(MediaTypes.APPLICATION_JSON_VALUE)
    @OpenApi.RequestBody(value = "Explicit request schema",
                         content = @OpenApi.Content(value = MediaTypes.APPLICATION_JSON_VALUE,
                                                    schema = MessageRequest.class))
    Message explicitRequestSchema(@Http.Entity InternalPayload request) {
        return new Message(request.value());
    }

    @Http.PUT
    @Http.Path("/request-params/{id}")
    @Http.Consumes(MediaTypes.APPLICATION_JSON_VALUE)
    @Http.Produces(MediaTypes.APPLICATION_JSON_VALUE)
    @OpenApi.Parameter(name = "search", in = "query", value = "Request params search")
    @OpenApi.RequestBody("Request params body")
    Message requestParams(@Http.RequestParams GreetingRequestParams params) {
        return new Message(params.id() + params.search() + params.trace() + params.request().name());
    }

    @Http.POST
    @Http.Path("/form-cookie")
    @Http.Consumes(MediaTypes.APPLICATION_FORM_URLENCODED_VALUE)
    @Http.Produces(MediaTypes.APPLICATION_JSON_VALUE)
    @OpenApi.Parameter(name = "session", in = "cookie", value = "Session cookie")
    @OpenApi.RequestBody(value = "Greeting form",
                         content = @OpenApi.Content(value = MediaTypes.APPLICATION_FORM_URLENCODED_VALUE,
                                                    examples = @OpenApi.Example(name = "form-example",
                                                                                value = "{\"prefix\":\"Hello\","
                                                                                        + "\"name\":\"Ada\"}")))
    Message formCookie(@Http.CookieParam("session") String session,
                       @Http.FormParam("prefix") String prefix,
                       @Http.RequestParams GreetingFormParams params) {
        return new Message(session + prefix + params.name() + params.tags());
    }

    @Http.GET
    @Http.Path("/constrained/{id:[0-9]+}")
    @Http.Produces(MediaTypes.APPLICATION_JSON_VALUE)
    Message constrained(@Http.PathParam("id") String id) {
        return new Message("Hello " + id);
    }

    @Http.GET
    @Http.Path("/override[/{id}]")
    @Http.Produces(MediaTypes.APPLICATION_JSON_VALUE)
    @OpenApi.Operation(path = "/greetings/override/{id}")
    Message overridePath(@Http.PathParam("id") String id) {
        return new Message("Hello " + id);
    }

    @Http.GET
    @Http.Path("/optional/{name}")
    @Http.Produces(MediaTypes.APPLICATION_JSON_VALUE)
    Optional<Message> maybeFind(@Http.PathParam("name") String name) {
        return name.isBlank() ? Optional.empty() : Optional.of(new Message("Hello " + name));
    }

    @Http.DELETE
    @Http.Path("/{name}")
    void remove(@Http.PathParam("name") String name) {
    }

    @Http.GET
    @Http.Path("/internal")
    @OpenApi.Hidden
    String internal() {
        return "internal";
    }

    record GreetingRequestParams(@Http.PathParam("id") String id,
                                 @Http.QueryParam("search") String search,
                                 @Http.HeaderParam("X-Trace") String trace,
                                 @Http.Entity MessageRequest request) {
    }

    record GreetingFormParams(@Http.CookieParam("tracking") Optional<String> tracking,
                              @Http.FormParam("name") String name,
                              @Http.FormParam("tag") Optional<List<String>> tags) {
    }

    private static String message(String prefix, String name, Optional<String> language) {
        return language.map(it -> prefix + " " + name + " in " + it)
                .orElse(prefix + " " + name);
    }
}
