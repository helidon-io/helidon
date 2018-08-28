/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
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

import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * A wrapper on top of {@code Optional} to replicate some of the new Java9
 *  methods.
 * @param <T> the type of the underlying optional value
 */
public class OptionalHelper<T> {

    private Optional<T> optional;

    private OptionalHelper(Optional<T> optional){
        Objects.requireNonNull(optional);
        this.optional = optional;
    }

    /**
     * Static factory method to create a new {@code OptionalHelper} instance.
     * @param <T> the type of the underly optional value
     * @param optional the optional to wrap
     * @return the created {@code OptionalHelper} instance
     */
    public static <T> OptionalHelper<T> from(Optional<T> optional){
        return new OptionalHelper<>(optional);
    }

    /**
     * Get the underlying {@code Optional} instance.
     * @return the wrapped {@code Optional}
     */
    public Optional<T> asOptional(){
        return optional;
    }

    /**
     * If the underlying {@code Optional} does not have a value, set it to the
     * {@code Optional} produced by the supplying function.
     *
     * @param supplier the supplying function that produces an {@code Optional}
     * @return returns this instance of {@code OptionalHelper} with the same
     *         the underlying {@code Optional} if a value is present, otherwise
     *         with the {@code Optional} produced by the supplying function.
     * @throws NullPointerException if the supplying function is {@code null} or
     *         produces a {@code null} result
     */
    public OptionalHelper<T> or(Supplier<? extends Optional<T>> supplier){
        Objects.requireNonNull(supplier);
        if (!optional.isPresent()) {
            Optional<T> supplied = supplier.get();
            Objects.requireNonNull(supplied);
            optional = supplied;
        }
        return this;
    }

    /**
     * If a value is present, performs the given action with the value,
     * otherwise performs the given empty-based action.
     *
     * @param action the action to be performed, if a value is present
     * @param emptyAction the empty-based action to be performed, if no value is
     *        present
     * @throws NullPointerException if a value is present and the given action
     *         is {@code null}, or no value is present and the given empty-based
     *         action is {@code null}.
     */
    public void ifPresentOrElse(Consumer<T> action, Runnable emptyAction) {
        if (optional.isPresent()) {
            action.accept(optional.get());
        } else {
            emptyAction.run();
        }
    }

    /**
     * If a value is present, returns a sequential {@link Stream} containing
     * only that value, otherwise returns an empty {@code Stream}.
     *
     * @return the optional value as a {@code Stream}
     */
    public Stream<T> stream(){
        if (!optional.isPresent()) {
            return Stream.empty();
        } else {
            return Stream.of(optional.get());
        }
    }
}
