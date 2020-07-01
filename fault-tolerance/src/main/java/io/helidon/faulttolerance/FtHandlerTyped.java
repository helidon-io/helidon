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

package io.helidon.faulttolerance;

import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.function.Supplier;

import io.helidon.common.reactive.Multi;
import io.helidon.common.reactive.Single;

/**
 * A typed fault tolerance handler, to allow combination
 * of multiple handlers without losing type information.
 *
 * @param <T> type of future result
 */
public interface FtHandlerTyped<T> {
    /**
     * Invoke this fault tolerance handler on a supplier of a {@link java.util.concurrent.CompletionStage}, such as a
     * {@link io.helidon.common.reactive.Single}.
     *
     * @param supplier that provides the initial value for processing; depending on handler type, the supplier may be called
     *                 multiple times
     * @return a new single that can be consumed that will carry out the fault tolerance in the background, may have a different
     *  error states than the original result obtained from supplier
     */
    Single<T> invoke(Supplier<? extends CompletionStage<T>> supplier);

    /**
     * Invoke this fault tolerance handler on a supplier of a {@link java.util.concurrent.Flow.Publisher}, such as a
     * {@link io.helidon.common.reactive.Multi}.
     *
     * @param supplier that provides the initial value for processing; depending on handler type, the supplier may be called
     *                 multiple times
     * @return a new multi that can be consumed that will carry out the fault tolerance in the background, may have a different
     *  error states than the original result obtained from supplier
     */
    Multi<T> invokeMulti(Supplier<? extends Flow.Publisher<T>> supplier);
}
