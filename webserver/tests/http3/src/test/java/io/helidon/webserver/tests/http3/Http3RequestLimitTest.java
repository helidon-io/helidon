/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.webserver.tests.http3;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.common.concurrency.limits.LimitAlgorithm;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Http3RequestLimitTest {

    @Test
    void listenerRequestLimitAppliesToHttp3RequestLifetime() throws Exception {
        CountDownLatch requestStarted = new CountDownLatch(1);
        CountDownLatch releaseRequest = new CountDownLatch(1);
        AtomicReference<LimitAlgorithm.Outcome> observedOutcome = new AtomicReference<>();

        try (Http3TestSupport.TestEnvironment environment = Http3TestSupport.sharedListener(
                builder -> builder.maxConcurrentRequests(1),
                routing -> routing.get("/limited", (request, response) -> {
                    observedOutcome.set(request.context().get(LimitAlgorithm.Outcome.class).orElseThrow());
                    requestStarted.countDown();
                    releaseRequest.await();
                    response.send("accepted");
                }));
             HttpClient client = environment.http3Client()) {
            CompletableFuture<HttpResponse<String>> first =
                    client.sendAsync(environment.http3Get("/limited"), HttpResponse.BodyHandlers.ofString());
            try {
                assertThat(requestStarted.await(5, TimeUnit.SECONDS), equalTo(true));

                assertThrows(IOException.class,
                             () -> client.send(environment.http3Get("/limited"),
                                               HttpResponse.BodyHandlers.ofString()));

                releaseRequest.countDown();
                HttpResponse<String> accepted = first.get(5, TimeUnit.SECONDS);
                assertThat(accepted.statusCode(), equalTo(200));
                assertThat(accepted.body(), equalTo("accepted"));
                assertThat(observedOutcome.get().disposition(),
                           equalTo(LimitAlgorithm.Outcome.Disposition.ACCEPTED));

                HttpResponse<String> afterRelease =
                        client.send(environment.http3Get("/limited"), HttpResponse.BodyHandlers.ofString());
                assertThat(afterRelease.statusCode(), equalTo(200));
            } finally {
                releaseRequest.countDown();
            }
        }
    }
}
