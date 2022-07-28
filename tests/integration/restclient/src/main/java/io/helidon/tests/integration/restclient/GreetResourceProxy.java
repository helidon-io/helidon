/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.json.JsonObject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriInfo;

import org.eclipse.microprofile.rest.client.RestClientBuilder;
import org.eclipse.microprofile.rest.client.inject.RestClient;

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
}
