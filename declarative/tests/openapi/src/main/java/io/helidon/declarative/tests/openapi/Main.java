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
@OpenApi.Contact(value = "Helidon Team", url = "https://helidon.io", email = "helidon@example.com")
@OpenApi.License(value = "Apache License 2.0",
                 identifier = "Apache-2.0",
                 url = "https://www.apache.org/licenses/LICENSE-2.0")
@OpenApi.Server(value = "http://localhost:${server.port}", description = "Test server")
@OpenApi.Tag(value = "greeting", description = "Greeting operations")
@OpenApi.Tag(value = "farewell", description = "Farewell operations")
@OpenApi.ExternalDocs(value = "https://helidon.io/docs", description = "Helidon documentation")
@OpenApi.Extension(name = "x-test-document", value = "declarative-openapi")
@OpenApi.SecurityScheme(name = "bearerAuth",
                        type = "http",
                        description = "Bearer token authentication",
                        scheme = "bearer",
                        bearerFormat = "JWT")
@OpenApi.SecurityScheme(name = "oauth2",
                        type = "oauth2",
                        description = "OAuth2 client credentials",
                        flows = @OpenApi.OAuthFlows(clientCredentials = @OpenApi.OAuthFlow(
                                tokenUrl = "https://id.example.com/oauth2/token",
                                scopes = @OpenApi.OAuthScope(value = "greeting:read", description = "Read greetings"))))
@OpenApi.SecurityRequirement("bearerAuth")
@OpenApi.SecurityRequirement(value = "oauth2", scopes = "greeting:read")
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
