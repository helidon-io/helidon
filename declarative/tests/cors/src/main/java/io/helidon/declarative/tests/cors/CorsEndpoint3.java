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

package io.helidon.declarative.tests.cors;

import io.helidon.http.Http;
import io.helidon.http.Status;
import io.helidon.service.registry.Service;
import io.helidon.webserver.http.RestServer;

@SuppressWarnings("deprecation")
@RestServer.Endpoint
@Service.Singleton
@Http.Path("/cors3")
class CorsEndpoint3 {
    CorsEndpoint3() {
    }

    @Http.GET
    String get() {
        return "get";
    }

    @Http.DELETE
    @RestServer.Status(Status.ACCEPTED_202_CODE)
    void delete() {
    }

    @Http.PUT
    @RestServer.Status(Status.ACCEPTED_202_CODE)
    void put() {
    }
}
