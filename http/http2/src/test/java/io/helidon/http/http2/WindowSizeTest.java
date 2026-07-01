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

package io.helidon.http.http2;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

class WindowSizeTest {

    @Test
    void observesUpdateBeforeBlockingWaitStarts() {
        ConnectionFlowControl connection = ConnectionFlowControl.clientBuilder((_, _) -> { })
                .blockTimeout(Duration.ofSeconds(1))
                .build();
        WindowSize.Outbound outbound = connection.outbound();
        outbound.decrementWindowSize(outbound.getRemainingWindowSize());

        outbound.incrementWindowSize(1);
        outbound.blockTillUpdate();

        assertThat("positive window must be observed before waiting", outbound.getRemainingWindowSize(), is(1));
    }

    @Test
    void resumesAllWaitersAfterWindowUpdate() throws InterruptedException {
        ConnectionFlowControl connection = ConnectionFlowControl.clientBuilder((_, _) -> { })
                .blockTimeout(Duration.ofSeconds(3))
                .build();
        WindowSize.Outbound outbound = connection.outbound();
        outbound.decrementWindowSize(outbound.getRemainingWindowSize());

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger returnedNormally = new AtomicInteger();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread[] blockers = new Thread[2];
        for (int i = 0; i < blockers.length; i++) {
            blockers[i] = Thread.ofVirtual().start(() -> {
                try {
                    ready.countDown();
                    start.await();
                    outbound.blockTillUpdate();
                    returnedNormally.incrementAndGet();
                } catch (Throwable t) {
                    failure.compareAndSet(null, t);
                }
            });
        }
        try {
            assertThat("flow-control waiters must start", ready.await(1, TimeUnit.SECONDS), is(true));
            start.countDown();
            long waitDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
            while ((blockers[0].getState() != Thread.State.TIMED_WAITING
                    || blockers[1].getState() != Thread.State.TIMED_WAITING)
                    && System.nanoTime() < waitDeadline) {
                Thread.onSpinWait();
            }
            assertThat("first flow-control wait must start", blockers[0].getState(), is(Thread.State.TIMED_WAITING));
            assertThat("second flow-control wait must start", blockers[1].getState(), is(Thread.State.TIMED_WAITING));

            outbound.incrementWindowSize(1024);
            for (Thread blocker : blockers) {
                blocker.join();
            }
        } finally {
            start.countDown();
            outbound.incrementWindowSize(1024);
            for (Thread blocker : blockers) {
                blocker.join();
            }
        }

        assertThat("both flow-control waiters must resume", returnedNormally.get(), is(2));
        assertThat(failure.get(), is(nullValue()));
    }

    @Test
    void rejectsWindowUpdateAfterTimeout() throws InterruptedException {
        ConnectionFlowControl connection = ConnectionFlowControl.clientBuilder((_, _) -> { })
                .blockTimeout(Duration.ofSeconds(2))
                .build();
        WindowSize.Outbound outbound = connection.outbound();
        outbound.decrementWindowSize(outbound.getRemainingWindowSize());

        AtomicBoolean returnedNormally = new AtomicBoolean();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread blocker = Thread.ofVirtual().start(() -> {
            try {
                outbound.blockTillUpdate();
                returnedNormally.set(true);
            } catch (Throwable t) {
                failure.set(t);
            }
        });
        try {
            long waitDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
            while (blocker.isAlive()
                    && blocker.getState() != Thread.State.TIMED_WAITING
                    && System.nanoTime() < waitDeadline) {
                Thread.onSpinWait();
            }
            assertThat("flow-control wait must start", blocker.getState(), is(Thread.State.TIMED_WAITING));
            Thread.sleep(Duration.ofMillis(2100));
        } finally {
            outbound.incrementWindowSize(1);
            blocker.join();
        }

        assertThat("late update must not resume the write", returnedNormally.get(), is(false));
        assertThat(failure.get(), instanceOf(Http2Exception.class));
        assertThat(((Http2Exception) failure.get()).code(), is(Http2ErrorCode.FLOW_CONTROL));
    }

    @Test
    void rejectsLateUpdateBetweenTimeoutChecks() throws InterruptedException {
        CountDownLatch afterTimedWait = new CountDownLatch(1);
        CountDownLatch resumeBlocker = new CountDownLatch(1);
        Handler handler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                if (record.getMessage().contains("Window depleted, waiting for update.")) {
                    afterTimedWait.countDown();
                    try {
                        resumeBlocker.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        handler.setLevel(Level.ALL);
        Logger logger = Logger.getLogger(FlowControl.class.getName() + ".ofc");
        Level previousLevel = logger.getLevel();
        boolean previousUseParentHandlers = logger.getUseParentHandlers();
        logger.addHandler(handler);
        logger.setLevel(Level.FINE);
        logger.setUseParentHandlers(false);

        ConnectionFlowControl connection = ConnectionFlowControl.clientBuilder((_, _) -> { })
                .blockTimeout(Duration.ofMillis(100))
                .build();
        WindowSize.Outbound outbound = connection.outbound();
        outbound.decrementWindowSize(outbound.getRemainingWindowSize());
        AtomicBoolean returnedNormally = new AtomicBoolean();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread blocker = Thread.ofVirtual().start(() -> {
            try {
                outbound.blockTillUpdate();
                returnedNormally.set(true);
            } catch (Throwable t) {
                failure.set(t);
            }
        });
        Thread updater = null;
        try {
            assertThat("flow-control waiter must finish its timed wait",
                       afterTimedWait.await(1, TimeUnit.SECONDS),
                       is(true));
            updater = Thread.ofVirtual().start(() -> outbound.incrementWindowSize(1));
            long updateDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
            while (outbound.getRemainingWindowSize() < 1 && System.nanoTime() < updateDeadline) {
                Thread.onSpinWait();
            }
            assertThat("late window update must arrive before the waiter resumes",
                       outbound.getRemainingWindowSize(),
                       is(1));
        } finally {
            resumeBlocker.countDown();
            if (updater != null) {
                updater.join();
            }
            blocker.join();
            logger.removeHandler(handler);
            logger.setLevel(previousLevel);
            logger.setUseParentHandlers(previousUseParentHandlers);
        }

        assertThat("late update must not resume the write", returnedNormally.get(), is(false));
        assertThat(failure.get(), instanceOf(Http2Exception.class));
        assertThat(((Http2Exception) failure.get()).code(), is(Http2ErrorCode.FLOW_CONTROL));
    }

    @Test
    void restoresConnectionAndStreamCreditIndependently() {
        AtomicInteger connectionUpdate = new AtomicInteger();
        AtomicInteger streamUpdate = new AtomicInteger();
        ConnectionFlowControl connection = ConnectionFlowControl.serverBuilder((streamId, update) -> {
            if (streamId == 0) {
                connectionUpdate.addAndGet(update.windowSizeIncrement());
            } else {
                assertThat(streamId, is(1));
                streamUpdate.addAndGet(update.windowSizeIncrement());
            }
        }).build();
        FlowControl.Inbound stream = connection.createStreamFlowControl(1,
                                                                         WindowSize.DEFAULT_WIN_SIZE,
                                                                         WindowSize.DEFAULT_MAX_FRAME_SIZE)
                .inbound();

        stream.decrementWindowSize(WindowSize.DEFAULT_MAX_FRAME_SIZE);

        connection.incrementInboundConnectionWindowSize(WindowSize.DEFAULT_MAX_FRAME_SIZE);
        assertThat(connectionUpdate.get(), is(WindowSize.DEFAULT_MAX_FRAME_SIZE));
        assertThat(streamUpdate.get(), is(0));

        stream.incrementStreamWindowSize(WindowSize.DEFAULT_MAX_FRAME_SIZE);
        assertThat(connectionUpdate.get(), is(WindowSize.DEFAULT_MAX_FRAME_SIZE));
        assertThat(streamUpdate.get(), is(WindowSize.DEFAULT_MAX_FRAME_SIZE));
    }
}
