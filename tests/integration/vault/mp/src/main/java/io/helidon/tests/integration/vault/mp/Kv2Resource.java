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

import io.helidon.integrations.vault.cdi.VaultName;
import io.helidon.integrations.vault.cdi.VaultPath;
import io.helidon.integrations.vault.secrets.kv2.CreateKv2;
import io.helidon.integrations.vault.secrets.kv2.DeleteAllKv2;
import io.helidon.integrations.vault.secrets.kv2.Kv2Secret;
import io.helidon.integrations.vault.secrets.kv2.Kv2Secrets;

@Path("/kv2")
public class Kv2Resource {
    private final Kv2Secrets secrets;

    @Inject
    Kv2Resource(@VaultName("app-role") @VaultPath("custom") Kv2Secrets secrets) {
        this.secrets = secrets;
    }

    @POST
    @Path("/secrets/{path: .*}")
    public Response createSecret(@PathParam("path") String path, String secret) {
        CreateKv2.Response response = secrets.create(path, Map.of("secret", secret));

        return Response.ok()
                .entity("Created secret on path: " + path + ", key is \"secret\", original status: " + response.status().code())
                .build();
    }

    @DELETE
    @Path("/secrets/{path: .*}")
    public Response deleteSecret(@PathParam("path") String path) {
        DeleteAllKv2.Response response = secrets.deleteAll(path);

        return Response.ok()
                .entity("Deleted secret on path: " + path + ". Original status: " + response.status().code())
                .build();
    }

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
