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
package io.helidon.tests.functional.jaxrs.subresource;

import javax.annotation.security.PermitAll;
import javax.annotation.security.RolesAllowed;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;

import io.helidon.security.annotations.Authenticated;

/**
 * A JAX-RS resource class.
 */
@Path("/main")
@RolesAllowed("user")
@Authenticated
public class MpMainResource {
    @GET
    @Path("/public")
    @PermitAll
    public String getPublic() {
        return "Hello World";
    }
    /**
     * Simple hello world endpoint.
     *
     * @return hello world
     */
    @GET
    public String getIt() {
        return "Hello World";
    }

    /**
     * Sub resource locator.
     *
     * @param name name to use in sub resource
     * @return sub resource
     */
    @Path("/sub/{name}")
    @RolesAllowed("sub")
    public MpSubResource subResource(@PathParam("name") String name) {
        return new MpSubResource(name);
    }
}
