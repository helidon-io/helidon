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

import io.helidon.webserver.WebServer;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.helidon.tests.apps.bookstore.se.TestServer.APPLICATION_JSON;

/**
 * Tests SSL/TLS with HTTP 1.1.
 */
public class SslTest {

    private static WebServer webServer;
    private static OkHttpClient client;

    @BeforeAll
    public static void startServer() throws Exception {
        webServer = TestServer.start(true, false);
        client = TestServer.newOkHttpClient(true);
    }

    @AfterAll
    public static void stopServer() throws Exception {
        TestServer.stop(webServer);
    }

    @Test
    public void testHelloWorldSsl() throws Exception {
        Request.Builder builder = TestServer.newRequestBuilder(webServer, "/books", true);

        Request getBooks = builder.build();
        try (Response getBooksRes = client.newCall(getBooks).execute()) {
            Assertions.assertEquals(getBooksRes.code(), 200);
        }

        Request postBook = builder.post(
                RequestBody.create(APPLICATION_JSON, TestServer.getBookAsJson())).build();
        try (Response postBookRes = client.newCall(postBook).execute()) {
            Assertions.assertEquals(postBookRes.code(), 200);
        }

        builder = TestServer.newRequestBuilder(webServer, "/books/123456", true);
        Request getBook = builder.build();
        try (Response getBookRes = client.newCall(getBook).execute()) {
            Assertions.assertEquals(getBookRes.code(), 200);
        }

        Request deleteBook = builder.delete().build();
        try (Response deleteBookRes = client.newCall(deleteBook).execute()) {
            Assertions.assertEquals(deleteBookRes.code(), 200);
        }

        Request getNoBook = builder.build();
        try (Response getNoBookRes = client.newCall(getNoBook).execute()) {
            Assertions.assertEquals(getNoBookRes.code(), 404);
        }
    }
}
