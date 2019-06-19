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
 */

package io.helidon.tests.apps.bookstore.se;

import java.net.HttpURLConnection;

import io.helidon.webserver.WebServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Class SslTest.
 */
public class SslTest {

    private static WebServer webServer;

    @BeforeAll
    public static void startServer() throws Exception {
        webServer = TestServer.start(true, false);
    }

    @AfterAll
    public static void stopServer() throws Exception {
        TestServer.stop(webServer);
    }

    @Test
    public void testHelloWorldSsl() throws Exception {
        HttpURLConnection conn = TestServer.openConnection(webServer, "GET", "/books", true);
        Assertions.assertEquals(200, conn.getResponseCode(), "HTTP response1");
        Assertions.assertNotNull(conn.getHeaderField("content-length"));
    }
}
