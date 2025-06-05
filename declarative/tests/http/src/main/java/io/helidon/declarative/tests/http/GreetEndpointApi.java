/*
 * Copyright (c) 2024, 2025 Oracle and/or its affiliates.
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

package io.helidon.declarative.tests.http;

import java.util.Optional;

import io.helidon.common.media.type.MediaTypes;
import io.helidon.http.HeaderNames;
import io.helidon.http.Http;
import io.helidon.webclient.api.RestClient;
import io.helidon.webserver.http.RestServer;

import jakarta.json.JsonObject;

@Http.Path("/greet")
interface GreetEndpointApi {
    /**
     * Return a worldly greeting message.
     */
    @Http.GET
    @Http.Produces(MediaTypes.APPLICATION_JSON_VALUE)
    JsonObject getDefaultMessageHandler();

    @Http.GET
    @Http.Path("/ft/fallback")
    String failingFallback(@Http.HeaderParam(HeaderNames.HOST_NAME) String host);

    @Http.GET
    @Http.Path("/ft/retry")
    String retriable();

    @Http.GET
    @Http.Path("/ft/breaker")
    String breaker();

    @Http.GET
    @Http.Path("/ft/timeout")
    String timeout(@Http.QueryParam("sleepSeconds") Optional<Integer> sleep);

    /**
     * Return a greeting message using the name that was provided.
     */
    @Http.GET
    @Http.Path("/{name}")
    @Http.Produces(MediaTypes.APPLICATION_JSON_VALUE)
    @RestServer.Header(name = "X-Server", value = "server")
    JsonObject getMessageHandler(@Http.PathParam("name") String name);

    /**
     * Set the greeting to use in future messages.
     *
     * @param greetingMessage the entity
     */
    @Http.PUT
    @Http.Path("/greeting")
    @Http.Consumes(MediaTypes.APPLICATION_JSON_VALUE)
    void updateGreetingHandler(@Http.Entity JsonObject greetingMessage);

    /**
     * Set the greeting to use in future messages.
     *
     * @param greetingMessage the entity
     * @return Hello World message
     */
    @Http.POST
    @Http.Path("/greeting/returning")
    @Http.Consumes(MediaTypes.APPLICATION_JSON_VALUE)
    @Http.Produces(MediaTypes.APPLICATION_JSON_VALUE)
    @RestClient.Header(name = "X-First", value = "first")
    @RestClient.Header(name = "X-Second", value = "second")
    JsonObject updateGreetingHandlerReturningCurrent(@Http.Entity JsonObject greetingMessage);
}
