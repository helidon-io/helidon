/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

package io.helidon.tests.integration.restclient;

import java.util.concurrent.CompletionStage;

import jakarta.json.JsonObject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.faulttolerance.Asynchronous;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.faulttolerance.Timeout;
import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

/**
 * RestClient interface for a simple greet resource that includes a few FT annotations.
 */
@Path("/greet")
@RegisterProvider(GreetResourceFilter.class)
@RegisterRestClient(baseUri="http://localhost:8080")
public interface GreetResourceClient {

    @GET
    @Retry
    @Timeout(value = 3000)
    @Produces(MediaType.APPLICATION_JSON)
    JsonObject getDefaultMessage();

    @GET
    @Path("async")
    @Asynchronous
    @Produces(MediaType.APPLICATION_JSON)
    CompletionStage<JsonObject> getDefaultMessageAsync();
}
