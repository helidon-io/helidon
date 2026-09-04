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

package io.helidon.webclient.api;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

import io.helidon.common.Api;
import io.helidon.common.context.Context;
import io.helidon.common.context.Contexts;
import io.helidon.http.ClientRequestHeaders;
import io.helidon.http.Header;
import io.helidon.http.HeaderName;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.Headers;
import io.helidon.http.HttpMediaType;
import io.helidon.http.WritableHeaders;

/**
 * Bounded preflight for a streaming entity writer.
 * <p>
 * The writer runs once on a dedicated virtual thread against isolated, operation-recording request headers. Preparation
 * returns when the writer first writes, flushes, or closes the stream, after atomically committing the writer's header
 * operations. A transport attaches once and drains a fixed byte ring with downstream backpressure. A writer flush is an
 * acknowledged barrier: the producer resumes only after all preceding bytes have reached the transport and its
 * {@link OutputStream#flush()} has completed.
 * <p>
 * Before attachment this object retains at most {@code bufferCapacity} produced bytes. While attached it additionally
 * uses one reusable transfer array of at most 8192 bytes.
 *
 * @see io.helidon.http.media.EntityWriter
 */
@Api.Internal
public final class EntityWriterPreflight {
    private static final int TRANSFER_CHUNK_SIZE = 8192;

    private final Context context;
    private final BiConsumer<OutputStream, WritableHeaders<?>> writer;
    private final byte[] buffer;
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition stateChanged = lock.newCondition();
    private final Condition hasCapacity = lock.newCondition();
    private final CompletableFuture<Void> whenTerminated = new CompletableFuture<>();

    private int readPosition;
    private int writePosition;
    private int buffered;
    private long produced;
    private long consumed;
    private long flushSequence;
    private boolean flushPending;
    private boolean prepared;
    private boolean committed;
    private boolean attached;
    private boolean producerClosed;
    private boolean producerDone;
    private boolean cancelled;
    private Throwable producerFailure;
    private Throwable cancellationCause;
    private Thread producerThread;
    private HeaderChanges headerChanges = HeaderChanges.empty();

    private EntityWriterPreflight(int bufferCapacity,
                                  Context context,
                                  BiConsumer<OutputStream, WritableHeaders<?>> writer) {
        if (bufferCapacity < 1) {
            throw new IllegalArgumentException("Entity writer preflight buffer capacity must be positive: "
                                                       + bufferCapacity);
        }
        this.buffer = new byte[bufferCapacity];
        this.context = Objects.requireNonNull(context, "context");
        this.writer = Objects.requireNonNull(writer, "writer");
    }

    /**
     * Create a bounded entity-writer preflight.
     *
     * @param bufferCapacity maximum number of queued entity bytes
     * @param context request context propagated to the producer thread
     * @param writer writer callback; the callback has the same close-stream contract as an entity writer
     * @return a new preflight
     */
    public static EntityWriterPreflight create(int bufferCapacity,
                                               Context context,
                                               BiConsumer<OutputStream, WritableHeaders<?>> writer) {
        return new EntityWriterPreflight(bufferCapacity, context, writer);
    }

    /**
     * Create a deep, stable request-header copy, including changing or lazily supplied multi-value headers.
     *
     * @param headers source headers
     * @return stable request-header copy
     */
    public static ClientRequestHeaders copyOf(Headers headers) {
        Objects.requireNonNull(headers, "headers");
        WritableHeaders<?> copy = WritableHeaders.create();
        headers.forEach(header -> copy.set(copyHeader(header)));
        return ClientRequestHeaders.create(copy);
    }

    /**
     * Wrap request headers so mutations are applied locally and captured as immutable writer operations.
     *
     * @param headers headers to mutate
     * @return recording headers
     */
    public static HeaderRecorder record(ClientRequestHeaders headers) {
        return new HeaderRecorder(Objects.requireNonNull(headers, "headers"));
    }

    /**
     * Run the writer to its header commit point and apply the committed operations to the supplied headers.
     *
     * @param requestHeaders service-final request headers
     * @return target-local application that can conditionally remove the writer's effects while preserving later changes
     */
    public Application prepare(ClientRequestHeaders requestHeaders) {
        Objects.requireNonNull(requestHeaders, "requestHeaders");
        HeaderChanges changes = null;
        Throwable failure = null;
        boolean settleWithoutProducer = false;
        lock.lock();
        try {
            if (cancelled) {
                failure = cancellationCause;
            } else if (!prepared) {
                HeaderRecorder isolated = record(copyOf(requestHeaders));
                prepared = true;
                try {
                    producerThread = Thread.ofVirtual()
                            .name("helidon-webclient-entity-writer")
                            .unstarted(() -> produce(isolated));
                    producerThread.start();
                } catch (RuntimeException | Error startFailure) {
                    producerFailure = startFailure;
                    producerDone = true;
                    failure = startFailure;
                    settleWithoutProducer = true;
                }
            }
            while (failure == null && !committed && producerFailure == null && !cancelled) {
                try {
                    stateChanged.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    Thread thread = cancelLocked(e);
                    if (thread != null) {
                        thread.interrupt();
                    }
                    failure = e;
                }
            }
            if (failure == null && !committed) {
                failure = cancelled ? cancellationCause : producerFailure;
            }
            if (failure == null) {
                changes = headerChanges;
            }
        } finally {
            lock.unlock();
        }
        if (settleWithoutProducer) {
            whenTerminated.completeExceptionally(failure);
        }
        if (failure != null) {
            throwUnchecked(failure, "Request entity writer failed before committing headers");
        }
        try {
            return changes.apply(requestHeaders);
        } catch (RuntimeException | Error applyFailure) {
            cancel(applyFailure);
            throw applyFailure;
        }
    }

    /**
     * Immutable writer operations captured at the commit point.
     *
     * @return captured header operations
     */
    public HeaderChanges headerChanges() {
        lock.lock();
        try {
            if (!committed) {
                throw new IllegalStateException("Entity writer headers have not been committed");
            }
            return headerChanges;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Whether the committed one-shot producer remains available for transport attachment.
     *
     * @return whether a transport can attach
     */
    public boolean canAttach() {
        lock.lock();
        try {
            return prepared && committed && !attached && !cancelled && producerFailure == null;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Attach the one permitted transport stream and drain the producer into it.
     *
     * @param outputStream transport output stream
     * @throws IOException when the transport or producer fails with an I/O failure
     */
    public void writeTo(OutputStream outputStream) throws IOException {
        Objects.requireNonNull(outputStream, "outputStream");
        lock.lock();
        try {
            if (!prepared || !committed) {
                throw new IllegalStateException("Request entity writer has not completed preflight");
            }
            if (attached) {
                throw new IllegalStateException("Request entity writer is one-shot and has already been attached");
            }
            if (cancelled) {
                throwTransferFailure(cancellationCause, "Request entity writer was cancelled");
            }
            if (producerFailure != null) {
                throwTransferFailure(producerFailure, "Request entity writer failed");
            }
            attached = true;
            stateChanged.signalAll();
            hasCapacity.signalAll();
        } finally {
            lock.unlock();
        }

        byte[] transferBuffer = new byte[Math.min(TRANSFER_CHUNK_SIZE, buffer.length)];
        try {
            while (true) {
                int length = 0;
                long pendingFlush = -1;
                boolean close = false;
                Throwable failure = null;
                lock.lock();
                try {
                    while (buffered == 0
                            && !(flushPending && consumed >= flushSequence)
                            && producerFailure == null
                            && !producerDone
                            && !cancelled) {
                        try {
                            stateChanged.await();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            Thread thread = cancelLocked(e);
                            if (thread != null) {
                                thread.interrupt();
                            }
                            throw new IOException("Interrupted while sending a request entity", e);
                        }
                    }
                    if (cancelled) {
                        failure = cancellationCause;
                    } else if (producerFailure != null) {
                        failure = producerFailure;
                    } else if (buffered > 0) {
                        long beforeFlush = flushPending ? flushSequence - consumed : Long.MAX_VALUE;
                        if (beforeFlush > 0) {
                            length = (int) Math.min(Math.min(buffered, transferBuffer.length), beforeFlush);
                            int first = Math.min(length, buffer.length - readPosition);
                            System.arraycopy(buffer, readPosition, transferBuffer, 0, first);
                            if (first < length) {
                                System.arraycopy(buffer, 0, transferBuffer, first, length - first);
                            }
                            readPosition = (readPosition + length) % buffer.length;
                            buffered -= length;
                            consumed += length;
                            hasCapacity.signalAll();
                        }
                    }
                    if (failure == null && length == 0 && flushPending && consumed >= flushSequence) {
                        pendingFlush = flushSequence;
                    } else if (failure == null && length == 0 && producerDone) {
                        close = true;
                    }
                } finally {
                    lock.unlock();
                }

                if (failure != null) {
                    throwTransferFailure(failure, "Request entity writer failed");
                }
                if (length > 0) {
                    outputStream.write(transferBuffer, 0, length);
                    continue;
                }
                if (pendingFlush >= 0) {
                    outputStream.flush();
                    lock.lock();
                    try {
                        if (flushPending && flushSequence == pendingFlush) {
                            flushPending = false;
                            stateChanged.signalAll();
                        }
                    } finally {
                        lock.unlock();
                    }
                    continue;
                }
                if (close) {
                    if (!producerClosed) {
                        throw new IllegalStateException("Entity writer returned without closing its output stream");
                    }
                    outputStream.close();
                    return;
                }
            }
        } catch (IOException | RuntimeException | Error transferFailure) {
            cancel(transferFailure);
            throw transferFailure;
        }
    }

    /**
     * Cancel the producer and invalidate any unattached buffered body.
     *
     * @param cause cancellation cause
     */
    public void cancel(Throwable cause) {
        Objects.requireNonNull(cause, "cause");
        Thread thread;
        boolean terminateWithoutProducer;
        lock.lock();
        try {
            boolean producerStarted = prepared;
            thread = cancelLocked(cause);
            terminateWithoutProducer = !producerStarted && cancelled;
        } finally {
            lock.unlock();
        }
        if (terminateWithoutProducer) {
            whenTerminated.completeExceptionally(cause);
        }
        if (thread != null && thread != Thread.currentThread()) {
            thread.interrupt();
        }
    }

    /**
     * Cancel the producer.
     */
    public void cancel() {
        cancel(new CancellationException("Request entity writer was cancelled"));
    }

    /**
     * Cancel only if no transport has attached. This is used when request preparation, policy, or a response
     * short-circuits an attempt before body dispatch.
     *
     * @param cause cancellation cause
     */
    public void cancelIfUnattached(Throwable cause) {
        Objects.requireNonNull(cause, "cause");
        Thread thread = null;
        boolean terminateWithoutProducer = false;
        lock.lock();
        try {
            if (!attached) {
                boolean producerStarted = prepared;
                thread = cancelLocked(cause);
                terminateWithoutProducer = !producerStarted && cancelled;
            }
        } finally {
            lock.unlock();
        }
        if (terminateWithoutProducer) {
            whenTerminated.completeExceptionally(cause);
        }
        if (thread != null && thread != Thread.currentThread()) {
            thread.interrupt();
        }
    }

    /**
     * Completion reporting the exact producer termination.
     *
     * @return producer termination completion
     */
    public CompletableFuture<Void> whenTerminated() {
        return whenTerminated.copy();
    }

    private void produce(HeaderRecorder isolated) {
        ProducerOutputStream output = new ProducerOutputStream(isolated);
        Throwable failure = null;
        try {
            Contexts.runInContext(context, () -> writer.accept(output, isolated));
            if (!output.closed()) {
                throw new IllegalStateException("Entity writer returned without closing its output stream");
            }
        } catch (Throwable t) {
            failure = t;
        } finally {
            lock.lock();
            try {
                producerDone = true;
                if (!cancelled && failure != null) {
                    producerFailure = failure;
                }
                stateChanged.signalAll();
                hasCapacity.signalAll();
            } finally {
                lock.unlock();
            }
            if (cancelled) {
                whenTerminated.completeExceptionally(cancellationCause);
            } else if (failure == null) {
                whenTerminated.complete(null);
            } else {
                whenTerminated.completeExceptionally(failure);
            }
        }
    }

    private Thread cancelLocked(Throwable cause) {
        if (!cancelled) {
            cancelled = true;
            cancellationCause = cause;
            buffered = 0;
            readPosition = writePosition;
            if (!prepared) {
                producerDone = true;
            }
            stateChanged.signalAll();
            hasCapacity.signalAll();
        }
        return producerDone ? null : producerThread;
    }

    private void commitHeaders(HeaderRecorder isolated) throws IOException {
        if (cancelled) {
            throw cancellationIOException();
        }
        if (!committed) {
            headerChanges = isolated.commit();
            committed = true;
            stateChanged.signalAll();
        }
    }

    private IOException cancellationIOException() {
        return new IOException("Request entity writer was cancelled", cancellationCause);
    }

    private static Header copyHeader(Header header) {
        return HeaderValues.create(header.headerName(),
                                   header.changing(),
                                   header.sensitive(),
                                   header.allValues().toArray(String[]::new));
    }

    private static void throwUnchecked(Throwable failure, String message) {
        if (failure instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        if (failure instanceof Error error) {
            throw error;
        }
        if (failure instanceof IOException ioException) {
            throw new UncheckedIOException(message, ioException);
        }
        throw new IllegalStateException(message, failure);
    }

    private static void throwTransferFailure(Throwable failure, String message) throws IOException {
        if (failure instanceof IOException ioException) {
            throw ioException;
        }
        if (failure instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        if (failure instanceof Error error) {
            throw error;
        }
        throw new IOException(message, failure);
    }

    private final class ProducerOutputStream extends OutputStream {
        private final HeaderRecorder isolated;
        private boolean closed;

        private ProducerOutputStream(HeaderRecorder isolated) {
            this.isolated = isolated;
        }

        @Override
        public void write(int value) throws IOException {
            lock.lock();
            try {
                ensureOpen();
                commitHeaders(isolated);
                while (buffered == buffer.length && !cancelled) {
                    try {
                        hasCapacity.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IOException("Interrupted while producing a request entity", e);
                    }
                }
                if (cancelled) {
                    throw cancellationIOException();
                }
                buffer[writePosition] = (byte) value;
                writePosition = (writePosition + 1) % buffer.length;
                buffered++;
                produced++;
                stateChanged.signalAll();
            } finally {
                lock.unlock();
            }
        }

        @Override
        public void write(byte[] bytes, int offset, int length) throws IOException {
            Objects.checkFromIndexSize(offset, length, bytes.length);
            if (length == 0) {
                return;
            }
            lock.lock();
            try {
                ensureOpen();
                commitHeaders(isolated);
                int remaining = length;
                int sourceOffset = offset;
                while (remaining > 0) {
                    while (buffered == buffer.length && !cancelled) {
                        try {
                            hasCapacity.await();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new IOException("Interrupted while producing a request entity", e);
                        }
                    }
                    if (cancelled) {
                        throw cancellationIOException();
                    }
                    int copied = Math.min(remaining, buffer.length - buffered);
                    int first = Math.min(copied, buffer.length - writePosition);
                    System.arraycopy(bytes, sourceOffset, buffer, writePosition, first);
                    if (first < copied) {
                        System.arraycopy(bytes, sourceOffset + first, buffer, 0, copied - first);
                    }
                    writePosition = (writePosition + copied) % buffer.length;
                    buffered += copied;
                    produced += copied;
                    sourceOffset += copied;
                    remaining -= copied;
                    stateChanged.signalAll();
                }
            } finally {
                lock.unlock();
            }
        }

        @Override
        public void flush() throws IOException {
            lock.lock();
            try {
                ensureOpen();
                commitHeaders(isolated);
                flushPending = true;
                flushSequence = produced;
                stateChanged.signalAll();
                while (flushPending && !cancelled) {
                    try {
                        stateChanged.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IOException("Interrupted while flushing a request entity", e);
                    }
                }
                if (cancelled) {
                    throw cancellationIOException();
                }
            } finally {
                lock.unlock();
            }
        }

        @Override
        public void close() throws IOException {
            lock.lock();
            try {
                if (closed) {
                    return;
                }
                commitHeaders(isolated);
                closed = true;
                producerClosed = true;
                stateChanged.signalAll();
            } finally {
                lock.unlock();
            }
        }

        private boolean closed() {
            lock.lock();
            try {
                return closed;
            } finally {
                lock.unlock();
            }
        }

        private void ensureOpen() throws IOException {
            if (closed) {
                throw new IOException("Request entity writer output stream is closed");
            }
            if (cancelled) {
                throw cancellationIOException();
            }
        }
    }

    /**
     * Request headers that capture writer operations while applying them to an isolated delegate.
     */
    @Api.Internal
    public static final class HeaderRecorder implements ClientRequestHeaders {
        private final ClientRequestHeaders delegate;
        private final List<HeaderOperation> operations = new ArrayList<>();
        private final List<AppliedMutation> appliedMutations = new ArrayList<>();
        private boolean committed;

        private HeaderRecorder(ClientRequestHeaders delegate) {
            this.delegate = delegate;
        }

        /**
         * Immutable operations recorded so far.
         *
         * @return recorded operations
         */
        public HeaderChanges changes() {
            return operations.isEmpty() ? HeaderChanges.empty() : new HeaderChanges(List.copyOf(operations));
        }

        private HeaderChanges commit() {
            committed = true;
            return changes();
        }

        /**
         * Application representing the mutations performed through this recorder so far.
         *
         * @return target-local application
         */
        public Application application() {
            return new Application(List.copyOf(appliedMutations));
        }

        @Override
        public ClientRequestHeaders setIfAbsent(Header header) {
            ensureMutable();
            Header stable = copyHeader(header);
            if (!stable.headerName().equals(HeaderNames.CONTENT_LENGTH)) {
                HeaderOperation operation = new SetIfAbsentOperation(stable);
                operations.add(operation);
                operation.apply(delegate, appliedMutations);
            } else {
                delegate.setIfAbsent(stable);
            }
            return this;
        }

        @Override
        public ClientRequestHeaders add(Header header) {
            ensureMutable();
            Header stable = copyHeader(header);
            if (!stable.headerName().equals(HeaderNames.CONTENT_LENGTH)) {
                HeaderOperation operation = new AddOperation(stable);
                operations.add(operation);
                operation.apply(delegate, appliedMutations);
            } else {
                delegate.add(stable);
            }
            return this;
        }

        @Override
        public ClientRequestHeaders remove(HeaderName name) {
            ensureMutable();
            if (!name.equals(HeaderNames.CONTENT_LENGTH)) {
                HeaderOperation operation = new RemoveOperation(name);
                operations.add(operation);
                operation.apply(delegate, appliedMutations);
            } else {
                delegate.remove(name);
            }
            return this;
        }

        @Override
        public ClientRequestHeaders remove(HeaderName name, Consumer<Header> removedConsumer) {
            ensureMutable();
            Objects.requireNonNull(removedConsumer, "removedConsumer");
            Header removed = delegate.contains(name) ? copyHeader(delegate.get(name)) : null;
            if (!name.equals(HeaderNames.CONTENT_LENGTH)) {
                operations.add(new RemoveOperation(name));
            }
            delegate.remove(name, removedConsumer);
            if (removed != null && !name.equals(HeaderNames.CONTENT_LENGTH)) {
                appliedMutations.add(new AppliedMutation(name, removed, null));
            }
            return this;
        }

        @Override
        public ClientRequestHeaders set(Header header) {
            ensureMutable();
            Header stable = copyHeader(header);
            if (!stable.headerName().equals(HeaderNames.CONTENT_LENGTH)) {
                HeaderOperation operation = new SetOperation(stable);
                operations.add(operation);
                operation.apply(delegate, appliedMutations);
            } else {
                delegate.set(stable);
            }
            return this;
        }

        @Override
        public ClientRequestHeaders clear() {
            ensureMutable();
            operations.add(ClearOperation.INSTANCE);
            delegate.forEach(header -> {
                if (!header.headerName().equals(HeaderNames.CONTENT_LENGTH)) {
                    appliedMutations.add(new AppliedMutation(header.headerName(), copyHeader(header), null));
                }
            });
            delegate.clear();
            return this;
        }

        @Override
        public ClientRequestHeaders from(Headers headers) {
            ensureMutable();
            Objects.requireNonNull(headers, "headers");
            headers.forEach(this::set);
            return this;
        }

        @Override
        public List<String> all(HeaderName name, Supplier<List<String>> defaultSupplier) {
            return delegate.all(name, defaultSupplier);
        }

        @Override
        public boolean contains(HeaderName name) {
            return delegate.contains(name);
        }

        @Override
        public boolean contains(Header header) {
            return delegate.contains(header);
        }

        @Override
        public Header get(HeaderName name) {
            return delegate.get(name);
        }

        @Override
        public int size() {
            return delegate.size();
        }

        @Override
        public List<HttpMediaType> acceptedTypes() {
            return delegate.acceptedTypes();
        }

        @Override
        public Iterator<Header> iterator() {
            return delegate.iterator();
        }

        private void ensureMutable() {
            if (committed) {
                throw new IllegalStateException("Entity writer headers have already been committed");
            }
        }
    }

    /**
     * Immutable entity-writer header operations.
     */
    @Api.Internal
    public static final class HeaderChanges {
        private static final HeaderChanges EMPTY = new HeaderChanges(List.of());

        private final List<HeaderOperation> operations;

        private HeaderChanges(List<HeaderOperation> operations) {
            this.operations = operations;
        }

        /**
         * Whether no operations were captured.
         *
         * @return whether this change set is empty
         */
        public boolean isEmpty() {
            return operations.isEmpty();
        }

        /**
         * Apply writer operations once and capture enough target-local state to conditionally remove their effects.
         *
         * @param headers target headers
         * @return target-local application
         */
        public Application apply(ClientRequestHeaders headers) {
            Objects.requireNonNull(headers, "headers");
            List<AppliedMutation> appliedMutations = new ArrayList<>();
            try {
                operations.forEach(operation -> operation.apply(headers, appliedMutations));
                Application application = new Application(List.copyOf(appliedMutations));
                return application;
            } catch (RuntimeException | Error failure) {
                try {
                    new Application(List.copyOf(appliedMutations)).rollback(headers);
                } catch (RuntimeException | Error rollbackFailure) {
                    if (failure != rollbackFailure) {
                        failure.addSuppressed(rollbackFailure);
                    }
                }
                throw failure;
            }
        }

        /**
         * Compose these operations with operations that run after them.
         *
         * @param after later operations
         * @return composed operations
         */
        public HeaderChanges andThen(HeaderChanges after) {
            Objects.requireNonNull(after, "after");
            if (operations.isEmpty()) {
                return after;
            }
            if (after.operations.isEmpty()) {
                return this;
            }
            List<HeaderOperation> combined = new ArrayList<>(operations.size() + after.operations.size());
            combined.addAll(operations);
            combined.addAll(after.operations);
            return new HeaderChanges(List.copyOf(combined));
        }

        /**
         * Empty writer-operation set.
         *
         * @return empty operations
         */
        public static HeaderChanges empty() {
            return EMPTY;
        }
    }

    /**
     * A single header-operation application with target-local, conditional inverse operations.
     */
    @Api.Internal
    public static final class Application {
        private final List<AppliedMutation> appliedMutations;
        private final AtomicBoolean rolledBack = new AtomicBoolean();

        private Application(List<AppliedMutation> appliedMutations) {
            this.appliedMutations = appliedMutations;
        }

        /**
         * Compose this application with a later application.
         *
         * @param after later application
         * @return combined application
         */
        public Application andThen(Application after) {
            Objects.requireNonNull(after, "after");
            List<AppliedMutation> combined = new ArrayList<>(appliedMutations.size()
                                                                    + after.appliedMutations.size());
            combined.addAll(appliedMutations);
            combined.addAll(after.appliedMutations);
            return new Application(List.copyOf(combined));
        }

        /**
         * Conditionally remove this application's effects while preserving later, differing mutations. A later replacement
         * with the same value is inherently indistinguishable from this application and is therefore removed. This
         * application can be rolled back only once.
         *
         * @param headers target headers or a copy containing this application
         */
        public void rollback(ClientRequestHeaders headers) {
            Objects.requireNonNull(headers, "headers");
            if (!rolledBack.compareAndSet(false, true)) {
                throw new IllegalStateException("Entity writer header application has already been rolled back");
            }
            for (int i = appliedMutations.size() - 1; i >= 0; i--) {
                appliedMutations.get(i).rollback(headers);
            }
        }
    }

    private interface HeaderOperation {
        void apply(ClientRequestHeaders headers, List<AppliedMutation> appliedMutations);
    }

    private record SetIfAbsentOperation(Header header) implements HeaderOperation {
        @Override
        public void apply(ClientRequestHeaders headers, List<AppliedMutation> appliedMutations) {
            Header before = current(headers, header.headerName());
            headers.setIfAbsent(header);
            Header after = current(headers, header.headerName());
            if (!Objects.equals(before, after)) {
                appliedMutations.add(new AppliedMutation(header.headerName(), before, after));
            }
        }
    }

    private record AddOperation(Header header) implements HeaderOperation {
        @Override
        public void apply(ClientRequestHeaders headers, List<AppliedMutation> appliedMutations) {
            Header before = current(headers, header.headerName());
            headers.add(header);
            Header after = current(headers, header.headerName());
            if (!Objects.equals(before, after)) {
                appliedMutations.add(new AppliedMutation(header.headerName(), before, after));
            }
        }
    }

    private record RemoveOperation(HeaderName name) implements HeaderOperation {
        @Override
        public void apply(ClientRequestHeaders headers, List<AppliedMutation> appliedMutations) {
            Header before = current(headers, name);
            headers.remove(name);
            if (before != null) {
                appliedMutations.add(new AppliedMutation(name, before, null));
            }
        }
    }

    private record SetOperation(Header header) implements HeaderOperation {
        @Override
        public void apply(ClientRequestHeaders headers, List<AppliedMutation> appliedMutations) {
            Header before = current(headers, header.headerName());
            headers.set(header);
            Header after = current(headers, header.headerName());
            if (!Objects.equals(before, after)) {
                appliedMutations.add(new AppliedMutation(header.headerName(), before, after));
            }
        }
    }

    private enum ClearOperation implements HeaderOperation {
        INSTANCE;

        @Override
        public void apply(ClientRequestHeaders headers, List<AppliedMutation> appliedMutations) {
            List<Header> removed = new ArrayList<>();
            headers.forEach(header -> {
                if (!header.headerName().equals(HeaderNames.CONTENT_LENGTH)) {
                    removed.add(copyHeader(header));
                }
            });
            removed.forEach(header -> {
                headers.remove(header.headerName());
                appliedMutations.add(new AppliedMutation(header.headerName(), header, null));
            });
        }
    }

    private record AppliedMutation(HeaderName name, Header before, Header after) {
        private void rollback(ClientRequestHeaders headers) {
            Header current = current(headers, name);
            if (after == null) {
                if (current == null && before != null) {
                    headers.set(before);
                }
                return;
            }
            List<String> beforeValues = values(name, before);
            List<String> afterValues = values(name, after);
            List<String> currentValues = values(name, current);
            if (!startsWith(currentValues, afterValues)) {
                return;
            }
            List<String> restored = new ArrayList<>(beforeValues.size() + currentValues.size() - afterValues.size());
            restored.addAll(beforeValues);
            restored.addAll(currentValues.subList(afterValues.size(), currentValues.size()));
            if (restored.isEmpty()) {
                headers.remove(name);
                return;
            }
            Header template = restored.size() == beforeValues.size() && before != null ? before : current;
            String[] values = name.equals(HeaderNames.COOKIE)
                    ? new String[] {String.join("; ", restored)}
                    : restored.toArray(String[]::new);
            headers.set(HeaderValues.create(name,
                                            template != null && template.changing(),
                                            template != null && template.sensitive(),
                                            values));
        }

        private static List<String> values(HeaderName name, Header header) {
            if (header == null) {
                return List.of();
            }
            if (!name.equals(HeaderNames.COOKIE)) {
                return List.copyOf(header.allValues());
            }
            List<String> pairs = new ArrayList<>();
            for (String value : header.allValues()) {
                for (String pair : value.split(";", -1)) {
                    pair = pair.trim();
                    if (!pair.isEmpty()) {
                        pairs.add(pair);
                    }
                }
            }
            return pairs;
        }

        private static boolean startsWith(List<String> values, List<String> prefix) {
            if (values.size() < prefix.size()) {
                return false;
            }
            for (int i = 0; i < prefix.size(); i++) {
                if (!values.get(i).equals(prefix.get(i))) {
                    return false;
                }
            }
            return true;
        }
    }

    private static Header current(ClientRequestHeaders headers, HeaderName name) {
        return headers.contains(name) ? copyHeader(headers.get(name)) : null;
    }
}
