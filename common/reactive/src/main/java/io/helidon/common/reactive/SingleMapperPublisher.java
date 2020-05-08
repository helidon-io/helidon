/*
 * Copyright (c) 2020 Oracle and/or its affiliates. All rights reserved.
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

import java.util.concurrent.Flow;
import java.util.function.Function;

import io.helidon.common.mapper.Mapper;

/**
 * Maps the upstream item via a {@link Mapper} function.
 * @param <T> the upstream value type
 * @param <R> the result value type
 */
final class SingleMapperPublisher<T, R> implements Single<R> {

    private final Flow.Publisher<T> source;

    private final Function<? super T, ? extends R> mapper;

    SingleMapperPublisher(Flow.Publisher<T> source, Function<? super T, ? extends R> mapper) {
        this.source = source;
        this.mapper = mapper;
    }

    @Override
    public void subscribe(Flow.Subscriber<? super R> subscriber) {
        source.subscribe(new MultiMapperPublisher.MapperSubscriber<>(subscriber, mapper));
    }

}
