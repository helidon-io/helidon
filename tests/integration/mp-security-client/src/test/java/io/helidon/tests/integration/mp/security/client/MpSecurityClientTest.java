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

package io.helidon.tests.integration.mp.security.client;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import javax.json.Json;
import javax.json.JsonObject;

import io.helidon.common.http.Http;
import io.helidon.microprofile.server.Server;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Unit test for {@link MpSecurityClientMain}.
 */
class MpSecurityClientTest {
    private static Server server;
    // I must use an HTTP client that is not integrated with Helidon

    @BeforeAll
    static void initClass() {
        // start the program
        server = MpSecurityClientMain.startTheServer();
    }

    @AfterAll
    static void destroyClass() {
        server.stop();
    }

    @Test
    void testNotAuthenticated() throws Exception {
        HttpURLConnection con = (HttpURLConnection) new URL("http://localhost:9998/client").openConnection();

        con.connect();

        assertThat("Response code should be unauthorized", con.getResponseCode(), is(Http.Status.UNAUTHORIZED_401.code()));

        con.disconnect();
    }

    @Test
    void testAdmin() throws Exception {
        HttpURLConnection con = (HttpURLConnection) new URL("http://localhost:9998/client").openConnection();

        con.setRequestProperty("Authorization", buildBasic("jack", "password"));
        con.connect();

        assertThat("Response code should be 200", con.getResponseCode(), is(Http.Status.OK_200.code()));

        JsonObject jsonObject;
        try(InputStream inputStream = con.getInputStream()) {
            jsonObject = Json.createReader(inputStream)
                    .readObject();
        }

        assertThat("Should be authenticated", jsonObject.getBoolean("authenticated"), is(true));
        assertThat("Should be jack", jsonObject.getString("name"), is("jack"));
        assertThat("Should have admin role", jsonObject.getBoolean("admin"), is(true));
        assertThat("Should not have user role", jsonObject.getBoolean("user"), is(false));

        assertThat("Server should be authenticated", jsonObject.getBoolean("server.authenticated"), is(true));
        assertThat("Server should be jack", jsonObject.getString("server.name"), is("jack"));
        assertThat("Server should have admin role", jsonObject.getBoolean("server.admin"), is(true));
        assertThat("Server should not have user role", jsonObject.getBoolean("server.user"), is(false));

        con.disconnect();
    }

    @Test
    void testUser() throws Exception {
        HttpURLConnection con = (HttpURLConnection) new URL("http://localhost:9998/client").openConnection();

        con.setRequestProperty("Authorization", buildBasic("john", "password"));
        con.connect();

        assertThat("Response code should be 200", con.getResponseCode(), is(Http.Status.OK_200.code()));

        JsonObject jsonObject;
        try(InputStream inputStream = con.getInputStream()) {
            jsonObject = Json.createReader(inputStream)
                    .readObject();
        }

        assertThat("Should be authenticated", jsonObject.getBoolean("authenticated"), is(true));
        assertThat("Should be john", jsonObject.getString("name"), is("john"));
        assertThat("Should not have admin role", jsonObject.getBoolean("admin"), is(false));
        assertThat("Should have user role", jsonObject.getBoolean("user"), is(true));

        assertThat("Server should be authenticated", jsonObject.getBoolean("server.authenticated"), is(true));
        assertThat("Server should be john", jsonObject.getString("server.name"), is("john"));
        assertThat("Server should not have admin role", jsonObject.getBoolean("server.admin"), is(false));
        assertThat("Server should have user role", jsonObject.getBoolean("server.user"), is(true));

        con.disconnect();
    }

    private String buildBasic(String user, String password) {
        return "basic " + Base64.getEncoder()
                .encodeToString((user + ":" + password).getBytes(StandardCharsets.UTF_8));
    }
}