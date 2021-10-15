/*
 * Copyright (c) 2019, 2021 Oracle and/or its affiliates.
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

package io.helidon.tests.bookstore;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.logging.Logger;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;

import io.helidon.common.http.Http;
import io.helidon.common.http.MediaType;
import io.helidon.media.jsonp.JsonpSupport;
import io.helidon.webclient.WebClient;
import io.helidon.webclient.WebClientResponse;

import com.oracle.bedrock.runtime.Application;
import com.oracle.bedrock.runtime.LocalPlatform;
import com.oracle.bedrock.runtime.console.CapturingApplicationConsole;
import com.oracle.bedrock.runtime.options.Arguments;
import com.oracle.bedrock.runtime.options.Console;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;

class MainTest {

    private static String appJarPathSE = System.getProperty("app.jar.path.se", "please-set-app.jar.path.se");
    private static String appJarPathMP = System.getProperty("app.jar.path.mp", "please-set-app.jar.path.mp");

    private static Application application;
    private static LocalPlatform localPlatform = LocalPlatform.get();
    private static int port = localPlatform.getAvailablePorts().next();
    private static URL healthUrl;
    private static WebClient webClient;

    private static final String MODULE_NAME_MP = "io.helidon.tests.apps.bookstore.mp";
    private static final String MODULE_NAME_SE = "io.helidon.tests.apps.bookstore.se";
    private static final Logger LOGGER = Logger.getLogger(MainTest.class.getName());

    @BeforeAll
    static void setup() throws Exception {
        LOGGER.info("Using port number" + port);
        healthUrl = new URL("http://localhost:" + port + "/health");
        appJarPathSE = Paths.get(appJarPathSE).normalize().toString();
        appJarPathMP = Paths.get(appJarPathMP).normalize().toString();
        webClient = WebClient.builder()
                .baseUri("http://localhost:" + port)
                .addMediaSupport(JsonpSupport.create())
                .build();
    }

    @AfterEach
    void stopTheApplication() throws Exception {
        if (application != null) {
            application.close();
        }
        application = null;
        waitForApplicationDown();
    }

    /**
     * Start the application using the application jar and classpath
     *
     * @param appJarPath    Path to jar file with application
     * @param javaArgs      Additional java arguments to pass
     * @throws Exception
     */
    void startTheApplication(String appJarPath, List<String> javaArgs) throws Exception {
        Arguments args = toArguments(appJarPath, javaArgs, null);
        application = localPlatform.launch("java", args);
        waitForApplicationUp();
    }

    /**
     * Start the application using the application jar and module path
     *
     * @param appJarPath    Path to jar file with application
     * @param javaArgs      Additional java arguments to pass
     * @param moduleName    Name of application's module that contains Main
     * @throws Exception
     */
    void startTheApplicationModule(String appJarPath, List<String> javaArgs, String moduleName) throws Exception {
        Arguments args = toArguments(appJarPath, javaArgs, moduleName);
        application = localPlatform.launch("java", args);
        waitForApplicationUp();
    }

    @Test
    void exitOnStartedSe() throws Exception {
        runExitOnStartedTest("se");
    }

    @Test
    void exitOnStartedMp() throws Exception {
        runExitOnStartedTest("mp");
    }

    private void runExitOnStartedTest(String edition) throws Exception {
        Arguments args = toArguments(editionToJarPath(edition), List.of("-Dexit.on.started=!"), null);
        CapturingApplicationConsole console = new CapturingApplicationConsole();
        application = localPlatform.launch("java", args, Console.of(console));
        Queue<String> stdOut = console.getCapturedOutputLines();
        long maxTime = System.currentTimeMillis() + (10 * 1000);
        do {
            if (stdOut.stream().anyMatch(line -> line.contains("exit.on.started"))) {
                return;
            }
            Thread.sleep(500);
        } while (System.currentTimeMillis() < maxTime);

        String eol = System.getProperty("line.separator");
        Assertions.fail("quickstart " + edition + " did not exit as expected." + eol
                                + eol
                                + "stdOut: " + stdOut + eol
                                + eol
                                + " stdErr: " + console.getCapturedErrorLines());
    }

    @Test
    void basicTestJsonP() throws Exception {
        runJsonFunctionalTest("se", "jsonp");
    }

    @Test
    void basicTestJsonB() throws Exception {
        runJsonFunctionalTest("se", "jsonb");
    }

    @Test
    void basicTestJackson() throws Exception {
        runJsonFunctionalTest("se", "jackson");
    }

    @Test
    void basicTestJsonMP() throws Exception {
        runJsonFunctionalTest("mp", "");
    }

    @Test
    void basicTestMetricsHealthSE() throws Exception {
        runMetricsAndHealthTest("se", "jsonp", false);
    }

    @Test
    void basicTestMetricsHealthMP() throws Exception {
        runMetricsAndHealthTest("mp", "", false);
    }

    @Test
    void basicTestMetricsHealthJsonB() throws Exception {
        runMetricsAndHealthTest("se", "jsonb", false);
    }

    @Test
    void basicTestMetricsHealthJackson() throws Exception {
        runMetricsAndHealthTest("se", "jackson", false);
    }

    @Test
    void basicTestMetricsHealthSEModules() throws Exception {
        runMetricsAndHealthTest("se", "jsonp", true);
    }

    @Test
    void basicTestMetricsHealthMPModules() throws Exception {
        runMetricsAndHealthTest("mp", "", true);
    }

    /**
     * Run some basic CRUD operations on the server. The server supports
     * running with any of our three JSON libraries: jsonp, jsonb, jackson.
     * So we set a system property to select the library to use before starting
     * the server
     *
     * @param edition     "mp", "se"
     * @param jsonLibrary "jsonp", "jsonb" or "jackson"
     * @throws Exception on test failure
     */
    private void runJsonFunctionalTest(String edition, String jsonLibrary) throws Exception {
        JsonObject json = getBookAsJsonObject();
        int numberOfBooks = 1000;
        List<String> systemPropertyArgs = new LinkedList<>();

        systemPropertyArgs.add("-Dbookstore.size=" + numberOfBooks);
        if (jsonLibrary != null && !jsonLibrary.isEmpty()) {
            systemPropertyArgs.add("-Dapp.json-library=" + jsonLibrary);
        }

        startTheApplication(editionToJarPath(edition), systemPropertyArgs);

        webClient.get()
                .path("/books")
                .request(JsonArray.class)
                .thenAccept(bookArray -> assertThat("Number of books", bookArray.size(), is(numberOfBooks)))
                .toCompletableFuture()
                .get();

        webClient.post()
                .path("/books")
                .submit(json)
                .thenAccept(it -> assertThat("HTTP response POST", it.status(), is(Http.Status.OK_200)))
                .thenCompose(it -> webClient.get()
                        .path("/books/123456")
                        .request(JsonObject.class))
                .thenAccept(it -> assertThat("Checking if correct ISBN", it.getString("isbn"), is("123456")))
                .toCompletableFuture()
                .get();

        webClient.get()
                .path("/books/0000")
                .request()
                .thenAccept(it -> assertThat("HTTP response GET bad ISBN", it.status(), is(Http.Status.NOT_FOUND_404)))
                .toCompletableFuture()
                .get();

        webClient.get()
                .path("/books")
                .request()
                .thenApply(it -> {
                    assertThat("HTTP response list books", it.status(), is(Http.Status.OK_200));
                    return it;
                })
                .thenCompose(WebClientResponse::close)
                .toCompletableFuture()
                .get();

        webClient.delete()
                .path("/books/123456")
                .request()
                .thenAccept(it -> assertThat("HTTP response delete book", it.status(), is(Http.Status.OK_200)))
                .toCompletableFuture()
                .get();
    }

    /**
     * Run some basic metrics and health operations on the server. The server supports
     * running with any of our three JSON libraries: jsonp, jsonb, jackson.
     * So we set a system property to select the library to use before starting
     * the server
     *
     * @param edition     "mp", "se"
     * @param jsonLibrary "jsonp", "jsonb" or "jackson"
     * @param useModules true to use modulepath, false to use classpath
     * @throws Exception on test failure
     */
    private void runMetricsAndHealthTest(String edition, String jsonLibrary, boolean useModules) throws Exception {

        List<String> systemPropertyArgs = new LinkedList<>();
        if (jsonLibrary != null && !jsonLibrary.isEmpty()) {
            systemPropertyArgs.add("-Dapp.json-library=" + jsonLibrary);
        }

        if (useModules) {
            startTheApplicationModule(editionToJarPath(edition), systemPropertyArgs, editionToModuleName(edition));
        } else {
            startTheApplication(editionToJarPath(edition), systemPropertyArgs);
        }

        // Get Prometheus style metrics
        webClient.get()
                .accept(MediaType.WILDCARD)
                .path("/metrics")
                .request(String.class)
                // Make sure we got prometheus metrics
                .thenAccept(it -> assertThat("Making sure we got Prometheus format", it, startsWith("# TYPE")))
                .toCompletableFuture()
                .get();

        // Get JSON encoded metrics
        webClient.get()
                .accept(MediaType.APPLICATION_JSON)
                .path("/metrics")
                .request(JsonObject.class)
                // Makes sure we got JSON metrics
                .thenAccept(it -> assertThat("Checking request count",
                                             it.getJsonObject("vendor").getInt("requests.count"),
                                             greaterThan(0)))
                .toCompletableFuture()
                .get();

        // Get JSON encoded metrics/base
        webClient.get()
                .accept(MediaType.APPLICATION_JSON)
                .path("/metrics/base")
                .request(JsonObject.class)
                // Makes sure we got JSON metrics
                .thenAccept(it -> assertThat("Checking request count",
                                             it.getInt("thread.count"),
                                             greaterThan(0)))
                .toCompletableFuture()
                .get();

        // Get JSON encoded health check
        webClient.get()
                .accept(MediaType.APPLICATION_JSON)
                .path("/health")
                .request(JsonObject.class)
                .thenAccept(it -> {
                    assertThat("Checking health outcome", it.getString("outcome"), is("UP"));
                    assertThat("Checking health status", it.getString("status"), is("UP"));
                    // Verify that built-in health checks are disabled in MP according to
                    // 'microprofile-config.properties' setting in bookstore application
                    if (edition.equals("mp")) {
                        assertThat("Checking built-in health checks disabled",
                                   it.getJsonArray("checks").size(),
                                   is(0));
                    }
                })
                .toCompletableFuture()
                .get();
    }

    @ParameterizedTest
    @ValueSource(strings = {"se", "mp"})
    void routing(String edition) throws Exception {

        startTheApplication(editionToJarPath(edition), Collections.emptyList());

        webClient.get()
                .accept(MediaType.APPLICATION_JSON)
                .skipUriEncoding()
                .path("/boo%6bs")
                .request()
                .thenAccept(it -> {
                    if ("se".equals(edition)) {
                        assertThat("Checking encode URL response SE", it.status(), is(Http.Status.OK_200));
                    } else {
                        // JAXRS does not decode URLs before matching
                        assertThat("Checking encode URL response MP", it.status(), is(Http.Status.NOT_FOUND_404));
                    }
                })
                .toCompletableFuture()
                .get();

        webClient.get()
                .accept(MediaType.APPLICATION_JSON)
                .path("/badurl")
                .request()
                .thenAccept(it -> assertThat("Checking encode URL response", it.status(), is(Http.Status.NOT_FOUND_404)))
                .toCompletableFuture()
                .get();
    }

    private JsonObject getBookAsJsonObject() throws IOException {
        InputStream is = getClass().getClassLoader().getResourceAsStream("book.json");
        if (is != null) {
            return Json.createReader(is).readObject();
        } else {
            throw new IOException("Could not find resource book.json");
        }
    }

    private void waitForApplicationUp() throws Exception {
        waitForApplication(healthUrl, true);
    }

    private void waitForApplicationDown() throws Exception {
        waitForApplication(healthUrl, false);
    }

    /**
     * Wait for the application to be up or down.
     *
     * @param url    URL to ping. URL should return 200 when application is up.
     * @param toBeUp true if waiting to come up, false if waiting to go down
     * @throws Exception on a failure
     */
    private void waitForApplication(URL url, boolean toBeUp) throws Exception {
        long timeout = 15 * 1000; // 15 seconds should be enough to start/stop the server
        long now = System.currentTimeMillis();
        String operation = (toBeUp ? "start" : "stop");

        HttpURLConnection conn = null;
        int responseCode;
        do {
            LOGGER.info("Waiting for application to " + operation);
            if ((System.currentTimeMillis() - now) > timeout) {
                Assertions.fail("Application failed to " + operation);
            }
            try {
                conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(500);
                responseCode = conn.getResponseCode();
                if (toBeUp && responseCode != 200) {
                    LOGGER.info("Application returned bad health response  " + responseCode);
                }
            } catch (Exception ex) {
                if (toBeUp) {
                    LOGGER.info("Failed to connect to application at " + url.toString() + ": " + ex.toString());
                }
                responseCode = -1;
            }
            if (conn != null) {
                conn.disconnect();
            }
            Thread.sleep(500);
        } while ((toBeUp && responseCode != 200) || (!toBeUp && responseCode != -1));
    }

    private static Arguments toArguments(String appJarPath, List<String> javaArgs, String moduleName) {
        if (application != null) {
            Assertions.fail("Can't start the application, it is already running");
        }

        List<String> startArgs = new ArrayList<>(javaArgs);
        startArgs.add("-Dserver.port=" + port);

        if (moduleName != null && ! moduleName.isEmpty() ) {
            File jarFile = new File(appJarPath);
            // --module-path target/bookstore-se.jar:target/libs -m io.helidon.tests.apps.bookstore.se/io.helidon.tests.apps.bookstore.se.Main
            startArgs.add("--module-path");
            startArgs.add(appJarPath + File.pathSeparatorChar + jarFile.getParent() + "/libs");
            startArgs.add("-m");
            startArgs.add(moduleName + "/" + moduleName + ".Main");
        } else {
            startArgs.add("-jar");
            startArgs.add(appJarPath);
        }
        return Arguments.of(startArgs);
    }

    private static String editionToJarPath(String edition) {
        if ("se".equals(edition)) {
            return appJarPathSE;
        } else if ("mp".equals(edition)) {
            return appJarPathMP;
        } else {
            throw new IllegalArgumentException("Invalid edition '" + edition + "'. Must be 'se' or 'mp'");
        }
    }

    private static String editionToModuleName(String edition) {
        if ("se".equals(edition)) {
            return MODULE_NAME_SE;
        } else if ("mp".equals(edition)) {
            return MODULE_NAME_MP;
        } else {
            throw new IllegalArgumentException("Invalid edition '" + edition + "'. Must be 'se' or 'mp'");
        }
    }
}
