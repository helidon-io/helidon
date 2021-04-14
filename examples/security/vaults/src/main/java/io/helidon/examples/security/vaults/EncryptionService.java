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

class EncryptionService implements Service {
    private final Security security;

    EncryptionService(Security security) {
        this.security = security;
    }

    @Override
    public void update(Routing.Rules rules) {
        rules.get("/encrypt/{config}/{text:.*}", this::encrypt)
                .get("/decrypt/{config}/{cipherText:.*}", this::decrypt);
    }

    private void encrypt(ServerRequest req, ServerResponse res) {
        String configName = req.path().param("config");
        String text = req.path().param("text");

        security.encrypt(configName, text.getBytes(StandardCharsets.UTF_8))
                .forSingle(res::send)
                .exceptionally(res::send);
    }

    private void decrypt(ServerRequest req, ServerResponse res) {
        String configName = req.path().param("config");
        String cipherText = req.path().param("cipherText");

        security.decrypt(configName, cipherText)
                .forSingle(res::send)
                .exceptionally(res::send);
    }
}
