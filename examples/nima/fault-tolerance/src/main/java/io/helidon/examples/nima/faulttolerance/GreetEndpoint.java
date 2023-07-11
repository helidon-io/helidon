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

package io.helidon.examples.nima.faulttolerance;

import java.time.temporal.ChronoUnit;

import io.helidon.common.http.Endpoint;
import io.helidon.common.http.Http;
import io.helidon.inject.api.InjectionException;
import io.helidon.nima.faulttolerance.FaultTolerance;

import jakarta.inject.Singleton;

@Singleton
@Endpoint.Path("/greet")
class GreetEndpoint {
    private String greeting = "Hello";

    GreetEndpoint() {
    }

    static String greetNamedFallback(String name,
                                     String shouldThrow,
                                     String hostHeader,
                                     Throwable t) {
        return "Fallback for \"greetNamed\": Failed to obtain greeting for " + name + ", message: " + t.getMessage();
    }

    @Endpoint.GET
    String greet() {
        return greeting + " World!";
    }

    @Endpoint.GET
    @Endpoint.Path("/{name}")
    @FaultTolerance.Retry(name = "someName", calls = 2, delayTime = 1, timeUnit = ChronoUnit.SECONDS, overallTimeout = 10,
                          applyOn = InjectionException.class, skipOn = {OutOfMemoryError.class, StackOverflowError.class})
    @FaultTolerance.Fallback("greetNamedFallback")
    @FaultTolerance.CircuitBreaker
    @FaultTolerance.Bulkhead(name = "bulkhead-it")
    String greetNamed(@Endpoint.PathParam("name") String name,
                      @Endpoint.QueryParam(value = "throw", defaultValue = "false") String shouldThrow,
                      @Endpoint.HeaderParam(Http.Header.HOST_STRING) String hostHeader) {
        if ("true".equalsIgnoreCase(shouldThrow)) {
            throw new InjectionException("Failed on purpose");
        }
        return greeting + " " + name + "! Requested host: " + hostHeader;
    }

    @Endpoint.POST
    void post(@Endpoint.Entity String message) {
        greeting = message;
    }
}
