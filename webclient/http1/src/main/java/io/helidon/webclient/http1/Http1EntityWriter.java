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

package io.helidon.webclient.http1;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiConsumer;

import io.helidon.common.context.Context;
import io.helidon.common.context.Contexts;
import io.helidon.http.ClientRequestHeaders;

/**
 * Prepares a streamed entity writer without sending its body before the HTTP handshake.
 * The request thread owns this helper; the producer owns its output stream and one bounded buffer.
 */
final class Http1EntityWriter implements AutoCloseable {
    private static final int BUFFER_SIZE = 8192;
    private static final int INITIAL_BUFFER_SIZE = 256;
    private static final int MAX_BULK_WRITE_SIZE = 64 * 1024;

    private final BiConsumer<OutputStream, ClientRequestHeaders> writer;
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition changed = lock.newCondition();

    private Thread producer;
    private Operation pending;
    private Throwable producerFailure;
    private volatile Throwable stopFailure;
    private boolean prepared;
    private boolean hasEntity;
    private boolean authorized;
    private boolean done;
    private boolean transferFinished;
    private boolean failureReported;
    private boolean closed;

    Http1EntityWriter(BiConsumer<OutputStream, ClientRequestHeaders> writer) {
        this.writer = Objects.requireNonNull(writer);
    }

    boolean canWriteInline() {
        return !closed && (producer == null || transferFinished);
    }

    void writeInline(OutputStream output, ClientRequestHeaders headers) {
        ensureOpen();
        Objects.requireNonNull(output);
        Objects.requireNonNull(headers);
        if (!canWriteInline()) {
            throw new IllegalStateException("Cannot run entity writer inline while its producer is active");
        }
        joinProducer();
        producer = null;
        pending = null;
        producerFailure = null;
        stopFailure = null;
        prepared = false;
        hasEntity = false;
        authorized = false;
        done = false;
        transferFinished = false;
        failureReported = false;
        writer.accept(output, headers);
    }

    boolean prepare(ClientRequestHeaders headers, Context context) throws IOException {
        ensureOpen();
        if (transferFinished) {
            joinProducer();
            producer = null;
        }
        try {
            if (producer == null) {
                start(Objects.requireNonNull(headers), Objects.requireNonNull(context));
            }
            lock.lock();
            try {
                while (!prepared && !done) {
                    awaitChange();
                }
                if (producerFailure != null) {
                    throw propagate(producerFailure);
                }
                return hasEntity;
            } finally {
                lock.unlock();
            }
        } catch (IOException | RuntimeException | Error failure) {
            stop(failure);
            joinProducer();
            throw propagate(completionFailure(failure));
        }
    }

    void writeTo(OutputStream output) throws IOException {
        ensureOpen();
        Objects.requireNonNull(output);
        lock.lock();
        try {
            if (!prepared || authorized) {
                throw new IllegalStateException("Entity writer must be prepared before each transfer");
            }
            authorized = true;
            changed.signalAll();
        } finally {
            lock.unlock();
        }

        Throwable failure = null;
        try {
            Operation operation;
            while ((operation = awaitOperation()) != null) {
                try {
                    switch (operation.type) {
                    case WRITE -> output.write(operation.data, operation.offset, operation.length);
                    case FLUSH -> output.flush();
                    case CLOSE -> output.close();
                    default -> throw new IllegalStateException("Unsupported entity writer operation: " + operation.type);
                    }
                } catch (IOException | RuntimeException | Error operationFailure) {
                    acknowledge(operation, operationFailure);
                    throw operationFailure;
                }
                acknowledge(operation, null);
            }
        } catch (IOException | RuntimeException | Error transferFailure) {
            failure = transferFailure;
            stop(transferFailure);
        } finally {
            joinProducer();
            transferFinished = true;
        }
        failure = completionFailure(failure);
        if (failure != null) {
            throw propagate(failure);
        }
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        if (producer == null) {
            return;
        }
        stop(new WriterCancelledException());
        joinProducer();
        Throwable failure = completionFailure(null);
        if (failure != null) {
            try {
                throw propagate(failure);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    private static IOException propagate(Throwable failure) {
        return switch (failure) {
        case IOException e -> e;
        case RuntimeException e -> throw e;
        case Error e -> throw e;
        default -> throw new IllegalStateException("Entity writer failed", failure);
        };
    }

    private static boolean causedBy(Throwable failure, Throwable expected) {
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Throwable current = failure; current != null && visited.add(current); current = current.getCause()) {
            if (current == expected) {
                return true;
            }
        }
        return false;
    }

    private static Throwable independentFailure(Throwable failure, Throwable expected) {
        return independentFailure(failure, expected, Collections.newSetFromMap(new IdentityHashMap<>()));
    }

    private static Throwable independentFailure(Throwable failure, Throwable expected, Set<Throwable> visited) {
        if (failure == null || !visited.add(failure)) {
            return null;
        }
        if (!causedBy(failure, expected)) {
            return failure;
        }
        Throwable result = null;
        Set<Throwable> causes = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Throwable current = failure; current != null && causes.add(current); current = current.getCause()) {
            for (Throwable suppressed : current.getSuppressed()) {
                result = combine(result, independentFailure(suppressed, expected, visited));
            }
            if (current == expected) {
                break;
            }
        }
        return result;
    }

    private static Throwable combine(Throwable primary, Throwable secondary) {
        if (primary == null) {
            return secondary;
        }
        if (secondary == null || causedBy(primary, secondary) || causedBy(secondary, primary)) {
            return primary;
        }
        for (Throwable suppressed : primary.getSuppressed()) {
            if (suppressed == secondary) {
                return primary;
            }
        }
        primary.addSuppressed(secondary);
        return primary;
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("Entity writer is closed");
        }
    }

    private void start(ClientRequestHeaders headers, Context context) {
        pending = null;
        producerFailure = null;
        stopFailure = null;
        prepared = false;
        hasEntity = false;
        authorized = false;
        done = false;
        transferFinished = false;
        failureReported = false;
        WriterStream stream = new WriterStream();
        ExecutorService executor = Executors.newThreadPerTaskExecutor(task -> {
            producer = Thread.ofVirtual()
                    .name("helidon-http1-entity-writer")
                    .unstarted(() -> runProducer(task));
            return producer;
        });
        try {
            ExecutorService contextualExecutor = Contexts.wrap(executor);
            Contexts.runInContext(context, () -> contextualExecutor.execute(() -> {
                try {
                    writer.accept(stream, headers);
                    if (!stream.streamClosed && stopFailure == null) {
                        throw new IllegalStateException("Output stream was not closed in entity writer");
                    }
                } catch (RuntimeException | Error failure) {
                    lock.lock();
                    try {
                        producerFailure = failure;
                    } finally {
                        lock.unlock();
                    }
                    throw failure;
                }
            }));
        } finally {
            // One submission only; the actual producer thread remains owned until it is joined.
            executor.shutdown();
        }
    }

    private void runProducer(Runnable task) {
        Throwable failure = null;
        try {
            task.run();
        } catch (Throwable e) {
            failure = e;
        } finally {
            lock.lock();
            try {
                // Include failures from propagation setup/cleanup, which wrap the writer invocation.
                producerFailure = combine(producerFailure, failure);
                done = true;
                changed.signalAll();
            } finally {
                lock.unlock();
            }
        }
    }

    private void awaitChange() throws IOException {
        try {
            changed.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (stopFailure != null) {
                throw propagate(stopFailure);
            }
            InterruptedIOException failure = new InterruptedIOException("Interrupted while waiting for entity writer");
            failure.initCause(e);
            throw failure;
        }
    }

    private void authorize(boolean entity) throws IOException {
        lock.lock();
        try {
            checkStopped();
            prepared = true;
            hasEntity = entity;
            changed.signalAll();
            while (!authorized) {
                checkStopped();
                awaitChange();
            }
            checkStopped();
        } catch (IOException | RuntimeException | Error failure) {
            if (stopFailure == null) {
                stopFailure = failure;
            }
            changed.signalAll();
            throw failure;
        } finally {
            lock.unlock();
        }
    }

    private Operation awaitOperation() throws IOException {
        lock.lock();
        try {
            while (pending == null && !done) {
                awaitChange();
            }
            return pending;
        } finally {
            lock.unlock();
        }
    }

    private void submit(Operation operation) throws IOException {
        lock.lock();
        try {
            checkStopped();
            pending = operation;
            changed.signalAll();
            while (!operation.completed) {
                checkStopped();
                awaitChange();
            }
            if (operation.failure != null) {
                throw propagate(operation.failure);
            }
        } catch (IOException | RuntimeException | Error failure) {
            if (stopFailure == null) {
                stopFailure = failure;
            }
            changed.signalAll();
            throw failure;
        } finally {
            lock.unlock();
        }
    }

    private void acknowledge(Operation operation, Throwable failure) {
        lock.lock();
        try {
            operation.failure = failure;
            operation.completed = true;
            pending = null;
            if (failure != null && stopFailure == null) {
                stopFailure = failure;
            }
            changed.signalAll();
        } finally {
            lock.unlock();
        }
    }

    private void checkStopped() throws IOException {
        if (stopFailure != null) {
            throw propagate(stopFailure);
        }
    }

    private void stop(Throwable failure) {
        lock.lock();
        try {
            if (done) {
                return;
            }
            if (stopFailure == null) {
                stopFailure = failure;
            }
            changed.signalAll();
        } finally {
            lock.unlock();
        }
        if (producer != null) {
            producer.interrupt();
        }
    }

    private void joinProducer() {
        if (producer == null) {
            return;
        }
        boolean interrupted = false;
        while (producer.isAlive()) {
            try {
                producer.join();
            } catch (InterruptedException _) {
                interrupted = true;
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private Throwable completionFailure(Throwable primary) {
        if (failureReported) {
            return primary;
        }
        failureReported = true;
        Throwable failure = producerFailure;
        if (stopFailure instanceof WriterCancelledException) {
            failure = independentFailure(failure, stopFailure);
        }
        if (primary != null) {
            failure = independentFailure(failure, primary);
        }
        return combine(primary, failure);
    }

    private enum OperationType {
        WRITE,
        FLUSH,
        CLOSE
    }

    private static final class Operation {
        private final OperationType type;
        private final byte[] data;
        private final int offset;
        private final int length;

        private boolean completed;
        private Throwable failure;

        private Operation(OperationType type, byte[] data, int offset, int length) {
            this.type = type;
            this.data = data;
            this.offset = offset;
            this.length = length;
        }
    }

    private static final class WriterCancelledException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        private WriterCancelledException() {
            super("Entity writer cancelled", null, true, false);
        }
    }

    private final class WriterStream extends OutputStream {
        private byte[] buffer;
        private int buffered;
        private boolean gatePassed;
        private boolean streamClosed;

        @Override
        public void write(int value) throws IOException {
            checkOpen();
            passGate(true);
            if (buffered == BUFFER_SIZE) {
                flushBuffer();
            }
            ensureBufferCapacity(buffered + 1);
            buffer[buffered++] = (byte) value;
        }

        @Override
        public void write(byte[] data, int offset, int length) throws IOException {
            Objects.checkFromIndexSize(offset, length, data.length);
            checkOpen();
            if (length == 0) {
                return;
            }
            passGate(true);
            while (length > 0) {
                if (length >= BUFFER_SIZE) {
                    flushBuffer();
                    // Borrow the caller's array until the request thread acknowledges this bounded bulk write.
                    int chunkLength = Math.min(length, MAX_BULK_WRITE_SIZE);
                    submit(new Operation(OperationType.WRITE, data, offset, chunkLength));
                    offset += chunkLength;
                    length -= chunkLength;
                } else {
                    int chunkLength = Math.min(length, BUFFER_SIZE - buffered);
                    ensureBufferCapacity(buffered + chunkLength);
                    System.arraycopy(data, offset, buffer, buffered, chunkLength);
                    buffered += chunkLength;
                    offset += chunkLength;
                    length -= chunkLength;
                    if (buffered == BUFFER_SIZE) {
                        flushBuffer();
                    }
                }
            }
        }

        @Override
        public void flush() throws IOException {
            checkOpen();
            if (gatePassed) {
                flushBuffer();
                submit(new Operation(OperationType.FLUSH, null, 0, 0));
            }
        }

        @Override
        public void close() throws IOException {
            if (streamClosed) {
                return;
            }
            try {
                if (stopFailure == null) {
                    passGate(false);
                    flushBuffer();
                    submit(new Operation(OperationType.CLOSE, null, 0, 0));
                }
            } finally {
                streamClosed = true;
                buffered = 0;
            }
        }

        private void checkOpen() throws IOException {
            if (streamClosed) {
                throw new IOException("Entity writer output stream is closed");
            }
            checkStopped();
        }

        private void passGate(boolean entity) throws IOException {
            if (!gatePassed) {
                authorize(entity);
                gatePassed = true;
            }
        }

        private void flushBuffer() throws IOException {
            if (buffered != 0) {
                submit(new Operation(OperationType.WRITE, buffer, 0, buffered));
                buffered = 0;
            }
        }

        private void ensureBufferCapacity(int capacity) {
            if (buffer == null) {
                buffer = new byte[capacity <= INITIAL_BUFFER_SIZE ? INITIAL_BUFFER_SIZE : BUFFER_SIZE];
            } else if (capacity > buffer.length) {
                // Grow at most once, without turning a full small buffer into an early transport write.
                buffer = Arrays.copyOf(buffer, BUFFER_SIZE);
            }
        }
    }
}
