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

package io.helidon.declarative.tests.http;

import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.http.Http;
import io.helidon.http.HttpException;
import io.helidon.http.Status;
import io.helidon.service.registry.Service;
import io.helidon.webserver.http.RestServer;

@RestServer.Endpoint
@Service.Singleton
class InheritedFtEndpoint {
    private static final AtomicInteger CALLS = new AtomicInteger();

    @Service.Inject
    InheritedFtEndpoint() {
    }

    static void reset() {
        CALLS.set(0);
    }

    static int calls() {
        return CALLS.get();
    }

    @Http.GET
    @Http.Path("/inherited-ft/retry")
    String retry() {
        if (CALLS.incrementAndGet() == 1) {
            throw new HttpException("Retry me", Status.SERVICE_UNAVAILABLE_503);
        }
        return "retried";
    }
}
