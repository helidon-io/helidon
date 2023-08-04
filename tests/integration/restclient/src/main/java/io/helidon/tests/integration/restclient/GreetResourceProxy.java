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

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import io.helidon.common.context.Contexts;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import jakarta.json.JsonObject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.UriInfo;
import org.eclipse.microprofile.rest.client.RestClientBuilder;
import org.eclipse.microprofile.rest.client.inject.RestClient;

/**
 * A greet resource proxy class that forwards calls to the real greet service
 * using a RestClient proxy. It tries both a proxy built manually as well as
 * one injected into this class.
 */
@Path("/proxy")
public class GreetResourceProxy {

    @Context
    private UriInfo uriInfo;

    /**
     * An injected client whose base URI needs to be patched by {@link GreetResourceFilter}
     * to use the ephemeral port.
     */
    @Inject
    @RestClient
    private GreetResourceClient injectedClient;

    /**
     * A built client whose base URI can be updated upon creation time.
     */
    private GreetResourceClient builderClient;

    @PostConstruct
    public void initialize() {
        builderClient = RestClientBuilder.newBuilder()
                .baseUri(uriInfo.getBaseUri())
                .build(GreetResourceClient.class);

        // Register base URI to update dynamic port in GreetResourceFilter
        // given that the request scope is not provided by Jersey with async
        Contexts.globalContext().register(GreetResourceFilter.class, uriInfo.getBaseUri());
    }

    /**
     * Test both clients and compares responses before returning one.
     *
     * @return JSON response.
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public JsonObject getDefaultMessage() {
        JsonObject msg1 = injectedClient.getDefaultMessage();
        JsonObject msg2 = builderClient.getDefaultMessage();
        if (!msg1.getString("message").equals(msg2.getString("message"))) {
            throw new IllegalStateException("Oops");
        }
        return msg1;
    }

    /**
     * Test both clients and compares responses before returning one. Async version.
     *
     * @return JSON response.
     */
    @GET
    @Path("async")
    @Produces(MediaType.APPLICATION_JSON)
    public JsonObject getDefaultMessageAsync() throws ExecutionException, InterruptedException, TimeoutException {
        JsonObject msg1 = injectedClient.getDefaultMessageAsync()
                .toCompletableFuture().get(5, TimeUnit.SECONDS);
        JsonObject msg2 = builderClient.getDefaultMessageAsync()
                .toCompletableFuture().get(5, TimeUnit.SECONDS);
        if (!msg1.getString("message").equals(msg2.getString("message"))) {
            throw new IllegalStateException("Oops");
        }
        return msg1;
    }
}
