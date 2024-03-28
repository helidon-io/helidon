/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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

package io.helidon.declarative.tests.webserver;

import java.io.IOException;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import io.helidon.common.config.Config;
import io.helidon.faulttolerance.FaultTolerance;
import io.helidon.http.HeaderNames;
import io.helidon.http.Headers;
import io.helidon.http.Http;
import io.helidon.service.inject.api.InjectRegistry;
import io.helidon.service.inject.api.Injection;
import io.helidon.service.registry.ServiceRegistryException;

@Http.Path("/greet")
class GreetEndpoint {
    private final InjectRegistry registry;
    private volatile String greeting;

    @Injection.Inject
    GreetEndpoint(Config config, InjectRegistry serviceRegistry) {
        this.greeting = config.get("app.greeting").asString().orElse("Hello");
        this.registry = serviceRegistry;
    }

    static String greetNamedFallback(String name,
                                     Optional<Boolean> shouldThrow,
                                     String hostHeader,
                                     Throwable t) {
        return "Fallback for \"greetNamed\": Failed to obtain greeting for " + name + ", message: " + t.getMessage();
    }

    @Http.GET
    @Http.Path("/default")
    String greet() {
        return greeting + " World!";
    }

    @Http.GET
    @Http.Path("/{name}")
    @FaultTolerance.Retry(name = "someName", calls = 2, delayTime = 1, timeUnit = ChronoUnit.SECONDS, overallTimeout = 10,
                          applyOn = ServiceRegistryException.class, skipOn = {OutOfMemoryError.class, StackOverflowError.class})
    @FaultTolerance.Fallback("greetNamedFallback")
    @FaultTolerance.CircuitBreaker
    @FaultTolerance.Bulkhead(name = "bulkhead-it")
    String greetNamed(@Http.PathParam("name") String name,
                      @Http.QueryParam(value = "throw") Optional<Boolean> shouldThrow,
                      @Http.HeaderParam(HeaderNames.HOST_STRING) String hostHeader) {
        // try to generate this method with "request.headers()..." and still start reqeust scope to see perf impact
        /*
        Maybe request scope can be a switch by the user
         */
        if (shouldThrow.orElse(false)) {
            throw new ServiceRegistryException("Failed on purpose");
        }
        return greeting + " " + name + "! Requested host: " + hostHeader;
    }

    @Http.POST
    @Http.Status(value = 254, reason = "CustomCode")
    void post(@Http.Entity String message) throws IOException {
        greeting = message;
        registry.get(Headers.class);
    }
}
