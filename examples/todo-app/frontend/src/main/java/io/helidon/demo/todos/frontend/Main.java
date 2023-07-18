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

package io.helidon.demo.todos.frontend;

import java.util.List;

import io.helidon.config.Config;
import io.helidon.config.FileSystemWatcher;
import io.helidon.logging.common.LogConfig;
import io.helidon.nima.observe.ObserveFeature;
import io.helidon.nima.webclient.http1.Http1Client;
import io.helidon.nima.webclient.security.WebClientSecurity;
import io.helidon.nima.webserver.WebServer;
import io.helidon.nima.webserver.WebServerConfig;
import io.helidon.nima.webserver.accesslog.AccessLogFeature;
import io.helidon.nima.webserver.staticcontent.StaticContentService;
import io.helidon.security.Security;
import io.helidon.security.integration.nima.SecurityFeature;
import io.helidon.tracing.Tracer;
import io.helidon.tracing.TracerBuilder;

import static io.helidon.config.ConfigSources.classpath;
import static io.helidon.config.ConfigSources.environmentVariables;
import static io.helidon.config.ConfigSources.file;
import static io.helidon.config.PollingStrategies.regular;
import static java.time.Duration.ofSeconds;

/**
 * Main class to start the service.
 */
public final class Main {

    /**
     * Interval for config polling.
     */
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

        // needed for default connection of Jersey client
        // to allow our headers to be set
        System.setProperty("sun.net.http.allowRestrictedHeaders", "true");

        // to allow us to set host header explicitly
        System.setProperty("sun.net.http.allowRestrictedHeaders", "true");

        WebServerConfig.Builder builder = WebServer.builder();
        setup(builder);
        WebServer server = builder.build();

        // start the web server
        server.start();
        System.out.println("WEB server is up! http://localhost:" + server.port());
    }

    private static void setup(WebServerConfig.Builder server) {

        Config config = buildConfig();

        Security security = Security.create(config.get("security"));

        Http1Client client = Http1Client.builder().baseUri(config.get("services.backend.endpoint").asString().get())
                                        .addService(WebClientSecurity.create())
                                        .build();

        BackendServiceClient bsc = new BackendServiceClient(client);

        Tracer tracer = TracerBuilder.create(config.get("tracing")).build();

        server.config(config.get("webserver"))
                .routing(routing -> routing
                        .addFeature(AccessLogFeature.create())
                        .addFeature(ObserveFeature.create())
                        .addFeature(SecurityFeature.create(security, config.get("security")))
                        // register static content support (on "/")
                        .register(StaticContentService.builder("/WEB").welcomeFileName("index.html"))
                        // register API handler (on "/api") - this path is secured (see application.yaml)
                        .register("/api", new TodosHandler(bsc, tracer))
                        // and a simple environment handler to see where we are
                        .register("/env", new EnvHandler(config)));
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
