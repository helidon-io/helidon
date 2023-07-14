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
package io.helidon.security.examples.signatures;

import io.helidon.common.http.Http;
import io.helidon.common.http.HttpMediaType;
import io.helidon.nima.webclient.http1.Http1Client;
import io.helidon.nima.webclient.http1.Http1ClientResponse;
import io.helidon.nima.webclient.security.WebClientSecurity;
import io.helidon.nima.webserver.http.HttpRules;
import io.helidon.nima.webserver.http.HttpService;
import io.helidon.nima.webserver.http.ServerRequest;
import io.helidon.nima.webserver.http.ServerResponse;
import io.helidon.security.SecurityContext;

class Service1 implements HttpService {

    private final Http1Client client = Http1Client.builder()
            .addService(WebClientSecurity.create())
            .baseUri("http://localhost:8080")
            .build();

    @Override
    public void routing(HttpRules rules) {
        rules.get("/service1", this::service1)
                .get("/service1-rsa", this::service1Rsa);
    }

    private void service1(ServerRequest req, ServerResponse res) {
        handle(req, res, "/service2");
    }

    private void service1Rsa(ServerRequest req, ServerResponse res) {
        handle(req, res, "/service2-rsa");
    }

    private void handle(ServerRequest req, ServerResponse res, String path) {
        res.headers().contentType(HttpMediaType.PLAINTEXT_UTF_8);
        req.context()
                .get(SecurityContext.class)
                .ifPresentOrElse(context -> {
                    try (Http1ClientResponse clientRes = client.get(path).request()) {
                        if (clientRes.status() == Http.Status.OK_200) {
                            res.send(clientRes.entity().as(String.class));
                        } else {
                            res.send("Request failed, status: " + clientRes.status());
                        }
                    }
                }, () -> res.send("Security context is null"));
    }
}
