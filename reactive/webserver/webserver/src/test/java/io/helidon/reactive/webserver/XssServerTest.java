/*
 * Copyright (c) 2021, 2022 Oracle and/or its affiliates.
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

package io.helidon.reactive.webserver;

import java.util.Arrays;
import java.util.List;

import io.helidon.common.http.HtmlEncoder;
import io.helidon.common.http.Http;

import io.helidon.common.testing.http.junit5.SocketHttpClient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

class XssServerTest extends BaseServerTest {

    @BeforeAll
    static void startServer() throws Exception {
        Routing routing = Routing.builder()
                .get("/foo", (req, res) -> {
                            res.send(HtmlEncoder.encode("<script>bad</script>"));
                        }).build();
        startServer(0, routing);
    }

    @Test
    void testScriptInjection() throws Exception {
        try (SocketHttpClient client = socketClient()) {
            String s = client.sendAndReceive("/bar%3cscript%3eevil%3c%2fscript%3e",
                    Http.Method.GET, null);
            assertThat(s, not(containsString("<script>")));
            assertThat(s, not(containsString("</script>")));
        }
    }

    @Test
    void testScriptInjectionIllegalUrlChar() throws Exception {
        try (SocketHttpClient client = socketClient()) {
            String s = client.sendAndReceive("/bar<script/>evil</script>",
                    Http.Method.GET, null);
            assertThat(s, not(containsString("<script>")));
            assertThat(s, not(containsString("</script>")));
        }
    }

    @Test
    void testScriptInjectionContentType() throws Exception {
        try (SocketHttpClient client = socketClient()) {
            List<String> requestHeaders = Arrays.asList("Content-Type: <script>evil</script>");
            String s = client.sendAndReceive("/foo",
                    Http.Method.GET, null, requestHeaders);
            assertThat(s, not(containsString("<script>")));
            assertThat(s, not(containsString("</script>")));
        }
    }

    @Test
    void testResponseEncoding() throws Exception {
        try (SocketHttpClient client = socketClient()) {
            String s = client.sendAndReceive("/foo",
                    Http.Method.GET, null);
            assertThat(s, not(containsString("<script>")));
            assertThat(s, not(containsString("</script>")));
        }
    }
}
