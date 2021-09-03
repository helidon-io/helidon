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

package io.helidon.tests.apps.bookstore.se;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import io.helidon.common.configurable.ThreadPoolSupplier;
import io.helidon.webserver.WebServer;

import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.helidon.tests.apps.bookstore.se.TestServer.APPLICATION_JSON;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Tests SSL/TLS with HTTP 2 upgrades.
 */
public class Http2SslTest {

    private static WebServer webServer;
    private static OkHttpClient client;

    @BeforeAll
    public static void startServer() throws Exception {
        webServer = TestServer.start(true, true);
        client = TestServer.newOkHttpClient(true);
    }

    @AfterAll
    public static void stopServer() throws Exception {
        TestServer.stop(webServer);
    }

    @Test
    public void testHelloWorldHttp2Ssl() throws Exception {
        Request.Builder builder = TestServer.newRequestBuilder(webServer, "/books", true);

        Request getBooks = builder.build();
        try (Response getBooksRes = client.newCall(getBooks).execute()) {
            assertEquals(getBooksRes.code(), 200);
            assertEquals(getBooksRes.protocol(), Protocol.HTTP_2);
        }

        Request postBook = builder.post(
                RequestBody.create(APPLICATION_JSON, TestServer.getBookAsJson())).build();
        try (Response postBookRes = client.newCall(postBook).execute()) {
            assertEquals(postBookRes.code(), 200);
            assertEquals(postBookRes.protocol(), Protocol.HTTP_2);
        }

        builder = TestServer.newRequestBuilder(webServer, "/books/123456", true);
        Request getBook = builder.build();
        try (Response getBookRes = client.newCall(getBook).execute()) {
            assertEquals(getBookRes.code(), 200);
            assertEquals(getBookRes.protocol(), Protocol.HTTP_2);
        }

        Request deleteBook = builder.delete().build();
        try (Response deleteBookRes = client.newCall(deleteBook).execute()) {
            assertEquals(deleteBookRes.code(), 200);
            assertEquals(deleteBookRes.protocol(), Protocol.HTTP_2);

        }

        Request getNoBook = builder.build();
        try (Response getNoBookRes = client.newCall(getNoBook).execute()) {
            assertEquals(getNoBookRes.code(), 404);
            assertEquals(getNoBookRes.protocol(), Protocol.HTTP_2);
        }
    }

    @Test
    public void testHelloWorldHttp2SslPostFirst() throws Exception {
        Request.Builder builder = TestServer.newRequestBuilder(webServer, "/books", true);
        Request postBook = builder.post(
                RequestBody.create(APPLICATION_JSON, TestServer.getBookAsJson())).build();
        try (Response postBookRes = client.newCall(postBook).execute()) {
            assertEquals(postBookRes.code(), 200);
            assertEquals(postBookRes.protocol(), Protocol.HTTP_2);
        }

        builder = TestServer.newRequestBuilder(webServer, "/books/123456", true);
        Request deleteBook = builder.delete().build();
        try (Response deleteBookRes = client.newCall(deleteBook).execute()) {
            assertEquals(deleteBookRes.code(), 200);
            assertEquals(deleteBookRes.protocol(), Protocol.HTTP_2);
        }
    }

    @Test
    public void testHelloWorldHttp2SslConcurrent() throws Exception {
        ExecutorService executor = ThreadPoolSupplier.create().get();
        Request.Builder builder = TestServer.newRequestBuilder(webServer, "/books", true);
        Request getBooks = builder.build();

        List<Callable<Response>> tasks = Collections.nCopies(10, () -> client.newCall(getBooks).execute());
        executor.invokeAll(tasks).forEach(f -> {
            try {
                Response r = f.get(1, TimeUnit.SECONDS);
                assertEquals(r.code(), 200);
                assertEquals(r.protocol(), Protocol.HTTP_2);
            } catch (Exception e) {
                fail(e);
            }
        });
    }
}
