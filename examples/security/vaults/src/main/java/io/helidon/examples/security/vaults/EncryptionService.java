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

        try {
            String encrypted = security.encrypt(configName, text.getBytes(StandardCharsets.UTF_8));
            res.send(encrypted);
        } catch (Exception e) {
            res.send(e);
        }
    }

    private void decrypt(ServerRequest req, ServerResponse res) {
        String configName = req.path().param("config");
        String cipherText = req.path().param("cipherText");

        try {
            byte[] decrypted = security.decrypt(configName, cipherText);
            res.send(decrypted);
        } catch (Exception e) {
            res.send(e);
        }
    }
}
