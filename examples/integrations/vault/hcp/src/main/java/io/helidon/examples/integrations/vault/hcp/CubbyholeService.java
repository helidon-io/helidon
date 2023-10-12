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

import io.helidon.http.Status;
import io.helidon.integrations.vault.Secret;
import io.helidon.integrations.vault.secrets.cubbyhole.CubbyholeSecrets;
import io.helidon.integrations.vault.sys.Sys;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http.HttpService;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

class CubbyholeService implements HttpService {
    private final Sys sys;
    private final CubbyholeSecrets secrets;

    CubbyholeService(Sys sys, CubbyholeSecrets secrets) {
        this.sys = sys;
        this.secrets = secrets;
    }

    @Override
    public void routing(HttpRules rules) {
        rules.get("/create", this::createSecrets)
             .get("/secrets/{path:.*}", this::getSecret);
    }

    private void createSecrets(ServerRequest req, ServerResponse res) {
        secrets.create("first/secret", Map.of("key", "secretValue"));
        res.send("Created secret on path /first/secret");
    }

    private void getSecret(ServerRequest req, ServerResponse res) {
        String path = req.path().pathParameters().get("path");
        Optional<Secret> secret = secrets.get(path);
        if (secret.isPresent()) {
            // using toString so we do not need to depend on JSON-B
            res.send(secret.get().values().toString());
        } else {
            res.status(Status.NOT_FOUND_404);
            res.send();
        }
    }
}
