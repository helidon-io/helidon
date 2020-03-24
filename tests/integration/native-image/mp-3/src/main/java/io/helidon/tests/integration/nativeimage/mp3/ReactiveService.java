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
package io.helidon.tests.integration.nativeimage.mp3;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import io.helidon.microprofile.server.RoutingPath;
import io.helidon.security.Security;
import io.helidon.webserver.Routing;
import io.helidon.webserver.Service;

/**
 * Reactive webserver service.
 * Supports only injection of {@link javax.enterprise.context.ApplicationScoped} beans.
 */
@ApplicationScoped
@RoutingPath("/reactive")
public class ReactiveService implements Service {
    @Inject
    private Security security;

    @Override
    public void update(Routing.Rules rules) {
        rules.get("/", (req, res) -> res.send("Security: " + security));
    }
}
