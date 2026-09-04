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

package io.helidon.webserver.http3;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

/**
 * Isolates virtual request-worker return from HTTP/3 terminal dispatch finalization.
 *
 * <p>This is a mechanism benchmark rather than a full {@code Http3ServerStream} exchange. It measures the virtual
 * request worker that observes the terminal receipt and returns. The completed-receipt cases include the inline
 * fast path. The blocking baseline includes delayed dispatch handoff, virtual-thread park/wake, finalization, and worker
 * return. The continuation cases deliberately delay dispatch until after the measured worker returns, submit lifecycle
 * finalization to a virtual-thread executor, then drain it during invocation teardown outside the measured worker-return
 * interval.</p>
 *
 * <p>The continuation results therefore isolate request-worker occupancy rather than full-server throughput. Use the
 * full HTTP/3 HttpArena workload for end-to-end throughput evidence. Trial teardown rejects leaked or duplicate
 * lifecycle completion.</p>
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class Http3TerminalDispatchCompletionJmhBenchmark {
    private static final int CONCURRENT_WORKERS = 8;

    /**
     * Measures one virtual request worker with an already-completed terminal dispatch receipt.
     *
     * @param state shared dispatch state
     * @param invocation per-worker lifecycle guard
     * @return completed lifecycle sequence
     */
    @Benchmark
    @Threads(1)
    public long completedReceipt(DispatchState state, InvocationState invocation) {
        return state.runCompleted(invocation);
    }

    /**
     * Measures concurrent virtual request workers with already-completed terminal dispatch receipts.
     *
     * @param state shared dispatch state
     * @param invocation per-worker lifecycle guard
     * @return completed lifecycle sequence
     */
    @Benchmark
    @Threads(CONCURRENT_WORKERS)
    public long completedReceiptConcurrent(DispatchState state, InvocationState invocation) {
        return state.runCompleted(invocation);
    }

    /**
     * Measures the old behavior with one virtual request worker blocked on a delayed terminal dispatch receipt.
     *
     * @param state shared dispatch state
     * @param invocation per-worker lifecycle guard
     * @return completed lifecycle sequence
     */
    @Benchmark
    @Threads(1)
    public long delayedReceiptBlockingBaseline(DispatchState state, InvocationState invocation) {
        return state.runBlocking(invocation);
    }

    /**
     * Measures the old behavior with concurrent virtual request workers blocked on delayed terminal dispatch receipts.
     *
     * @param state shared dispatch state
     * @param invocation per-worker lifecycle guard
     * @return completed lifecycle sequence
     */
    @Benchmark
    @Threads(CONCURRENT_WORKERS)
    public long delayedReceiptBlockingBaselineConcurrent(DispatchState state, InvocationState invocation) {
        return state.runBlocking(invocation);
    }

    /**
     * Measures one virtual request worker returning before delayed terminal dispatch continuation finalization.
     *
     * @param state shared dispatch state
     * @param invocation per-worker lifecycle guard
     * @return submitted lifecycle sequence
    */
    @Benchmark
    @Threads(1)
    public long delayedReceiptContinuation(DispatchState state, InvocationState invocation) {
        return state.runContinuation(invocation);
    }

    /**
     * Measures concurrent virtual request workers returning before delayed terminal dispatch continuation finalization.
     *
     * @param state shared dispatch state
     * @param invocation per-worker lifecycle guard
     * @return submitted lifecycle sequence
    */
    @Benchmark
    @Threads(CONCURRENT_WORKERS)
    public long delayedReceiptContinuationConcurrent(DispatchState state, InvocationState invocation) {
        return state.runContinuation(invocation);
    }

    /**
     * Shared bounded dispatcher and lifecycle accounting.
     */
    @State(Scope.Benchmark)
    public static class DispatchState {
        private static final int QUEUE_CAPACITY = CONCURRENT_WORKERS * 2;
        private static final long AWAIT_SECONDS = 5;

        private final ArrayBlockingQueue<Operation> dispatchQueue = new ArrayBlockingQueue<>(QUEUE_CAPACITY);
        private final AtomicLong sequence = new AtomicLong();
        private final AtomicLong submitted = new AtomicLong();
        private final AtomicLong finalized = new AtomicLong();
        private final AtomicInteger outstanding = new AtomicInteger();
        private final AtomicInteger maximumOutstanding = new AtomicInteger();
        private final AtomicReference<Throwable> dispatcherFailure = new AtomicReference<>();
        private final AtomicBoolean stopping = new AtomicBoolean();
        private ExecutorService completionExecutor;
        private Thread dispatcher;

        /**
         * Starts the bounded receipt dispatcher.
         */
        @Setup(Level.Trial)
        public void setup() {
            completionExecutor = Executors.newThreadPerTaskExecutor(Thread.ofVirtual()
                                                                           .name("http3-terminal-completion-jmh-", 0)
                                                                           .factory());
            dispatcher = Thread.ofPlatform()
                    .name("http3-terminal-dispatch-jmh")
                    .daemon()
                    .start(this::dispatchLoop);
        }

        /**
         * Stops the dispatcher and verifies that measured invocations did not accumulate unfinished lifecycle work.
         *
         * @throws InterruptedException if interrupted while stopping the dispatcher
         */
        @TearDown(Level.Trial)
        public void tearDown() throws InterruptedException {
            stopping.set(true);
            dispatcher.interrupt();
            dispatcher.join(TimeUnit.SECONDS.toMillis(AWAIT_SECONDS));
            if (dispatcher.isAlive()) {
                throw new IllegalStateException("Terminal dispatch benchmark dispatcher did not stop");
            }
            Throwable failure = dispatcherFailure.get();
            if (failure != null) {
                throw new IllegalStateException("Terminal dispatch benchmark dispatcher failed", failure);
            }
            if (!dispatchQueue.isEmpty()) {
                throw new IllegalStateException("Terminal dispatch benchmark retained " + dispatchQueue.size()
                                                        + " queued receipts");
            }
            long submittedCount = submitted.get();
            long finalizedCount = finalized.get();
            if (submittedCount != finalizedCount || outstanding.get() != 0) {
                throw new IllegalStateException("Terminal dispatch lifecycle accumulation: submitted="
                                                        + submittedCount + ", finalized=" + finalizedCount
                                                        + ", outstanding=" + outstanding.get());
            }
            if (maximumOutstanding.get() > CONCURRENT_WORKERS) {
                throw new IllegalStateException("Terminal dispatch benchmark exceeded its worker bound: "
                                                        + maximumOutstanding.get());
            }
            completionExecutor.close();
        }

        private long runCompleted(InvocationState invocation) {
            Operation operation = newOperation(ReceiptMode.COMPLETED);
            invocation.operation(operation);
            operation.start();
            long completedSequence = operation.awaitWorker();
            if (!operation.receipt.isDone() || !operation.finalization.isDone()) {
                throw new IllegalStateException("Completed dispatch receipt did not finalize inline");
            }
            return completedSequence;
        }

        private long runBlocking(InvocationState invocation) {
            Operation operation = newOperation(ReceiptMode.BLOCKING);
            invocation.operation(operation);
            operation.start();
            long completedSequence = operation.awaitWorker();
            if (!operation.receipt.isDone() || !operation.finalization.isDone()) {
                throw new IllegalStateException("Blocking dispatch receipt did not finalize before request-worker return");
            }
            return completedSequence;
        }

        private long runContinuation(InvocationState invocation) {
            Operation operation = newOperation(ReceiptMode.CONTINUATION);
            invocation.operation(operation);
            operation.start();
            long submittedSequence = operation.awaitWorker();
            if (operation.receipt.isDone() || operation.finalization.isDone()) {
                throw new IllegalStateException("Delayed dispatch receipt completed before request-worker return");
            }
            return submittedSequence;
        }

        private Operation newOperation(ReceiptMode mode) {
            Throwable failure = dispatcherFailure.get();
            if (failure != null) {
                throw new IllegalStateException("Terminal dispatch benchmark dispatcher failed", failure);
            }
            long operationSequence = sequence.incrementAndGet();
            submitted.incrementAndGet();
            int currentOutstanding = outstanding.incrementAndGet();
            maximumOutstanding.accumulateAndGet(currentOutstanding, Math::max);
            return new Operation(this, operationSequence, mode);
        }

        private void submit(Operation operation) {
            if (!dispatchQueue.offer(operation)) {
                operation.fail(new IllegalStateException("Terminal dispatch benchmark queue is full"));
            }
        }

        private void dispatchLoop() {
            try {
                while (!stopping.get() || !dispatchQueue.isEmpty()) {
                    Operation operation;
                    try {
                        operation = dispatchQueue.poll(100, TimeUnit.MILLISECONDS);
                    } catch (InterruptedException e) {
                        if (stopping.get()) {
                            continue;
                        }
                        throw e;
                    }
                    if (operation == null) {
                        continue;
                    }
                    operation.awaitDispatchRelease();
                    operation.receipt.complete(null);
                }
            } catch (Throwable failure) {
                dispatcherFailure.compareAndSet(null, failure);
            }
        }

        private void finalized() {
            finalized.incrementAndGet();
            int remaining = outstanding.decrementAndGet();
            if (remaining < 0) {
                dispatcherFailure.compareAndSet(null,
                                                new IllegalStateException("Terminal dispatch outstanding count is negative"));
            }
        }

        private void submitFinalization(Operation operation, Throwable failure) {
            completionExecutor.execute(() -> operation.finish(failure));
        }
    }

    /**
     * Per-JMH-worker guard which drains delayed finalization after each measured request-worker return.
     */
    @State(Scope.Thread)
    public static class InvocationState {
        private Operation operation;

        /**
         * Rejects an operation retained from an earlier invocation.
         */
        @Setup(Level.Invocation)
        public void setup() {
            if (operation != null) {
                throw new IllegalStateException("Terminal dispatch benchmark retained an invocation");
            }
        }

        /**
         * Releases delayed dispatch and verifies exactly-once lifecycle completion.
         */
        @TearDown(Level.Invocation)
        public void tearDown() {
            Operation current = operation;
            operation = null;
            if (current == null) {
                throw new IllegalStateException("Terminal dispatch benchmark invocation was not initialized");
            }
            current.releaseDispatch();
            current.awaitFinalization();
        }

        private void operation(Operation operation) {
            if (this.operation != null) {
                throw new IllegalStateException("Terminal dispatch benchmark invocation was already initialized");
            }
            this.operation = operation;
        }
    }

    private static final class Operation {
        private final DispatchState owner;
        private final long sequence;
        private final ReceiptMode mode;
        private final CompletableFuture<Void> receipt;
        private final CompletableFuture<Long> finalization = new CompletableFuture<>();
        private final CountDownLatch dispatchRelease;
        private final AtomicInteger finalizationCount = new AtomicInteger();
        private Thread worker;

        private Operation(DispatchState owner, long sequence, ReceiptMode mode) {
            this.owner = owner;
            this.sequence = sequence;
            this.mode = mode;
            this.receipt = mode == ReceiptMode.COMPLETED
                    ? CompletableFuture.completedFuture(null)
                    : new CompletableFuture<>();
            this.dispatchRelease = new CountDownLatch(mode == ReceiptMode.CONTINUATION ? 1 : 0);
        }

        private void start() {
            worker = Thread.ofVirtual().start(() -> {
                switch (mode) {
                    case COMPLETED -> finishCompleted();
                    case BLOCKING -> {
                        owner.submit(this);
                        awaitAndFinish();
                    }
                    case CONTINUATION -> {
                        receipt.whenComplete((_, failure) -> owner.submitFinalization(this, failure));
                        owner.submit(this);
                    }
                }
            });
        }

        private void finishCompleted() {
            if (!receipt.isDone()) {
                finish(new IllegalStateException("Completed terminal dispatch receipt is not complete"));
                return;
            }
            try {
                receipt.join();
                finish(null);
            } catch (Throwable failure) {
                finish(failure);
            }
        }

        private void awaitAndFinish() {
            try {
                receipt.get();
                finish(null);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                finish(e);
            } catch (ExecutionException e) {
                finish(e.getCause());
            }
        }

        private long awaitWorker() {
            try {
                worker.join(TimeUnit.SECONDS.toMillis(DispatchState.AWAIT_SECONDS));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while awaiting terminal dispatch request worker", e);
            }
            if (worker.isAlive()) {
                throw new IllegalStateException("Terminal dispatch request worker did not return");
            }
            return sequence;
        }

        private void releaseDispatch() {
            dispatchRelease.countDown();
        }

        private void awaitDispatchRelease() throws InterruptedException {
            dispatchRelease.await();
        }

        private void awaitFinalization() {
            try {
                long completedSequence = finalization.get(DispatchState.AWAIT_SECONDS, TimeUnit.SECONDS);
                if (completedSequence != sequence) {
                    throw new IllegalStateException("Terminal dispatch finalized sequence " + completedSequence
                                                            + " instead of " + sequence);
                }
            } catch (TimeoutException e) {
                throw new IllegalStateException("Timed out awaiting terminal dispatch finalization", e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while awaiting terminal dispatch finalization", e);
            } catch (ExecutionException e) {
                throw new IllegalStateException("Terminal dispatch finalization failed", e.getCause());
            }
            if (finalizationCount.get() != 1) {
                throw new IllegalStateException("Terminal dispatch finalized " + finalizationCount.get() + " times");
            }
        }

        private void finish(Throwable failure) {
            if (!finalizationCount.compareAndSet(0, 1)) {
                fail(new IllegalStateException("Terminal dispatch lifecycle completed more than once"));
                return;
            }
            owner.finalized();
            if (failure == null) {
                finalization.complete(sequence);
            } else {
                finalization.completeExceptionally(failure);
            }
        }

        private void fail(Throwable failure) {
            owner.dispatcherFailure.compareAndSet(null, failure);
            receipt.completeExceptionally(failure);
            finalization.completeExceptionally(failure);
        }
    }

    private enum ReceiptMode {
        COMPLETED,
        BLOCKING,
        CONTINUATION
    }
}
