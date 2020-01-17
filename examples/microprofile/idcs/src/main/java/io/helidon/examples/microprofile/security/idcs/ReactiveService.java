/*
 * Copyright (c) 2020 Oracle and/or its affiliates. All rights reserved.
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

import javax.enterprise.context.ApplicationScoped;

import io.helidon.microprofile.server.RoutingPath;
import io.helidon.security.Principal;
import io.helidon.security.SecurityContext;
import io.helidon.security.Subject;
import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;
import io.helidon.webserver.Service;

/**
 * Reactive service implementation.
 */
@ApplicationScoped
@RoutingPath("/reactive")
public class ReactiveService implements Service {

    @Override
    public void update(Routing.Rules rules) {
        rules.get(this::reactiveRoute);
    }

    private void reactiveRoute(ServerRequest req, ServerResponse res) {
        String username = req.context()
                .get(SecurityContext.class)
                .flatMap(SecurityContext::user)
                .map(Subject::principal)
                .map(Principal::getName)
                .orElse("not authenticated");

        res.send("Hello from reactive service, you are " + username);
    }
}
