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
class GreetingEndpoint {

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
    @OpenApi.Server(value = "https://api.example.com/greetings", description = "Operation server")
    @OpenApi.ExternalDocs(value = "https://helidon.io/docs/openapi", description = "Operation documentation")
    @OpenApi.Extension(name = "x-test-operation", value = "documented-greeting")
    @OpenApi.SecurityRequirement({"bearerAuth", "oauth2"})
    @OpenApi.SecurityRequirement(value = "oauth2", scopes = "greeting:read")
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

    @Http.POST
    @Http.Consumes(MediaTypes.APPLICATION_JSON_VALUE)
    @Http.Produces(MediaTypes.APPLICATION_JSON_VALUE)
    @RestServer.Status(Status.CREATED_201_CODE)
    Message create(@Http.Entity MessageRequest request) {
        return new Message(request.prefix() + " " + request.name());
    }

    @Http.GET
    @Http.Path("/optional/{name}")
    @Http.Produces(MediaTypes.APPLICATION_JSON_VALUE)
    Optional<Message> maybeFind(@Http.PathParam("name") String name) {
        return name.isBlank() ? Optional.empty() : Optional.of(new Message("Hello " + name));
    }

    @Http.GET
    @Http.Path("/internal")
    @OpenApi.Hidden
    String internal() {
        return "internal";
    }

    private static String message(String prefix, String name, Optional<String> language) {
        return language.map(it -> prefix + " " + name + " in " + it)
                .orElse(prefix + " " + name);
    }
}
