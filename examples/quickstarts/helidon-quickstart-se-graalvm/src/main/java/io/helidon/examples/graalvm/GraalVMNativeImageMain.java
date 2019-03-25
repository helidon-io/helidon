/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
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
package io.helidon.examples.graalvm;

import java.io.IOException;
import java.util.logging.LogManager;

import javax.json.Json;
import javax.json.JsonObject;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.health.HealthSupport;
import io.helidon.health.checks.HealthChecks;
import io.helidon.media.jsonb.server.JsonBindingSupport;
import io.helidon.media.jsonp.server.JsonSupport;
import io.helidon.metrics.MetricsSupport;
import io.helidon.metrics.RegistryFactory;
import io.helidon.security.Security;
import io.helidon.security.integration.webserver.WebSecurity;
import io.helidon.tracing.TracerBuilder;
import io.helidon.webserver.Handler;
import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerConfiguration;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;
import io.helidon.webserver.WebServer;

import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.MetricRegistry;

/**
 * Runnable class for GraalVM native image integration example.
 * <p>
 * Steps:
 * <ol>
 * <li>Follow "Setting up the development environment" guide from: https://github.com/cstancu/netty-native-demo</li>
 * <li>Configure the {@code native.image} property in pom.xml of the example</li>
 * <li>Run {@code mvn clean package exec:exec} in the example directory</li>
 * </ol>
 */
public final class GraalVMNativeImageMain {
    private static long timestamp;

    static {
        try {
            LogManager.getLogManager().readConfiguration(GraalVMNativeImageMain.class.getResourceAsStream("/logging.properties"));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // private constructor
    private GraalVMNativeImageMain() {
    }

    /**
     * Start this example.
     *
     * @param args not used
     * @throws java.io.IOException if we fail to read logging configuration
     */
    public static void main(String[] args) throws IOException {
        // this property is not available in Graal SVM, and is not mandatory, yet YAML parser fails if it not present
        System.setProperty("java.runtime.name", "Graal SubstrateVM");

        timestamp = System.currentTimeMillis();

        Config config = createConfig();

        ServerConfiguration serverConfig = ServerConfiguration.builder(config.get("server"))
                /*
                 Tracing registration
                 */
                .tracer(TracerBuilder.create(config.get("tracing")).buildAndRegister())
                .build();

        WebServer.create(serverConfig, routing(config))
                .start()
                .thenAccept(GraalVMNativeImageMain::webServerStarted)
                .exceptionally(GraalVMNativeImageMain::webServerFailed);
    }

    private static Config createConfig() {
        return Config.create(
                ConfigSources.file("conf/dev-application.yaml").optional(),
                ConfigSources.classpath("application.yaml")
        );
    }

    private static Void webServerFailed(Throwable throwable) {
        System.err.println("Failed to start webserver");
        throwable.printStackTrace();
        return null;
    }

    private static void webServerStarted(WebServer webServer) {
        //System.exit(0);

        long time = System.currentTimeMillis() - timestamp;
        System.out.println("Application started in " + time + " milliseconds");
        System.out.println("Application is available at:");
        System.out.println("http://localhost:" + webServer.port() + "/");
    }

    private static Routing routing(Config config) {
        /*
         * Config
         */
        String message = config.get("my-app.message")
                .asString()
                .orElse("Default message");

        /*
         * Metrics
         */
        // there is an ordering requirement for metric support in v 1.0.0 - to be fixed in later versions
        MetricsSupport metrics = MetricsSupport.create(config.get("metrics"));
        MetricRegistry registry = RegistryFactory.getInstance().getRegistry(MetricRegistry.Type.APPLICATION);
        Counter counter = registry.counter("counter");

        /*
         * Health
         */
        HealthSupport health = HealthSupport.builder()
                .config(config.get("health"))
                .add((HealthCheck) () -> HealthCheckResponse.builder()
                        .name("test")
                        .up()
                        .withData("time", System.currentTimeMillis())
                        .build())
                .add(HealthChecks.heapMemoryCheck())
                .build();

        /*
         * Security
         */
        Config securityConfig = config.get("security");
        Security security = Security.create(securityConfig);
        WebSecurity webSecurity = WebSecurity.create(security, securityConfig);

        return Routing.builder()
                // register /metrics endpoint that serves metric information
                .register(metrics)
                // register security restrictions for our routing
                .register(webSecurity)
                // register /health endpoint that serves health checks
                .register(health)
                // simple routing
                .get("/", (req, res) -> res.send(message))
                // secured routing with metric
                .get("/hello", (req, res) -> {
                    res.send("Hello World");
                    counter.inc();
                })
                /*
                 * JSON-P
                 */
                // register JSON-P support
                .register("/json", JsonSupport.create())
                // JSON-P endpoint
                .get("/json", GraalVMNativeImageMain::jsonResponse)
                // JSON-P echo endpoint
                .put("/json", Handler.create(JsonObject.class, GraalVMNativeImageMain::jsonRequest))
                /*
                 * JSON-B
                 */
                // register JSON-B support
                .register("/jsonb", JsonBindingSupport.create())
                // JSON-B endpoint
                .get("/jsonb", GraalVMNativeImageMain::jsonbResponse)
                // JSON-B echo endpoint
                .put("/jsonb", Handler.create(JsonBHello.class, GraalVMNativeImageMain::jsonbRequest))
                // and now build the routing
                .build();
    }

    // JSON-B echo endpoint
    private static void jsonbRequest(ServerRequest request, ServerResponse serverResponse, JsonBHello hello) {
        serverResponse.send(hello);
    }

    // JSON-P echo endpoint
    private static void jsonRequest(ServerRequest request, ServerResponse serverResponse, JsonObject jsonObject) {
        serverResponse.send(jsonObject);
    }

    // JSON-B endpoint
    private static void jsonbResponse(ServerRequest req, ServerResponse res) {
        res.send(new JsonBHello(req.queryParams().first("param").orElse("default")));
    }

    // JSON-P endpoint
    private static void jsonResponse(ServerRequest req, ServerResponse res) {
        String param = req.queryParams().first("param").orElse("default");

        JsonObject theObject = Json.createObjectBuilder()
                .add("key", "value")
                .add("time", System.currentTimeMillis())
                .add("parameter", param)
                .build();

        res.send(theObject);
    }
}
