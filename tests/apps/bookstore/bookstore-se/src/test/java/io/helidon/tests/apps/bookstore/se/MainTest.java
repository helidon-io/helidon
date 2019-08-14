/*
 * Copyright (c) 2018, 2019 Oracle and/or its affiliates. All rights reserved.
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

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;;

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

public class MainTest {

    private static WebServer webServer;
    private static OkHttpClient client;

    @BeforeAll
    public static void startServer() throws Exception {
        webServer = TestServer.start(false, false);
        client = TestServer.newOkHttpClient(false);
    }

    @AfterAll
    public static void stopServer() throws Exception {
        TestServer.stop(webServer);
    }

    @Test
    public void testHelloWorld() throws Exception {
        Request.Builder builder = TestServer.newRequestBuilder(webServer, "/books", false);

        Request getBooks = builder.build();
        try (Response getBooksRes = client.newCall(getBooks).execute()) {
            Assertions.assertEquals(200, getBooksRes.code());
            Assertions.assertNotNull(getBooksRes.header("content-length"));
            String body = getBooksRes.body().string();
            Assertions.assertEquals(body, "[]");
        }

        Request postBook = builder.post(
                RequestBody.create(APPLICATION_JSON, TestServer.getBookAsJson())).build();
        try (Response postBookRes = client.newCall(postBook).execute()) {
            Assertions.assertEquals(200, postBookRes.code());
        }

        builder = TestServer.newRequestBuilder(webServer, "/books/123456", false);
        Request getBook = builder.build();
        try (Response getBookRes = client.newCall(getBook).execute()) {
            Assertions.assertEquals(200, getBookRes.code());
            Assertions.assertNotNull(getBookRes.header("content-length"));
            JsonReader jsonReader = Json.createReader(getBookRes.body().byteStream());
            JsonObject jsonObject = jsonReader.readObject();
            Assertions.assertEquals("123456", jsonObject.getString("isbn"),
                    "Checking if correct ISBN");
        }

        Request deleteBook = builder.delete().build();
        try (Response deleteBookRes = client.newCall(deleteBook).execute()) {
            Assertions.assertEquals(200, deleteBookRes.code());
        }

        Request getNoBook = builder.build();
        try (Response getNoBookRes = client.newCall(getNoBook).execute()) {
            Assertions.assertEquals(getNoBookRes.code(), 404);
        }
    }
}
