/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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

import java.util.List;
import java.util.function.Supplier;

/**
 * Wrapper for list of {@link io.helidon.common.LazyValue}s while keeping laziness.
 *
 * @param <T> type of the provided object
 */
public interface LazyList<T> extends List<T> {

    /**
     * Add another lazy item to the list.
     *
     * @param supplier to be invoke only when necessary
     */
    void add(Supplier<T> supplier);

    /**
     * Create wrapper from provided list of {@link io.helidon.common.LazyValue}s.
     *
     * @param lazyValues to be wrapped seamlessly while keeping laziness
     * @param <T>        type of the provided object
     * @return List invoking underlined {@link io.helidon.common.LazyValue}s only when raw value is needed.
     */
    static <T> LazyList<T> create(List<LazyValue<T>> lazyValues) {
        return new LazyListImpl<>(lazyValues);
    }
}
