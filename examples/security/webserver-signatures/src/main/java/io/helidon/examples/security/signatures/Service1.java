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
package io.helidon.examples.security.signatures;

import io.helidon.common.LazyValue;
import io.helidon.common.context.Contexts;
import io.helidon.http.Http;
import io.helidon.http.HttpMediaTypes;
import io.helidon.security.SecurityContext;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http1.Http1ClientResponse;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http.HttpService;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

class Service1 implements HttpService {

    private final LazyValue<Http1Client> client = LazyValue.create(() -> Contexts.context()
            .flatMap(c -> c.get(WebServer.class))
            .map(server -> Http1Client.builder()
                    .baseUri("http://localhost:" + server.port("service2"))
                    .build())
            .orElseThrow(() -> new IllegalStateException("Unable to get server instance from current context")));

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
        res.headers().contentType(HttpMediaTypes.PLAINTEXT_UTF_8);
        req.context()
                .get(SecurityContext.class)
                .ifPresentOrElse(context -> {
                    try (Http1ClientResponse clientRes = client.get().get(path).request()) {
                        if (clientRes.status() == Http.Status.OK_200) {
                            res.send(clientRes.entity().as(String.class));
                        } else {
                            res.send("Request failed, status: " + clientRes.status());
                        }
                    }
                }, () -> res.send("Security context is null"));
    }
}
