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

package io.helidon.webserver.benchmark.jmh;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.http.http2.ConnectionFlowControl;
import io.helidon.http.http2.FlowControl;
import io.helidon.http.http2.WindowSize;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;

public class Http2ConnectionWindowUpdateJmhBenchmark {
    private static final Duration FLOW_CONTROL_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration ENROLLMENT_TIMEOUT = Duration.ofSeconds(10);

    @Benchmark
    @BenchmarkMode(Mode.SingleShotTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void updateConnectionWindow(UpdateState state) {
        state.updateConnectionWindow();
    }

    @State(Scope.Thread)
    public static class UpdateState {
        private final AtomicReference<Throwable> failure = new AtomicReference<>();
        @Param({"1", "128", "1024", "8192"})
        private int streamCount;
        private WindowSize.Outbound connectionWindow;
        private Thread[] writers;
        private volatile boolean released;

        @Setup(Level.Invocation)
        public void setup() throws InterruptedException {
            released = false;
            ConnectionFlowControl connection = ConnectionFlowControl.serverBuilder((_, _) -> { })
                    .blockTimeout(FLOW_CONTROL_TIMEOUT)
                    .build();
            connectionWindow = connection.outbound();
            connectionWindow.decrementWindowSize(connectionWindow.getRemainingWindowSize());
            writers = new Thread[streamCount];
            CountDownLatch ready = new CountDownLatch(streamCount);
            for (int i = 0; i < streamCount; i++) {
                FlowControl.Outbound flowControl = connection.createStreamFlowControl(i * 2 + 1,
                                                                                       WindowSize.DEFAULT_WIN_SIZE,
                                                                                       WindowSize.DEFAULT_MAX_FRAME_SIZE)
                        .outbound();
                writers[i] = Thread.ofVirtual().start(() -> {
                    ready.countDown();
                    try {
                        flowControl.blockTillUpdate();
                        if (!released) {
                            failure.compareAndSet(null,
                                                  new AssertionError("Flow-control wait returned before WINDOW_UPDATE"));
                        }
                    } catch (Throwable t) {
                        failure.compareAndSet(null, t);
                    }
                });
            }
            if (!ready.await(ENROLLMENT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                throw new IllegalStateException("Flow-control writers did not start");
            }
            long deadline = System.nanoTime() + ENROLLMENT_TIMEOUT.toNanos();
            for (Thread writer : writers) {
                while (writer.getState() != Thread.State.TIMED_WAITING && System.nanoTime() < deadline) {
                    Thread.onSpinWait();
                }
                if (writer.getState() != Thread.State.TIMED_WAITING) {
                    throw new IllegalStateException("Flow-control writer did not block");
                }
            }
        }

        @TearDown(Level.Invocation)
        public void tearDown() throws InterruptedException {
            if (!released) {
                updateConnectionWindow();
            }
            for (Thread writer : writers) {
                writer.join();
            }
            Throwable throwable = failure.getAndSet(null);
            if (throwable != null) {
                throw new IllegalStateException("Connection WINDOW_UPDATE benchmark failed", throwable);
            }
        }

        private void updateConnectionWindow() {
            released = true;
            connectionWindow.incrementWindowSize(1);
        }
    }
}
