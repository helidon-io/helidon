/*
 * Copyright (c) 2018, 2021 Oracle and/or its affiliates.
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

package io.helidon.security.examples.google;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.helidon.common.http.Http;
import io.helidon.webclient.WebClient;
import io.helidon.webserver.WebServer;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.helidon.config.testing.OptionalMatcher.value;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Google login common unit tests.
 */
public abstract class GoogleMainTest {
    private static WebClient client;

    @BeforeAll
    public static void classInit() {
        client = WebClient.create();
    }

    static void stopServer(WebServer server) throws InterruptedException {
        CountDownLatch cdl = new CountDownLatch(1);
        long t = System.nanoTime();
        if (null == server) {
            return;
        }
        server.shutdown().thenAccept(webServer -> {
            long time = System.nanoTime() - t;
            System.out.println("Server shutdown in " + TimeUnit.NANOSECONDS.toMillis(time) + " ms");
            cdl.countDown();
        });

        if (!cdl.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Failed to shutdown server within 5 seconds");
        }
    }

    @Test
    public void testEndpoint() {
        client.get()
                .uri("http://localhost:" + port() + "/rest/profile")
                .request()
                .thenAccept(it -> {
                    assertThat(it.status(), is(Http.Status.UNAUTHORIZED_401));
                    assertThat(it.headers().first(Http.Header.WWW_AUTHENTICATE),
                               value(is("Bearer realm=\"helidon\",scope=\"openid profile email\"")));
                })
                .await();
    }

    abstract int port();
}
