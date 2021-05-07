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

package io.helidon.microprofile.grpc.core;

import java.util.Objects;
import java.util.function.Supplier;

import io.grpc.Status;

/**
 * A supplier of instances of objects.
 */
public interface Instance {

    /**
     * Create a {@link Supplier} that supplies a
     * singleton.
     *
     * @param instance  the singleton instance to supply
     * @param <T>       the type of instance supplied
     * @return  the singleton instance
     *
     * @throws java.lang.NullPointerException if the instance parameter is null
     */
    static <T> Supplier<T> singleton(T instance) {
        return new SingletonInstance<>(Objects.requireNonNull(instance));
    }

    /**
     * Create an {@link Supplier} that supplies a
     * singleton of the specified Class.
     * <p>
     * The Class provided must have a no-args default constructor.
     *
     * @param cls  the Class of the singleton instance to supply
     * @param <T>       the type of instance supplied
     * @return  the singleton instance of the specified Class
     *
     * @throws java.lang.NullPointerException if the class is null
     */
    static <T> Supplier<T> singleton(Class<T> cls) {
        return new SingletonInstance<>(cls);
    }

    /**
     * Create an {@link Supplier} that supplies a
     * new instances of the specified Class.
     * <p>
     * The Class provided must have a no-args default constructor.
     * <p>
     * A new instance of the specified Class is created for every
     * call to {@link Supplier#get()}.
     *
     * @param cls  the Class of the singleton instance to supply
     * @param <T>       the type of instance supplied
     * @return  the singleton instance of the specified Class
     *
     * @throws java.lang.NullPointerException if the class is null
     */
    static <T> Supplier<T> create(Class<T> cls) {
        return new NewInstance<>(cls);
    }

    /**
     * A {@link Supplier} implementation that supplies new instances
     * of a class each time its {@link Supplier#get() get()} method
     * is called.
     *
     * @param <T> the type of instance supplied
     */
    class NewInstance<T>
            implements Supplier<T> {

        private final Class<T> instanceClass;

        protected NewInstance(Class<T> instanceClass) {
            this.instanceClass = Objects.requireNonNull(instanceClass);
        }

        @Override
        public T get() {
            try {
                return instanceClass.newInstance();
            } catch (Throwable e) {
                throw Status.INTERNAL.withCause(e).asRuntimeException();
            }
        }
    }

    /**
     * A {@link Supplier} implementation that supplies the same singleton
     * instance of a value each time its {@link Supplier#get() get()}
     * method is called.
     *
     * @param <T> the type of instance supplied
     */
    class SingletonInstance<T>
            implements Supplier<T> {

        private final Class<? extends T> instanceClass;

        private T instance;

        private SingletonInstance(Class<T> instanceClass) {
            this.instanceClass = instanceClass;
        }

        private SingletonInstance(T instance) {
            this.instanceClass = null;
            this.instance = Objects.requireNonNull(instance);
        }

        @Override
        public T get() {
            return ensureInstance();
        }

        private synchronized T ensureInstance() {
            if (instance == null) {
                try {
                    instance = instanceClass.newInstance();
                } catch (Throwable e) {
                    throw Status.INTERNAL.withCause(e).asRuntimeException();
                }
            }
            return instance;
        }
    }
}
