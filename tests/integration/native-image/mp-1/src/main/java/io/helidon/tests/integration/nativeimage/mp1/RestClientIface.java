/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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

import javax.json.JsonObject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

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
}
