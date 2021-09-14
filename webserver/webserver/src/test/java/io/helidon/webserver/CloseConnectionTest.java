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

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import io.helidon.common.http.DataChunk;
import io.helidon.common.http.Http;
import io.helidon.common.reactive.Multi;
import io.helidon.webserver.utils.SocketHttpClient;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

public class CloseConnectionTest {

    private WebServer webServer;
    private final ScheduledExecutorService exec = Executors.newSingleThreadScheduledExecutor();

    @BeforeEach
    void setUp() throws UnknownHostException {
        InetAddress localHost = InetAddress.getLocalHost();
        webServer = WebServer
                .builder()
                .port(0)
                .bindAddress(localHost)
                .routing(Routing.builder()
                        .get((req, res) -> {
                            res.send(Multi
                                    .interval(100, 200, TimeUnit.MILLISECONDS, exec)
                                    .peek(i -> {
                                        if (i > 2) {
                                            req.close();
                                        }
                                    })
                                    .map(i -> "item" + i)
                                    .limit(10)
                                    .map(String::getBytes)
                                    .map(DataChunk::create)
                                    .flatMap(bb -> Multi.just(bb, DataChunk.create(true)))
                            );
                        })
                        .build())
                .build()
                .start()
                .await();
    }

    @AfterEach
    void tearDown() {
        webServer.shutdown().await();
        exec.shutdown();
    }

    @Test
    void closeManually() throws Exception {
        try (SocketHttpClient c = new SocketHttpClient(webServer)) {
            c.request(Http.Method.GET);
            String result = c.receive();
            SocketHttpClient.assertConnectionIsClosed(c);
            assertThat(result, containsString("item0"));
            assertThat(result, not(containsString("item9")));
        }
    }
}
