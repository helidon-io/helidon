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

package io.helidon.integrations.examples.oci.ListRegions.se;

import java.util.Collections;
import java.util.logging.Logger;

import javax.json.Json;
import javax.json.JsonBuilderFactory;
import javax.json.JsonObject;

import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;
import io.helidon.webserver.Service;

import com.oracle.bmc.Region;
import com.oracle.bmc.auth.AuthenticationDetailsProvider;
import com.oracle.bmc.identity.IdentityClient;
import com.oracle.bmc.identity.requests.ListRegionsRequest;
import com.oracle.bmc.identity.responses.ListRegionsResponse;


public class RegionsService implements Service {
    private static final Logger LOGGER = Logger.getLogger(RegionsService.class.getName());
    private static final JsonBuilderFactory JSON = Json.createBuilderFactory(Collections.emptyMap());

    private final IdentityClient identityClient;

    /**
     * Constructor for RegionService.
     * @param inProvider
     */
    public RegionsService(AuthenticationDetailsProvider inProvider) {
        super();
        this.identityClient = new IdentityClient(inProvider);
        identityClient.setRegion(Region.US_PHOENIX_1);
    }

    /**
     * A service registers itself by updating the routing rules.
     *
     * @param rules the routing rules.
     */
    @Override
    public void update(Routing.Rules rules) {
        rules.get("/all", this::getRegionsHandler);
    }

    /**
     * Return all regions in OCI.
     *
     * @param request  the server request
     * @param response the server response
     */
    private void getRegionsHandler(ServerRequest request, ServerResponse response) {
        final ListRegionsResponse regionsResponse =
                identityClient.listRegions(ListRegionsRequest.builder().build());
        LOGGER.info("The regions are " + regionsResponse.getItems().toString());
        JsonObject returnObject = JSON.createObjectBuilder()
                .add("Regions", regionsResponse.getItems().toString())
                .build();
        response.send(returnObject);
    }
}
