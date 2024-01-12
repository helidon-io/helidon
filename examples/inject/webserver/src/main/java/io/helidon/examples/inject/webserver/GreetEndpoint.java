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

package io.helidon.examples.inject.webserver;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.common.config.Config;
import io.helidon.http.HeaderNames;
import io.helidon.http.Http;
import io.helidon.inject.InjectionException;
import io.helidon.inject.service.Injection;

@Http.Path("/greet")
@Injection.Singleton
class GreetEndpoint {
    private final AtomicReference<String> greeting = new AtomicReference<>();

    @Injection.Inject
    GreetEndpoint(Config config) {
        this.greeting.set(config.get("app.greeting").asString().orElse("Hello"));
    }

    @Http.GET
    @Http.Path("/default")
    String greet() {
        return greeting + " World!";
    }

    @Http.GET
    @Http.Path("/named/{name}")
    String greetNamed(@Http.PathParam("name") String name,
                      @Http.QueryParam(value = "throw") Optional<Boolean> shouldThrow,
                      @Http.HeaderParam(HeaderNames.HOST_STRING) String hostHeader) {
        if (shouldThrow.orElse(false)) {
            throw new InjectionException("Failed on purpose");
        }
        return greeting.get() + " " + name + "! Requested host: " + hostHeader;
    }

    @Http.POST
    @Http.Status(value = 254, reason = "CustomCode")
    void post(@Http.Entity String message) throws IOException {
        greeting.set(message);
    }
}
