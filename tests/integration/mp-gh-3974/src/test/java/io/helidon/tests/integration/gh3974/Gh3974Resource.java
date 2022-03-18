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

package io.helidon.tests.integration.gh3974;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;

@Path("/")
public class Gh3974Resource {
    @GET
    @Path("/test1")
    public Response test1() {
        return Response.status(Response.Status.NOT_FOUND).build();
    }

    @GET
    @Path("/test2")
    public Response test2() {
        return Response.status(Response.Status.NOT_FOUND).entity("").build();
    }

    @GET
    @Path("/test3")
    public Response test3() {
        return Response.status(Response.Status.NOT_FOUND).entity("NO").build();
    }

    @GET
    @Path("/test4")
    public Response test4() {
        throw new NotFoundException();
    }

    @GET
    @Path("/test5")
    public Response test5() {
        throw new NotFoundException("");
    }

    @GET
    @Path("/test6")
    public Response test6() {
        throw new NotFoundException(Response.status(Response.Status.NOT_FOUND).entity("NO").build());
    }
}
