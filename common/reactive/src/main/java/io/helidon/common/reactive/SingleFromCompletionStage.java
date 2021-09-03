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

import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;

/**
 * Signal the outcome of the give CompletionStage.
 * @param <T> the element type of the source and result
 */
final class SingleFromCompletionStage<T> extends CompletionSingle<T> {

    private final CompletionStage<T> source;

    private final boolean nullMeansEmpty;

    SingleFromCompletionStage(CompletionStage<T> source, boolean nullMeansEmpty) {
        this.source = source;
        this.nullMeansEmpty = nullMeansEmpty;
    }

    @Override
    public void subscribe(Flow.Subscriber<? super T> subscriber) {
        MultiFromCompletionStage.subscribe(subscriber, source, nullMeansEmpty);
    }

    @Override
    public Single<T> cancel() {
        Single<T> single = super.cancel();
        source.toCompletableFuture().cancel(true);
        return single;
    }
}
