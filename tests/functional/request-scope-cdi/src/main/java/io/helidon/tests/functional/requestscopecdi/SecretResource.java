/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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

package io.helidon.tests.functional.requestscopecdi;

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.json.Json;
import javax.json.JsonBuilderFactory;
import javax.json.JsonObject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.Collections;
import java.util.logging.Logger;

@Path("/greet")
@RequestScoped
public class SecretResource {
    private static final Logger LOGGER = Logger.getLogger(SecretResource.class.getName());
    private static final JsonBuilderFactory JSON = Json.createBuilderFactory(Collections.emptyMap());

    @Inject
    private AsyncWorker worker;

    @Inject
    private SharedBean shared;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getDefaultMessage() {
        try {
            // Access shared bean to get it proxied before calling the async op
            // for it to be migrated to FT thread
            LOGGER.info("Secret is " + shared.secret());

            // Call async operation
            var f = worker.asyncOp();

            // Both secrets returned should match
            JsonObject o = JSON.createObjectBuilder()
                    .add("secret1", shared.secret())
                    .add("secret2", f.get())
                    .build();
            LOGGER.info("Response is " + o);

            return Response.ok(o).build();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
        }
    }
}
