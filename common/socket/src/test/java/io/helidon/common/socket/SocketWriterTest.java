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

package io.helidon.common.socket;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import io.helidon.common.buffers.BufferData;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SocketWriterTest {

    @Test
    void smartWriterCloseStopsAsyncWriter() throws InterruptedException {
        AtomicReference<Thread> writerThread = new AtomicReference<>();
        ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "smart-socket-writer-test");
            writerThread.set(thread);
            return thread;
        });
        TestSocket socket = new TestSocket(false);
        SocketWriter writer = SocketWriter.create(executor, socket.socket(), 2, true);

        try {
            writer.write(BufferData.create(new byte[] {1}));

            assertThat("Initial async write did not complete", socket.awaitFirstWrite(), is(true));
            assertThat(writerThread.get(), notNullValue());

            writer.close();
            executor.shutdown();

            assertThat("Smart writer close did not stop the async writer",
                       executor.awaitTermination(1, TimeUnit.SECONDS),
                       is(true));
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(10, TimeUnit.SECONDS);
        }
    }

    @Test
    void asyncWriterCloseFlushesQueuedBuffers() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> new Thread(runnable, "async-writer-test"));
        ExecutorService closeExecutor = Executors.newSingleThreadExecutor(runnable -> new Thread(runnable, "close-test"));
        TestSocket socket = new TestSocket(true);
        SocketWriterAsync writer = new SocketWriterAsync(executor, socket.socket(), 2);
        CountDownLatch closeWaiting = new CountDownLatch(1);
        Future<?> close;

        try {
            writer.beforeCloseAwait(closeWaiting::countDown);
            writer.write(BufferData.create(new byte[] {1}));
            assertThat("Initial async write did not start", socket.awaitFirstWrite(), is(true));
            writer.write(BufferData.create(new byte[] {2}));

            close = closeExecutor.submit(writer::close);
            assertThat("Close did not wait for the current async write",
                       closeWaiting.await(10, TimeUnit.SECONDS),
                       is(true));

            socket.releaseFirstWrite();
            close.get(2, TimeUnit.SECONDS);

            assertThat(socket.writtenBytes(), is(new byte[] {1, 2}));
            assertThat(socket.writeThreadNames(), contains("[test child]", "[test child]"));
        } finally {
            socket.releaseFirstWrite();
            closeExecutor.shutdownNow();
            executor.shutdownNow();
            closeExecutor.awaitTermination(10, TimeUnit.SECONDS);
            executor.awaitTermination(10, TimeUnit.SECONDS);
        }
    }

    @Test
    void asyncWriterCloseDiscardsQueuedBuffersWhenWriterDoesNotStop() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        TestSocket socket = new TestSocket(true);
        SocketWriterAsync writer = new SocketWriterAsync(executor, socket.socket(), 2);

        try {
            writer.write(BufferData.create(new byte[] {1}));
            assertThat("Initial async write did not start", socket.awaitFirstWrite(), is(true));
            writer.write(BufferData.create(new byte[] {2}));
            writer.write(BufferData.create(new byte[] {3}));

            writer.close();

            assertThat("Close did not discard queued buffers after timeout", writer.queuedBufferCount(), is(0));
        } finally {
            socket.releaseFirstWrite();
            executor.shutdownNow();
            executor.awaitTermination(10, TimeUnit.SECONDS);
        }
    }

    @Test
    void asyncWriterCloseFlushesFullQueueWhenCurrentWriteCompletes() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        ExecutorService closeExecutor = Executors.newSingleThreadExecutor();
        TestSocket socket = new TestSocket(true);
        SocketWriterAsync writer = new SocketWriterAsync(executor, socket.socket(), 2);
        CountDownLatch closeWaiting = new CountDownLatch(1);
        Future<?> close;

        try {
            writer.beforeCloseAwait(closeWaiting::countDown);
            writer.write(BufferData.create(new byte[] {1}));
            assertThat("Initial async write did not start", socket.awaitFirstWrite(), is(true));
            writer.write(BufferData.create(new byte[] {2}));
            writer.write(BufferData.create(new byte[] {3}));

            close = closeExecutor.submit(writer::close);
            assertThat("Close did not wait for the current async write",
                       closeWaiting.await(10, TimeUnit.SECONDS),
                       is(true));

            socket.releaseFirstWrite();
            close.get(2, TimeUnit.SECONDS);

            assertThat(socket.writtenBytes(), is(new byte[] {1, 2, 3}));
        } finally {
            socket.releaseFirstWrite();
            closeExecutor.shutdownNow();
            executor.shutdownNow();
            closeExecutor.awaitTermination(10, TimeUnit.SECONDS);
            executor.awaitTermination(10, TimeUnit.SECONDS);
        }
    }

    @Test
    void smartWriterCloseDoesNotBlockWhenQueueIsFull() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        ExecutorService closeExecutor = Executors.newSingleThreadExecutor();
        TestSocket socket = new TestSocket(true);
        SocketWriter writer = SocketWriter.create(executor, socket.socket(), 2, true);
        Future<?> close;

        try {
            writer.write(BufferData.create(new byte[] {1}));
            assertThat("Initial async write did not start", socket.awaitFirstWrite(), is(true));
            writer.write(BufferData.create(new byte[] {2}));
            writer.write(BufferData.create(new byte[] {3}));

            close = closeExecutor.submit(writer::close);

            close.get(2, TimeUnit.SECONDS);
            executor.shutdown();

            assertThat("Smart writer close did not stop the async writer after a full queue",
                       executor.awaitTermination(1, TimeUnit.SECONDS),
                       is(true));
        } finally {
            socket.releaseFirstWrite();
            closeExecutor.shutdownNow();
            executor.shutdownNow();
            closeExecutor.awaitTermination(10, TimeUnit.SECONDS);
            executor.awaitTermination(10, TimeUnit.SECONDS);
        }
    }

    @Test
    void asyncWriterWriteFailsWhenCloseWinsBlockedProducerRace() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        ExecutorService producerExecutor = Executors.newSingleThreadExecutor();
        ExecutorService closeExecutor = Executors.newSingleThreadExecutor();
        TestSocket socket = new TestSocket(true);
        SocketWriterAsync writer = new SocketWriterAsync(executor, socket.socket(), 2);
        CountDownLatch producerWaiting = new CountDownLatch(1);
        Future<?> blockedWrite;

        try {
            writer.beforeWriteQueueAwait(producerWaiting::countDown);
            writer.write(BufferData.create(new byte[] {1}));
            assertThat("Initial async write did not start", socket.awaitFirstWrite(), is(true));
            writer.write(BufferData.create(new byte[] {2}));
            writer.write(BufferData.create(new byte[] {3}));

            blockedWrite = producerExecutor.submit(() -> writer.write(BufferData.create(new byte[] {4})));
            assertThat("Producer write did not block behind the full queue",
                       producerWaiting.await(10, TimeUnit.SECONDS),
                       is(true));

            closeExecutor.submit(writer::close).get(2, TimeUnit.SECONDS);

            ExecutionException e = assertThrows(ExecutionException.class, () -> blockedWrite.get(2, TimeUnit.SECONDS));
            assertThat(e.getCause(), instanceOf(SocketWriterException.class));
            assertThat(socket.writtenBytes(), is(BufferData.EMPTY_BYTES));
        } finally {
            socket.releaseFirstWrite();
            closeExecutor.shutdownNow();
            producerExecutor.shutdownNow();
            executor.shutdownNow();
            closeExecutor.awaitTermination(10, TimeUnit.SECONDS);
            producerExecutor.awaitTermination(10, TimeUnit.SECONDS);
            executor.awaitTermination(10, TimeUnit.SECONDS);
        }
    }

    @Test
    void smartWriterWriteFailsWhenCloseWinsBlockedProducerRace() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        ExecutorService producerExecutor = Executors.newSingleThreadExecutor();
        ExecutorService closeExecutor = Executors.newSingleThreadExecutor();
        TestSocket socket = new TestSocket(true);
        SmartSocketWriter writer = new SmartSocketWriter(executor, socket.socket(), 2);
        CountDownLatch producerWaiting = new CountDownLatch(1);
        Future<?> blockedWrite;

        try {
            writer.beforeWriteQueueAwait(producerWaiting::countDown);
            writer.write(BufferData.create(new byte[] {1}));
            assertThat("Initial async write did not start", socket.awaitFirstWrite(), is(true));
            writer.write(BufferData.create(new byte[] {2}));
            writer.write(BufferData.create(new byte[] {3}));

            blockedWrite = producerExecutor.submit(() -> writer.write(BufferData.create(new byte[] {4})));
            assertThat("Smart producer write did not block behind the full queue",
                       producerWaiting.await(10, TimeUnit.SECONDS),
                       is(true));

            closeExecutor.submit(writer::close).get(2, TimeUnit.SECONDS);

            ExecutionException e = assertThrows(ExecutionException.class, () -> blockedWrite.get(2, TimeUnit.SECONDS));
            assertThat(e.getCause(), instanceOf(SocketWriterException.class));
            assertThat(socket.writtenBytes(), is(BufferData.EMPTY_BYTES));
        } finally {
            socket.releaseFirstWrite();
            closeExecutor.shutdownNow();
            producerExecutor.shutdownNow();
            executor.shutdownNow();
            closeExecutor.awaitTermination(10, TimeUnit.SECONDS);
            producerExecutor.awaitTermination(10, TimeUnit.SECONDS);
            executor.awaitTermination(10, TimeUnit.SECONDS);
        }
    }

    @Test
    void asyncWriterCloseInterruptionStopsAsyncWriter() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        ExecutorService closeExecutor = Executors.newSingleThreadExecutor();
        TestSocket socket = new TestSocket(true);
        SocketWriterAsync writer = new SocketWriterAsync(executor, socket.socket(), 2);
        CountDownLatch closeWaiting = new CountDownLatch(1);
        Future<?> close;

        try {
            writer.beforeCloseAwait(closeWaiting::countDown);
            writer.write(BufferData.create(new byte[] {1}));
            assertThat("Initial async write did not start", socket.awaitFirstWrite(), is(true));

            close = closeExecutor.submit(writer::close);
            assertThat("Close did not start waiting for the async writer",
                       closeWaiting.await(10, TimeUnit.SECONDS),
                       is(true));

            close.cancel(true);
            closeExecutor.shutdown();
            executor.shutdown();

            assertThat("Interrupted close did not finish",
                       closeExecutor.awaitTermination(1, TimeUnit.SECONDS),
                       is(true));
            assertThat("Interrupted close did not stop the async writer",
                       executor.awaitTermination(1, TimeUnit.SECONDS),
                       is(true));
        } finally {
            socket.releaseFirstWrite();
            closeExecutor.shutdownNow();
            executor.shutdownNow();
            closeExecutor.awaitTermination(10, TimeUnit.SECONDS);
            executor.awaitTermination(10, TimeUnit.SECONDS);
        }
    }

    @Test
    void smartWriterCloseDoesNotWaitForSyncWrite() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        ExecutorService writeExecutor = Executors.newSingleThreadExecutor();
        ExecutorService closeExecutor = Executors.newSingleThreadExecutor();
        TestSocket socket = new TestSocket(true);
        SmartSocketWriter writer = new SmartSocketWriter(executor, socket.socket(), 2);
        Future<?> write;
        Future<?> close;

        try {
            writer.switchToSyncMode();
            write = writeExecutor.submit(() -> writer.write(BufferData.create(new byte[] {1})));
            assertThat("Sync write did not start", socket.awaitFirstWrite(), is(true));

            close = closeExecutor.submit(writer::close);
            close.get(2, TimeUnit.SECONDS);
            assertThrows(SocketWriterException.class, () -> writer.write(BufferData.create(new byte[] {2})));

            socket.releaseFirstWrite();
            write.get(2, TimeUnit.SECONDS);

            assertThat(socket.writtenBytes(), is(new byte[] {1}));
        } finally {
            socket.releaseFirstWrite();
            closeExecutor.shutdownNow();
            writeExecutor.shutdownNow();
            executor.shutdownNow();
            closeExecutor.awaitTermination(10, TimeUnit.SECONDS);
            writeExecutor.awaitTermination(10, TimeUnit.SECONDS);
            executor.awaitTermination(10, TimeUnit.SECONDS);
        }
    }

    @Test
    void smartWriterSyncWriteFailsAfterClose() {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        TestSocket socket = new TestSocket(false);
        SmartSocketWriter writer = new SmartSocketWriter(executor, socket.socket(), 2);

        try {
            writer.switchToSyncMode();
            writer.close();

            assertThrows(SocketWriterException.class, () -> writer.write(BufferData.create(new byte[] {1})));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void smartWriterWriteNowInAsyncModeWaitsForQueuedWrites() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        ExecutorService writeNowExecutor = Executors.newSingleThreadExecutor(runnable -> new Thread(runnable, "write-now-test"));
        TestSocket socket = new TestSocket(true);
        SmartSocketWriter writer = new SmartSocketWriter(executor, socket.socket(), 2);
        CountDownLatch writeNowWaiting = new CountDownLatch(1);
        Future<?> writeNow;

        try {
            writer.beforeFlushAwait(writeNowWaiting::countDown);
            writer.write(BufferData.create(new byte[] {1}));
            assertThat("Initial async write did not start", socket.awaitFirstWrite(), is(true));

            writeNow = writeNowExecutor.submit(() -> writer.writeNow(BufferData.create(new byte[] {2})));

            assertThat("writeNow did not reach the async flush wait",
                       writeNowWaiting.await(10, TimeUnit.SECONDS),
                       is(true));
            assertThat("writeNow completed before the queued write was released", writeNow.isDone(), is(false));
            socket.releaseFirstWrite();
            writeNow.get(2, TimeUnit.SECONDS);

            assertThat(socket.writtenBytes(), is(new byte[] {1, 2}));
            assertThat(socket.writeThreadNames(), contains("[test child]", "write-now-test"));
        } finally {
            socket.releaseFirstWrite();
            writeNowExecutor.shutdownNow();
            executor.shutdownNow();
            writeNowExecutor.awaitTermination(10, TimeUnit.SECONDS);
            executor.awaitTermination(10, TimeUnit.SECONDS);
        }
    }

    @Test
    void smartWriterFlushInAsyncModeWaitsForQueuedWrites() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        ExecutorService flushExecutor = Executors.newSingleThreadExecutor(runnable -> new Thread(runnable, "flush-test"));
        TestSocket socket = new TestSocket(true);
        SmartSocketWriter writer = new SmartSocketWriter(executor, socket.socket(), 2);
        CountDownLatch flushWaiting = new CountDownLatch(1);
        Future<?> flush;

        try {
            writer.beforeFlushAwait(flushWaiting::countDown);
            writer.write(BufferData.create(new byte[] {1}));
            assertThat("Initial async write did not start", socket.awaitFirstWrite(), is(true));
            writer.write(BufferData.create(new byte[] {2}));

            flush = flushExecutor.submit(writer::flush);

            assertThat("flush did not reach the async flush wait",
                       flushWaiting.await(10, TimeUnit.SECONDS),
                       is(true));
            assertThat("flush completed before the queued write was released", flush.isDone(), is(false));
            socket.releaseFirstWrite();
            flush.get(2, TimeUnit.SECONDS);

            assertThat(socket.writtenBytes(), is(new byte[] {1, 2}));
        } finally {
            socket.releaseFirstWrite();
            flushExecutor.shutdownNow();
            executor.shutdownNow();
            flushExecutor.awaitTermination(10, TimeUnit.SECONDS);
            executor.awaitTermination(10, TimeUnit.SECONDS);
        }
    }

    @Test
    void asyncWriterWriteNowIsNotOvertakenByLaterWrites() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        ExecutorService writeNowExecutor = Executors.newSingleThreadExecutor();
        ExecutorService producerExecutor = Executors.newSingleThreadExecutor();
        TestSocket socket = new TestSocket(true);
        SocketWriterAsync writer = new SocketWriterAsync(executor, socket.socket(), 2);
        CountDownLatch flushWaiting = new CountDownLatch(1);
        Future<?> writeNow;
        Future<?> producer;

        try {
            writer.beforeFlushAwait(flushWaiting::countDown);
            writer.write(BufferData.create(new byte[] {1}));
            assertThat("Initial async write did not start", socket.awaitFirstWrite(), is(true));

            writeNow = writeNowExecutor.submit(() -> writer.writeNow(BufferData.create(new byte[] {2})));
            assertThat("writeNow did not reach the async flush wait",
                       flushWaiting.await(10, TimeUnit.SECONDS),
                       is(true));

            producer = producerExecutor.submit(() -> writer.write(BufferData.create(new byte[] {3})));

            socket.releaseFirstWrite();
            writeNow.get(2, TimeUnit.SECONDS);
            producer.get(2, TimeUnit.SECONDS);
            writer.flush();

            assertThat(socket.writtenBytes(), is(new byte[] {1, 2, 3}));
        } finally {
            socket.releaseFirstWrite();
            producerExecutor.shutdownNow();
            writeNowExecutor.shutdownNow();
            executor.shutdownNow();
            producerExecutor.awaitTermination(10, TimeUnit.SECONDS);
            writeNowExecutor.awaitTermination(10, TimeUnit.SECONDS);
            executor.awaitTermination(10, TimeUnit.SECONDS);
        }
    }

    @Test
    void asyncAndSmartWritersRejectNullBuffers() {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        TestSocket socket = new TestSocket(false);
        SocketWriterAsync asyncWriter = new SocketWriterAsync(executor, socket.socket(), 2);
        SmartSocketWriter smartWriter = new SmartSocketWriter(executor, socket.socket(), 2);

        try {
            assertThrows(NullPointerException.class, () -> asyncWriter.write((BufferData) null));
            assertThrows(NullPointerException.class, () -> asyncWriter.writeNow((BufferData) null));
            assertThrows(NullPointerException.class, () -> smartWriter.write((BufferData) null));
            assertThrows(NullPointerException.class, () -> smartWriter.writeNow((BufferData) null));
            assertThat(socket.writtenBytes(), is(BufferData.EMPTY_BYTES));
        } finally {
            asyncWriter.close();
            smartWriter.close();
            executor.shutdownNow();
        }
    }

    @Test
    void asyncWriterStartDoesNotHoldStateLockDuringSubmit() throws Exception {
        BlockingSubmitExecutor executor = new BlockingSubmitExecutor();
        ExecutorService writeExecutor = Executors.newSingleThreadExecutor();
        ExecutorService closeExecutor = Executors.newSingleThreadExecutor();
        TestSocket socket = new TestSocket(false);
        SocketWriterAsync writer = new SocketWriterAsync(executor, socket.socket(), 2);
        CountDownLatch closeWaiting = new CountDownLatch(1);
        Future<?> write;
        Future<?> close;

        try {
            writer.beforeCloseAwait(closeWaiting::countDown);
            write = writeExecutor.submit(() -> writer.write(BufferData.create(new byte[] {1})));

            assertThat("Writer task submission did not start",
                       executor.awaitSubmitStarted(),
                       is(true));

            close = closeExecutor.submit(writer::close);
            assertThat("Close did not reach its await hook while writer submission was blocked",
                       closeWaiting.await(10, TimeUnit.SECONDS),
                       is(true));

            executor.releaseSubmit();
            close.get(2, TimeUnit.SECONDS);

            ExecutionException e = assertThrows(ExecutionException.class, () -> write.get(2, TimeUnit.SECONDS));
            assertThat(e.getCause(), instanceOf(SocketWriterException.class));
        } finally {
            executor.releaseSubmit();
            closeExecutor.shutdownNow();
            writeExecutor.shutdownNow();
            executor.shutdownNow();
            closeExecutor.awaitTermination(10, TimeUnit.SECONDS);
            writeExecutor.awaitTermination(10, TimeUnit.SECONDS);
            executor.awaitTermination(10, TimeUnit.SECONDS);
        }
    }

    @Test
    void asyncWriterFlushOnSameExecutorDrainsQueuedWrites() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        TestSocket socket = new TestSocket(false);
        SocketWriterAsync writer = new SocketWriterAsync(executor, socket.socket(), 2);

        try {
            Future<?> flush = executor.submit(() -> {
                writer.write(BufferData.create(new byte[] {1}));
                writer.flush();
            });

            flush.get(2, TimeUnit.SECONDS);

            assertThat(socket.writtenBytes(), is(new byte[] {1}));
        } finally {
            writer.close();
            executor.shutdownNow();
            executor.awaitTermination(10, TimeUnit.SECONDS);
        }
    }

    @Test
    void smartWriterWriteNowOnSameExecutorDoesNotDeadlock() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> new Thread(runnable, "same-executor-test"));
        TestSocket socket = new TestSocket(false);
        SmartSocketWriter writer = new SmartSocketWriter(executor, socket.socket(), 2);

        try {
            Future<?> write = executor.submit(() -> {
                writer.write(BufferData.create(new byte[] {1}));
                writer.writeNow(BufferData.create(new byte[] {2}));
            });

            write.get(2, TimeUnit.SECONDS);

            assertThat(socket.writtenBytes(), is(new byte[] {1, 2}));
            assertThat(socket.writeThreadNames(), contains("same-executor-test", "same-executor-test"));
        } finally {
            writer.close();
            executor.shutdownNow();
            executor.awaitTermination(10, TimeUnit.SECONDS);
        }
    }

    @Test
    void smartWriterWriteNowFailsAfterClose() {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        TestSocket socket = new TestSocket(false);
        SmartSocketWriter writer = new SmartSocketWriter(executor, socket.socket(), 2);

        try {
            writer.close();

            assertThrows(SocketWriterException.class, () -> writer.writeNow(BufferData.create(new byte[] {1})));
            assertThat(socket.writtenBytes(), is(BufferData.EMPTY_BYTES));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void smartWriterSwitchesToSyncModeAfterLowQueueWindow() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        TestSocket socket = new TestSocket(false);
        SmartSocketWriter writer = new SmartSocketWriter(executor, socket.socket(), 2);

        try {
            for (int i = 1; i <= 1000; i++) {
                writer.write(BufferData.create(new byte[] {(byte) i}));
                assertThat("Async write did not complete", socket.awaitWriteCount(i), is(true));
            }

            assertThat(writer.asyncMode(), is(false));
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(10, TimeUnit.SECONDS);
        }
    }

    @Test
    void asyncWriterFlushPropagatesWriteFailureWithoutSuccessRace() {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        RuntimeException failure = new RuntimeException("boom");
        TestSocket socket = new TestSocket(false, failure);
        SocketWriterAsync writer = new SocketWriterAsync(executor, socket.socket(), 2);

        try {
            writer.write(BufferData.create(new byte[] {1}));

            SocketWriterException flushFailure = assertThrows(SocketWriterException.class, writer::flush);
            assertThat(flushFailure.getCause(), is(failure));

            SocketWriterException writeFailure = assertThrows(SocketWriterException.class,
                                                             () -> writer.write(BufferData.create(new byte[] {2})));
            assertThat(writeFailure.getCause(), is(failure));
        } finally {
            executor.shutdownNow();
        }
    }

    private static final class BlockingSubmitExecutor extends AbstractExecutorService {
        private final CountDownLatch submitStarted = new CountDownLatch(1);
        private final CountDownLatch releaseSubmit = new CountDownLatch(1);
        private final CountDownLatch terminated = new CountDownLatch(1);
        private final AtomicBoolean shutdown = new AtomicBoolean();
        private final AtomicReference<Thread> worker = new AtomicReference<>();

        @Override
        public void shutdown() {
            shutdown.set(true);
            releaseSubmit();
        }

        @Override
        public List<Runnable> shutdownNow() {
            shutdown();
            Thread thread = worker.get();
            if (thread != null) {
                thread.interrupt();
            }
            return List.of();
        }

        @Override
        public boolean isShutdown() {
            return shutdown.get();
        }

        @Override
        public boolean isTerminated() {
            return terminated.getCount() == 0;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
            return terminated.await(timeout, unit);
        }

        @Override
        public void execute(Runnable command) {
            submitStarted.countDown();
            try {
                releaseSubmit.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                terminated.countDown();
                return;
            }
            if (shutdown.get()) {
                terminated.countDown();
                return;
            }
            Thread thread = new Thread(() -> {
                try {
                    command.run();
                } finally {
                    terminated.countDown();
                }
            }, "blocking-submit-writer");
            worker.set(thread);
            thread.start();
        }

        private boolean awaitSubmitStarted() throws InterruptedException {
            return submitStarted.await(10, TimeUnit.SECONDS);
        }

        private void releaseSubmit() {
            releaseSubmit.countDown();
        }
    }

    private static final class TestSocket {
        private final HelidonSocket socket = mock(HelidonSocket.class);
        private final CountDownLatch firstWriteStarted = new CountDownLatch(1);
        private final CountDownLatch releaseFirstWrite = new CountDownLatch(1);
        private final ArrayList<String> writeThreadNames = new ArrayList<>();
        private final ByteArrayOutputStream written = new ByteArrayOutputStream();
        private final AtomicInteger writeCount = new AtomicInteger();
        private final AtomicInteger completedWriteCount = new AtomicInteger();
        private final Lock writtenLock = new ReentrantLock();
        private final Condition writeCountChanged = writtenLock.newCondition();
        private final boolean blockFirstWrite;
        private final RuntimeException firstWriteFailure;

        private TestSocket(boolean blockFirstWrite) {
            this(blockFirstWrite, null);
        }

        private TestSocket(boolean blockFirstWrite, RuntimeException firstWriteFailure) {
            this.blockFirstWrite = blockFirstWrite;
            this.firstWriteFailure = firstWriteFailure;
            when(socket.socketId()).thenReturn("test");
            when(socket.childSocketId()).thenReturn("child");
            doAnswer(invocation -> {
                write(invocation.getArgument(0));
                return null;
            }).when(socket).write(any(BufferData.class));
        }

        private HelidonSocket socket() {
            return socket;
        }

        private boolean awaitFirstWrite() throws InterruptedException {
            return firstWriteStarted.await(10, TimeUnit.SECONDS);
        }

        private boolean awaitWriteCount(int expectedCount) throws InterruptedException {
            long remaining = TimeUnit.SECONDS.toNanos(10);
            writtenLock.lock();
            try {
                while (completedWriteCount.get() < expectedCount) {
                    if (remaining <= 0) {
                        return false;
                    }
                    remaining = writeCountChanged.awaitNanos(remaining);
                }
                return true;
            } finally {
                writtenLock.unlock();
            }
        }

        private void releaseFirstWrite() {
            releaseFirstWrite.countDown();
        }

        private byte[] writtenBytes() {
            writtenLock.lock();
            try {
                return written.toByteArray();
            } finally {
                writtenLock.unlock();
            }
        }

        private List<String> writeThreadNames() {
            writtenLock.lock();
            try {
                return List.copyOf(writeThreadNames);
            } finally {
                writtenLock.unlock();
            }
        }

        private void write(BufferData buffer) {
            if (writeCount.incrementAndGet() == 1) {
                firstWriteStarted.countDown();
                if (firstWriteFailure != null) {
                    throw firstWriteFailure;
                }
                if (blockFirstWrite) {
                    try {
                        releaseFirstWrite.await(10, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("Interrupted while blocking the first write", e);
                    }
                }
            }
            writtenLock.lock();
            try {
                writeThreadNames.add(Thread.currentThread().getName());
                written.writeBytes(buffer.readBytes());
                completedWriteCount.incrementAndGet();
                writeCountChanged.signalAll();
            } finally {
                writtenLock.unlock();
            }
        }
    }
}
