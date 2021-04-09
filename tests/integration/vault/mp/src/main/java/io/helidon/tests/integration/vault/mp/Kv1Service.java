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

package io.helidon.tests.integration.vault.mp;

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
import io.helidon.integrations.vault.secrets.kv1.CreateKv1;
import io.helidon.integrations.vault.secrets.kv1.DeleteKv1;
import io.helidon.integrations.vault.secrets.kv1.Kv1Secrets;
import io.helidon.integrations.vault.secrets.kv1.Kv1SecretsRx;
import io.helidon.integrations.vault.sys.DisableEngine;
import io.helidon.integrations.vault.sys.EnableEngine;
import io.helidon.integrations.vault.sys.Sys;

@Path("/kv1")
public class Kv1Service {
    private final Sys sys;
    private final Kv1Secrets secrets;

    @Inject
    Kv1Service(Sys sys, Kv1Secrets secrets) {
        this.sys = sys;
        this.secrets = secrets;
    }

    @Path("/engine")
    @GET
    public Response enableEngine() {
        EnableEngine.Response response = sys.enableEngine(Kv1SecretsRx.ENGINE);

        return Response.ok()
                .entity("Key/value version 1 secret engine is now enabled. Original status: " + response.status().code())
                .build();
    }

    @Path("/engine")
    @DELETE
    public Response disableEngine() {
        DisableEngine.Response response = sys.disableEngine(Kv1SecretsRx.ENGINE);
        return Response.ok()
                .entity("Key/value version 1 secret engine is now disabled. Original status: " + response.status().code())
                .build();
    }

    @POST
    @Path("/secrets/{path: .*}")
    public Response createSecret(@PathParam("path") String path, String secret) {
        CreateKv1.Response response = secrets.create(path, Map.of("secret", secret));

        return Response.ok()
                .entity("Created secret on path: " + path + ", key is \"secret\", original status: " + response.status().code())
                .build();
    }

    @DELETE
    @Path("/secrets/{path: .*}")
    public Response deleteSecret(@PathParam("path") String path) {
        DeleteKv1.Response response = secrets.delete(path);

        return Response.ok()
                .entity("Deleted secret on path: " + path + ". Original status: " + response.status().code())
                .build();
    }

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
