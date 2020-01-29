/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates.
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
package io.helidon.tests.integration.nativeimage.mp1;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import javax.enterprise.inject.Instance;
import javax.enterprise.inject.spi.CDI;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import io.helidon.common.Errors;
import io.helidon.common.configurable.Resource;
import io.helidon.microprofile.server.Server;
import io.helidon.security.jwt.Jwt;
import io.helidon.security.jwt.SignedJwt;
import io.helidon.security.jwt.jwk.JwkKeys;
import io.helidon.security.jwt.jwk.JwkRSA;

import io.opentracing.Tracer;
import io.opentracing.util.GlobalTracer;
import org.eclipse.microprofile.faulttolerance.exceptions.TimeoutException;

import static io.helidon.common.http.Http.Status.FORBIDDEN_403;
import static io.helidon.common.http.Http.Status.OK_200;
import static io.helidon.common.http.Http.Status.UNAUTHORIZED_401;

/**
 * Main class of this integration test.
 */
public final class Mp1Main {
    /**
     * Cannot be instantiated.
     */
    private Mp1Main() {
    }

    /**
     * Application main entry point.
     * @param args command line arguments.
     */
    public static void main(final String[] args) {
        // cleanup before tests
        cleanup();

        String jwtToken = generateJwtToken();

        // start CDI
        //Main.main(args);

        Server.builder()
                .port(8087)
                .applications(new JaxRsApplicationNoCdi())
                .retainDiscoveredApplications(true)
                .basePath("/cdi")
                .build()
                .start();

        long now = System.currentTimeMillis();
        testBean(jwtToken);
        long time = System.currentTimeMillis() - now;
        System.out.println("Tests finished in " + time + " millis");
    }

    private static String generateJwtToken() {
        Jwt jwt = Jwt.builder()
                .subject("jack")
                .addUserGroup("admin")
                .addScope("admin_scope")
                .algorithm(JwkRSA.ALG_RS256)
                .issuer("native-image-mp1")
                .audience("http://localhost:8087/jwt")
                .issueTime(Instant.now())
                .userPrincipal("jack")
                .keyId("SIGNING_KEY")
                .build();

        JwkKeys jwkKeys = JwkKeys.builder()
                .resource(Resource.create("sign-jwk.json"))
                .build();

        SignedJwt signed = SignedJwt.sign(jwt, jwkKeys.forKeyId("sign-rsa").get());
        String tokenContent = signed.tokenContent();

        System.out.println("JWT token to use for /jwt requests: " + tokenContent);

        return tokenContent;
    }

    private static void cleanup() {
        Path path = Paths.get("access.log");
        if (Files.exists(path)) {
            try {
                Files.delete(path);
            } catch (IOException e) {
                System.err.println("Failed to delete access.log");
                e.printStackTrace();
            }
        }
    }

    private static void testBean(String jwtToken) {
        Client client = ClientBuilder.newClient();
        WebTarget target = client.target("http://localhost:8087");

        // select a bean
        Instance<TestBean> select = CDI.current().select(TestBean.class);
        TestBean aBean = select.iterator().next();

        Errors.Collector collector = Errors.collector();

        // testing all modules
        // CDI - (tested indirectly by other tests)
        // Server - capability to start JAX-RS (tested indirectly by other tests)

        // Configuration
        invoke(collector, "Config injection", "Properties message", aBean::config);

        // Rest Client
        invoke(collector, "Rest client", "Properties message", aBean::restClientMessage);
        // + JSON-P
        invoke(collector, "Rest client JSON-P", "json-p", aBean::restClientJsonP);
        // + JSON-B
        invoke(collector, "Rest client JSON-B", "json-b", aBean::restClientJsonB);

        // Fault Tolerance
        invoke(collector, "FT Fallback", "Fallback success", aBean::fallback);
        invoke(collector, "FT Retry", "Success on 3. try", aBean::retry);
        invoke(collector, "FT Async", "Async response", () -> {
            try {
                return aBean.asynchronous().toCompletableFuture().get();
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        });
        invoke(collector, "FT timeout", "timeout", () -> {
            try {
                return aBean.timeout();
            } catch (TimeoutException ignored) {
                return "timeout";
            }
        });

        // JWT-Auth
        validateJwtProtectedResource(collector, target, jwtToken);

        // OIDC Authentication (same as JWT-Auth, using a different provider with different configuration)
        validateOidcProtectedResource(collector, target, jwtToken);

        // Basic-Auth
        validateBasicAuthProtectedResource(collector, target);

        // Metrics
        validateMetrics(collector, target);

        // Access Log
        vaidateAccessLog(collector);

        // Tracing
        validateTracing(collector);

        // Health Checks
        validateHealth(collector, target);

        collector.collect()
                .checkValid();
    }

    private static void validateBasicAuthProtectedResource(Errors.Collector collector, WebTarget target) {
        String path = "/basic";
        WebTarget basicAuthTarget = target.path(path);

        testPublic(collector, basicAuthTarget, path);

        testScopeNoAuth(collector, basicAuthTarget, path);
        testScopeAuth(collector, basicAuthTarget, path, basic("jack", "password"), false);

        testRoleNoAuth(collector, basicAuthTarget, path);
        testRoleAuth(collector, basicAuthTarget, path, basic("jack", "password"), true);
        testRoleAuth(collector, basicAuthTarget, path, basic("john", "password"), false);
    }

    private static void validateEndpointWithJwtHeader(Errors.Collector collector,
                                                      WebTarget target,
                                                      String path,
                                                      String jwtToken) {
        WebTarget jwtTarget = target.path(path);

        // public
        testPublic(collector, jwtTarget, path);

        // scope
        String authorizationHeader = "bearer " + jwtToken;
        testScopeNoAuth(collector, jwtTarget, path);
        testScopeAuth(collector, jwtTarget, path, authorizationHeader, true);


        // role
        testRoleNoAuth(collector, jwtTarget, path);
        testRoleAuth(collector, jwtTarget, path, authorizationHeader, true);
    }

    private static void validateOidcProtectedResource(Errors.Collector collector, WebTarget target, String jwtToken) {
        validateEndpointWithJwtHeader(collector, target, "/oidc", jwtToken);
    }

    private static void validateJwtProtectedResource(Errors.Collector collector, WebTarget target, String jwtToken) {
        validateEndpointWithJwtHeader(collector, target, "/jwt", jwtToken);
    }

    private static void validateTracing(Errors.Collector collector) {
        Tracer tracer = GlobalTracer.get();
        if (!tracer.toString().contains("Jaeger")) {
            // could not find how to get the actual instance of tracer from API
            collector.fatal(tracer, "This application should be configured with Jaeger tracer, yet it is not: " + tracer);
        }
    }

    private static void validateHealth(Errors.Collector collector, WebTarget target) {
        JsonObject health = target.path("/health/ready")
                .request(MediaType.APPLICATION_JSON)
                .get(JsonObject.class);

        JsonArray checks = health.getJsonArray("checks");
        if (checks.size() < 1) {
            collector.fatal("There should be at least one readiness healtcheck provided by this app");
        }

        health = target.path("/health/live")
                .request(MediaType.APPLICATION_JSON)
                .get(JsonObject.class);

        checks = health.getJsonArray("checks");
        if (checks.size() < 1) {
            collector.fatal("There should be at least one liveness healtcheck provided by this app");
        }
    }

    private static void validateMetrics(Errors.Collector collector, WebTarget target) {
        JsonObject vendor = target.path("/metrics/vendor")
                .request(MediaType.APPLICATION_JSON)
                .get(JsonObject.class);

        int count = vendor.getInt("requests.count");
        if (count == 0) {
            collector.fatal("Vendor metric \"requests.count\" must not be zero");
        }

        JsonObject base = target.path("/metrics/base")
                .request(MediaType.APPLICATION_JSON)
                .get(JsonObject.class);

        long uptime = base.getJsonNumber("jvm.uptime").longValue();
        if (uptime == 0) {
            collector.fatal("Base metric \"jvm.uptime\" must not be zero");
        }

        JsonObject application = target.path("/metrics/application")
                .request(MediaType.APPLICATION_JSON)
                .get(JsonObject.class);

        JsonObject timer = application.getJsonObject("io.helidon.tests.integration.nativeimage.mp1.TestBean.config");
        if (null == timer) {
            collector.fatal("Timer for TestBean.config() is not present in metrics result");
        } else {
            count = timer.getInt("count");
            if (count == 0) {
                collector.fatal(timer, "The TestBean.config() should have bean called at least once, yet metric is 0");
            }
        }
    }

    private static void vaidateAccessLog(Errors.Collector collector) {
        Path path = Paths.get("access.log");
        // 1. File access.log must be present on filesystem (in current dir)
        if (Files.exists(path)) {
            // and contain the access log statements
            // First three lines are known
            try {
                List<String> firstThreeLines = getThreeLines(path);
                if (firstThreeLines.size() == 3) {
                    // first line should be something like:
                    // 127.0.0.1 - [09/Jan/2020:17:14:28 +0000] "GET /cdi/property HTTP/1.1" 200 18 1443
                    String line = firstThreeLines.get(0);
                    if (!line.contains("\"GET /cdi/property HTTP/1.1\" 200")) {
                        collector.fatal(line, "First line of access log should contain /cdi/property");
                    }
                    line = firstThreeLines.get(1);
                    if (!line.contains("\"GET /cdi/jsonp HTTP/1.1\" 200")) {
                        collector.fatal(line, "Second line of access log should contain /cdi/jsonp");
                    }
                    line = firstThreeLines.get(2);
                    if (!line.contains("\"GET /cdi/jsonb HTTP/1.1\" 200")) {
                        collector.fatal(line, "Third line of access log should contain /cdi/jsonb");
                    }
                } else {
                    collector.fatal(path, "access.log should contain at least three lines, but contains: "
                            + firstThreeLines.size());
                }
            } catch (IOException e) {
                collector.fatal(path, "Failed to read access.log: " + e.getClass().getName() + ": " + e.getMessage());
            }
        } else {
            collector.fatal(path, "access.log not found, should be in current directory");
        }
    }

    private static List<String> getThreeLines(Path path) throws IOException {
        AtomicInteger counter = new AtomicInteger();
        List<String> result = new LinkedList<>();

        Files.lines(path)
                .forEach(line -> {
                    if (counter.incrementAndGet() <= 3) {
                        result.add(line);
                    }
                });

        return result;
    }

    private static void invoke(Errors.Collector collector, String assertionName, String expected, Supplier<String> invoke) {
        try {
            String actual = invoke.get();
            if (!expected.equals(actual)) {
                collector.fatal(assertionName + ", expected \"" + expected + "\", actual: \"" + actual + "\"");
            }
        } catch (Exception e) {
            e.printStackTrace();
            collector.fatal(assertionName + " failed. " + e.getClass().getName() + ": " + e.getMessage());
        }
    }

    private static void assertResponse(Errors.Collector collector, String assertionName, String actual, String expected) {
        if (!expected.equals(actual)) {
            collector.fatal(assertionName + ", expected \"" + expected + "\", actual: \"" + actual + "\"");
        }
    }


    private static void testPublic(Errors.Collector collector, WebTarget target, String path) {
        // public
        Response response = target.path("/public")
                .request()
                .get();

        if (response.getStatus() == OK_200.code()) {
            String entity = response.readEntity(String.class);
            if (!"Hello anybody".equals(entity)) {
                collector.fatal("Endpoint " + path + "/public should return \"Hello anybody\", but returned \"" + entity + "\"");
            }
        } else {
            collector.fatal("Endpoint " + path + "/public should be accessible without authentication. Status received: "
                                    + response.getStatus());
        }
    }

    private static void testScopeNoAuth(Errors.Collector collector, WebTarget target, String path) {
        Response response = target.path("/scope")
                .request()
                .get();

        if (response.getStatus() != UNAUTHORIZED_401.code()) {
            collector.fatal("Endpoint " + path + "/scope should be protected, yet response status was "
                                    + response.getStatus());
        }
    }

    private static void testScopeAuth(Errors.Collector collector,
                                      WebTarget target,
                                      String path,
                                      String authorizationHeader,
                                      boolean shouldSucceed) {
        Response response = target.path("/scope")
                .request()
                .header("Authorization", authorizationHeader)
                .get();

        if (shouldSucceed) {
            if (response.getStatus() == OK_200.code()) {
                String entity = response.readEntity(String.class);
                if (!"Hello scope".equals(entity)) {
                    collector.fatal("Endpoint " + path + "/scope should return \"Hello scope\", but returned \"" + entity + "\"");
                }
            } else {
                collector.fatal("Endpoint " + path + "/scope should be accessible with JWT Token. Status received: " + response
                        .getStatus());
            }
        } else {
            if (response.getStatus() == OK_200.code()) {
                collector.fatal("Endpoint " + path + "/scope should not be accessible even when authenticated");
            } else if (response.getStatus() != FORBIDDEN_403.code()) {
                collector.fatal("Endpoint " + path + "/scope should have been forbidden, but returned code "
                                        + response.getStatus());
            }
        }
    }

    private static void testRoleNoAuth(Errors.Collector collector, WebTarget target, String path) {
        Response response = target.path("/role")
                .request()
                .get();

        if (response.getStatus() != UNAUTHORIZED_401.code()) {
            collector.fatal("Endpoint " + path + "/role should be protected, yet response status was "
                                    + response.getStatus());
        }
    }

    private static void testRoleAuth(Errors.Collector collector,
                                     WebTarget target,
                                     String path,
                                     String authorizationHeader,
                                     boolean shouldSucceed) {
        Response response = target.path("/role")
                .request()
                .header("Authorization", authorizationHeader)
                .get();

        if (shouldSucceed) {
            if (response.getStatus() == OK_200.code()) {
                String entity = response.readEntity(String.class);
                if (!"Hello role".equals(entity)) {
                    collector.fatal("Endpoint " + path + "/role should return \"Hello role\", but returned \"" + entity + "\"");
                }
            } else {
                collector.fatal("Endpoint " + path + "/role should be accessible with security. Status received: "
                                        + response.getStatus());
            }
        } else {
            if (response.getStatus() == OK_200.code()) {
                collector.fatal("Endpoint " + path + "/role should not be accessible, as user does not have the correct role");
            } else if (response.getStatus() != FORBIDDEN_403.code()) {
                collector.fatal("Endpoint " + path + "/role should have been forbidden, but returned code "
                                        + response.getStatus());
            }
        }
    }

    private static String basic(String username, String password) {
        return "basic " + Base64.getEncoder().encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
    }
}

