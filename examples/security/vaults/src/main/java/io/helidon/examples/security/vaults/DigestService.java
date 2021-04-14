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

package io.helidon.examples.security.vaults;

import java.nio.charset.StandardCharsets;

import io.helidon.security.Security;
import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;
import io.helidon.webserver.Service;

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

        security.digest(configName, text.getBytes(StandardCharsets.UTF_8))
                .forSingle(res::send)
                .exceptionally(res::send);
    }

    private void verify(ServerRequest req, ServerResponse res) {
        String configName = req.path().param("config");
        String text = req.path().param("text");
        String digest = req.path().param("digest");

        security.verifyDigest(configName, text.getBytes(StandardCharsets.UTF_8), digest)
                .map(it -> it ? "Valid" : "Invalid")
                .forSingle(res::send)
                .exceptionally(res::send);
    }
}
