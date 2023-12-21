/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.tests.integration.declarative;

import java.time.temporal.ChronoUnit;

import io.helidon.common.config.Config;
import io.helidon.faulttolerance.FaultTolerance;
import io.helidon.http.HeaderNames;
import io.helidon.http.Http;
import io.helidon.inject.InjectionException;
import io.helidon.inject.service.Injection;
import io.helidon.security.SecurityContext;

@Http.Path("/greet")
class GreetEndpoint {
    private String greeting;

    @Injection.Inject
    GreetEndpoint(Config config) {
        this.greeting = config.get("app.greeting").asString().orElse("Hello");
    }

    static String greetNamedFallback(String name,
                                     boolean shouldThrow,
                                     String hostHeader,
                                     SecurityContext securityContext,
                                     Throwable t) {
        return "Fallback for \"greetNamed\": Failed to obtain greeting for " + name + ", message: " + t.getMessage();
    }

    @Http.GET
    String greet() {
        return greeting + " World!";
    }

    @Http.GET
    @Http.Path("/{name}")
    @FaultTolerance.Retry(name = "someName", calls = 2, delayTime = 1, timeUnit = ChronoUnit.SECONDS, overallTimeout = 10,
                          applyOn = InjectionException.class, skipOn = {OutOfMemoryError.class, StackOverflowError.class})
    @FaultTolerance.Fallback("greetNamedFallback")
    @FaultTolerance.CircuitBreaker
    @FaultTolerance.Bulkhead(name = "bulkhead-it")
    String greetNamed(@Http.PathParam("name") String name,
                      @Http.QueryParam(value = "throw", defaultValue = "false") boolean shouldThrow,
                      @Http.HeaderParam(HeaderNames.HOST_STRING) String hostHeader,
                      SecurityContext securityContext) {
        if (shouldThrow) {
            throw new InjectionException("Failed on purpose");
        }
        return greeting + " " + name + "! Requested host: " + hostHeader;
    }

    @Http.POST
    void post(@Http.Entity String message) {
        greeting = message;
    }
}
