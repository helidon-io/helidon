/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates. All rights reserved.
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

import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

/**
 * A completion stage that allows processing of cases when the element
 * is present and when not.
 *
 * @param <T> return type of the asynchronous operation
 */
public interface OptionalCompletionStage<T> extends CompletionStage<Optional<T>> {

    /**
     * Returns a new {@code OptionalCompletionStage} that, when this stage completes
     * normally and returns {@code null}, executes the given action.
     *
     * @param action the action to perform before completing the
     *                 returned {@code OptionalCompletionStage}
     * @return the new {@code OptionalCompletionStage}
     */
    OptionalCompletionStage<T> onEmpty(Runnable action);

    /**
     * Returns a new {@code OptionalCompletionStage} that, when this stage completes
     * normally and returns non-{@code null}, is executed with this stage's
     * result as the argument to the supplied action.
     *
     * @param action the action to perform before completing the
     *               returned {@code OptionalCompletionStage}
     * @return the new {@code OptionalCompletionStage}
     */
    OptionalCompletionStage<T> onValue(Consumer<? super T> action);

    /**
     * Creates a new instance of the completion stage that allows processing of cases when the element
     * is present and when not.
     *
     * @param <T> return type of the asynchronous operation
     * @param originalStage source completion stage instance
     * @return the new {@code OptionalCompletionStage}
     */
    static <T> OptionalCompletionStage<T> create(CompletionStage<Optional<T>> originalStage) {
        return new OptionalCompletionStageImpl<>(originalStage);
    }

}
