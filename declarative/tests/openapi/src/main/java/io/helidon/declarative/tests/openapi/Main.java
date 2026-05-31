/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.declarative.tests.openapi;

import io.helidon.logging.common.LogConfig;
import io.helidon.openapi.OpenApi;
import io.helidon.service.registry.Service;
import io.helidon.service.registry.ServiceRegistryManager;
import io.helidon.service.registry.Services;
import io.helidon.webserver.WebServer;

/**
 * Custom application main class.
 */
@OpenApi.Document
@OpenApi.Info(title = "Declarative OpenAPI Test", version = "1.0.0")
@OpenApi.Server(value = "http://localhost:${server.port}", description = "Test server")
@OpenApi.Tag(value = "greeting", description = "Greeting operations")
@OpenApi.Tag(value = "farewell", description = "Farewell operations")
@Service.GenerateBinding // annotation is required to generate application binding
public final class Main {
    static {
        // used when building with GraalVM native image to configure logging during build
        LogConfig.initClass();
    }

    private Main() {
    }

    /**
     * Application main entry point.
     *
     * @param args command line arguments.
     */
    public static void main(String[] args) {
        // used to configure logging
        LogConfig.configureRuntime();

        ServiceRegistryManager.start(ApplicationBinding.create());

        WebServer webServer = Services.get(WebServer.class);
        System.out.println("Server started on: http://localhost:" + webServer.port() + "/openapi");
    }
}
