/*
 * Copyright (c) 2018, 2022 Oracle and/or its affiliates.
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

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.helidon.tests.apps.bookstore.se.TestServer.APPLICATION_JSON;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

public class MainTest {

    private static WebServer webServer;
    private static OkHttpClient client;

    @BeforeAll
    public static void startServer() throws Exception {
        webServer = TestServer.start(false, false, false);
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
            assertThat(getBooksRes.code(), is(200));
            assertThat(getBooksRes.header("content-length"), notNullValue());
            String body = getBooksRes.body().string();
            assertThat(body, is("[]"));
        }

        Request postBook = builder.post(
                RequestBody.create(APPLICATION_JSON, TestServer.getBookAsJson())).build();
        try (Response postBookRes = client.newCall(postBook).execute()) {
            assertThat(postBookRes.code(), is(200));
        }

        builder = TestServer.newRequestBuilder(webServer, "/books/123456", false);
        Request getBook = builder.build();
        try (Response getBookRes = client.newCall(getBook).execute()) {
            assertThat(getBookRes.code(), is(200));
            assertThat(getBookRes.header("content-length"), notNullValue());
            JsonReader jsonReader = Json.createReader(getBookRes.body().byteStream());
            JsonObject jsonObject = jsonReader.readObject();
            assertThat("Checking if correct ISBN", jsonObject.getString("isbn"),
                    is("123456"));
        }

        Request deleteBook = builder.delete().build();
        try (Response deleteBookRes = client.newCall(deleteBook).execute()) {
            assertThat(deleteBookRes.code(), is(200));
        }

        Request getNoBook = builder.build();
        try (Response getNoBookRes = client.newCall(getNoBook).execute()) {
            assertThat(getNoBookRes.code(), is(404));
        }
    }
}
