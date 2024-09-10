/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;

/**
 * A context based "singleton" value holder that provides an indirection mechanism for static values.
 * "Singleton" values are stored and resolved from a context classified with {@value #STATIC_CONTEXT_CLASSIFIER}
 * in the {@link io.helidon.common.context.Contexts#context()} current context},
 * or the {@link io.helidon.common.context.Contexts#globalContext() global context if none is defined}.
 *
 * @param <T> type of the value stored in context
 */
public final class ContextSingleton<T> {
    /**
     * Classifier used to register a context that is to serve as the static context.
     */
    public static final String STATIC_CONTEXT_CLASSIFIER = "helidon-static-context";

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final ContextSingletonClassifier<T> classifier;
    private final Supplier<T> supplier;

    private ContextSingleton(ContextSingletonClassifier<T> qualifier, Supplier<T> supplier) {
        this.classifier = qualifier;
        this.supplier = supplier;
    }

    /**
     * Create a new context value.
     *
     * @param ownerClass type owning this context singleton
     * @param clazz      type of the value
     * @param <T>        type of the value
     * @return a new context value with nothing set.
     */
    public static <T> ContextSingleton<T> create(Class<?> ownerClass, Class<T> clazz) {
        Objects.requireNonNull(ownerClass);
        Objects.requireNonNull(clazz);

        return new ContextSingleton<>(new ContextSingletonClassifier<>(ownerClass, clazz), null);
    }

    /**
     * Create a new context value with a supplier of instances for each context.
     *
     * @param ownerClass type owning this context singleton
     * @param clazz type of the value
     * @param value value supplier
     * @param <T>   type of the value
     * @return a new context value
     */
    public static <T> ContextSingleton<T> create(Class<?> ownerClass, Class<T> clazz, Supplier<T> value) {
        Objects.requireNonNull(ownerClass);
        Objects.requireNonNull(clazz);
        Objects.requireNonNull(value);

        return new ContextSingleton<>(new ContextSingletonClassifier<>(ownerClass, clazz), value);
    }

    /**
     * Set the value in current context.
     *
     * @param value value to use
     */
    public void set(T value) {
        lock.writeLock().lock();
        try {
            context().register(classifier, value);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Get the current value in this context, or call the provided supplier to get the value and register
     * it, if not yet registered.
     *
     * @return current value (or the new one obtained from supplier)
     * @throws NoSuchElementException in case there is no value, and no supplier
     * @see #set(Object)
     */
    public T get() throws NoSuchElementException {
        return get(this.supplier);
    }

    /**
     * Get the current value in this context, or call the provided supplier to get the value and register
     * it, if not yet registered.
     *
     * @param supplier supplier to call if the current value is not present
     * @return current value (or the new one obtained from supplier)
     * @throws NoSuchElementException in case there is no value, and the supplier returned {@code null}
     * @see #set(Object)
     */
    public T get(Supplier<T> supplier) {
        var current = current();
        if (current.isPresent()) {
            return current.get();
        }
        lock.writeLock().lock();
        try {
            current = current();
            if (current.isPresent()) {
                return current.get();
            }
            if (supplier != null) {
                var value = supplier.get();
                set(value);
                return value;
            }
            throw new NoSuchElementException("There is no value available in the current context, "
                                                     + "and supplier was not provided when creating this instance for: "
                                                     + " " + classifier);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Current value as an {@link java.util.Optional}.
     *
     * @return current value, or empty if not configured
     */
    public Optional<T> value() {
        return current();
    }

    /**
     * Whether there is a value in current context.
     *
     * @return {@code true} if there is a value
     */
    public boolean isPresent() {
        return current().isPresent();
    }

    private static Context context() {
        Context globalContext = Contexts.globalContext();

        // this is the context we expect to get (and set global instances)
        return Contexts.context()
                .orElse(globalContext)
                .get(STATIC_CONTEXT_CLASSIFIER, Context.class)
                .orElse(globalContext);
    }

    private Optional<T> current() {
        lock.readLock().lock();
        try {
            return context().get(classifier, classifier.valueType());
        } finally {
            lock.readLock().unlock();
        }
    }

    @SuppressWarnings("rawtypes")
    private record ContextSingletonClassifier<T>(Class<ContextSingleton> contextSingleton,
                                                 Class<?> ownerClass,
                                                 Class<T> valueType) {
        private ContextSingletonClassifier(Class<?> ownerClass, Class<T> valueType) {
            this(ContextSingleton.class, ownerClass, valueType);
        }
    }
}
