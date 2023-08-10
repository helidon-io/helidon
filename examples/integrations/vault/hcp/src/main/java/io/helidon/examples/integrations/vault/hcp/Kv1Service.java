/*
 * Copyright (c) 2021, 2023 Oracle and/or its affiliates.
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

package io.helidon.examples.integrations.vault.hcp;

import java.util.Map;
import java.util.Optional;

import io.helidon.http.Http;
import io.helidon.integrations.vault.Secret;
import io.helidon.integrations.vault.secrets.kv1.Kv1Secrets;
import io.helidon.integrations.vault.sys.Sys;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http.HttpService;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

class Kv1Service implements HttpService {
    private final Sys sys;
    private final Kv1Secrets secrets;

    Kv1Service(Sys sys, Kv1Secrets secrets) {
        this.sys = sys;
        this.secrets = secrets;
    }

    @Override
    public void routing(HttpRules rules) {
        rules.get("/enable", this::enableEngine)
             .get("/create", this::createSecrets)
             .get("/secrets/{path:.*}", this::getSecret)
             .delete("/secrets/{path:.*}", this::deleteSecret)
             .get("/disable", this::disableEngine);
    }

    private void disableEngine(ServerRequest req, ServerResponse res) {
        sys.disableEngine(Kv1Secrets.ENGINE);
        res.send("KV1 Secret engine disabled");
    }

    private void enableEngine(ServerRequest req, ServerResponse res) {
        sys.enableEngine(Kv1Secrets.ENGINE);
        res.send("KV1 Secret engine enabled");
    }

    private void createSecrets(ServerRequest req, ServerResponse res) {
        secrets.create("first/secret", Map.of("key", "secretValue"));
        res.send("Created secret on path /first/secret");
    }

    private void deleteSecret(ServerRequest req, ServerResponse res) {
        String path = req.path().pathParameters().value("path");
        secrets.delete(path);
        res.send("Deleted secret on path " + path);
    }

    private void getSecret(ServerRequest req, ServerResponse res) {
        String path = req.path().pathParameters().value("path");

        Optional<Secret> secret = secrets.get(path);
        if (secret.isPresent()) {
            // using toString so we do not need to depend on JSON-B
            res.send(secret.get().values().toString());
        } else {
            res.status(Http.Status.NOT_FOUND_404);
            res.send();
        }
    }
}
