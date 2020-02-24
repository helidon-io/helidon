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

import java.util.Objects;
import java.util.concurrent.Flow.Publisher;

import io.helidon.common.mapper.Mapper;

/**
 * Processor of {@link Publisher} to {@link Single} that publishes and maps each received item.
 *
 * @param <T> subscribed type
 * @param <U> published type
 */
public final class MultiMapProcessor<T, U> extends BaseProcessor<T, U> implements Multi<U> {

    private final Mapper<T, U> mapper;

    private MultiMapProcessor(Mapper<T, U> mapper) {
        super();
        this.mapper = Objects.requireNonNull(mapper, "mapper is null!");
    }

    /**
     * Processor of {@link Publisher} to {@link Single} that publishes and maps each received item.
     *
     * @param mapper supplied for all items to be mapped with
     * @param <T>    subscribed type
     * @param <U>    published type
     * @return {@link MultiMapProcessor}
     */
    public static <T, U> MultiMapProcessor<T, U> create(Mapper<T, U> mapper) {
        return new MultiMapProcessor<T, U>(mapper);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void submit(T item) {
        U value = null;
        try {
            value = mapper.map(item);
        } catch (Throwable t) {
            getSubscription().cancel();
            onError(t);
            return;
        }
        if (value == null) {
            getSubscription().cancel();
            onError(new NullPointerException("Mapper returned a null value"));
            return;
        }
        getSubscriber().onNext((U) value);
    }
}
