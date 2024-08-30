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
 * This is similar to {@link java.lang.ThreadLocal}, except it uses context to store a value.
 * <p>
 * When test context is available as a parent of current context
 * (identified as a context that has ID starting with {@code test-}), the instance is registered with that context.
 * Otherwise, it is registered with {@link io.helidon.common.context.Contexts#globalContext()}.
 * <p>
 * The intention of this type is to have singletons for production runtime, while maintaining a separation during
 * testing, where Helidon test extension run each part of the test class in a test context.
 * <p>
 * Note that the instance is not simply registered in the context, but it uses this class as a classifier.
 *
 * @param <T> type of the value stored in context
 */
public final class ContextValue<T> {
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final Class<T> clazz;
    private final Supplier<T> supplier;

    private ContextValue(Class<T> clazz, Supplier<T> supplier) {
        this.clazz = clazz;
        this.supplier = supplier;
    }

    /**
     * Create a new context value.
     *
     * @param clazz type of the value
     * @param <T>   type of the value
     * @return a new context value with nothing set.
     */
    public static <T> ContextValue<T> create(Class<T> clazz) {
        return new ContextValue<>(clazz, null);
    }

    /**
     * Create a new context value with a supplier of instances for each context.
     *
     * @param clazz type of the value
     * @param value value supplier
     * @param <T>   type of the value
     * @return a new context value
     */
    public static <T> ContextValue<T> create(Class<T> clazz, Supplier<T> value) {
        Objects.requireNonNull(value);
        return new ContextValue<>(clazz, value);
    }

    /**
     * Set the value in current context.
     *
     * @param value value to use
     */
    public void set(T value) {
        lock.writeLock().lock();
        try {
            context().register(ContextValue.class, value);
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
                                                     + "and supplier was not provided when creating this instance for type"
                                                     + " " + clazz.getName());
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
                .get("global-instances", Context.class)
                .orElse(globalContext);
    }

    private Optional<T> current() {
        lock.readLock().lock();
        try {
            return context().get(ContextValue.class, clazz);
        } finally {
            lock.readLock().unlock();
        }
    }
}
