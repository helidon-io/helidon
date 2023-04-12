/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

package io.helidon.jersey.client;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;

import io.helidon.common.context.Context;
import io.helidon.common.context.Contexts;
import io.helidon.common.reactive.Single;
import io.helidon.webserver.WebServer;

import org.glassfish.jersey.client.ClientAsyncExecutor;
import org.glassfish.jersey.spi.ThreadPoolExecutorProvider;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.core.StringStartsWith.startsWith;

import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.InvocationCallback;

public class ContextTest {

    private static final Duration TIME_OUT = Duration.ofSeconds(5);
    private static final String TEST_KEY = "test-key";
    private static final String TEST_VAL = "test-val";
    private static final String TEST_THREAD_NAME_PREFIX = "test-exec";
    private static WebServer server;

    @BeforeAll
    static void beforeAll() {
        server = WebServer.builder()
                .host("localhost")
                .routing(r -> r.put((req, res) -> res.send("I'm Frank!")))
                .build()
                .start()
                .await(TIME_OUT);
    }

    @AfterAll
    static void afterAll() {
        if (server != null) {
            server.shutdown().await(TIME_OUT);
        }
    }

    @Test
    void defaultThreadPool() {
        test(Function.identity(), ExecutorProvider.THREAD_NAME_PREFIX, TEST_VAL);
    }

    @Test
    void overrideDefaultThreadPoolWithBuilder() throws InterruptedException {
        ExecutorService executorService = Executors.newSingleThreadExecutor(r -> new Thread(r, TEST_THREAD_NAME_PREFIX));
        try {
            test(cb -> cb.executorService(executorService), TEST_THREAD_NAME_PREFIX, null);
        } finally {
            executorService.shutdown();
            assertThat(executorService.awaitTermination(TIME_OUT.toSeconds(), TimeUnit.SECONDS), is(true));
        }
    }

    @Test
    @Disabled("Disabled until better defaulting spi is available in Jersey")
    void overrideDefaultThreadPoolWithExecutorProvider() throws InterruptedException {
        test(cb -> cb.register(TestNoCtxExecutorProvider.class), TEST_THREAD_NAME_PREFIX, null);
    }

    void test(Function<ClientBuilder, ClientBuilder> builderTweaker, String expectedThreadPrefix, String expectedContextValue) {
        Context ctx = Context.create();
        ctx.register(TEST_KEY, TEST_VAL);

        CompletableFuture<String> contextValFuture = new CompletableFuture<>();
        CompletableFuture<String> threadNameFuture = new CompletableFuture<>();

        Contexts.runInContext(ctx, () ->
                builderTweaker.apply(ClientBuilder.newBuilder())
                        .build()
                        .target("http://localhost:" + server.port())
                        .request()
                        .async()
                        .put(Entity.text(""), ClientCallback.create(s -> {
                            threadNameFuture.complete(Thread.currentThread().getName());
                            contextValFuture.complete(Contexts.context()
                                    .flatMap(c -> c.get(TEST_KEY, String.class))
                                    .orElse(null));
                        }, threadNameFuture::completeExceptionally)));

        String actThreadName = Single.create(threadNameFuture, true).await(TIME_OUT);
        assertThat(actThreadName, startsWith(expectedThreadPrefix));
        String actContextVal = Single.create(contextValFuture, true).await(TIME_OUT);
        assertThat(actContextVal, is(expectedContextValue));
    }

    @ClientAsyncExecutor
    public static class TestNoCtxExecutorProvider extends ThreadPoolExecutorProvider {

        public TestNoCtxExecutorProvider() {
            super(TEST_THREAD_NAME_PREFIX);
        }

        @Override
        public ExecutorService getExecutorService() {
            return Executors.newSingleThreadExecutor();
        }

        @Override
        public void dispose(ExecutorService executorService) {
            executorService.shutdown();
            try {
                assertThat(executorService.awaitTermination(TIME_OUT.toSeconds(), TimeUnit.SECONDS), is(true));
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    static class ClientCallback implements InvocationCallback<String> {
        Consumer<String> completed;
        Consumer<Throwable> exceptionally;

        private ClientCallback() {
        }

        static ClientCallback create(Consumer<String> completed, Consumer<Throwable> exceptionally) {
            ClientCallback c = new ClientCallback();
            c.completed = completed;
            c.exceptionally = exceptionally;
            return c;
        }

        @Override
        public void completed(String s) {
            completed.accept(s);
        }

        @Override
        public void failed(Throwable t) {
            exceptionally.accept(t);
        }
    }
}
