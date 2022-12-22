/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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
package io.helidon.tests.integration.nativeimage.se1;

import io.helidon.common.http.Http;
import io.helidon.config.Config;
import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;
import io.helidon.webserver.Service;

/**
 * Test service to exercise config enum mapping.
 */
public class ColorService implements Service {

    /**
     * Enum used in config mapping.
     */
    public enum Color {RED, YELLOW, BLUE}

    private final Color configuredColor;

    /**
     * Creates a new instance of the service.
     *
     * @param config config tree
     */
    public ColorService(Config config) {
        // Attempt the mapping now to force a failure (if any) during server start-up when it's easily and quickly visible.
        configuredColor = config.get("color.tint").as(Color.class).get();
    }

    @Override
    public void update(Routing.Rules rules) {
        rules.get("/", this::reportColor);
    }

    private void reportColor(ServerRequest request, ServerResponse response) {
        response.send(configuredColor.name());
    }
}
