/*
 * Copyright (c) 2017, 2020 Oracle and/or its affiliates.
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
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;

import io.helidon.common.LogConfig;
import io.helidon.config.Config;
import io.helidon.config.FileSystemWatcher;
import io.helidon.media.jsonp.JsonpSupport;
import io.helidon.metrics.MetricsSupport;
import io.helidon.security.Security;
import io.helidon.security.integration.webserver.WebSecurity;
import io.helidon.tracing.TracerBuilder;
import io.helidon.webserver.Routing;
import io.helidon.webserver.StaticContentSupport;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.accesslog.AccessLogSupport;

import io.opentracing.Tracer;
import org.glassfish.jersey.logging.LoggingFeature;

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

        Config config = buildConfig();

        // build a client (Jersey)
        // and apply security and tracing features on it
        Client client = ClientBuilder.newClient();
        client.register(new LoggingFeature(Logger.getGlobal(), Level.FINE, LoggingFeature.Verbosity.PAYLOAD_ANY, 8192));

        BackendServiceClient bsc = new BackendServiceClient(client, config);

        // create a web server
        WebServer server = WebServer.builder(createRouting(
                    Security.create(config.get("security")),
                    config,
                    bsc))
                .config(config.get("webserver"))
                .addMediaSupport(JsonpSupport.create())
                .tracer(registerTracer(config))
                .build();

        // start the web server
        server.start()
                .whenComplete(Main::started);
    }

    /**
     * Create a {@code Tracer} instance using the given {@code Config}.
     * @param config the configuration root
     * @return the created {@code Tracer}
     */
    private static Tracer registerTracer(final Config config) {
        return TracerBuilder.create(config.get("tracing")).build();
    }

    /**
     * Create the web server routing and register all handlers.
     * @param security the security features
     * @param config the configuration root
     * @param bsc the backend service client to use
     * @return the created {@code Routing}
     */
    private static Routing createRouting(final Security security,
                                         final Config config,
                                         final BackendServiceClient bsc) {

        return Routing.builder()
                .register(AccessLogSupport.create())
                // register metrics features (on "/metrics")
                .register(MetricsSupport.create())
                // register security features
                .register(WebSecurity.create(security, config))
                // register static content support (on "/")
                .register(StaticContentSupport.builder("/WEB").welcomeFileName("index.html"))
                // register API handler (on "/api") - this path is secured (see application.yaml)
                .register("/api", new TodosHandler(bsc))
                // and a simple environment handler to see where we are
                .register("/env", new EnvHandler(config))
                .build();
    }

    /**
     * Handle web server started event: if successful print server started
     * message in the console with the corresponding URL, otherwise print an
     * error message and exit the application.
     * @param webServer the {@code WebServer} instance
     * @param throwable if non {@code null}, indicate a server startup error
     */
    private static void started(final WebServer webServer,
                                final Throwable throwable) {

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
                                .pollingStrategy(regular(
                                        ofSeconds(POLLING_INTERVAL)))
                                .optional(),
                        // in jar file
                        // (see src/main/resources/application.yaml)
                        classpath("application.yaml")))
                .build();
    }
}
