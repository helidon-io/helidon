/*
 * Copyright (c) 2019, 2024 Oracle and/or its affiliates.
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
package io.helidon.tests.integration.packaging.mp3;

import io.helidon.microprofile.server.RoutingPath;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http.HttpService;
import io.helidon.security.Security;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Webserver service.
 * Supports only injection of {@link jakarta.enterprise.context.ApplicationScoped} beans.
 */
@ApplicationScoped
@RoutingPath("/routing")
public class RoutingService implements HttpService {
    private Security security;
    @Inject
    public RoutingService(Security security) {
        this.security = security;
    }

    @Override
    public void routing(HttpRules rules) {
        rules.get("/", (req, res) -> res.send("Security: " + security));
    }
}
