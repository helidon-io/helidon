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

package io.helidon.common;

import java.util.function.Supplier;

/**
 * Interface for builders, to be able to accept a builder in addition to an instance.
 * <p>
 * This interface is similar to {@link java.util.function.Supplier} as it provides an instance, only for classes that act
 * as instance builders (fluent API builder pattern), where method {@link java.util.function.Supplier#get()} would be
 * misleading.
 *
 * @param <T> Type of the built instance
 */
@FunctionalInterface
public interface Builder<T> extends Supplier<T> {
    /**
     * Build the instance from this builder.
     *
     * @return instance of the built type
     */
    T build();

    @Override
    default T get() {
        return build();
    }
}

