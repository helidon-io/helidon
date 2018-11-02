/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.webserver.synchronous;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.common.configurable.ThreadPoolSupplier;
import io.helidon.common.http.Http;
import io.helidon.webserver.Routing;
import io.helidon.webserver.testsupport.TestClient;
import io.helidon.webserver.testsupport.TestRequest;
import io.helidon.webserver.testsupport.TestResponse;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

/**
 * Unit test for {@link SyncSupport}.
 */
class SyncSupportTest {
    public static final String THREAD_PREFIX = "unit-test-sync-support";
    private static Routing routing;

    @BeforeAll
    static void initClass() {
        routing = Routing.builder()
                .register(SyncSupport.
                        builder()
                                  .executorServiceSupplier(ThreadPoolSupplier.builder()
                                                                   .threadNamePrefix(THREAD_PREFIX)
                                                                   .build())
                                  .build())
                .get("/plain", (req, res) -> res.send(Thread.currentThread().getName()))
                .get("/supplier", (req, res) -> {
                    Sync.submit(req, () -> Thread.currentThread().getName())
                            .thenAccept(res::send);
                })
                .get("/runnable", (req, res) -> {
                    Sync.accept(req, () -> res.send(Thread.currentThread().getName()));
                })
                .get("/cancel", (req, res) -> {
                    CompletableFuture<Void> future = Sync.accept(req, () -> {
                        try {
                            Thread.sleep(10000);
                        } catch (InterruptedException ignored) {
                            //ignored
                        }
                    });
                    future.cancel(true);
                    res.send("Finished");
                })
                .build();
    }

    @Test
    @DisplayName("Test webserver without synchronous support")
    void testPlain() throws TimeoutException, InterruptedException, ExecutionException {
        TestRequest request = TestClient.create(routing)
                .path("/plain");

        TestResponse response = request.get();
        assertThat(response.status(), is(Http.Status.OK_200));

        AtomicReference<String> resultRef = new AtomicReference<>();
        AtomicReference<Throwable> throwRef = new AtomicReference<>();

        response.asString()
                .thenAccept(resultRef::set)
                .exceptionally(throwable -> {
                    throwRef.set(throwable);
                    return null;
                })
                .toCompletableFuture()
                .get(1000, TimeUnit.MILLISECONDS);

        assertAll(() -> assertThat(resultRef.get(), not(startsWith(THREAD_PREFIX))),
                  () -> assertThat(throwRef.get(), nullValue()));
    }

    @Test
    @DisplayName("Test synchronous support with Supplier")
    void testSupplier() throws TimeoutException, InterruptedException, ExecutionException {
        TestRequest request = TestClient.create(routing)
                .path("/supplier");

        TestResponse response = request.get();
        assertThat(response.status(), is(Http.Status.OK_200));

        AtomicReference<String> resultRef = new AtomicReference<>();
        AtomicReference<Throwable> throwRef = new AtomicReference<>();

        response.asString()
                .thenAccept(resultRef::set)
                .exceptionally(throwable -> {
                    throwRef.set(throwable);
                    return null;
                }).toCompletableFuture()
                .get(1000, TimeUnit.MILLISECONDS);

        assertAll(() -> assertThat(resultRef.get(), startsWith(THREAD_PREFIX)),
                  () -> assertThat(throwRef.get(), nullValue()));
    }

    @Test
    @DisplayName("Test synchronous support with Runnable")
    void testRunnable() throws TimeoutException, InterruptedException, ExecutionException {
        TestRequest request = TestClient.create(routing)
                .path("/runnable");

        TestResponse response = request.get();
        assertThat(response.status(), is(Http.Status.OK_200));

        AtomicReference<String> resultRef = new AtomicReference<>();
        AtomicReference<Throwable> throwRef = new AtomicReference<>();

        response.asString()
                .thenAccept(resultRef::set)
                .exceptionally(throwable -> {
                    throwRef.set(throwable);
                    return null;
                }).toCompletableFuture()
                .get(1000, TimeUnit.MILLISECONDS);

        assertAll(() -> assertThat(resultRef.get(), startsWith(THREAD_PREFIX)),
                  () -> assertThat(throwRef.get(), nullValue()));
    }

    @Test
    @DisplayName("Test synchronous support with cancellation")
    void testCancelled() throws TimeoutException, InterruptedException, ExecutionException {
        TestRequest request = TestClient.create(routing)
                .path("/cancel");

        TestResponse response = request.get();
        assertThat(response.status(), is(Http.Status.OK_200));

        AtomicReference<String> resultRef = new AtomicReference<>();
        AtomicReference<Throwable> throwRef = new AtomicReference<>();

        response.asString()
                .thenAccept(resultRef::set)
                .exceptionally(throwable -> {
                    throwRef.set(throwable);
                    return null;
                }).toCompletableFuture()
                .get(1000, TimeUnit.MILLISECONDS);

        assertAll(() -> assertThat(resultRef.get(), is("Finished")),
                  () -> assertThat(throwRef.get(), nullValue()));
    }
}