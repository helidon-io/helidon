/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.common.configurable;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

class WrappedVirtualExecutor implements ExecutorService {
    private final AtomicLong threads = new AtomicLong();
    private final AtomicInteger inProgress = new AtomicInteger();
    private final ExecutorService delegate;

    WrappedVirtualExecutor(ExecutorService delegate) {
        this.delegate = delegate;
    }

    @Override
    public void shutdown() {
        delegate.shutdown();
    }

    @Override
    public List<Runnable> shutdownNow() {
        return delegate.shutdownNow();
    }

    @Override
    public boolean isShutdown() {
        return delegate.isShutdown();
    }

    @Override
    public boolean isTerminated() {
        return delegate.isTerminated();
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        return delegate.awaitTermination(timeout, unit);
    }

    @Override
    public <T> Future<T> submit(Callable<T> task) {
        return delegate.submit(wrap(task));
    }

    @Override
    public <T> Future<T> submit(Runnable task, T result) {
        return delegate.submit(wrap(task), result);
    }

    @Override
    public Future<?> submit(Runnable task) {
        return delegate.submit(wrap(task));
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException {
        return delegate.invokeAll(wrap(tasks));
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
            throws InterruptedException {
        return delegate.invokeAll(wrap(tasks), timeout, unit);
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException {
        return delegate.invokeAny(wrap(tasks));
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
            throws InterruptedException, ExecutionException, TimeoutException {
        return delegate.invokeAny(wrap(tasks), timeout, unit);
    }

    @Override
    public void close() {
        delegate.close();
    }

    @Override
    public void execute(Runnable command) {
        delegate.execute(wrap(command));
    }

    long threads() {
        return threads.get();
    }

    int inProgress() {
        return inProgress.get();
    }

    private <T> Collection<? extends Callable<T>> wrap(Collection<? extends Callable<T>> tasks) {
        return tasks.stream()
                .map(this::wrap)
                .collect(Collectors.toList());
    }

    private Runnable wrap(Runnable r) {
        return () -> {
            try {
                inProgress.incrementAndGet();
                threads.incrementAndGet();
                r.run();
            } finally {
                inProgress.decrementAndGet();
            }
        };
    }

    private <T> Callable<T> wrap(Callable<T> c) {
        return () -> {
            try {
                inProgress.incrementAndGet();
                threads.incrementAndGet();
                return c.call();
            } finally {
                inProgress.decrementAndGet();
            }
        };
    }
}
