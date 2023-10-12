/*
 * Copyright (c) 2021, 2023 Oracle and/or its affiliates.
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

package io.helidon.webserver.tests;

import java.util.List;

import io.helidon.common.testing.http.junit5.SocketHttpClient;
import io.helidon.http.HtmlEncoder;
import io.helidon.http.Method;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

@ServerTest
class XssServerTest {
    private final SocketHttpClient socketHttpClient;

    XssServerTest(SocketHttpClient socketHttpClient) {
        this.socketHttpClient = socketHttpClient;
    }

    @SetUpRoute
    static void routing(HttpRules rules) {
        rules.get("/foo", (req, res) -> res.send(HtmlEncoder.encode("<script>bad</script>")));
    }

    @Test
    void testScriptInjection() {
        String s = socketHttpClient.sendAndReceive(Method.GET,
                                                   "/bar%3cscript%3eevil%3c%2fscript%3e",
                                                   null);
        assertThat(s, not(containsString("<script>")));
        assertThat(s, not(containsString("</script>")));
    }

    @Test
    void testScriptInjectionIllegalUrlChar() {
        String s = socketHttpClient.sendAndReceive(Method.GET,
                                                   "/bar<script/>evil</script>",
                                                   null);
        assertThat(s, not(containsString("<script>")));
        assertThat(s, not(containsString("</script>")));
    }

    @Test
    void testScriptInjectionContentType() {
        List<String> requestHeaders = List.of("Content-Type: <script>evil</script>");
        String s = socketHttpClient.sendAndReceive(Method.GET,
                                                   "/foo",
                                                   null,
                                                   requestHeaders);
        assertThat(s, not(containsString("<script>")));
        assertThat(s, not(containsString("</script>")));
    }

    @Test
    void testResponseEncoding() {
        String s = socketHttpClient.sendAndReceive(Method.GET,
                                                   "/foo",
                                                   null);
        assertThat(s, not(containsString("<script>")));
        assertThat(s, not(containsString("</script>")));
    }
}
