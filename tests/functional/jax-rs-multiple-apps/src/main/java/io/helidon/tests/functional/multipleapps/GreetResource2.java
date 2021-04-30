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
package io.helidon.tests.functional.multipleapps;

import java.util.Collections;

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.json.Json;
import javax.json.JsonBuilderFactory;
import javax.json.JsonObject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

/**
 * Resource included as part of second application.
 */
@Path("/greet2")
@RequestScoped
public class GreetResource2 {

    private static final JsonBuilderFactory JSON = Json.createBuilderFactory(Collections.emptyMap());

    private final GreetingProvider greetingProvider;

    /**
     * Constructor for this resource.
     *
     * @param greetingProvider the provider
     */
    @Inject
    public GreetResource2(GreetingProvider greetingProvider) {
        this.greetingProvider = greetingProvider;
    }

    @SuppressWarnings("checkstyle:designforextension")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public JsonObject getDefaultMessage() {
        return createResponse(greetingProvider.headers().getHeaderString("who"));
    }

    private JsonObject createResponse(String who) {
        String msg = String.format("%s %s!", greetingProvider.getMessage(), who);
        return JSON.createObjectBuilder()
                .add("message", msg)
                .build();
    }
}
