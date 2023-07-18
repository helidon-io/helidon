/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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
package io.helidon.webserver.examples.mtls;

import java.security.Principal;

import io.helidon.common.http.Http;
import io.helidon.nima.webserver.http.HttpRules;
import io.helidon.nima.webserver.http.HttpService;

class SecureService implements HttpService {
    @Override
    public void routing(HttpRules rules) {
        rules.any((req, res) -> {
            String cn = req.remotePeer()
                    .tlsPrincipal()
                    .map(Principal::getName)
                    .flatMap(CertificateHelper::clientCertificateName)
                    .orElse("Unknown CN");

            // close to avoid re-using cached connections on the client side
            res.header(Http.HeaderValues.CONNECTION_CLOSE);
            res.send("Hello " + cn + "!");
        });
    }
}
