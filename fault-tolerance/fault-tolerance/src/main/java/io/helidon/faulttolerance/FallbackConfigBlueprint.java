/*
 * Copyright (c) 2023, 2025 Oracle and/or its affiliates.
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

package io.helidon.faulttolerance;

import java.util.Set;
import java.util.function.Function;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;

/**
 * {@link Fallback} configuration.
 *
 * @param <T> return type of the fallback method
 */
@Prototype.Blueprint
interface FallbackConfigBlueprint<T> {
    /**
     * A fallback function.
     *
     * @return fallback function to obtain alternative result
     */
    Function<Throwable, ? extends T> fallback();

    /**
     * These throwables will not be considered retriable, all other will.
     *
     * @return throwable classes to skip retries
     * @see #applyOn()
     */
    @Option.Singular
    Set<Class<? extends Throwable>> skipOn();

    /**
     * These throwables will be considered retriable.
     *
     * @return throwable classes to trigger retries
     * @see #skipOn()
     */
    @Option.Singular
    Set<Class<? extends Throwable>> applyOn();
}
