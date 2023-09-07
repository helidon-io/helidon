/*
 * Copyright (c) 2019, 2023 Oracle and/or its affiliates.
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
package io.helidon.openapi;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.ProtocolException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;

import io.helidon.common.http.MediaType;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.webserver.WebServer;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Starts a server with the default OpenAPI endpoint to test a static OpenAPI
 * document file in various ways.
 */
public class ServerTest {

    private static WebServer greetingWebServer;
    private static WebServer timeWebServer;

    private static final String GREETING_PATH = "/openapi-greeting";
    private static final String TIME_PATH = "/openapi-time";

    private static final Config OPENAPI_CONFIG_DISABLED_CORS = Config.create(
            ConfigSources.classpath("serverNoCORS.properties").build()).get(OpenAPISupport.Builder.CONFIG_KEY);

    private static final Config OPENAPI_CONFIG_RESTRICTED_CORS = Config.create(
            ConfigSources.classpath("serverCORSRestricted.yaml").build()).get(OpenAPISupport.Builder.CONFIG_KEY);

    static final OpenAPISupport.Builder<?> GREETING_OPENAPI_SUPPORT_BUILDER
            = OpenAPISupport.builder()
                    .staticFile("src/test/resources/openapi-greeting.yml")
                    .webContext(GREETING_PATH)
                    .config(OPENAPI_CONFIG_DISABLED_CORS);

    static final OpenAPISupport.Builder<?> TIME_OPENAPI_SUPPORT_BUILDER
            = OpenAPISupport.builder()
                    .staticFile("src/test/resources/openapi-time-server.yml")
                    .webContext(TIME_PATH)
                    .config(OPENAPI_CONFIG_RESTRICTED_CORS);

    public ServerTest() {
    }

    @BeforeAll
    public static void startup() {
        greetingWebServer = TestUtil.startServer(GREETING_OPENAPI_SUPPORT_BUILDER);
        timeWebServer = TestUtil.startServer(TIME_OPENAPI_SUPPORT_BUILDER);
    }

    @AfterAll
    public static void shutdown() {
        TestUtil.shutdownServer(greetingWebServer);
        TestUtil.shutdownServer(timeWebServer);
    }

    @Test
    void testWithEmptyAccept() throws IOException {
        URL url = new URL("http://localhost:" + greetingWebServer.port() + GREETING_PATH);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Accept", ""); // Yes, to test a bug fix specify Accept with nothing.
        Map<String, Object> openAPIDocument = TestUtil.yamlFromResponse(conn);

        // Check one simple value.
        ArrayList<Map<String, Object>> servers = TestUtil.as(
                ArrayList.class, openAPIDocument.get("servers"));
        Map<String, Object> server = servers.get(0);
        assertThat("unexpected URL", server.get("url"), is("http://localhost:8000"));
        assertThat("unexpected description", server.get("description"), is("Local test server"));
    }

    /**
     * Accesses the OpenAPI endpoint, requesting a YAML response payload, and
     * makes sure that navigating among the YAML yields what we expect.
     *
     * @throws Exception in case of errors sending the request or reading the
     * response
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testGreetingAsYAML() throws Exception {
        HttpURLConnection cnx = TestUtil.getURLConnection(
                greetingWebServer.port(),
                "GET",
                GREETING_PATH,
                MediaType.APPLICATION_OPENAPI_YAML);
        Map<String, Object> openAPIDocument = TestUtil.yamlFromResponse(cnx);

        ArrayList<Map<String, Object>> servers = TestUtil.as(
                ArrayList.class, openAPIDocument.get("servers"));
        Map<String, Object> server = servers.get(0);
        assertThat("unexpected URL", server.get("url"), is("http://localhost:8000"));
        assertThat("unexpected description", server.get("description"), is("Local test server"));

        Map<String, Object> paths = TestUtil.as(Map.class, openAPIDocument.get("paths"));
        Map<String, Object> setGreetingPath = TestUtil.as(Map.class, paths.get("/greet/greeting"));
        Map<String, Object> put = TestUtil.as(Map.class, setGreetingPath.get("put"));
        assertThat(put.get("summary"), is("Sets the greeting prefix"));
        Map<String, Object> requestBody = TestUtil.as(Map.class, put.get("requestBody"));
        assertThat(Boolean.class.cast(requestBody.get("required")), is(true));
        Map<String, Object> content = TestUtil.as(Map.class, requestBody.get("content"));
        Map<String, Object> applicationJson = TestUtil.as(Map.class, content.get("application/json"));
        Map<String, Object> schema = TestUtil.as(Map.class, applicationJson.get("schema"));

        assertThat(schema.get("type"), is("object"));
    }

    /**
     * Tests the OpenAPI support by converting the response payload as YAML and
     * then creating a {@code Config} instance from that YAML for ease of
     * accessing its values in the test.
     *
     * @throws Exception in case of errors sending the request or receiving the
     * response
     */
    @Test
    public void testGreetingAsConfig() throws Exception {
        HttpURLConnection cnx = TestUtil.getURLConnection(
                greetingWebServer.port(),
                "GET",
                GREETING_PATH,
                MediaType.APPLICATION_OPENAPI_YAML);
        Config c = TestUtil.configFromResponse(cnx);
        assertThat(TestUtil.fromConfig(c, "paths./greet/greeting.put.summary"),
                is("Sets the greeting prefix"));
        assertThat(TestUtil.fromConfig(c,
                        "paths./greet/greeting.put.requestBody.content."
                            + "application/json.schema.properties.greeting.type"),
                is("string"));
    }

    /**
     * Makes sure that the response content type is consistent with the Accept
     * media type.
     *
     * @throws Exception in case of errors sending the request or receiving the
     * response
     */
    @ParameterizedTest
    @MethodSource()
    public void checkExplicitResponseMediaTypeViaHeaders(MediaType testMediaType) throws Exception {
        connectAndConsumePayload(testMediaType);
    }

    static Stream<MediaType> checkExplicitResponseMediaTypeViaHeaders() {
        return Stream.of(MediaType.APPLICATION_OPENAPI_YAML,
                         MediaType.APPLICATION_YAML,
                         MediaType.APPLICATION_OPENAPI_JSON,
                         MediaType.APPLICATION_JSON);
    }

    @Test
    void checkExplicitResponseMediaTypeViaQueryParameter() throws Exception {
        TestUtil.connectAndConsumePayload(greetingWebServer.port(),
                                          GREETING_PATH,
                                          "format=JSON",
                                          MediaType.APPLICATION_JSON);

        TestUtil.connectAndConsumePayload(greetingWebServer.port(),
                                          GREETING_PATH,
                                          "format=YAML",
                                          MediaType.APPLICATION_OPENAPI_YAML);
    }

    @Test
    public void testTimeAsConfig() throws Exception {
        commonTestTimeAsConfig(null);
    }

    @Test
    public void testTimeUnrestrictedCors() throws Exception {
        commonTestTimeAsConfig(cnx -> {

            cnx.setRequestProperty("Origin", "http://foo.bar");
            cnx.setRequestProperty("Host", "localhost");
        });

    }

    private void commonTestTimeAsConfig(Consumer<HttpURLConnection> headerSetter) throws Exception {
        HttpURLConnection cnx = TestUtil.getURLConnection(
                timeWebServer.port(),
                "GET",
                TIME_PATH,
                MediaType.APPLICATION_OPENAPI_YAML);
        if (headerSetter != null) {
            headerSetter.accept(cnx);
        }
        Config c = TestUtil.configFromResponse(cnx);
        assertThat(TestUtil.fromConfig(c, "paths./timecheck.get.summary"),
                is("Returns the current time"));
        assertThat(TestUtil.fromConfig(c,
                        "paths./timecheck.get.responses.200.content."
                                + "application/json.schema.properties.message.type"),
                is("string"));
    }

    @Test
    public void ensureNoCrosstalkAmongPorts() throws Exception {
        HttpURLConnection timeCnx = TestUtil.getURLConnection(
                timeWebServer.port(),
                "GET",
                TIME_PATH,
                MediaType.APPLICATION_OPENAPI_YAML);
        HttpURLConnection greetingCnx = TestUtil.getURLConnection(
                greetingWebServer.port(),
                "GET",
                GREETING_PATH,
                MediaType.APPLICATION_OPENAPI_YAML);
        Config greetingConfig = TestUtil.configFromResponse(greetingCnx);
        Config timeConfig = TestUtil.configFromResponse(timeCnx);
        assertThat("Incorrectly found greeting-related item in time OpenAPI document",
                timeConfig.get("paths./greet/greeting.put.summary").exists(), is(false));
        assertThat("Incorrectly found time-related item in greeting OpenAPI document",
                greetingConfig.get("paths./timecheck.get.summary").exists(), is(false));
    }

    private static void connectAndConsumePayload(MediaType mt) throws Exception {
        TestUtil.connectAndConsumePayload(greetingWebServer.port(), GREETING_PATH, mt);
    }
}
