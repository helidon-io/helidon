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

package io.helidon.tests.integration.h2spec.client;

import java.io.Closeable;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.stream.Stream;

import com.github.dockerjava.api.async.ResultCallback;

// Owns the blocking Docker work for one suite. Shutdown never waits indefinitely for Docker.
final class H2SpecExecution implements AutoCloseable {
    private static final Path DIAGNOSTICS = Path.of("target/h2spec-client-diagnostics");
    private final ExecutorService workers = Executors.newFixedThreadPool(2,
            Thread.ofPlatform().daemon().name("h2spec-worker-", 0).factory());
    private final ExecutorService maintenance = Executors.newCachedThreadPool(
            Thread.ofPlatform().daemon().name("h2spec-maintenance-", 0).factory());
    private final List<Closeable> resources = new ArrayList<>();
    private final List<Future<?>> tasks = new CopyOnWriteArrayList<>();
    private final List<Diagnostic> diagnostics = new CopyOnWriteArrayList<>();
    private final Duration maintenanceTimeout;
    private final Path diagnosticsDirectory;
    private boolean closed;

    H2SpecExecution(Duration maintenanceTimeout) throws IOException {
        this(maintenanceTimeout, DIAGNOSTICS);
    }

    H2SpecExecution(Duration maintenanceTimeout, Path diagnosticsDirectory) throws IOException {
        this.maintenanceTimeout = maintenanceTimeout;
        this.diagnosticsDirectory = diagnosticsDirectory;
        Files.createDirectories(diagnosticsDirectory);
        try (Stream<Path> paths = Files.list(diagnosticsDirectory)) {
            for (Path path : paths.filter(Files::isRegularFile).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    static void progress(String message) {
        System.out.println(Instant.now() + " [h2spec-client] " + message);
        System.out.flush();
    }

    <T> Future<T> submit(Callable<T> task) {
        Future<T> future = workers.submit(task);
        tasks.add(future);
        return future;
    }

    <T> T await(Future<T> future, long deadline, String phase) {
        try {
            return future.get(Math.max(1, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
        } catch (TimeoutException e) {
            progress("TIMEOUT: " + phase + "; collecting diagnostics before cleanup");
            diagnose(phase);
            throw new IllegalStateException("Timed out during " + phase + "; see " + diagnosticsDirectory, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted during " + phase, e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Failed during " + phase, e.getCause());
        }
    }

    // Registration also handles resources acquired after a timeout has already initiated shutdown.
    synchronized void own(Closeable resource) throws IOException {
        if (closed) {
            resource.close();
            throw new IOException("h2spec execution already stopped");
        }
        resources.add(resource);
    }

    void diagnostic(String name, Callable<String> capture) {
        diagnostics.add(new Diagnostic(name, capture));
    }

    @Override
    public void close() {
        List<Closeable> owned;
        synchronized (this) {
            if (closed) {
                return;
            }
            closed = true;
            owned = List.copyOf(resources);
        }
        workers.shutdownNow();
        tasks.forEach(task -> task.cancel(true));
        List<Future<?>> cleanup = new ArrayList<>();
        for (Closeable resource : owned) {
            cleanup.add(maintenance.submit(() -> {
                resource.close();
                return null;
            }));
        }
        long deadline = System.nanoTime() + maintenanceTimeout.toNanos();
        for (Future<?> future : cleanup) {
            boundedWait(future, deadline, "resource cleanup");
        }
        maintenance.shutdownNow();
        try {
            if (!workers.awaitTermination(Math.max(1, deadline - System.nanoTime()), TimeUnit.NANOSECONDS)) {
                progress("Cleanup deadline reached; Docker worker has not terminated");
            }
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
        }
        progress("Cleanup finished; any incomplete operations are reported above");
    }

    private void diagnose(String phase) {
        List<Diagnostic> captures = new ArrayList<>(diagnostics);
        captures.add(new Diagnostic("host-threads.txt", () -> {
            StringBuilder dump = new StringBuilder(Instant.now() + " " + phase + "\n");
            for (var thread : ManagementFactory.getThreadMXBean().dumpAllThreads(true, true)) {
                dump.append(thread).append('\n');
                // ThreadInfo.toString truncates long stacks; retain every frame.
                for (StackTraceElement frame : thread.getStackTrace()) {
                    dump.append("    at ").append(frame).append('\n');
                }
            }
            return dump.toString();
        }));
        List<Future<?>> pending = new ArrayList<>();
        for (Diagnostic diagnostic : captures) {
            pending.add(maintenance.submit(() -> {
                Files.createDirectories(diagnosticsDirectory);
                try {
                    Files.writeString(diagnosticsDirectory.resolve(diagnostic.name()), diagnostic.capture().call());
                } catch (Exception e) {
                    Files.writeString(diagnosticsDirectory.resolve(diagnostic.name()), e.toString());
                }
                return null;
            }));
        }
        long deadline = System.nanoTime() + maintenanceTimeout.toNanos();
        for (Future<?> future : pending) {
            boundedWait(future, deadline, "diagnostic capture");
        }
    }

    private void boundedWait(Future<?> future, long deadline, String operation) {
        try {
            future.get(Math.max(1, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            progress(operation + " interrupted");
        } catch (ExecutionException | TimeoutException e) {
            future.cancel(true);
            progress(operation + " incomplete: " + e);
        }
    }

    private record Diagnostic(String name, Callable<String> capture) {
    }

    // docker-java can deliver onStart after cancellation. Close that late stream as well.
    static final class Callback<T> implements ResultCallback<T> {
        private final Consumer<T> consumer;
        private final CountDownLatch completed = new CountDownLatch(1);
        private Closeable stream;
        private volatile Throwable failure;
        private boolean closed;

        Callback(Consumer<T> consumer) {
            this.consumer = consumer;
        }

        @Override
        public synchronized void onStart(Closeable stream) {
            if (closed) {
                try {
                    stream.close();
                } catch (IOException e) {
                    failure = e;
                }
            } else {
                this.stream = stream;
            }
        }

        @Override
        public synchronized void onNext(T item) {
            if (!closed) {
                consumer.accept(item);
            }
        }

        @Override
        public synchronized void onError(Throwable throwable) {
            failure = throwable;
            onComplete();
        }

        @Override
        public void onComplete() {
            try {
                close();
            } catch (IOException e) {
                failure = e;
            }
        }

        @Override
        public synchronized void close() throws IOException {
            closed = true;
            try {
                if (stream != null) {
                    stream.close();
                    stream = null;
                }
            } finally {
                completed.countDown();
            }
        }

        void await() throws InterruptedException {
            completed.await();
            if (failure != null) {
                throw new IllegalStateException("Docker stream failed", failure);
            }
        }
    }
}
