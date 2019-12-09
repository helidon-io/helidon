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
package io.helidon.microprofile.reactive;

import java.util.Objects;

import io.helidon.common.mapper.Mapper;
import io.helidon.common.reactive.Flow;
import io.helidon.common.reactive.RSCompatibleProcessor;

/**
 * Processor of {@link io.helidon.common.reactive.Flow.Publisher} to {@link io.helidon.common.reactive.Single} that publishes and maps each received item.
 *
 * @param <T> subscribed type
 * @param <U> published type
 */
public final class MapProcessor<T, U> extends RSCompatibleProcessor<T, U> {

    private final Mapper<T, U> mapper;

    /**
     * Processor of {@link io.helidon.common.reactive.Flow.Publisher} to {@link io.helidon.common.reactive.Single} that publishes and maps each received item.
     *
     * @param mapper supplied for all items to be mapped with
     */
    public MapProcessor(Mapper<T, U> mapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper is null!");
    }

    @Override
    protected void hookOnCancel(Flow.Subscription subscription) {
        subscription.cancel();
    }

    @Override
    protected void hookOnNext(T item) {
        U value = mapper.map(item);
        if (value == null) {
            getSubscription().get().cancel();
            onError(new NullPointerException("Mapper returned a null value"));
        } else {
            submit(value);
        }
    }
}
