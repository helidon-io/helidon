/*
 * Copyright (c) 2019, 2021 Oracle and/or its affiliates.
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
package io.helidon.tests.integration.nativeimage.mp1;

import jakarta.json.JsonObject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

/**
 * Rest client example.
 */
@RegisterRestClient(baseUri = "http://localhost:8087/cdi")
@Path("/")
public interface RestClientIface {
    @GET
    @Path("/property")
    String message();

    @GET
    @Path("/beantype")
    String beanType();

    @GET
    @Path("/jsonp")
    JsonObject jsonProcessing();

    @GET
    @Path("/jsonb")
    @Produces(MediaType.APPLICATION_JSON)
    TestDto jsonBinding();

    @GET
    @Path("/queryparam")
    String queryParam(@QueryParam("long") Long longParam);

    @GET
    @Path("/queryparam2")
    String queryParam(@QueryParam("boolean") Boolean booleanParam);

    @GET
    @Path("/queryparam3")
    String queryParam(@QueryParam("int") int intParam);
}
