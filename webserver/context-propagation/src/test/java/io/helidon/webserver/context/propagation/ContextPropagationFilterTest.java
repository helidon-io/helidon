/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.webserver.context.propagation;

import java.time.Duration;

import io.helidon.common.LogConfig;
import io.helidon.common.context.Context;
import io.helidon.config.Config;
import io.helidon.webclient.WebClient;
import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;
import io.helidon.webserver.WebServer;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

class ContextPropagationFilterTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static WebServer webServer;
    private static WebClient webClient;

    @BeforeAll
    static void startServer() {
        LogConfig.configureRuntime();

        webServer = WebServer.builder()
                .host("localhost")
                .routing(Routing.builder()
                                 .any(ContextPropagationFilter.create(Config.create().get("unit-1")))
                                 .get("/unit", ContextPropagationFilterTest::unitRoute))
                .build()
                .start()
                .await(TIMEOUT);

        webClient = WebClient.builder()
                .baseUri("http://localhost:" + webServer.port())
                .build();
    }

    @AfterAll
    static void stopServer() {
        if (webServer != null) {
            webServer.shutdown();
        }
    }

    @Test
    void contextPropagationWithAllValuesNoArray() {
        String noDefault = "first";
        String tid = "second";
        String cid = "third";

        String actual = webClient.get()
                .path("/unit")
                .addHeader("x_helidon_no_default", noDefault)
                .addHeader("x_helidon_tid", tid)
                .addHeader("x_helidon_cid", cid)
                .request(String.class)
                .await(TIMEOUT);

        assertThat(actual, is(noDefault + "_" + tid + "_" + cid));
    }

    @Test
    void contextPropagationWithAllValuesArray() {
        String noDefault = "first";
        String tid = "second";
        String[] cid = new String[] {"third", "fourth"};

        String actual = webClient.get()
                .path("/unit")
                .addHeader("x_helidon_no_default", noDefault)
                .addHeader("x_helidon_tid", tid)
                .addHeader("x_helidon_cid", cid)
                .request(String.class)
                .await(TIMEOUT);

        assertThat(actual, is(noDefault + "_" + tid + "_third,fourth"));
    }

    @Test
    void contextPropagationWithDefaultedValues() {
        String actual = webClient.get()
                .path("/unit")
                .request(String.class)
                .await(TIMEOUT);

        assertThat(actual, is("null_unknown_first,second"));
    }

    private static void unitRoute(ServerRequest req, ServerResponse res) {
        // we expect the following headers:
        // x_helidon_no_default
        // x_helidon_tid
        // x_helidon_cid
        Context context = req.context();
        String noDefault = context.get("io.helidon.webclient.context.propagation.no-default", String.class).orElse(null);
        String[] tid = context.get("io.helidon.webclient.context.propagation.tid", String[].class).orElse(null); // has default
        String[] cid = context.get("io.helidon.webclient.context.propagation.cid", String[].class).orElse(null); // has default

        // send it back
        res.send(noDefault + "_" + String.join(",", tid) + "_" + String.join(",", cid));
    }
}