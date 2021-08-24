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

package io.helidon.security.examples.signatures;

import java.util.Optional;

import io.helidon.common.http.Http;
import io.helidon.common.http.MediaType;
import io.helidon.security.SecurityContext;
import io.helidon.webclient.WebClient;
import io.helidon.webclient.security.WebClientSecurity;
import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;
import io.helidon.webserver.Service;

/**
 * Service 1 implementation.
 * This service acts as a client to service 2.
 */
class Service1 implements Service {
    private final WebClient client;

    Service1() {
        // at the time this instance is created, the second server is already started
        client = WebClient.builder()
                .baseUri("http://localhost:" + SignatureExampleUtil.server2Port())
                .addService(WebClientSecurity.create())
                .build();
    }

    @Override
    public void update(Routing.Rules rules) {
        rules.get("/", (req, res) -> this.processService1Request(req, res, "/service2"))
                .get("/rsa", (req, res) -> this.processService1Request(req, res, "/service2/rsa"));
    }

    void processService1Request(ServerRequest req, ServerResponse res, String path) {
        Optional<SecurityContext> securityContext = req.context().get(SecurityContext.class);

        res.headers().contentType(MediaType.TEXT_PLAIN.withCharset("UTF-8"));

        securityContext.ifPresentOrElse(context -> {
            client.get()
                    .path(path)
                    .request()
                    .thenAccept(it -> {
                        if (it.status() == Http.Status.OK_200) {
                            it.content().as(String.class)
                                    .thenAccept(res::send)
                                    .exceptionally(throwable -> {
                                        res.send("Getting server response failed!");
                                        return null;
                                    });
                        } else {
                            res.send("Request to service2 failed for path " + path + ", status: " + it.status());
                        }
                    });

        }, () -> res.send("Security context is null"));
    }
}
