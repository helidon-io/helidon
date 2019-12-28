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
import java.util.concurrent.Flow.Publisher;

import io.helidon.common.mapper.Mapper;

/**
 * Processor of {@link Single} to {@link Publisher} that expands the first (and
 * only) item to a publisher.
 *
 * @param <T> subscribed type
 * @param <U> published type
 */
final class SingleMultiMappingProcessor<T, U> extends BaseProcessor<T, U> implements Multi<U> {

    private Publisher<U> delegate;
    private final Mapper<T, Publisher<U>> mapper;

    SingleMultiMappingProcessor(Mapper<T, Publisher<U>> mapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper is null!");
    }

    @Override
    protected void hookOnNext(T item) {
        Publisher<U> value = mapper.map(item);
        if (value == null) {
            onError(new IllegalStateException("Mapper returned a null value"));
        } else {
            delegate = value;
        }
    }

    @Override
    protected void hookOnComplete() {
        if (delegate != null) {
            doSubscribe(delegate);
        }
    }
}
