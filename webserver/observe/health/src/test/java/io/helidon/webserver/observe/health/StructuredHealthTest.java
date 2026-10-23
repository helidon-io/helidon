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

package io.helidon.webserver.observe.health;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.common.context.Context;
import io.helidon.common.context.Contexts;
import io.helidon.health.HealthCheck;
import io.helidon.health.HealthCheckResponse;
import io.helidon.http.media.json.JsonSupport;
import io.helidon.json.JsonObject;
import io.helidon.json.JsonParser;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.observe.ObserveFeature;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;

class StructuredHealthTest {
    @Test
    void concurrentChecksKeepDuplicateNamesAndResultOrderAndIsolateExceptions() throws Exception {
        var entered = new CountDownLatch(2);
        var release = new CountDownLatch(1);
        var secondThread = new AtomicReference<Thread>();
        var threads = new ConcurrentLinkedQueue<Thread>();
        HealthCheck first = check("duplicate", () -> {
            threads.add(Thread.currentThread());
            entered.countDown();
            await(release);
            if (!secondThread.get().join(Duration.ofSeconds(10))) {
                throw new IllegalStateException("Second check thread did not terminate");
            }
            return HealthCheckResponse.builder().status(HealthCheckResponse.Status.DOWN).detail("order", "first").build();
        });
        HealthCheck second = check("duplicate", () -> {
            secondThread.set(Thread.currentThread());
            threads.add(Thread.currentThread());
            entered.countDown();
            await(release);
            return HealthCheckResponse.builder().status(HealthCheckResponse.Status.UP).detail("order", "second").build();
        });
        HealthCheck failing = check("failure", () -> {
            throw new IllegalArgumentException("<failure>");
        });
        WebServer server = server(List.of(first, second, failing)).build().start();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var request = executor.submit(() -> {
                try (var response = Http1Client.create().get("http://localhost:" + server.port() + "/observe/health").request()) {
                    assertThat(response.status().code(), is(500));
                    return JsonParser.create(response.as(String.class)).readJsonObject();
                }
            });
            try {
                assertThat("Both checks must enter before either is released", entered.await(10, TimeUnit.SECONDS), is(true));
            } finally {
                release.countDown();
            }
            JsonObject result = request.get(10, TimeUnit.SECONDS);
            List<JsonObject> results = result.arrayValue("checks").orElseThrow().values().stream()
                    .map(JsonObject.class::cast)
                    .toList();
            assertThat(result.stringValue("status").orElseThrow(), is("ERROR"));
            assertThat(results.stream().map(it -> it.stringValue("name").orElseThrow()).toList(),
                       contains("duplicate", "duplicate", "failure"));
            assertThat(results.stream().map(it -> it.stringValue("status").orElseThrow()).toList(),
                       contains("DOWN", "UP", "ERROR"));
            assertThat(results.get(0).objectValue("data").orElseThrow().stringValue("order").orElseThrow(), is("first"));
            assertThat(results.get(1).objectValue("data").orElseThrow().stringValue("order").orElseThrow(), is("second"));
            assertThat(results.get(2).objectValue("data").orElseThrow().stringValue("message").orElseThrow(),
                       containsString("&lt;failure&gt;"));
            assertThat(threads.stream().map(Thread::isVirtual).toList(), contains(true, true));
            assertThat(threads.stream().map(Thread::getName).toList(),
                       containsInAnyOrder("health-check-0[duplicate]", "health-check-1[duplicate]"));
            assertThat(threads.stream().map(Thread::getName).distinct().count(), is(2L));
        } finally {
            release.countDown();
            server.stop();
        }
    }

    @Test
    void ownerInterruptionCleansUpChildrenAndPreservesInterruptFlag() throws Exception {
        var entered = new CountDownLatch(2);
        var exited = new CountDownLatch(2);
        var release = new CountDownLatch(1);
        var ownerExited = new CountDownLatch(1);
        var owner = new AtomicReference<Thread>();
        var preserved = new AtomicBoolean();
        var children = new ConcurrentLinkedQueue<Thread>();
        Callable<HealthCheckResponse> action = () -> {
            children.add(Thread.currentThread());
            entered.countDown();
            try {
                await(release);
                return HealthCheckResponse.builder().status(HealthCheckResponse.Status.UP).build();
            } finally {
                exited.countDown();
            }
        };
        var handler = new HealthHandler(JsonSupport.serverResponseWriter(), true,
                                        List.of(check("first", action), check("second", action)));
        WebServer server = WebServer.builder().port(0).routing(routing -> routing.get("/health", (req, res) -> {
            owner.set(Thread.currentThread());
            try {
                handler.handle(req, res);
            } finally {
                preserved.set(Thread.currentThread().isInterrupted());
                ownerExited.countDown();
            }
        })).build().start();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var request = executor.submit(() -> {
                try (var response = Http1Client.builder().readTimeout(Duration.ofSeconds(15)).build()
                        .get("http://localhost:" + server.port() + "/health").request()) {
                    return response.status().code();
                } catch (Exception _) {
                    // Interruption can prevent writing the error response; cleanup is the contract being tested here.
                    return -1;
                }
            });
            try {
                assertThat("Both children entered", entered.await(10, TimeUnit.SECONDS), is(true));
                owner.get().interrupt();
                assertThat("Both interrupted children exited", exited.await(10, TimeUnit.SECONDS), is(true));
                assertThat("Scope owner finished cleanup", ownerExited.await(10, TimeUnit.SECONDS), is(true));
                assertThat("Owner interrupt flag restored", preserved.get(), is(true));
                for (Thread child : children) {
                    assertThat("Child thread terminated: " + child.getName(), child.join(Duration.ofSeconds(10)), is(true));
                }
            } finally {
                release.countDown();
            }
            request.get(20, TimeUnit.SECONDS);
        } finally {
            release.countDown();
            server.stop();
        }
    }

    @Test
    void childChecksInheritRequestScopedContext() {
        Context context = Context.create();
        var observed = new ConcurrentLinkedQueue<Context>();
        HealthCheck first = check("first", () -> {
            observed.add(Contexts.context().orElseThrow());
            return HealthCheckResponse.builder().status(HealthCheckResponse.Status.UP).build();
        });
        HealthCheck second = check("second", () -> {
            observed.add(Contexts.context().orElseThrow());
            return HealthCheckResponse.builder().status(HealthCheckResponse.Status.UP).build();
        });
        WebServer server = server(List.of(first, second))
                .routing(routing -> routing.addFilter((chain, _, _) -> Contexts.runInContext(context, chain::proceed)))
                .build().start();
        try {
            try (var response = Http1Client.create().get("http://localhost:" + server.port() + "/observe/health").request()) {
                assertThat(response.status().code(), is(200));
                response.as(String.class);
            }
            assertThat(observed.size(), is(2));
            observed.forEach(actual -> assertThat(actual, sameInstance(context)));
        } finally {
            server.stop();
        }
    }

    @Test
    void emptyScopePreservesSuccessAndHeadResponse() {
        WebServer server = server(List.of()).build().start();
        try {
            var client = Http1Client.create();
            String uri = "http://localhost:" + server.port() + "/observe/health";
            try (var response = client.get(uri).request()) {
                assertThat(response.status().code(), is(200));
                assertThat(JsonParser.create(response.as(String.class)).readJsonObject()
                                   .arrayValue("checks").orElseThrow().size(), is(0));
            }
            try (var response = client.head(uri).request()) {
                assertThat(response.status().code(), is(204));
            }
        } finally {
            server.stop();
        }
    }

    static WebServerConfig.Builder server(List<HealthCheck> checks) {
        return WebServer.builder()
                .port(0)
                .addFeature(ObserveFeature.builder()
                                    .addObserver(HealthObserver.builder()
                                                         .useSystemServices(false)
                                                         .details(true)
                                                         .healthChecks(checks)
                                                         .build())
                                    .build());
    }

    static HealthCheck check(String name, Callable<HealthCheckResponse> action) {
        return new HealthCheck() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public HealthCheckResponse call() {
                try {
                    return action.call();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Health check interrupted", e);
                } catch (RuntimeException e) {
                    throw e;
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            }
        };
    }

    private static void await(CountDownLatch latch) throws InterruptedException {
        if (!latch.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Timed out waiting for test gate");
        }
    }
}
