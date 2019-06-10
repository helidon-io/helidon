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
package io.helidon.dbclient;

import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import java.util.function.Function;

import io.helidon.common.GenericType;
import io.helidon.common.reactive.Flow;

/**
 * Execution result containing result set with multiple rows.
 *
 * @param <T> type of the result, starts as {@link io.helidon.dbclient.DbRow}
 */
public interface DbRowResult<T> {
    /**
     * Map this row result using a mapping function.
     *
     * @param mapper mapping function
     * @param <U>    new type of the row result
     * @return row result of the correct type
     */
    <U> DbRowResult<U> map(Function<T, U> mapper);

    /**
     * Map this row result using a configured {@link io.helidon.common.mapper.Mapper} that can
     * map current type to the desired type.
     *
     * <p>
     * The first mapping for row results of type {@link io.helidon.dbclient.DbRow} will also try to locate
     * appropriate mapper using {@link io.helidon.dbclient.DbMapperManager}.
     *
     * @param type class to map values to
     * @param <U>  new type of the row result
     * @return row result of the correct type
     */
    <U> DbRowResult<U> map(Class<U> type);

    /**
     * Map this row result using a configured {@link io.helidon.common.mapper.Mapper} that can
     * map current type to the desired type.
     * <p>
     * The first mapping for row results of type {@link io.helidon.dbclient.DbRow} will also try to locate
     * appropriate mapper using {@link io.helidon.dbclient.DbMapperManager}.
     *
     * @param type generic type to map values to
     * @param <U>  new type of the row result
     * @return row result of the target type
     */
    <U> DbRowResult<U> map(GenericType<U> type);

    /**
     * Get this result as a publisher of rows mapped to the correct type.
     *
     * @return publisher
     */
    Flow.Publisher<T> publisher();

    /**
     * Collect all the results into a list of rows mapped to the correct type.
     * <p><b>This is a dangerous operation, as it collects all results in memory. Use with care.</b>
     * @return future with the list
     */
    CompletionStage<List<T>> collect();

    /**
     * Provide a possibility to consume this row result to support fluent API.
     *
     * @param consumer consumer of this result
     * @return completion stage that completes when the query is finished
     */
    CompletionStage<Void> consume(Consumer<DbRowResult<T>> consumer);
}
