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
import io.helidon.declarative.tests.openapi.external.Message;
import io.helidon.http.Http;
import io.helidon.webserver.http.RestServer;

/**
 * Endpoint with a schema type whose simple name collides with schemas used by other endpoint sources.
 */
@RestServer.Endpoint
@Http.Path("/external-message")
class ExternalMessageEndpoint {

    @Http.GET
    @Http.Produces(MediaTypes.APPLICATION_JSON_VALUE)
    Message get() {
        return new Message("external");
    }
}
