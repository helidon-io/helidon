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
 */

package io.helidon.tests.integration.mp.autodiscovery;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import javax.ws.rs.core.MediaType;

import io.helidon.common.http.Http.Status;
import io.helidon.microprofile.server.Server;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

/**
 * Unit test for {@link MpServicesMain}.
 */
class MpServicesTest {
    private static Server server;
    // I must use an HTTP client that is not integrated with Helidon

    @BeforeAll
    static void initClass() {
        // start the program
        server = MpServicesMain.startTheServer();
    }

    @AfterAll
    static void destroyClass() {
        server.stop();
    }

    @Test
    void testDiscovered() throws Exception {
        assertAll(
                // by priority, the service1 should be on this endpoint
                () -> testHeader(9998, "/hello", "X-Foo", "bar"),
                () -> test(9998, "/hello", MediaType.TEXT_HTML, "<h1>Hello</h1>"),
                () -> test(9998, "/hello/error", MediaType.TEXT_PLAIN, Status.CREATED_201, "HELLO ERROR")
        );
    }

    private void testHeader(int port, String path, String header, String expected) throws IOException {
        HttpURLConnection con = (HttpURLConnection) new URL("http://localhost:" + port + path).openConnection();

        con.connect();
        assertThat(con.getHeaderField(header), is(expected));
        con.disconnect();
    }

    private void test(int port, String path, String expected) throws IOException {
        test(port, path, null, Status.OK_200, expected);
    }

    private void test(int port, String path, String accept, String expected) throws IOException {
        test(port, path, accept, Status.OK_200, expected);
    }

    private void test(int port, String path, String accept, Status expectedStatus, String expected) throws IOException {
        HttpURLConnection con = (HttpURLConnection) new URL("http://localhost:" + port + path).openConnection();
        if (accept != null) {
            con.setRequestProperty("Accept", accept);
        }
        con.connect();

        assertThat("Should be a successful request (http://localhost:" + port + path + ")",
                   con.getResponseCode(),
                   is(expectedStatus.code()));

        InputStream inputStream = con.getInputStream();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int len;

        while ((len = inputStream.read(buffer)) > 0) {
            bytes.write(buffer, 0, len);
        }
        inputStream.close();
        String result = new String(bytes.toByteArray(), StandardCharsets.UTF_8);
        assertThat(result, is(expected));

        con.disconnect();
    }
}