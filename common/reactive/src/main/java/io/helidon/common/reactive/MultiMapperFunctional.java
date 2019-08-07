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
package io.helidon.common.reactive;

import java.util.Objects;
import java.util.function.Function;

/**
 * Implementation of {@link MultiMapper} backed by a java function for mapping
 * the items.
 *
 * @param <T> subscribed type
 * @param <U> published type
 */
final class MultiMapperFunctional<T, U>
        extends MultiMapper<T, U> {

    private final Function<? super T, ? extends U> function;

    MultiMapperFunctional(Function<? super T, ? extends U> function) {
        this.function = Objects.requireNonNull(function,
                "function cannot be null!");
    }

    @Override
    public U mapNext(T item) {
        return function.apply(item);
    }
}
