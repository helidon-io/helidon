/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.common;

import java.util.function.Supplier;

/**
 * A typed supplier that wraps another supplier and only retrieves the value on the first
 * request to {@link #get()}, caching the value for all subsequent invocations.
 * <p>
 * <b>Helidon implementations obtained through {@link #create(java.util.function.Supplier)}
 *  and {@link #create(Object)} are guaranteed to be thread safe.</b>
 *
 * @param <T> type of the provided object
 */
@FunctionalInterface
public interface LazyValue<T> extends Supplier<T> {
    /**
     * Create a lazy value from a supplier.
     * @param supplier supplier to get the value from
     * @param <T> type of the value
     * @return a lazy value that will obtain the value from supplier on first call to {@link #get()}
     */
    static <T> LazyValue<T> create(Supplier<T> supplier) {
        return new LazyValueImpl<>(supplier);
    }

    /**
     * Create a lazy value from a value.
     *
     * @param value actual value to return
     * @param <T> type of the value
     * @return a lazy value that will always return the value provided
     */
    static <T> LazyValue<T> create(T value) {
        return new LazyValueImpl<>(value);
    }

}
