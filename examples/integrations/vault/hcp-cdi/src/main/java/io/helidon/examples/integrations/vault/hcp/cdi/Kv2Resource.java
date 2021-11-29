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

package io.helidon.examples.integrations.vault.hcp.cdi;

import java.util.Map;
import java.util.Optional;

import io.helidon.integrations.vault.secrets.kv2.CreateKv2;
import io.helidon.integrations.vault.secrets.kv2.DeleteAllKv2;
import io.helidon.integrations.vault.secrets.kv2.Kv2Secret;
import io.helidon.integrations.vault.secrets.kv2.Kv2Secrets;

import jakarta.inject.Inject;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Response;

/**
 * JAX-RS resource for Key/Value version 2 secrets engine operations.
 */
@Path("/kv2")
public class Kv2Resource {
    private final Kv2Secrets secrets;

    @Inject
    Kv2Resource(Kv2Secrets secrets) {
        this.secrets = secrets;
    }

    /**
     * Create a secret from request entity, the name of the value is {@code secret}.
     *
     * @param path path of the secret taken from request path
     * @param secret secret from the entity
     * @return response
     */
    @POST
    @Path("/secrets/{path: .*}")
    public Response createSecret(@PathParam("path") String path, String secret) {
        CreateKv2.Response response = secrets.create(path, Map.of("secret", secret));

        return Response.ok()
                .entity("Created secret on path: " + path + ", key is \"secret\", original status: " + response.status().code())
                .build();
    }

    /**
     * Delete the secret on a specified path.
     *
     * @param path path of the secret taken from request path
     * @return response
     */
    @DELETE
    @Path("/secrets/{path: .*}")
    public Response deleteSecret(@PathParam("path") String path) {
        DeleteAllKv2.Response response = secrets.deleteAll(path);

        return Response.ok()
                .entity("Deleted secret on path: " + path + ". Original status: " + response.status().code())
                .build();
    }

    /**
     * Get the secret on a specified path.
     *
     * @param path path of the secret taken from request path
     * @return response
     */
    @GET
    @Path("/secrets/{path: .*}")
    public Response getSecret(@PathParam("path") String path) {

        Optional<Kv2Secret> secret = secrets.get(path);

        if (secret.isPresent()) {
            Kv2Secret kv2Secret = secret.get();
            return Response.ok()
                    .entity("Version " + kv2Secret.metadata().version() + ", secret: " + kv2Secret.values().toString())
                    .build();
        } else {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
    }
}
