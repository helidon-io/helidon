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
package io.helidon.dbclient.common;

import java.util.function.Function;

import io.helidon.common.reactive.Flow;

/**
 * A {@link Flow.Processor} that only maps the source type to target type using a mapping function.
 *
 * @param <SOURCE> type of the publisher we subscribe to
 * @param <TARGET> type of the publisher we expose
 */
public final class MappingProcessor<SOURCE, TARGET> implements Flow.Processor<SOURCE, TARGET> {
    private final Function<SOURCE, TARGET> resultMapper;
    private Flow.Subscriber<? super TARGET> mySubscriber;
    private Flow.Subscription subscription;

    private MappingProcessor(Function<SOURCE, TARGET> resultMapper) {
        this.resultMapper = resultMapper;
    }

    /**
     * Create a mapping processor for a mapping function.
     * @param mappingFunction function that maps source to target (applied for each record)
     * @param <S> Source type
     * @param <T> Target type
     * @return a new mapping processor
     */
    public static <S, T> MappingProcessor<S, T> create(Function<S, T> mappingFunction) {
        return new MappingProcessor<>(mappingFunction);
    }

    @Override
    public void subscribe(Flow.Subscriber<? super TARGET> subscriber) {
        this.mySubscriber = subscriber;
        subscriber.onSubscribe(new Flow.Subscription() {
            @Override
            public void request(long n) {
                if (null != subscription) {
                    subscription.request(n);
                }
            }

            @Override
            public void cancel() {
                if (null != subscription) {
                    subscription.cancel();
                }
            }
        });
    }

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
        this.subscription = subscription;
    }

    @Override
    public void onNext(SOURCE item) {
        mySubscriber.onNext(resultMapper.apply(item));
    }

    @Override
    public void onError(Throwable throwable) {
        mySubscriber.onError(throwable);
    }

    @Override
    public void onComplete() {
        mySubscriber.onComplete();
    }
}
