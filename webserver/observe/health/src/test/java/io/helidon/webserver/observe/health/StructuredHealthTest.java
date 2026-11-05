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
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.common.context.Context;
import io.helidon.common.context.Contexts;
import io.helidon.health.HealthCheck;
import io.helidon.health.HealthCheckResponse;
import io.helidon.http.HeaderName;
import io.helidon.http.HeaderNames;
import io.helidon.http.media.json.JsonSupport;
import io.helidon.json.JsonObject;
import io.helidon.json.JsonParser;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.context.ContextFeature;
import io.helidon.webserver.observe.ObserveFeature;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;

class StructuredHealthTest {
    private static final HeaderName TOKEN_HEADER = HeaderNames.create("X-Health-Token");
    private static final String TOKEN_CLASSIFIER = "health-token";

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
                try (var response = Http1Client.builder().readTimeout(Duration.ofSeconds(15)).build()
                        .get("http://localhost:" + server.port() + "/observe/health").request()) {
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
        var interrupted = new CountDownLatch(2);
        var release = new CountDownLatch(1);
        var finishCleanup = new Semaphore(0);
        var ownerExited = new CountDownLatch(1);
        var owner = new AtomicReference<Thread>();
        var preserved = new AtomicBoolean();
        var childrenTerminatedAtOwnerExit = new AtomicBoolean();
        var children = new ConcurrentLinkedQueue<Thread>();
        Callable<HealthCheckResponse> action = () -> {
            children.add(Thread.currentThread());
            entered.countDown();
            try {
                release.await();
                return HealthCheckResponse.builder().status(HealthCheckResponse.Status.UP).build();
            } catch (InterruptedException e) {
                interrupted.countDown();
                // Keep child cleanup in progress until the test allows it to finish.
                finishCleanup.acquireUninterruptibly();
                throw e;
            } finally {
                exited.countDown();
            }
        };
        var handler = new HealthHandler(JsonSupport.serverResponseWriter(), true,
                                        List.of(check("first", action), check("second", action)));
        WebServer server = WebServer.builder().featuresDiscoverServices(false).port(0)
                .routing(routing -> routing.get("/health", (req, res) -> {
                    owner.set(Thread.currentThread());
                    try {
                        handler.handle(req, res);
                    } finally {
                        preserved.set(Thread.currentThread().isInterrupted());
                        childrenTerminatedAtOwnerExit.set(children.stream().noneMatch(Thread::isAlive));
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
                assertThat("Both children received interruption", interrupted.await(10, TimeUnit.SECONDS), is(true));
                assertThat("Child cleanup is still held by the test", exited.getCount(), is(2L));
                assertThat("Owner cannot finish while child cleanup is held", ownerExited.getCount(), is(1L));
                finishCleanup.release(2);
                assertThat("Scope owner finished cleanup", ownerExited.await(10, TimeUnit.SECONDS), is(true));
                assertThat("Both child checks completed", exited.getCount(), is(0L));
                assertThat("Children terminated before the handler returned", childrenTerminatedAtOwnerExit.get(), is(true));
                assertThat("Owner interrupt flag restored", preserved.get(), is(true));
            } finally {
                release.countDown();
                finishCleanup.release(2);
            }
            request.get(20, TimeUnit.SECONDS);
        } finally {
            release.countDown();
            finishCleanup.release(2);
            server.stop();
        }
    }

    @Test
    void fatalErrorCancelsSiblingAndWaitsForCleanup() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var interrupted = new CountDownLatch(1);
        var finishCleanup = new Semaphore(0);
        var ownerExited = new CountDownLatch(1);
        var sibling = new AtomicReference<Thread>();
        var observedError = new AtomicReference<Error>();
        var terminatedAtOwnerExit = new AtomicBoolean();
        var fatal = new AssertionError("fatal check");
        HealthCheck waiting = check("waiting", () -> {
            sibling.set(Thread.currentThread());
            entered.countDown();
            try {
                await(release);
                throw new AssertionError("Unexpected release");
            } catch (InterruptedException e) {
                interrupted.countDown();
                finishCleanup.acquireUninterruptibly();
                throw e;
            }
        });
        HealthCheck failing = check("fatal", () -> {
            await(entered);
            throw fatal;
        });
        var handler = new HealthHandler(JsonSupport.serverResponseWriter(), true, List.of(waiting, failing));
        WebServer server = WebServer.builder().featuresDiscoverServices(false).port(0)
                .routing(routing -> routing.get("/health", (req, res) -> {
                    try {
                        handler.handle(req, res);
                    } catch (Error error) {
                        observedError.set(error);
                        terminatedAtOwnerExit.set(!sibling.get().isAlive());
                        res.status(500).send();
                    } finally {
                        ownerExited.countDown();
                    }
                })).build().start();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var request = executor.submit(() -> {
                try (var response = Http1Client.builder().readTimeout(Duration.ofSeconds(15)).build()
                        .get("http://localhost:" + server.port() + "/health").request()) {
                    return response.status().code();
                }
            });
            try {
                assertThat("Fatal failure interrupts its sibling", interrupted.await(10, TimeUnit.SECONDS), is(true));
                assertThat("Scope owner waits for sibling cleanup", ownerExited.getCount(), is(1L));
            } finally {
                finishCleanup.release();
            }
            assertThat(request.get(20, TimeUnit.SECONDS), is(500));
            assertThat(observedError.get(), sameInstance(fatal));
            assertThat("Sibling terminates before the fatal error escapes", terminatedAtOwnerExit.get(), is(true));
        } finally {
            release.countDown();
            finishCleanup.release();
            server.stop();
        }
    }

    @Test
    void overlappingRequestsInheritTheirOwnPropagatedContext() throws Exception {
        var entered = new CountDownLatch(4);
        var release = new CountDownLatch(1);
        Map<String, Context> requestContexts = new ConcurrentHashMap<>();
        var observed = new ConcurrentHashMap<String, ConcurrentLinkedQueue<Context>>();
        var requestThreadValue = new ThreadLocal<String>();
        Callable<HealthCheckResponse> action = () -> {
            Context context = Contexts.context().orElseThrow();
            String token = context.get(TOKEN_CLASSIFIER, String.class).orElseThrow();
            observed.computeIfAbsent(token, _ -> new ConcurrentLinkedQueue<>()).add(context);
            assertThat("Ordinary request-thread values are not inherited", requestThreadValue.get(), nullValue());
            entered.countDown();
            await(release);
            return HealthCheckResponse.builder().status(HealthCheckResponse.Status.UP).detail("token", token).build();
        };
        WebServer server = server(List.of(check("first", action), check("second", action)))
                .addFeature(ContextFeature.builder().addRecord(record -> record.header(TOKEN_HEADER)
                        .classifier(TOKEN_CLASSIFIER)).build())
                .routing(routing -> routing.addFilter((chain, req, _) -> {
                    String token = req.headers().get(TOKEN_HEADER).get();
                    requestContexts.put(token, req.context());
                    requestThreadValue.set(token);
                    try {
                        chain.proceed();
                    } finally {
                        requestThreadValue.remove();
                    }
                }))
                .build().start();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = executor.submit(() -> contextResponse(server, "one"));
            var second = executor.submit(() -> contextResponse(server, "two"));
            try {
                assertThat("All checks from both requests entered before release", entered.await(10, TimeUnit.SECONDS), is(true));
            } finally {
                release.countDown();
            }
            assertContextResponse(first.get(20, TimeUnit.SECONDS), "one");
            assertContextResponse(second.get(20, TimeUnit.SECONDS), "two");
            assertContextResponse(contextResponse(server, "three"), "three");
            assertThat(requestContexts.get("one"), not(sameInstance(requestContexts.get("two"))));
            assertThat(requestContexts.get("three"), not(sameInstance(requestContexts.get("one"))));
            assertThat(requestContexts.get("three"), not(sameInstance(requestContexts.get("two"))));
            for (String token : List.of("one", "two", "three")) {
                assertThat(observed.get(token).size(), is(2));
                observed.get(token).forEach(context -> assertThat(context, sameInstance(requestContexts.get(token))));
            }
        } finally {
            release.countDown();
            server.stop();
        }
    }

    @Test
    void checksHaveNoScopedContextWithoutContextFeature() {
        var observed = new ConcurrentLinkedQueue<Boolean>();
        Callable<HealthCheckResponse> action = () -> {
            observed.add(Contexts.context().isEmpty());
            return HealthCheckResponse.builder().status(HealthCheckResponse.Status.UP).build();
        };
        WebServer server = server(List.of(check("first", action), check("second", action))).build().start();
        try {
            contextResponse(server, "unused");
            assertThat(observed, contains(true, true));
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

    private static WebServerConfig.Builder server(List<HealthCheck> checks) {
        return WebServer.builder()
                .featuresDiscoverServices(false)
                .port(0)
                .addFeature(ObserveFeature.builder()
                                    .observersDiscoverServices(false)
                                    .addObserver(HealthObserver.builder()
                                                         .useSystemServices(false)
                                                         .details(true)
                                                         .healthChecks(checks)
                                                         .build())
                                    .build());
    }

    private static HealthCheck check(String name, Callable<HealthCheckResponse> action) {
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

    private static JsonObject contextResponse(WebServer server, String token) {
        try (var response = Http1Client.builder().readTimeout(Duration.ofSeconds(15)).build()
                .get("http://localhost:" + server.port() + "/observe/health").header(TOKEN_HEADER, token).request()) {
            assertThat(response.status().code(), is(200));
            return JsonParser.create(response.as(String.class)).readJsonObject();
        }
    }

    private static void assertContextResponse(JsonObject response, String token) {
        assertThat(response.stringValue("status").orElseThrow(), is("UP"));
        List<JsonObject> checks = response.arrayValue("checks").orElseThrow().values().stream()
                .map(JsonObject.class::cast).toList();
        assertThat(checks.stream().map(check -> check.stringValue("name").orElseThrow()).toList(), contains("first", "second"));
        assertThat(checks.stream().map(check -> check.stringValue("status").orElseThrow()).toList(), contains("UP", "UP"));
        checks.forEach(check -> assertThat(check.objectValue("data").orElseThrow().stringValue("token").orElseThrow(), is(token)));
    }
}
