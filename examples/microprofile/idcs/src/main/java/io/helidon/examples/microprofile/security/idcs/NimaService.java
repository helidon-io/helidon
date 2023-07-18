/*
 * Copyright (c) 2020, 2023 Oracle and/or its affiliates.
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
package io.helidon.examples.microprofile.security.idcs;

import io.helidon.microprofile.server.RoutingPath;
import io.helidon.nima.webserver.http.HttpRules;
import io.helidon.nima.webserver.http.HttpService;
import io.helidon.nima.webserver.http.ServerRequest;
import io.helidon.nima.webserver.http.ServerResponse;
import io.helidon.security.Principal;
import io.helidon.security.SecurityContext;
import io.helidon.security.Subject;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * Nima service implementation.
 */
@ApplicationScoped
@RoutingPath("/nima")
public class NimaService implements HttpService {

    @Override
    public void routing(HttpRules rules) {
        rules.get(this::nimaRoute);
    }

    private void nimaRoute(ServerRequest req, ServerResponse res) {
        String username = req.context()
                .get(SecurityContext.class)
                .flatMap(SecurityContext::user)
                .map(Subject::principal)
                .map(Principal::getName)
                .orElse("not authenticated");

        res.send("Hello from nima service, you are " + username);
    }
}
