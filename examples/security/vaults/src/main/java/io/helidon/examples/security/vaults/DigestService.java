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

package io.helidon.examples.security.vaults;

import java.nio.charset.StandardCharsets;

import io.helidon.reactive.webserver.Routing;
import io.helidon.reactive.webserver.ServerRequest;
import io.helidon.reactive.webserver.ServerResponse;
import io.helidon.reactive.webserver.Service;
import io.helidon.security.Security;

class DigestService implements Service {
    private final Security security;

    DigestService(Security security) {
        this.security = security;
    }

    @Override
    public void update(Routing.Rules rules) {
        rules.get("/digest/{config}/{text}", this::digest)
                .get("/verify/{config}/{text}/{digest:.*}", this::verify);
    }

    private void digest(ServerRequest req, ServerResponse res) {
        String configName = req.path().param("config");
        String text = req.path().param("text");

        String toSend = security.digest(configName, text.getBytes(StandardCharsets.UTF_8));
        res.send(toSend);
    }

    private void verify(ServerRequest req, ServerResponse res) {
        String configName = req.path().param("config");
        String text = req.path().param("text");
        String digest = req.path().param("digest");

        if (security.verifyDigest(configName, text.getBytes(StandardCharsets.UTF_8), digest)) {
            res.send("Valid");
        } else {
            res.send("Invalid");
        }
    }
}
