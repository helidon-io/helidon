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
package io.helidon.security.examples.outbound;

import io.helidon.nima.webclient.http1.Http1Client;
import io.helidon.nima.webclient.security.WebClientSecurity;
import io.helidon.nima.webserver.WebServer;
import io.helidon.nima.webserver.http.HttpRules;
import io.helidon.nima.webserver.http.HttpService;
import io.helidon.nima.webserver.http.ServerRequest;
import io.helidon.nima.webserver.http.ServerResponse;
import io.helidon.security.SecurityContext;
import io.helidon.security.providers.httpauth.HttpBasicAuthProvider;

class OverrideService implements HttpService {

    private final Http1Client client = Http1Client.builder()
            .addService(WebClientSecurity.create())
            .build();

    @Override
    public void routing(HttpRules rules) {
        rules.get("/override", this::override)
                .get("/propagate", this::propagate);
    }


    private void override(ServerRequest req, ServerResponse res) {
        SecurityContext context = req.context()
                .get(SecurityContext.class)
                .orElseThrow(() -> new RuntimeException("Security not configured"));

        WebServer server = req.context()
                .get(WebServer.class)
                .orElseThrow(() -> new RuntimeException("WebServer not found in context"));

        String result = client.get("http://localhost:" + server.port("backend") + "/hello")
                .property(HttpBasicAuthProvider.EP_PROPERTY_OUTBOUND_USER, "jill")
                .property(HttpBasicAuthProvider.EP_PROPERTY_OUTBOUND_PASSWORD, "anotherPassword")
                .request(String.class);

        res.send("You are: " + context.userName() + ", backend service returned: " + result + "\n");
    }

    private void propagate(ServerRequest req, ServerResponse res) {
        SecurityContext context = req.context()
                .get(SecurityContext.class)
                .orElseThrow(() -> new RuntimeException("Security not configured"));

        WebServer server = req.context()
                .get(WebServer.class)
                .orElseThrow(() -> new RuntimeException("WebServer not found in context"));

        String result = client.get("http://localhost:" + server.port("backend") + "/hello")
                .request(String.class);

        res.send("You are: " + context.userName() + ", backend service returned: " + result + "\n");
    }

}
