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

package io.helidon.examples.integrations.vault.hcp.reactive;

import java.util.Map;

import io.helidon.common.http.Http;
import io.helidon.integrations.vault.secrets.kv2.Kv2Secret;
import io.helidon.integrations.vault.secrets.kv2.Kv2Secrets;
import io.helidon.integrations.vault.sys.Sys;
import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;
import io.helidon.webserver.Service;

class Kv2Service implements Service {
    private final Sys sys;
    private final Kv2Secrets secrets;

    Kv2Service(Sys sys, Kv2Secrets secrets) {
        this.sys = sys;
        this.secrets = secrets;
    }

    @Override
    public void update(Routing.Rules rules) {
        rules.get("/create", this::createSecrets)
                .get("/secrets/{path:.*}", this::getSecret)
                .delete("/secrets/{path:.*}", this::deleteSecret);
    }

    private void createSecrets(ServerRequest req, ServerResponse res) {
        secrets.create("first/secret", Map.of("key", "secretValue"))
                .thenAccept(ignored -> res.send("Created secret on path /first/secret"))
                .exceptionally(res::send);
    }

    private void deleteSecret(ServerRequest req, ServerResponse res) {
        String path = req.path().param("path");

        secrets.deleteAll(path)
                .thenAccept(ignored -> res.send("Deleted secret on path " + path));
    }

    private void getSecret(ServerRequest req, ServerResponse res) {
        String path = req.path().param("path");

        secrets.get(path)
                .thenAccept(secret -> {
                    if (secret.isPresent()) {
                        // using toString so we do not need to depend on JSON-B
                        Kv2Secret kv2Secret = secret.get();
                        res.send("Version " + kv2Secret.metadata().version() + ", secret: " + kv2Secret.values().toString());
                    } else {
                        res.status(Http.Status.NOT_FOUND_404);
                        res.send();
                    }
                })
                .exceptionally(res::send);
    }
}
