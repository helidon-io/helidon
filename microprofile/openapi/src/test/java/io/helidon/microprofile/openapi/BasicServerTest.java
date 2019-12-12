/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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
package io.helidon.microprofile.openapi;

import java.net.HttpURLConnection;
import java.util.Map;

import io.helidon.common.http.MediaType;
import io.helidon.microprofile.server.Server;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Test that MP OpenAPI support works when retrieving the OpenAPI document
 * from the server's /openapi endpoint.
 */
public class BasicServerTest {

    private static final String OPENAPI_PATH = "/openapi";

    private static Server server;

    private static Map<String, Object> yaml;

    public BasicServerTest() {
    }

    /**
     * Start the server to run the test app and read the response from the
     * /openapi endpoint into a map that all tests can use.
     *
     * @throws Exception in case of error reading the response as yaml
     */
    @BeforeAll
    public static void startServer() throws Exception {
        server = TestUtil.startServer(TestApp.class);
        HttpURLConnection cnx = TestUtil.getURLConnection(
                server.port(),
                "GET",
                OPENAPI_PATH,
                MediaType.APPLICATION_OPENAPI_YAML);

        yaml = TestUtil.yamlFromResponse(cnx);
    }

    /**
     * Stop the server.
     */
    @AfterAll
    public static void stopServer() {
        if (server != null) {
            server.stop();
            server = null;
        }
    }

    /**
     * Make sure that the annotations in the test app were found and properly
     * incorporated into the OpenAPI document.
     *
     * @throws Exception in case of errors reading the HTTP response
     */
    @SuppressWarnings("unchecked")
    @Test
    public void simpleTest() throws Exception {
        String goSummary = TestUtil.fromYaml(yaml, "paths./testapp/go.get.summary", String.class);
        assertEquals(TestApp.GO_SUMMARY, goSummary);
    }
}
