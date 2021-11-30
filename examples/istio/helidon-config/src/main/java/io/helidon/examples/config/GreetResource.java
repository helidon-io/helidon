/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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
package io.helidon.examples.config;

import java.util.Collections;
import java.util.function.Supplier;

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.json.Json;
import javax.json.JsonBuilderFactory;
import javax.ws.rs.GET;
import javax.ws.rs.Path;

import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * A simple JAX-RS resource to greet you. Examples:
 *
 * Get default greeting message:
 * curl -X GET http://localhost:8080/first
 *
 * The message is returned as a String.
 */
@Path("/first")
@RequestScoped
public class GreetResource {

    private static final JsonBuilderFactory JSON = Json.createBuilderFactory(Collections.emptyMap());
    private final Supplier<String> message;

    /**
     * Using constructor injection to get a configuration property.
     *
     * @param message the configured greeting message
     */
    @Inject
    public GreetResource(@ConfigProperty(name = "app.greeting") Supplier<String> message) {
        this.message = message;
    }

    /**
     * Return a worldly greeting message.
     *
     * @return {@link String}
     */
    @GET
    public String getMessage() {
        return this.message.get();
    }

}
