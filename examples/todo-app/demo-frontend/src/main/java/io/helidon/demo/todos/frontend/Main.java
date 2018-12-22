/*
 * Copyright (c) 2017-2018 Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.util.logging.LogManager;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;

import io.helidon.config.Config;
import io.helidon.config.PollingStrategies;
import io.helidon.metrics.MetricsSupport;
import io.helidon.security.Security;
import io.helidon.security.jersey.ClientSecurityFeature;
import io.helidon.security.tools.config.SecureConfigFilter;
import io.helidon.security.webserver.WebSecurity;
import io.helidon.tracing.TracerBuilder;
import io.helidon.tracing.jersey.client.ClientTracingFilter;
import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerConfiguration;
import io.helidon.webserver.StaticContentSupport;
import io.helidon.webserver.WebServer;

import io.opentracing.Tracer;

import static io.helidon.config.ConfigSources.classpath;
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
    private Main() { }

    /**
     * Application main entry point.
     *
     * @param args command line arguments
     * @throws IOException if an error occurred while reading logging
     * configuration
     */
    public static void main(final String[] args) throws IOException {

        // load logging configuration
        LogManager.getLogManager().readConfiguration(
                Main.class.getResourceAsStream("/logging.properties"));

        // needed for default connection of Jersey client
        // to allow our headers to be set
        System.setProperty("sun.net.http.allowRestrictedHeaders", "true");

        Config config = buildConfig();

        // build a client (Jersey)
        // and apply security and tracing features on it
        Client client = ClientBuilder.newBuilder()
                .register(new ClientSecurityFeature())
                .register(ClientTracingFilter.class)
                .build();

        BackendServiceClient bsc = new BackendServiceClient(client, config);

        // create a web server
        WebServer server = createRouting(
                Security.fromConfig(config),
                config,
                bsc)
                .createServer(createConfiguration(config));

        // start the web server
        server.start()
                .whenComplete(Main::started);
    }

    /**
     * Create a {@code ServerConfiguration} instance using the given
     * {@code Config}.
     * @param config the configuration root
     * @return the created {@code ServerConfiguration}
     */
    private static ServerConfiguration createConfiguration(
            final Config config) {

        return ServerConfiguration.builder()
                .config(config.get("webserver"))
                .tracer(registerTracer(config))
                .build();
    }

    /**
     * Create a {@code Tracer} instance using the given {@code Config}.
     * @param config the configuration root
     * @return the created {@code Tracer}
     */
    private static Tracer registerTracer(final Config config) {
        return TracerBuilder.create(config.get("tracing"))
                .buildAndRegister();
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
                // register metrics features (on "/metrics")
                .register(MetricsSupport.create())
                // register security features
                .register(WebSecurity.from(security, config))
                // register static content support (on "/")
                .register(StaticContentSupport.create("/WEB"))
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
            System.out.println("--- Service started on http://localhost:"
                    + webServer.port() + "/index.html");
            System.out.println("--- To stop the application, hit CTRL+C");
        } else {
            System.out.println("--- Cannot start service!");
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
                .sources(
                        // expected on development machine
                        // to override props for dev
                        file("conf/dev.yaml")
                                .pollingStrategy(PollingStrategies::watch)
                                .optional(),
                        // expected in k8s runtime
                        // to configure testing/production values
                        file("conf/frontend.yaml")
                                .pollingStrategy(regular(
                                        ofSeconds(POLLING_INTERVAL)))
                                .optional(),
                        // in jar file
                        // (see src/main/resources/application.yaml)
                        classpath("application.yaml"))
                 // support for passwords in configuration
                .addFilter(SecureConfigFilter.fromConfig())
                .build();
    }
}
