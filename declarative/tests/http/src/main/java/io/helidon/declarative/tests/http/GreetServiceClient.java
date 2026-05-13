/*
 * Copyright (c) 2024, 2026 Oracle and/or its affiliates.
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
import io.helidon.http.Http;
import io.helidon.json.JsonObject;
import io.helidon.webclient.api.RestClient;

/**
 * API for typed client for the Greet endpoint HTTP API.
 */
@SuppressWarnings("deprecation")
@RestClient.Endpoint("${greet-service.client.uri:http://localhost:8080}")
public interface GreetServiceClient extends GreetService {
    /**
     * Return a worldly greeting message.
     *
     * @return greeting
     */
    @Http.GET
    @Http.Produces(MediaTypes.TEXT_PLAIN_VALUE)
    @RestClient.ComputedHeader(name = ClientHeaderFunction.HEADER_NAME, function = ClientHeaderFunction.SERVICE_NAME)
    String getDefaultMessageHandlerPlain();

    @Http.GET
    @Http.Path("/optional-present/{name}")
    @Http.Produces(MediaTypes.APPLICATION_JSON_VALUE)
    Optional<JsonObject> optionalMessage(@Http.PathParam("name") String name);

    @Http.GET
    @Http.Path("/optional/empty")
    @Http.Produces(MediaTypes.APPLICATION_JSON_VALUE)
    Optional<JsonObject> optionalMessageEmpty();

    @Http.GET
    @Http.Path("/optional/not-found")
    @Http.Produces(MediaTypes.APPLICATION_JSON_VALUE)
    Optional<JsonObject> optionalMessageNotFound();

    @Http.GET
    @Http.Path("/optional/not-found/handled")
    @Http.Produces(MediaTypes.APPLICATION_JSON_VALUE)
    Optional<JsonObject> optionalMessageNotFoundHandled();
}
