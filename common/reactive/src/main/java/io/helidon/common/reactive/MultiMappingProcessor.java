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

import io.helidon.common.mapper.Mapper;
import io.helidon.common.reactive.Flow.Publisher;

/**
 * Processor of {@link Publisher} to {@link Single} that publishes and maps each received item.
 *
 * @param <T> subscribed type
 * @param <U> published type
 */
final class MultiMappingProcessor<T, U> extends BaseProcessor<T, U> implements Multi<U> {

    private final Mapper<T, U> mapper;

    MultiMappingProcessor(Mapper<T, U> mapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper is null!");
    }

    @Override
    protected void hookOnNext(T item) {
        U value = mapper.map(item);
        if (value == null) {
            onError(new IllegalStateException("Mapper returned a null value"));
        } else {
            submit(value);
        }
    }
}
