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

package io.helidon.webserver;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;

import io.helidon.common.CollectionsHelper;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.helidon.webserver.TestHttpParsingDefaults.BAD_HEADER_NAME;
import static io.helidon.webserver.TestHttpParsingDefaults.GOOD_HEADER_NAME;
import static io.helidon.webserver.TestHttpParsingDefaults.testHeader;
import static io.helidon.webserver.TestHttpParsingDefaults.testHeaderName;
import static io.helidon.webserver.TestHttpParsingDefaults.testInitialLine;

class TestHttpParsingCustom {
    private static Client client;
    private static WebServer webServer;
    private static WebTarget target;

    @BeforeAll
    static void initClass() throws InterruptedException, ExecutionException, TimeoutException {
        client = ClientBuilder.newClient();
        Config config = Config.create(ConfigSources.create(CollectionsHelper.mapOf("validate-headers", "false")));

        ServerConfiguration sConfig = ServerConfiguration.builder()
                .config(config)
                .maxInitialLineLength(5100)
                .maxHeaderSize(9100)
                .build();

        webServer = WebServer.builder(Routing.builder()
                                              .any(TestHttpParsingDefaults::handleRequest)
                                              .build())
                .config(sConfig)
                .build()
                .start()
                .toCompletableFuture()
                .get(10, TimeUnit.SECONDS);

        target = client.target("http://localhost:" + webServer.port());
    }

    @AfterAll
    static void destroyClass() throws InterruptedException, ExecutionException, TimeoutException {
        if (client != null) {
            client.close();
        }
        if (webServer != null) {
            webServer.shutdown()
                    .toCompletableFuture()
                    .get(10, TimeUnit.SECONDS);
        }
    }

    @Test
    void testOkHeader() {
        testHeader(target, 8000, true);
    }

    @Test
    void testLongHeader() {
        testHeader(target, 8900, true);
    }

    @Test
    void testBadHeader() {
        testHeader(target, 10000, false);
    }


    @Test
    void testOkInitialLine() {
        testInitialLine(target, 10, true);
    }

    @Test
    void testLongInitialLine() {
        // now test with big initial line
        testInitialLine(target, 5000, true);
    }

    @Test
    void testBadInitialLine() {
        // now test with big initial line
        testInitialLine(target, 6000, false);
    }

    @Test
    void testGoodHeaderName() {
        testHeaderName(target, GOOD_HEADER_NAME, true);
    }

    @Test
    void testBadHeaderName() {
        testHeaderName(target, BAD_HEADER_NAME, true);
    }
}
