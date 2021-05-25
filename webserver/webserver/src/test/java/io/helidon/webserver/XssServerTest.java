/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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

package io.helidon.webserver;

import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import io.helidon.common.http.Http;
import io.helidon.webserver.utils.SocketHttpClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

class XssServerTest {
    private static final Logger LOGGER = Logger.getLogger(XssServerTest.class.getName());

    private static WebServer webServer;

    @BeforeAll
    static void startServer() throws Exception {
        startServer(0);
    }

    @AfterAll
    static void close() throws Exception {
        if (webServer != null) {
            webServer.shutdown()
                    .toCompletableFuture()
                    .get(10, TimeUnit.SECONDS);
        }
    }

    private static void startServer(int port) throws Exception {
        webServer = WebServer.builder()
                .port(port)
                .routing(Routing.builder()
                        .get("/foo", (req, res) -> {
                            res.send(HtmlEncoder.encode("<script>bad</script>"));
                        })
                        .build())
                .build()
                .start()
                .toCompletableFuture()
                .get(10, TimeUnit.SECONDS);

        LOGGER.info("Started server at: https://localhost:" + webServer.port());
    }

    @Test
    void testScriptInjection() throws Exception {
        String s = SocketHttpClient.sendAndReceive("/bar%3cscript%3eevil%3c%2fscript%3e",
                Http.Method.GET, null, webServer);
        assertThat(s, not(containsString("<script>")));
        assertThat(s, not(containsString("</script>")));
    }

    @Test
    void testResponseEncoding() throws Exception {
        String s = SocketHttpClient.sendAndReceive("/foo",
                Http.Method.GET, null, webServer);
        assertThat(s, not(containsString("<script>")));
        assertThat(s, not(containsString("</script>")));
    }
}
