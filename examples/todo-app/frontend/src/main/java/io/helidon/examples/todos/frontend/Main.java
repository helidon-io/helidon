/*
 * Copyright (c) 2017, 2023 Oracle and/or its affiliates.
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

package io.helidon.examples.todos.frontend;

import java.net.URI;
import java.util.List;

import io.helidon.config.Config;
import io.helidon.config.ConfigValue;
import io.helidon.config.FileSystemWatcher;
import io.helidon.http.HeaderNames;
import io.helidon.http.Status;
import io.helidon.logging.common.LogConfig;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.staticcontent.StaticContentService;

import static io.helidon.config.ConfigSources.classpath;
import static io.helidon.config.ConfigSources.environmentVariables;
import static io.helidon.config.ConfigSources.file;
import static io.helidon.config.PollingStrategies.regular;
import static java.time.Duration.ofSeconds;

/**
 * Main class to start the service.
 */
public final class Main {

    private static final Long POLLING_INTERVAL = 5L;

    /**
     * Cannot be instantiated.
     */
    private Main() {
    }

    /**
     * Application main entry point.
     *
     * @param args command line arguments
     */
    public static void main(final String[] args) {

        // load logging configuration
        LogConfig.configureRuntime();

        WebServerConfig.Builder builder = WebServer.builder();
        setup(builder);
        WebServer server = builder.build();

        // start the web server
        server.start();
        System.out.println("WEB server is up! http://localhost:" + server.port());
    }

    private static void setup(WebServerConfig.Builder server) {
        Config config = buildConfig();

        ConfigValue<URI> backendEndpoint = config.get("services.backend.endpoint").as(URI.class);

        server.config(config.get("server"))
                .routing(routing -> routing
                        // redirect POST / to GET /
                        .post("/", (req, res) -> {
                            res.header(HeaderNames.LOCATION, "/");
                            res.status(Status.SEE_OTHER_303);
                            res.send();
                        })
                        // register static content support (on "/")
                        .register(StaticContentService.builder("/WEB").welcomeFileName("index.html"))
                        // register API service (on "/api") - this path is secured (see application.yaml)
                        .register("/api", new TodoService(new BackendServiceClient(backendEndpoint::get))));
    }

    /**
     * Load the configuration from all sources.
     * @return the configuration root
     */
    private static Config buildConfig() {
        return Config.builder()
                .sources(List.of(
                        environmentVariables(),
                        // expected on development machine
                        // to override props for dev
                        file("dev.yaml")
                                .changeWatcher(FileSystemWatcher.create())
                                .optional(),
                        // expected in k8s runtime
                        // to configure testing/production values
                        file("prod.yaml")
                                .pollingStrategy(regular(
                                        ofSeconds(POLLING_INTERVAL)))
                                .optional(),
                        // in jar file
                        // (see src/main/resources/application.yaml)
                        classpath("application.yaml")))
                .build();
    }
}
