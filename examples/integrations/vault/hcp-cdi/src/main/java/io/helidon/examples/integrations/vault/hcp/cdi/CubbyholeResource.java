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

import javax.inject.Inject;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Response;

import io.helidon.integrations.vault.Secret;
import io.helidon.integrations.vault.secrets.cubbyhole.CreateCubbyhole;
import io.helidon.integrations.vault.secrets.cubbyhole.CubbyholeSecrets;
import io.helidon.integrations.vault.secrets.cubbyhole.DeleteCubbyhole;

/**
 * JAX-RS resource for Cubbyhole secrets engine operations.
 */
@Path("/cubbyhole")
public class CubbyholeResource {
    private final CubbyholeSecrets secrets;

    @Inject
    CubbyholeResource(CubbyholeSecrets secrets) {
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
        CreateCubbyhole.Response response = secrets.create(path, Map.of("secret", secret));

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
        DeleteCubbyhole.Response response = secrets.delete(path);

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
        Optional<Secret> secret = secrets.get(path);

        if (secret.isPresent()) {
            Secret kv1Secret = secret.get();
            return Response.ok()
                    .entity("Secret: " + secret.get().values().toString())
                    .build();
        } else {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
    }
}
