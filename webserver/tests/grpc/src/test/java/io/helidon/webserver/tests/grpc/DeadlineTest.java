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
package io.helidon.webserver.tests.grpc;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.webserver.Router;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.grpc.GrpcRouting;
import io.helidon.webserver.grpc.slow.Slow.SlowRequest;
import io.helidon.webserver.grpc.slow.Slow.SlowResponse;
import io.helidon.webserver.grpc.slow.SlowServiceGrpc;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;

import io.grpc.Context;
import io.grpc.Deadline;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ServerTest
class DeadlineTest extends BaseServiceTest {

    private SlowServiceGrpc.SlowServiceBlockingStub blockingStub;
    private SlowServiceGrpc.SlowServiceStub asyncStub;

    DeadlineTest(WebServer server) {
        super(server);
    }

    @SetUpRoute
    static void routing(Router.RouterBuilder<?> router) {
        router.addRouting(GrpcRouting.builder().service(new SlowService()));
    }

    @BeforeEach
    @Override
    void beforeEach() {
        super.beforeEach();
        blockingStub = SlowServiceGrpc.newBlockingStub(channel);
        asyncStub = SlowServiceGrpc.newStub(channel);
        SlowService.lastSeenDeadline.set(null);
    }

    @AfterEach
    @Override
    void afterEach() throws InterruptedException {
        super.afterEach();
    }

    @Test
    void unaryDeadlineExceeded() {
        // Server sleeps 2s, client deadline is 200ms → DEADLINE_EXCEEDED
        SlowRequest request = SlowRequest.newBuilder().setDelayMillis(2000).setText("hello").build();

        StatusRuntimeException ex = assertThrows(StatusRuntimeException.class,
                () -> blockingStub.withDeadlineAfter(200, TimeUnit.MILLISECONDS).slow(request));

        assertThat(ex.getStatus().getCode(), is(Status.Code.DEADLINE_EXCEEDED));
    }

    @Test
    void alreadyExpiredDeadline() {
        // Deadline of 0ns — should fail immediately without opening a connection
        SlowRequest request = SlowRequest.newBuilder().setDelayMillis(0).setText("hello").build();

        StatusRuntimeException ex = assertThrows(StatusRuntimeException.class,
                () -> blockingStub.withDeadlineAfter(0, TimeUnit.NANOSECONDS).slow(request));

        assertThat(ex.getStatus().getCode(), is(Status.Code.DEADLINE_EXCEEDED));
    }

    @Test
    void callCompletesWithinDeadline() {
        // Server responds quickly (10ms), deadline is generous (5s) → normal response
        SlowRequest request = SlowRequest.newBuilder().setDelayMillis(10).setText("hello").build();

        SlowResponse response = blockingStub.withDeadlineAfter(5, TimeUnit.SECONDS).slow(request);

        assertThat(response.getText(), is("hello"));
    }

    @Test
    void streamingDeadlineExceeded() throws InterruptedException {
        // Server-streaming call: 200ms deadline, server sleeps 2s → DEADLINE_EXCEEDED
        SlowRequest request = SlowRequest.newBuilder().setDelayMillis(2000).setText("hello").build();

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();

        asyncStub.withDeadlineAfter(200, TimeUnit.MILLISECONDS)
                .slowStream(request, new StreamObserver<SlowResponse>() {
                    @Override public void onNext(SlowResponse value) {}

                    @Override
                    public void onError(Throwable t) {
                        error.set(t);
                        latch.countDown();
                    }

                    @Override
                    public void onCompleted() {
                        latch.countDown();
                    }
                });

        assertThat("should receive error within timeout", latch.await(5, TimeUnit.SECONDS), is(true));
        assertThat("should have received an error", error.get(), is(notNullValue()));
        assertThat(Status.fromThrowable(error.get()).getCode(), is(Status.Code.DEADLINE_EXCEEDED));
    }

    @Test
    void serverSeesDeadlineInContext() {
        // After the call, SlowService.lastSeenDeadline should be non-null
        SlowRequest request = SlowRequest.newBuilder().setDelayMillis(10).setText("hello").build();

        blockingStub.withDeadlineAfter(5, TimeUnit.SECONDS).slow(request);

        Deadline deadline = SlowService.lastSeenDeadline.get();
        assertThat("server should see a deadline in Context", deadline, is(notNullValue()));
        long remainingMs = deadline.timeRemaining(TimeUnit.MILLISECONDS);
        assertThat("remaining time should be positive", remainingMs > 0, is(true));
        assertThat("remaining time should be less than original 5s", remainingMs < 5000, is(true));
    }

    @Test
    void serverSeesNoDeadlineWhenNoneSet() {
        SlowRequest request = SlowRequest.newBuilder().setDelayMillis(10).setText("hello").build();

        blockingStub.slow(request);  // No deadline on stub

        assertThat("server should see null deadline when none sent",
                SlowService.lastSeenDeadline.get(), is(nullValue()));
    }

    @Test
    void stubLevelDeadlineExceeded() {
        // withDeadlineAfter calls Deadline.after() immediately, fixing the expiry to the instant
        // the stub is configured. The countdown therefore starts at stub-creation time, not at
        // call-invocation time. See CallOptions.withDeadlineAfter:
        // https://github.com/grpc/grpc-java/blob/v1.73.0/api/src/main/java/io/grpc/CallOptions.java#L177-L179
        SlowServiceGrpc.SlowServiceBlockingStub timedStub =
                SlowServiceGrpc.newBlockingStub(channel)
                        .withDeadlineAfter(200, TimeUnit.MILLISECONDS);
        SlowRequest request = SlowRequest.newBuilder().setDelayMillis(2000).setText("hello").build();

        StatusRuntimeException ex = assertThrows(StatusRuntimeException.class,
                () -> timedStub.slow(request));

        assertThat(ex.getStatus().getCode(), is(Status.Code.DEADLINE_EXCEEDED));
    }

    @Test
    void callOptionsDeadlineTighterThanContext() throws Exception {
        // Context has 5s, CallOptions has 200ms → effective deadline is 200ms
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        try {
            Context ctx = Context.current()
                    .withDeadline(Deadline.after(5, TimeUnit.SECONDS), scheduler);
            ctx.run(() -> {
                SlowRequest request = SlowRequest.newBuilder()
                        .setDelayMillis(2000).setText("hello").build();
                StatusRuntimeException ex = assertThrows(StatusRuntimeException.class,
                        () -> blockingStub
                                .withDeadlineAfter(200, TimeUnit.MILLISECONDS)
                                .slow(request));
                assertThat(ex.getStatus().getCode(), is(Status.Code.DEADLINE_EXCEEDED));
            });
        } finally {
            scheduler.shutdown();
        }
    }

    @Test
    void contextDeadlineTighterThanCallOptions() throws Exception {
        // Context has 200ms, CallOptions has 5s → effective deadline is 200ms
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        try {
            Context ctx = Context.current()
                    .withDeadline(Deadline.after(200, TimeUnit.MILLISECONDS), scheduler);
            ctx.run(() -> {
                SlowRequest request = SlowRequest.newBuilder()
                        .setDelayMillis(2000).setText("hello").build();
                StatusRuntimeException ex = assertThrows(StatusRuntimeException.class,
                        () -> blockingStub
                                .withDeadlineAfter(5, TimeUnit.SECONDS)
                                .slow(request));
                assertThat(ex.getStatus().getCode(), is(Status.Code.DEADLINE_EXCEEDED));
            });
        } finally {
            scheduler.shutdown();
        }
    }
}
