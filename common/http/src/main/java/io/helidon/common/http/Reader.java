/*
 * Copyright (c) 2018, 2020 Oracle and/or its affiliates.
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

import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.function.BiFunction;

/**
 * The Reader transforms a {@link DataChunk} publisher into a completion stage of the associated type.
 *
 * @param <R> the requested type
 * @deprecated since 2.0.0, use {@code io.helidon.media.common.MessageBodyReader} instead
 */
@FunctionalInterface
@Deprecated
public interface Reader<R> extends BiFunction<Flow.Publisher<DataChunk>, Class<? super R>, CompletionStage<? extends R>> {

    /**
     * Transforms a publisher into a completion stage.
     * If an exception is thrown, the resulting completion stage of
     * {@link Content#as(Class)} method call ends exceptionally.
     *
     * @param publisher the publisher to transform
     * @param clazz     the requested type to be returned as a completion stage. The purpose of
     *                  this parameter is to know what the user of this Reader actually requested.
     * @return the result as a completion stage
     */
    @Override
    CompletionStage<? extends R> apply(Flow.Publisher<DataChunk> publisher, Class<? super R> clazz);

    /**
     * Transforms a publisher into a completion stage.
     * If an exception is thrown, the resulting completion stage of
     * {@link Content#as(Class)} method call ends exceptionally.
     * <p>
     * The default implementation calls {@link #apply(Flow.Publisher, Class)} with {@link Object} as
     * the class parameter.
     *
     * @param publisher the publisher to transform
     * @return the result as a completion stage
     */
    default CompletionStage<? extends R> apply(Flow.Publisher<DataChunk> publisher) {
        return apply(publisher, Object.class);
    }

    /**
     * Transforms a publisher into a completion stage.
     * If an exception is thrown, the resulting completion stage of
     * {@link Content#as(Class)} method call ends exceptionally.
     * <p>
     * The default implementation calls {@link #apply(Flow.Publisher, Class)} with {@link Object} as
     * the class parameter.
     *
     * @param publisher the publisher to transform
     * @param type      the desired type to cast the guarantied {@code R} type to
     * @param <T>       the desired type to cast the guarantied {@code R} type to
     * @return the result as a completion stage which might end exceptionally with
     * {@link ClassCastException} if the {@code R} type wasn't possible to cast
     * to {@code T}
     */
    @SuppressWarnings("unchecked")
    default <T extends R> CompletionStage<? extends T> applyAndCast(Flow.Publisher<DataChunk> publisher, Class<T> type) {
        // if this was implemented as (CompletionStage<? extends T>) apply(publisher, (Class<R>) clazz);
        // the class cast exception might occur outside of the completion stage which might be confusing
        return apply(publisher, (Class<R>) type).thenApply(type::cast);
    }
}
