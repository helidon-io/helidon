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
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
    void rejectsStreamClosedBeforeBlockingWaitStarts() {
        ConnectionFlowControl connection = ConnectionFlowControl.clientBuilder((_, _) -> { })
                .blockTimeout(Duration.ofSeconds(1))
                .build();
        FlowControl.Outbound outbound = connection.createStreamFlowControl(1,
                                                                            WindowSize.DEFAULT_WIN_SIZE,
                                                                            WindowSize.DEFAULT_MAX_FRAME_SIZE)
                .outbound();
        outbound.resetStreamWindowSize(0);

        outbound.streamClosed();
        Http2Exception exception = assertThrows(Http2Exception.class, outbound::blockTillUpdate);

        assertThat(exception.code(), is(Http2ErrorCode.CANCEL));
    }

    @Test
    void streamCloseReleasesConnectionWindowWaitWithoutWakingSibling() throws InterruptedException {
        ConnectionFlowControl connection = ConnectionFlowControl.clientBuilder((_, _) -> { })
                .blockTimeout(Duration.ofSeconds(10))
                .build();
        WindowSizeImpl.Outbound connectionWindow = (WindowSizeImpl.Outbound) connection.outbound();
        connectionWindow.decrementWindowSize(connectionWindow.getRemainingWindowSize());
        AtomicBoolean firstClosed = new AtomicBoolean();
        AtomicBoolean siblingClosed = new AtomicBoolean();
        AtomicInteger firstChecks = new AtomicInteger();
        AtomicInteger siblingChecks = new AtomicInteger();
        WindowSizeImpl.Outbound.ConnectionWindowWaiter first = connectionWindow.createConnectionWindowWaiter(() -> {
            firstChecks.incrementAndGet();
            return firstClosed.get();
        });
        WindowSizeImpl.Outbound.ConnectionWindowWaiter sibling = connectionWindow.createConnectionWindowWaiter(() -> {
            siblingChecks.incrementAndGet();
            return siblingClosed.get();
        });
        AtomicReference<Throwable> firstFailure = new AtomicReference<>();
        AtomicReference<Throwable> siblingFailure = new AtomicReference<>();
        AtomicBoolean siblingReturned = new AtomicBoolean();
        Thread firstBlocker = Thread.ofVirtual().start(() -> {
            try {
                connectionWindow.blockTillUpdate(first);
            } catch (Throwable t) {
                firstFailure.set(t);
            }
        });
        Thread siblingBlocker = Thread.ofVirtual().start(() -> {
            try {
                connectionWindow.blockTillUpdate(sibling);
                siblingReturned.set(true);
            } catch (Throwable t) {
                siblingFailure.set(t);
            }
        });
        try {
            long waitDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
            while ((firstBlocker.getState() != Thread.State.TIMED_WAITING
                    || siblingBlocker.getState() != Thread.State.TIMED_WAITING)
                    && System.nanoTime() < waitDeadline) {
                Thread.onSpinWait();
            }
            assertThat("first flow-control wait must start", firstBlocker.getState(), is(Thread.State.TIMED_WAITING));
            assertThat("sibling flow-control wait must start", siblingBlocker.getState(), is(Thread.State.TIMED_WAITING));

            int siblingChecksBeforeReset = siblingChecks.get();
            assertThat("first stream cancellation must be checked before waiting", firstChecks.get(), greaterThan(0));
            assertThat("sibling cancellation must be checked before waiting", siblingChecksBeforeReset, greaterThan(0));
            firstClosed.set(true);
            connectionWindow.triggerUpdate(first);
            firstBlocker.join(1000);
            assertThat("closed stream flow-control wait must finish", firstBlocker.isAlive(), is(false));
            assertThat(firstFailure.get(), instanceOf(Http2Exception.class));
            assertThat(((Http2Exception) firstFailure.get()).code(), is(Http2ErrorCode.CANCEL));
            assertThat("sibling flow-control wait must remain blocked", siblingBlocker.isAlive(), is(true));
            assertThat("targeted stream cancellation must not wake the sibling",
                       siblingChecks.get(), is(siblingChecksBeforeReset));

            connectionWindow.incrementWindowSize(1);
            siblingBlocker.join(1000);
        } finally {
            firstClosed.set(true);
            siblingClosed.set(true);
            connectionWindow.triggerUpdate(first);
            connectionWindow.triggerUpdate(sibling);
            connectionWindow.incrementWindowSize(1);
            firstBlocker.join();
            siblingBlocker.join();
        }

        assertThat("sibling flow-control wait must resume normally", siblingReturned.get(), is(true));
        assertThat("connection credit must wake the sibling", siblingChecks.get(), greaterThan(1));
        assertThat(siblingFailure.get(), is(nullValue()));
    }

    @Test
    void interruptedConnectionWaiterRemainsRegisteredForSiblingWriter() throws InterruptedException {
        ConnectionFlowControl connection = ConnectionFlowControl.clientBuilder((_, _) -> { })
                .blockTimeout(Duration.ofSeconds(10))
                .build();
        WindowSizeImpl.Outbound connectionWindow = (WindowSizeImpl.Outbound) connection.outbound();
        connectionWindow.decrementWindowSize(connectionWindow.getRemainingWindowSize());
        AtomicBoolean streamClosed = new AtomicBoolean();
        WindowSizeImpl.Outbound.ConnectionWindowWaiter waiter =
                connectionWindow.createConnectionWindowWaiter(streamClosed::get);
        AtomicReference<Throwable> firstFailure = new AtomicReference<>();
        AtomicReference<Throwable> secondFailure = new AtomicReference<>();
        AtomicBoolean secondReturned = new AtomicBoolean();
        Thread firstBlocker = Thread.ofVirtual().start(() -> {
            try {
                connectionWindow.blockTillUpdate(waiter);
            } catch (Throwable t) {
                firstFailure.set(t);
            }
        });
        Thread secondBlocker = Thread.ofVirtual().start(() -> {
            try {
                connectionWindow.blockTillUpdate(waiter);
                secondReturned.set(true);
            } catch (Throwable t) {
                secondFailure.set(t);
            }
        });
        try {
            long waitDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
            while ((firstBlocker.getState() != Thread.State.TIMED_WAITING
                    || secondBlocker.getState() != Thread.State.TIMED_WAITING)
                    && System.nanoTime() < waitDeadline) {
                Thread.onSpinWait();
            }
            assertThat("first flow-control wait must start", firstBlocker.getState(), is(Thread.State.TIMED_WAITING));
            assertThat("second flow-control wait must start", secondBlocker.getState(), is(Thread.State.TIMED_WAITING));

            firstBlocker.interrupt();
            firstBlocker.join(1000);
            assertThat(firstFailure.get(), instanceOf(Http2Exception.class));
            assertThat(((Http2Exception) firstFailure.get()).code(), is(Http2ErrorCode.FLOW_CONTROL));
            assertThat("second writer must remain blocked after its sibling is interrupted",
                       secondBlocker.isAlive(), is(true));

            connectionWindow.incrementWindowSize(1);
            secondBlocker.join(1000);
        } finally {
            streamClosed.set(true);
            connectionWindow.triggerUpdate(waiter);
            connectionWindow.incrementWindowSize(1);
            firstBlocker.join();
            secondBlocker.join();
        }

        assertThat("remaining writer must resume on connection credit", secondReturned.get(), is(true));
        assertThat(secondFailure.get(), is(nullValue()));
    }

    @Test
    void concurrentFirstConnectionWaitsShareLazyStreamWaiter() throws InterruptedException {
        ConnectionFlowControl connection = ConnectionFlowControl.clientBuilder((_, _) -> { })
                .blockTimeout(Duration.ofSeconds(10))
                .build();
        WindowSize.Outbound connectionWindow = connection.outbound();
        connectionWindow.decrementWindowSize(connectionWindow.getRemainingWindowSize());
        FlowControl.Outbound stream = connection.createStreamFlowControl(1,
                                                                          WindowSize.DEFAULT_WIN_SIZE,
                                                                          WindowSize.DEFAULT_MAX_FRAME_SIZE)
                .outbound();
        AtomicReference<Throwable> firstFailure = new AtomicReference<>();
        AtomicReference<Throwable> secondFailure = new AtomicReference<>();
        CountDownLatch start = new CountDownLatch(1);
        Thread firstBlocker = Thread.ofVirtual().start(() -> blockAfter(start, stream, firstFailure));
        Thread secondBlocker = Thread.ofVirtual().start(() -> blockAfter(start, stream, secondFailure));
        try {
            start.countDown();
            long waitDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
            while ((firstBlocker.getState() != Thread.State.TIMED_WAITING
                    || secondBlocker.getState() != Thread.State.TIMED_WAITING)
                    && System.nanoTime() < waitDeadline) {
                Thread.onSpinWait();
            }
            assertThat("first flow-control wait must start", firstBlocker.getState(), is(Thread.State.TIMED_WAITING));
            assertThat("second flow-control wait must start", secondBlocker.getState(), is(Thread.State.TIMED_WAITING));

            stream.streamClosed();
            firstBlocker.join(1000);
            secondBlocker.join(1000);
            assertThat("first targeted flow-control cancellation must finish", firstBlocker.isAlive(), is(false));
            assertThat("second targeted flow-control cancellation must finish", secondBlocker.isAlive(), is(false));
            assertThat(firstFailure.get(), instanceOf(Http2Exception.class));
            assertThat(((Http2Exception) firstFailure.get()).code(), is(Http2ErrorCode.CANCEL));
            assertThat(secondFailure.get(), instanceOf(Http2Exception.class));
            assertThat(((Http2Exception) secondFailure.get()).code(), is(Http2ErrorCode.CANCEL));
        } finally {
            start.countDown();
            stream.streamClosed();
            connectionWindow.incrementWindowSize(1);
            firstBlocker.join();
            secondBlocker.join();
        }
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

    private static void blockAfter(CountDownLatch start,
                                   FlowControl.Outbound flowControl,
                                   AtomicReference<Throwable> failure) {
        try {
            start.await();
            flowControl.blockTillUpdate();
        } catch (Throwable t) {
            failure.set(t);
        }
    }
}
