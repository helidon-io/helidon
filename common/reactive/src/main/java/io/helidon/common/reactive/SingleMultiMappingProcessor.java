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

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.concurrent.Flow.Publisher;
import java.util.function.Function;

/**
 * Processor of {@link Single} to {@link Publisher} that expands the first (and
 * only) item to a publisher.
 *
 * @param <T> subscribed type
 * @param <U> published type
 */
final class SingleMultiMappingProcessor<T, U> extends MultiFlatMapProcessor<T, U> implements Multi<U> {

    private CompletableFuture<Flow.Subscriber<? super U>> subscriberFuture = new CompletableFuture<>();

    private SingleMultiMappingProcessor() {
        super();
    }

    static <T, U> SingleMultiMappingProcessor<T, U> create() {
        return new SingleMultiMappingProcessor<>();
    }

    @Override
    public SingleMultiMappingProcessor<T, U> mapper(Function<T, Publisher<U>> mapper) {
        super.mapper(mapper);
        return this;
    }

    @Override
    public void subscribe(Flow.Subscriber<? super U> subscriber) {
        subscriberFuture.complete(subscriber);
        super.subscribe(subscriber);
    }

    @Override
    public void onComplete() {
        subscriberFuture.whenComplete((s, t) -> {
            if (Objects.isNull(getError())) {
                s.onComplete();
            }
        });
    }
}
