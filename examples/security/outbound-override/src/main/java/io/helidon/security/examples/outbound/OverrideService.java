/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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

import io.helidon.security.EndpointConfig;
import io.helidon.security.SecurityContext;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.security.WebClientSecurity;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http.HttpService;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

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
                .property(EndpointConfig.PROPERTY_OUTBOUND_ID, "jill")
                .property(EndpointConfig.PROPERTY_OUTBOUND_SECRET, "changeit")
                .requestEntity(String.class);

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
                .requestEntity(String.class);

        res.send("You are: " + context.userName() + ", backend service returned: " + result + "\n");
    }

}
