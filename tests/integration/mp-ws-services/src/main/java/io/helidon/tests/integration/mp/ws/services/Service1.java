/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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
package io.helidon.tests.integration.mp.ws.services;

import javax.annotation.Priority;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import io.helidon.microprofile.server.RoutingName;
import io.helidon.microprofile.server.RoutingPath;
import io.helidon.webserver.Routing;
import io.helidon.webserver.Service;

import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Service example.
 */
@Priority(1)
@ApplicationScoped
@RoutingPath("/services")
// by default, the routing name is not required
@RoutingName("wrong")
public class Service1 implements Service {
    // ApplicationScoped injection
    @Inject
    private MessageBean messageBean;
    // Dependent scope injection
    @Inject
    @ConfigProperty(name = "app.message")
    private String message;

    @Override
    public void update(Routing.Rules rules) {
        rules.get("/service1", (req, res) -> res.send("service1"))
                .get("/", (req, res) -> res.send("service1"))
                .get("/info", (req, res) -> res.send("Values: " + messageBean + ", " + message));
    }
}
