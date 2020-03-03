/*
 * Copyright (c) 2020 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.microprofile.graphql.server.application;

import javax.enterprise.context.RequestScoped;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Path("/")
@RequestScoped
public class GraphQLResource {
    /**
     * Process a GET request.
     *
     * @param query     query to execute
     * @param operation optional operation name. e.g "query name { fields }"
     * @param variables optional variables
     * @return a {@link Response} object.
     */
    @GET
    @Produces({ MediaType.APPLICATION_JSON })
    @Consumes({ MediaType.APPLICATION_JSON })
    public Response processGraphQLQueryGET(@QueryParam("query") String query,
                                           @QueryParam("operationName") String operation,
                                           @QueryParam("variables") String variables) {
        return Response.ok("GET").build();
    }

    /**
     * Process a POST request.
     *
     * @param body body of the request as Json
     * @return a {@link Response} object.
     */
    @POST
    @Produces({ MediaType.APPLICATION_JSON })
    @Consumes({ MediaType.APPLICATION_JSON })
    public Response processGraphQLQueryPOST(String body) {
        return Response.ok("POST").build();
    }
}
