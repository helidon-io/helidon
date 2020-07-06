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
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import javax.enterprise.inject.Instance;
import javax.enterprise.inject.spi.CDI;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.ws.rs.ServiceUnavailableException;
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
        String property = System.getProperty("java.class.path");
        if (null == property || property.trim().isEmpty()) {
            System.out.println("** Running on module path");
        } else {
            System.out.println("** Running on class path");
        }

        // cleanup before tests
        cleanup();

        String jwtToken = generateJwtToken();

        // start CDI
        //Main.main(args);

        Server server = Server.builder()
                .port(7001)
                .applications(new JaxRsApplicationNoCdi())
                .retainDiscoveredApplications(true)
                .basePath("/cdi")
                .build()
                .start();

        boolean failed = false;
        long now = System.currentTimeMillis();
        try {
            testBean(server.port(), jwtToken);
        } catch (Exception e) {
            e.printStackTrace();
            failed = true;
        }
        long time = System.currentTimeMillis() - now;
        System.out.println("Tests finished in " + time + " millis");

        //        Config config = ConfigProvider.getConfig();
        //        List<String> names = new ArrayList<>();
        //        config.getPropertyNames()
        //                .forEach(names::add);
        //        names.sort(String::compareTo);

        //        System.out.println("All configuration options:");
        //        names.forEach(it -> {
        //            config.getOptionalValue(it, String.class)
        //                    .ifPresent(value -> System.out.println(it + "=" + value));
        //        });

        server.stop();

        if (failed) {
            System.exit(-1);
        }
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

    private static void testBean(int port, String jwtToken) {
        Client client = ClientBuilder.newClient();
        WebTarget target = client.target("http://localhost:" + port);

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

        // Message from rest client, originating in BeanClass.BeanType
        invoke(collector, "Rest client bean type", "Properties message", aBean::restClientBeanType);

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

        // OpenAPI
        validateOpenAPI(collector, target);

        // Overall JAX-RS injection
        validateInjection(collector, target);

        // Static content
        validateStaticContent(collector, target);

        // Make sure resource and provider classes are discovered
        validateNoClassApp(collector, target);

        collector.collect()
                .checkValid();
    }

    private static void validateNoClassApp(Errors.Collector collector, WebTarget target) {
        String path = "/noclass";
        String expected = "Hello World ";

        Response response = target.path(path)
                .request()
                .get();

        if (response.getStatus() == OK_200.code()) {
            String entity = response.readEntity(String.class);
            if (!expected.equals(entity)) {
                collector.fatal("Endpoint " + path + "should return \"" + expected + "\", but returned \"" + entity + "\"");
            }
        } else {
            collector.fatal("Endpoint " + path + " should be handled by JaxRsResource.java. Status received: "
                                    + response.getStatus());
        }

        int count = AutoFilter.count();

        if (count == 0) {
            collector.fatal("Filter should have been added to JaxRsApplicationNoClass automatically");
        }
    }

    private static void validateStaticContent(Errors.Collector collector, WebTarget target) {
        String path = "/static/resource.txt";
        String expected = "classpath-resource-text";

        Response response = target.path(path)
                .request()
                .get();

        if (response.getStatus() == OK_200.code()) {
            String entity = response.readEntity(String.class);
            if (!expected.equals(entity)) {
                collector.fatal("Endpoint " + path + "should return \"" + expected + "\", but returned \"" + entity + "\"");
            }
        } else {
            collector.fatal("Endpoint " + path + " should contain static content from /web/resource.txt. Status received: "
                                    + response.getStatus());
        }

        path = "/static";
        expected = "welcome!";
        response = target.path(path)
                .request()
                .get();

        if (response.getStatus() == OK_200.code()) {
            String entity = response.readEntity(String.class);
            if (!expected.equals(entity)) {
                collector.fatal("Endpoint " + path + "should return welcome file's content \"" + expected
                                        + "\", but returned \"" + entity + "\"");
            }
        } else {
            collector.fatal("Endpoint " + path + " should contain static content from /web/welcome.txt. Status received: "
                                    + response.getStatus());
        }
    }

    private static void validateInjection(Errors.Collector collector, WebTarget target) {
        String path = "/cdi/fields";
        WebTarget fieldsTarget = target.path(path);

        try {
            fieldsTarget.request().get(String.class);
        } catch (Exception e) {
            collector.fatal(e, "JAX-RS field injection failed. Check the server log.");
        }
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

    private static void validateOpenAPI(Errors.Collector collector, WebTarget target) {
        JsonObject openApi = target.path("/openapi")
                .request(MediaType.APPLICATION_JSON)
                .get(JsonObject.class);

        // make sure we have all our applications, and for one method check annots are processed
        JsonObject info = openApi.getJsonObject("info");
        String expected = "Generated API";
        if (null == info) {
            collector.fatal("OpenAPI", "info from OpenAPI response is null");
            return;
        }
        String actual = info.getString("title");
        if (!expected.equals(actual)) {
            collector.fatal("OpenAPI", "info.title should be \"" + expected + "\", but is \"" + actual + "\"");
        }
        JsonObject paths = openApi.getJsonObject("paths");
        // each subkey is one path
        Set<String> actualPaths = paths.keySet();

        // for each application
        checkPlainApp(collector, actualPaths, "/cdi");
        checkPlainApp(collector, actualPaths, "/noncdi");
        checkProtectedApp(collector, actualPaths, "/basic");
        checkProtectedApp(collector, actualPaths, "/jwt");
        checkProtectedApp(collector, actualPaths, "/oidc");

        checkJsonContentType(collector, openApi, "/cdi/jsonb");
        checkJsonContentType(collector, openApi, "/noncdi/jsonb");

        checkDescription(collector, openApi, "/noncdi", "Hello world message");
        checkDescription(collector, openApi, "/noncdi/property", "Value of property 'app.message' from config.");
        checkDescription(collector, openApi, "/cdi", "Hello world message");
        checkDescription(collector, openApi, "/cdi/property", "Value of property 'app.message' from config.");
    }

    private static void checkDescription(Errors.Collector collector, JsonObject openApi, String path, String expected) {
        String actual = openApi.getJsonObject("paths")
                .getJsonObject(path)
                .getJsonObject("get")
                .getJsonObject("responses")
                .getJsonObject("200")
                .getString("description");

        if (expected.equals(actual)) {
            return;
        }

        collector.fatal("OpenAPI", "Description on path " + path + " should be \""
                + expected + "\", but is \"" + actual + "\"");
    }

    private static void checkJsonContentType(Errors.Collector collector, JsonObject openApi, String path) {
        JsonObject jsonObject = openApi.getJsonObject("paths")
                .getJsonObject(path)
                .getJsonObject("get")
                .getJsonObject("responses")
                .getJsonObject("200")
                .getJsonObject("content");

        if (!jsonObject.containsKey("application/json")) {
            collector.fatal("OpenAPI", "Path " + path + " should have type application/json");
        }
    }

    private static void checkProtectedApp(Errors.Collector collector, Set<String> actualPaths, String path) {
        // publicHello()
        checkPathExists(collector, actualPaths, path + "/public");
        // scope()
        checkPathExists(collector, actualPaths, path + "/scope");
        // role()
        checkPathExists(collector, actualPaths, path + "/role");
    }

    private static void checkPlainApp(Errors.Collector collector, Set<String> actualPaths, String path) {
        // hello()
        checkPathExists(collector, actualPaths, path);
        // message()
        checkPathExists(collector, actualPaths, path + "/property");
        // jaxRsMessage()
        checkPathExists(collector, actualPaths, path + "/jaxrsproperty");
        // number()
        checkPathExists(collector, actualPaths, path + "/number");
        // jsonObject()
        checkPathExists(collector, actualPaths, path + "/jsonp");
        // jsonBinding()
        checkPathExists(collector, actualPaths, path + "/jsonb");
    }

    private static void checkPathExists(Errors.Collector collector, Set<String> actualPaths, String expectedPath) {
        if (actualPaths.contains(expectedPath)) {
            return;
        }

        collector.fatal("openAPI", "OpenAPI document does not contain documentation of endpoint " + expectedPath);
    }

    private static void validateHealth(Errors.Collector collector, WebTarget target) {
        JsonObject health = target.path("/health/ready")
                .request(MediaType.APPLICATION_JSON)
                .get(JsonObject.class);

        JsonArray checks = health.getJsonArray("checks");
        if (checks.size() != 1) {
            collector.fatal("There should be a readiness health check provided by this app");
        } else {
            JsonObject check = checks.getJsonObject(0);
            if (!"mp1-ready".equals(check.getString("name"))) {
                collector.fatal("The readiness health check should be named \"mp1-ready\", but is " + check.getString("name"));
            }
        }

        try {
            health = target.path("/health/live")
                    .request(MediaType.APPLICATION_JSON)
                    .get(JsonObject.class);

            checks = health.getJsonArray("checks");
            if (checks.size() < 1) {
                collector.fatal("There should be at least one liveness healtcheck provided by this app");
            } else {
                Map<String, JsonObject> checkMap = new HashMap<>();
                checks.forEach(it -> {
                    JsonObject healthCheck = (JsonObject) it;
                    checkMap.put(healthCheck.getString("name"), healthCheck);
                });
                // now the check map contains all healthchecks we have
                // validate our own
                JsonObject healthCheck = healthExistsAndUp(collector, checkMap, "mp1-live");
                if (null != healthCheck) {
                    String message = healthCheck.getJsonObject("data").getString("app.message");
                    if (!"Properties message".equals(message)) {
                        collector.fatal("Message health check should return injected app.message from properties, but got "
                                                + message);
                    }
                }

                healthExistsAndUp(collector, checkMap, "deadlock");
                healthExistsAndUp(collector, checkMap, "diskSpace");
                healthExistsAndUp(collector, checkMap, "heapMemory");
            }
        } catch (ServiceUnavailableException e) {
            collector.fatal(e, "Failed to invoke health endpoint. Exception: " + e.getClass().getName()
                    + ", message: " + e.getMessage() + ", " + e.getResponse().readEntity(String.class));
        }
    }

    private static JsonObject healthExistsAndUp(Errors.Collector collector,
                                                Map<String, JsonObject> checkMap,
                                                String name) {
        JsonObject healthCheck = checkMap.get(name);
        if (null == healthCheck) {
            collector.fatal("\"" + name + "\" health check is not available");
        } else {
            String status = healthCheck.getString("state");
            if (!"UP".equals(status)) {
                collector.fatal("Health check \"" + name + "\" should be up, but is " + status);
            }
        }
        return healthCheck;
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

