/*
 * Copyright (c) 2019, 2021 Oracle and/or its affiliates.
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
package io.helidon.common.context;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import io.helidon.common.context.spi.DataPropagationProvider;
import io.helidon.common.serviceloader.HelidonServiceLoader;

class ContextAwareExecutorImpl implements ContextAwareExecutorService {

    @SuppressWarnings("rawtypes")
    private static final List<DataPropagationProvider> PROVIDERS = HelidonServiceLoader
            .builder(ServiceLoader.load(DataPropagationProvider.class)).build().asList();

    private final ExecutorService delegate;

    ContextAwareExecutorImpl(ExecutorService toWrap) {
        this.delegate = toWrap;
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
    public void execute(Runnable command) {
        delegate.execute(wrap(command));
    }

    @Override
    public ExecutorService unwrap() {
        return delegate;
    }

    protected <T> Collection<? extends Callable<T>> wrap(Collection<? extends Callable<T>> tasks) {
        return tasks.stream()
                .map(this::wrap)
                .collect(Collectors.toList());

    }

    @SuppressWarnings(value = "unchecked")
    protected <T> Callable<T> wrap(Callable<T> task) {
        Optional<Context> context = Contexts.context();
        if (context.isPresent()) {
            Map<Class<?>, Object> properties = new HashMap<>();
            PROVIDERS.forEach(provider -> properties.put(provider.getClass(), provider.data()));
            return () -> {
                try {
                    PROVIDERS.forEach(provider -> provider.propagateData(properties.get(provider.getClass())));
                    return Contexts.runInContext(context.get(), task);
                } finally {
                    PROVIDERS.forEach(provider -> provider.clearData(properties.get(provider.getClass())));
                }
            };
        } else {
            return task;
        }
    }

    @SuppressWarnings(value = "unchecked")
    protected Runnable wrap(Runnable command) {
        Optional<Context> context = Contexts.context();
        if (context.isPresent()) {
            Map<Class<?>, Object> properties = new HashMap<>();
            PROVIDERS.forEach(provider -> properties.put(provider.getClass(), provider.data()));
            return () -> {
                try {
                    PROVIDERS.forEach(provider -> provider.propagateData(properties.get(provider.getClass())));
                    Contexts.runInContext(context.get(), command);
                } finally {
                    PROVIDERS.forEach(provider -> provider.clearData(properties.get(provider.getClass())));
                }
            };
        } else {
            return command;
        }
    }
}
