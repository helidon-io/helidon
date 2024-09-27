/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

package io.helidon.webserver.concurrency.limits;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import io.helidon.http.Status;
import io.helidon.webclient.api.ClientResponseTyped;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@ServerTest
public class BulkheadTest {
    private static final CountDownLatch FIRST_ENCOUNTER = new CountDownLatch(1);
    private static final CountDownLatch FINISH_LATCH = new CountDownLatch(1);

    private final Http1Client client;

    public BulkheadTest(Http1Client client) {
        this.client = client;
    }

    @SetUpRoute
    public static void route(HttpRules rules) {
        rules.get("/greet", (req, res) -> res.send("Hello"))
                .get("/wait", (req, res) -> {
                    FIRST_ENCOUNTER.countDown();
                    FINISH_LATCH.await();
                    res.send("finished");
                });
    }

    @Test
    public void testRequest() {
        var response = client.get("/greet")
                .request(String.class);

        assertThat(response.status(), is(Status.OK_200));
        assertThat(response.entity(), is("Hello"));
    }

    @Test
    public void testBulkhead() throws Exception {
        Callable<ClientResponseTyped<String>> callable = () -> {
            return client.get("/wait")
                    .request(String.class);
        };
        try (ExecutorService es = Executors.newThreadPerTaskExecutor(Thread.ofVirtual().factory())) {
            var first = es.submit(callable);
            FIRST_ENCOUNTER.await();
            var secondResponse = es.submit(callable)
                    .get(5, TimeUnit.SECONDS);

            assertThat(secondResponse.status(), is(Status.SERVICE_UNAVAILABLE_503));
            FINISH_LATCH.countDown();
            var firstResponse = first.get(5, TimeUnit.SECONDS);
            assertThat(firstResponse.status(), is(Status.OK_200));
            assertThat(firstResponse.entity(), is("finished"));

        }
    }
}
