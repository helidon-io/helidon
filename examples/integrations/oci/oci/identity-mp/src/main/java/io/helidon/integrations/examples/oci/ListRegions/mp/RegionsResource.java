/*
 * Copyright (c) 2021 Oracle and/or its affiliates. All rights reserved.
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
package io.helidon.integrations.examples.oci.ListRegions.mp;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.oracle.bmc.Region;
import com.oracle.bmc.auth.AuthenticationDetailsProvider;
import com.oracle.bmc.identity.IdentityClient;
import com.oracle.bmc.identity.requests.ListRegionsRequest;
import com.oracle.bmc.identity.responses.ListRegionsResponse;

/**
 * A JAX-RS resource class rooted at {@code /regions}.
 *
 * @see #listRegions()
 */
@Path("/regions")
@ApplicationScoped
public class RegionsResource {

    private final IdentityClient identityClient;

    /**
     * Create resource.
     *
     * @param inProvider to create the object storage.
     */
    @Inject
    public RegionsResource(AuthenticationDetailsProvider inProvider) {
        super();
        this.identityClient = new IdentityClient(inProvider);
        identityClient.setRegion(Region.US_PHOENIX_1);
    }

    /**
     * Returns All regions.
     *
     * @return List with all regions available.
     */
    @GET
    @Path("/all")
    @Produces(MediaType.TEXT_HTML)
    public Response listRegions() {
        final ListRegionsResponse response =
                identityClient.listRegions(ListRegionsRequest.builder().build());

        return Response.ok()
                .entity(response.getItems().toString())
                .build();
    }
}
