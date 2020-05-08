/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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
 *
 */
package io.helidon.openapi;

import java.net.HttpURLConnection;

import io.helidon.common.http.Http;
import io.helidon.common.http.MediaType;
import io.helidon.config.Config;
import io.helidon.webserver.WebServer;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.helidon.openapi.ServerTest.GREETING_OPENAPI_SUPPORT_BUILDER;
import static io.helidon.openapi.ServerTest.TIME_OPENAPI_SUPPORT_BUILDER;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestCors {

    private static WebServer greetingWebServer;
    private static WebServer timeWebServer;

    private static final String GREETING_PATH = "/openapi-greeting";
    private static final String TIME_PATH = "/openapi-time";

    @BeforeAll
    public static void startup() {
        greetingWebServer = TestUtil.startServer(GREETING_OPENAPI_SUPPORT_BUILDER);
        timeWebServer = TestUtil.startServer(TIME_OPENAPI_SUPPORT_BUILDER);
    }
    @Test
    public void testCrossOriginGreetingWithoutCors() throws Exception {
        HttpURLConnection cnx = TestUtil.getURLConnection(
                greetingWebServer.port(),
                "GET",
                GREETING_PATH,
                MediaType.APPLICATION_OPENAPI_YAML);
        cnx.setRequestProperty("Origin", "http://foo.bar");
        cnx.setRequestProperty("Host", "localhost");

        Config c = TestUtil.configFromResponse(cnx);

        assertEquals(Http.Status.OK_200.code(), cnx.getResponseCode());
    }

    @Test
    public void testTimeRestrictedCorsValidOrigin() throws Exception {
        HttpURLConnection cnx = TestUtil.getURLConnection(
                timeWebServer.port(),
                "GET",
                TIME_PATH,
                MediaType.APPLICATION_OPENAPI_YAML);
        cnx.setRequestProperty("Origin", "http://foo.bar");
        cnx.setRequestProperty("Host", "localhost");

        assertEquals(Http.Status.OK_200.code(), cnx.getResponseCode());
    }

    @Test
    public void testTimeRestrictedCorsInvalidOrigin() throws Exception {
        HttpURLConnection cnx = TestUtil.getURLConnection(
                timeWebServer.port(),
                "GET",
                TIME_PATH,
                MediaType.APPLICATION_OPENAPI_YAML);
        cnx.setRequestProperty("Origin", "http://other.com");
        cnx.setRequestProperty("Host", "localhost");

        assertEquals(Http.Status.FORBIDDEN_403.code(), cnx.getResponseCode());
    }
}
