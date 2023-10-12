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

import io.helidon.security.Security;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http.HttpService;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

class SecretsService implements HttpService {
    private final Security security;

    SecretsService(Security security) {
        this.security = security;
    }


    @Override
    public void routing(HttpRules rules) {
        rules.get("/{name}", this::secret);
    }

    private void secret(ServerRequest req, ServerResponse res) {
        String secretName = req.path().pathParameters().get("name");
        res.send(security.secret(secretName, "default-" + secretName));
    }
}
