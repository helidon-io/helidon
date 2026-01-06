/*
 * Copyright (c) 2025, 2026 Oracle and/or its affiliates.
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

package io.helidon.declarative.tests.tracing;

import io.helidon.http.Http;
import io.helidon.http.NotFoundException;
import io.helidon.service.registry.Service;
import io.helidon.tracing.Span;
import io.helidon.tracing.Tracing;
import io.helidon.webserver.http.RestServer;

@SuppressWarnings("deprecation")
@RestServer.Endpoint
@Http.Path("/endpoint")
@Service.Singleton
@Tracing.Traced(tags = @Tracing.Tag(key = "endpoint", value = "Test"), kind = Span.Kind.SERVER)
class TestEndpoint {
    @Service.Inject
    TestEndpoint() {
    }

    @Http.GET
    @Http.Path("/greet")
    @Tracing.Traced(value = "explicit-name", tags = @Tracing.Tag(key = "custom", value = "customValue"))
    String greet(@Http.HeaderParam("User-Agent") @Tracing.ParamTag String userAgent) {
        return "Hello!";
    }

    @Http.GET
    @Http.Path("/traced")
    String traced() {
        return "traced";
    }

    @Http.GET
    @Http.Path("/failed")
    String failed() {
        throw new NotFoundException("Bad bad");
    }
}
