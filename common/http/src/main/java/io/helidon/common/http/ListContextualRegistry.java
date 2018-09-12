/*
 * Copyright (c) 2017, 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.common.http;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;

/**
 * A {@link ContextualRegistry} implementation with deque registry.
 */
class ListContextualRegistry implements ContextualRegistry {

    private final ContextualRegistry parent;
    private final ConcurrentHashMap<Object, ClassifiedRegistry> classifiers = new ConcurrentHashMap<>();
    private final ClassifiedRegistry registry = new ClassifiedRegistry();

    /**
     * Creates new instance with defined parent.
     *
     * @param parent a parent context or {@code null}.
     */
    ListContextualRegistry(ContextualRegistry parent) {
        this.parent = parent;
    }

    /**
     * Creates new instance.
     */
    ListContextualRegistry() {
        this(null);
    }

    @Override
    public <T> void register(T instance) {
        registry.register(instance);
    }

    @Override
    public <T> void supply(Class<T> type, Supplier<T> supplier) {
        registry.supply(type, supplier);
    }

    @Override
    public <T> Optional<T> get(Class<T> type) {
        T result = registry.get(type);
        if (result == null) {
            if (parent == null) {
                return Optional.empty();
            } else {
                return parent.get(type);
            }
        } else {
            return Optional.of(result);
        }
    }

    @Override
    public <T> void register(Object classifier, T instance) {
        Objects.requireNonNull(classifier, "Parameter 'classifier' is null!");
        ClassifiedRegistry cr = classifiers.computeIfAbsent(classifier, k -> new ClassifiedRegistry());
        cr.register(instance);
    }

    @Override
    public <T> void supply(Object classifier, Class<T> type, Supplier<T> supplier) {
        Objects.requireNonNull(classifier, "Parameter 'classifier' is null!");
        ClassifiedRegistry cr = classifiers.computeIfAbsent(classifier, k -> new ClassifiedRegistry());
        cr.supply(type, supplier);
    }

    @Override
    public <T> Optional<T> get(Object classifier, Class<T> type) {
        Objects.requireNonNull(classifier, "Parameter 'classifier' is null!");
        ClassifiedRegistry cr = classifiers.get(classifier);
        if (cr != null) {
            T result = cr.get(type);
            if (result == null && parent != null) {
                return parent.get(classifier, type);
            } else {
                return Optional.ofNullable(result);
            }
        } else {
            if (parent != null) {
                return parent.get(classifier, type);
            } else {
                return Optional.empty();
            }
        }
    }

    private interface RegisteredItem<T> {
        T get();

        Class<T> getType();
    }

    private static class ClassifiedRegistry {
        private final ReadWriteLock lock = new ReentrantReadWriteLock();
        private final List<RegisteredItem> content = new ArrayList<>();

        private void registerItem(RegisteredItem item) {
            Lock l = lock.writeLock();
            try {
                Class<?> c = item.getType();
                l.lock();
                for (int i = 0; i < content.size(); i++) {
                    RegisteredItem reg = content.get(i);
                    if (c == reg.getType()) {
                        content.remove(i);
                        break;
                    }
                }
                content.add(item);
            } finally {
                l.unlock();
            }
        }

        @SuppressWarnings("unchecked")
        <T> void register(T instance) {
            Objects.requireNonNull(instance, "Parameter 'instance' is null!");
            registerItem(new RegisteredInstance(instance));
        }

        @SuppressWarnings("unchecked")
        <T> void supply(Class<T> type, Supplier<T> supplier) {
            Objects.requireNonNull(type, "Parameter 'type' is null!");
            Objects.requireNonNull(supplier, "Parameter 'supplier' is null!");
            registerItem(new RegisteredSupplier(type, supplier));
        }

        <T> T get(Class<T> type) {
            Objects.requireNonNull(type, "Parameter 'type' is null!");
            Lock l = lock.readLock();
            try {
                l.lock();
                for (int i = content.size() - 1; i >= 0; i--) {
                    RegisteredItem item = content.get(i);
                    if (type.isAssignableFrom(item.getType())) {
                        return type.cast(item.get());
                    }
                }
            } finally {
                l.unlock();
            }
            return null;
        }
    }

    private static class RegisteredSupplier<T> implements RegisteredItem<T> {
        private final Class<T> type;
        private final Supplier<T> supplier;
        private volatile boolean supplied = false;
        private volatile T instance;

        RegisteredSupplier(Class<T> type, Supplier<T> supplier) {
            this.type = type;
            this.supplier = supplier;
        }

        @Override
        public T get() {
            if (!supplied) {
                synchronized (this) {
                    if (!supplied) {
                        supplied = true;
                        instance = supplier.get();
                    }
                }
            }
            return instance;
        }

        @Override
        public Class<T> getType() {
            return type;
        }
    }

    private static class RegisteredInstance<T> implements RegisteredItem<T> {
        private final T instance;

        RegisteredInstance(T instance) {
            this.instance = instance;
        }

        @Override
        public T get() {
            return instance;
        }

        @Override
        @SuppressWarnings("unchecked")
        public Class<T> getType() {
            return (Class<T>) instance.getClass();
        }
    }
}
