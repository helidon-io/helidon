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

import java.time.Duration;
import java.util.List;

import io.helidon.common.LogConfig;
import io.helidon.common.http.Http;
import io.helidon.config.Config;
import io.helidon.config.FileSystemWatcher;
import io.helidon.media.jsonp.JsonpSupport;
import io.helidon.metrics.serviceapi.MetricsSupport;
import io.helidon.security.Security;
import io.helidon.security.integration.webserver.WebSecurity;
import io.helidon.tracing.Tracer;
import io.helidon.tracing.TracerBuilder;
import io.helidon.webserver.Routing;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.accesslog.AccessLogSupport;
import io.helidon.webserver.staticcontent.StaticContentSupport;

import static io.helidon.config.ConfigSources.classpath;
import static io.helidon.config.ConfigSources.environmentVariables;
import static io.helidon.config.ConfigSources.file;
import static io.helidon.config.PollingStrategies.regular;

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

        Config config = buildConfig();

        Security security = Security.create(config.get("security"));

        // create a web server
        WebServer server = WebServer.builder()
                .addRouting(createRouting(security, config))
                .config(config.get("webserver"))
                .addMediaSupport(JsonpSupport.create())
                .tracer(registerTracer(config))
                .build();

        // start the web server
        server.start().whenComplete(Main::started);
    }

    /**
     * Create a {@code Tracer} instance using the given {@code Config}.
     * @param config the configuration root
     * @return the created {@code Tracer}
     */
    private static Tracer registerTracer(Config config) {
        return TracerBuilder.create(config.get("tracing")).build();
    }

    /**
     * Create the web server routing and register all handlers.
     * @param security the security features
     * @param config the configuration root
     * @return the created {@code Routing}
     */
    private static Routing createRouting(Security security, Config config) {
        return Routing.builder()
                .register(AccessLogSupport.create())
                // register metrics features (on "/metrics")
                .register(MetricsSupport.create())
                // register security features
                .register(WebSecurity.create(security, config.get("security")))
                // redirect POST / to GET /
                .post("/", (req, res) -> {
                    res.addHeader(Http.Header.LOCATION, "/");
                    res.status(Http.Status.SEE_OTHER_303);
                    res.send();
                })
                // register static content support (on "/")
                .register(StaticContentSupport.builder("/WEB").welcomeFileName("index.html"))
                // register API handler (on "/api") - this path is secured (see application.yaml)
                .register("/api", new TodoService(new BackendServiceClient(config)))
                .build();
    }

    /**
     * Handle web server started event: if successful print server started
     * message in the console with the corresponding URL, otherwise print an
     * error message and exit the application.
     * @param webServer the {@code WebServer} instance
     * @param throwable if non {@code null}, indicate a server startup error
     */
    private static void started(WebServer webServer, Throwable throwable) {
        if (throwable == null) {
            System.out.println("WEB server is up! http://localhost:" + webServer.port());
        } else {
            throwable.printStackTrace(System.out);
            System.exit(1);
        }
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
                                .pollingStrategy(regular(Duration.ofSeconds(POLLING_INTERVAL)))
                                .optional(),
                        // in jar file
                        // (see src/main/resources/application.yaml)
                        classpath("application.yaml")))
                .build();
    }
}
