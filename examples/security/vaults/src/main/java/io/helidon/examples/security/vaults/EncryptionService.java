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

import io.helidon.nima.webserver.http.HttpRules;
import io.helidon.nima.webserver.http.HttpService;
import io.helidon.nima.webserver.http.ServerRequest;
import io.helidon.nima.webserver.http.ServerResponse;
import io.helidon.security.Security;

import static java.nio.charset.StandardCharsets.UTF_8;

class EncryptionService implements HttpService {
    private final Security security;

    EncryptionService(Security security) {
        this.security = security;
    }


    @Override
    public void routing(HttpRules rules) {
        rules.get("/encrypt/{config}/{text:.*}", this::encrypt)
             .get("/decrypt/{config}/{cipherText:.*}", this::decrypt);
    }

    private void encrypt(ServerRequest req, ServerResponse res) {
        String configName = req.path().pathParameters().value("config");
        String text = req.path().pathParameters().value("text");

        res.send(security.encrypt(configName, text.getBytes(UTF_8)));
    }

    private void decrypt(ServerRequest req, ServerResponse res) {
        String configName = req.path().pathParameters().value("config");
        String cipherText = req.path().pathParameters().value("cipherText");

        res.send(security.decrypt(configName, cipherText));
    }
}
