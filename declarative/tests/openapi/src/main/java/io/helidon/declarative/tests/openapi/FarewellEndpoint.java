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

import io.helidon.common.media.type.MediaTypes;
import io.helidon.http.Http;
import io.helidon.webserver.http.RestServer;

/**
 * Farewell endpoint used to verify operation ids across endpoint classes with the same method names.
 */
@RestServer.Endpoint
@Http.Path("/farewells")
class FarewellEndpoint {

    @Http.GET
    @Http.Path("/{name}")
    @Http.Produces(MediaTypes.APPLICATION_JSON_VALUE)
    Message find(@Http.PathParam("name") String name) {
        return new Message("Goodbye " + name);
    }

    @Http.POST
    @Http.Consumes(MediaTypes.APPLICATION_JSON_VALUE)
    @Http.Produces(MediaTypes.APPLICATION_JSON_VALUE)
    Message create(@Http.Entity MessageRequest request) {
        return new Message("Goodbye " + request.name());
    }

    @Http.POST
    @Http.Path("/plain")
    @Http.Consumes(MediaTypes.TEXT_PLAIN_VALUE)
    @Http.Produces(MediaTypes.TEXT_PLAIN_VALUE)
    String createPlain(@Http.Entity String message) {
        return "Goodbye " + message;
    }
}
