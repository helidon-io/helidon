/*
 * Copyright (c) 2020 Oracle and/or its affiliates. All rights reserved.
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
package io.helidon.media.common;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import io.helidon.common.GenericType;

/**
 * Thread-safe hierarchical registry of message body operators.
 * @param <T> operator type
 */
final class MessageBodyOperators<T extends MessageBodyOperator<?>> implements Iterable<T>, AutoCloseable {

    private final LinkedList<T> operators;
    private final ReadWriteLock lock;
    private final AtomicBoolean readLocked;
    private MessageBodyOperators<T> parent;

    /**
     * Create a new parented registry.
     * @param parent parent registry
     */
    MessageBodyOperators(MessageBodyOperators<T> parent) {
        this.parent = parent;
        this.operators = new LinkedList<>();
        this.lock = new ReentrantReadWriteLock();
        this.readLocked = new AtomicBoolean(false);
    }

    /**
     * Create a new standalone (non parented) registry.
     */
    MessageBodyOperators() {
        this(null);
    }

    /**
     * Register the specified operator at the last position.
     * @param operator operation to register
     */
    void registerLast(T operator) {
        register(operator, false);
    }

    /**
     * Register the specified operator at the first position.
     * @param operator operation to register
     */
    void registerFirst(T operator) {
        register(operator, true);
    }

    /**
     * Perform the registration of an operator.
     * @param operator operator to register
     * @param addFirst {@code true} if the operator should be added first,
     * {@code false} if last
     */
    private void register(T operator, boolean addFirst) {
        Objects.requireNonNull(operator, "operator is null!");
        try {
            lock.writeLock().lock();
            if (addFirst) {
                operators.addFirst(operator);
            } else {
                operators.addLast(operator);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Select an operator using {@link Operator#accept}.
     * @param type the type representation
     * @param context the message body context
     * @return operator found, or {@code null} or no operator was found
     */
    <U extends MessageBodyOperator<V>, V extends MessageBodyContext> T select(GenericType<?> type, V context) {
        return select(type, context, /* fallback */ null);
    }

    /**
     * Select an operator using {@link Operator#accept}.
     * @param type the type representation
     * @param context the message body context
     * @param fallback a fallback registry to use if the operator is not
     * found in this registry hierarchy
     * @return operator, or {@code null} or no operator was found
     */
    @SuppressWarnings("unchecked")
    <U extends MessageBodyOperator<V>, V extends MessageBodyContext> T select(GenericType<?> type, V context,
            MessageBodyOperators<T> fallback) {

        Objects.requireNonNull(type, "type is null!");
        Objects.requireNonNull(context, "context is null!");
        try {
            lock.readLock().lock();
            for (T operator : operators) {
                if (((U) operator).accept(type, context)) {
                    return operator;
                }
            }
        } finally {
            lock.readLock().unlock();
        }
        if (parent != null) {
            return parent.select(type, context, fallback);
        }
        if (fallback != null) {
            return fallback.select(type, context, fallback);
        }
        return null;
    }

    /**
     * Select an operator by it class.
     * @param operatorClass required operator class
     * @return operator, or {@code null} or no operator was found
     */
    T get(Class<? extends MessageBodyOperator> operatorClass) {
        return get(operatorClass, null);
    }

    /**
     * Select an operator by it class.
     * @param operatorClass required operator class
     * @param fallback a fallback registry to use if the operator is not
     * found in this registry hierarchy
     * @return operator, or {@code null} or no operator was found
     */
    T get(Class<? extends MessageBodyOperator> operatorClass, MessageBodyOperators<T> fallback) {
        Objects.requireNonNull(operatorClass, "operatorClass is null!");
        try {
            lock.readLock().lock();
            for (T operator : operators) {
                if (operator.getClass().equals(operatorClass)) {
                    return operator;
                }
            }
        } finally {
            lock.readLock().unlock();
        }
        if (parent != null) {
            return parent.get(operatorClass, fallback);
        }
        if (fallback != null) {
            return fallback.get(operatorClass, fallback);
        }
        return null;
    }

    @Override
    public Iterator<T> iterator() {
        return new ParentedIterator<>(this);
    }

    @Override
    public void close() {
        if (readLocked.compareAndSet(true, false)) {
            lock.readLock().unlock();
        }
    }

    /**
     * A thread-safe iterator implementation to iterate over a registry
     * hierarchy.
     * @param <T> Operator type
     */
    private static final class ParentedIterator<T extends MessageBodyOperator<?>> implements Iterator<T> {

        private final Iterator<T> iterator;
        private final Lock readLock;
        private final Iterator<T> parent;
        private final AtomicBoolean locked;
        private final AtomicBoolean hasNext;

        ParentedIterator(MessageBodyOperators<T> registry) {
            iterator = registry.operators.iterator();
            readLock = registry.lock.readLock();
            if (registry.parent != null) {
                parent = registry.parent.iterator();
            } else {
                parent = null;
            }
            locked = registry.readLocked;
            hasNext = new AtomicBoolean(true);
        }

        @Override
        public boolean hasNext() {
            if (!hasNext.get()) {
                if (parent != null) {
                    return parent.hasNext();
                }
                return false;
            }
            if (locked.compareAndSet(false, true)) {
                readLock.lock();
            }
            if (iterator.hasNext()) {
                return true;
            }
            if (locked.compareAndSet(true, false)) {
                readLock.unlock();
            }
            hasNext.set(false);
            return false;
        }

        @Override
        public T next() {
            if (hasNext.get()) {
                return iterator.next();
            } else if (parent != null) {
                return parent.next();
            }
            throw new NoSuchElementException();
        }
    }
}
