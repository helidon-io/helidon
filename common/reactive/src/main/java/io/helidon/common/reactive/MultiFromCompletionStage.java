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
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;

/**
 * Signal the outcome of the give CompletionStage.
 * @param <T> the element type of the source and result
 */
final class MultiFromCompletionStage<T> implements Multi<T> {

    private final CompletionStage<T> source;

    private final boolean nullMeansEmpty;

    MultiFromCompletionStage(CompletionStage<T> source, boolean nullMeansEmpty) {
        this.source = source;
        this.nullMeansEmpty = nullMeansEmpty;
    }

    @Override
    public void subscribe(Flow.Subscriber<? super T> subscriber) {
        subscribe(subscriber, source, nullMeansEmpty);
    }

    static <T> void subscribe(Flow.Subscriber<? super T> subscriber, CompletionStage<T> source, boolean nullMeansEmpty) {
        AtomicBiConsumer<T> watcher = new AtomicBiConsumer<>();
        CompletionStageSubscription<T> css = new CompletionStageSubscription<>(subscriber, nullMeansEmpty, watcher, source);
        watcher.lazySet(css);

        subscriber.onSubscribe(css);
        source.whenComplete(watcher);
    }

    static final class CompletionStageSubscription<T> extends DeferredScalarSubscription<T> implements BiConsumer<T, Throwable> {

        private final boolean nullMeansEmpty;

        private final AtomicBiConsumer<T> watcher;

        private CompletionStage<T> source;
        CompletionStageSubscription(Flow.Subscriber<? super T> downstream, boolean nullMeansEmpty, AtomicBiConsumer<T> watcher,CompletionStage<T> source) {
            super(downstream);
            this.nullMeansEmpty = nullMeansEmpty;
            this.watcher = watcher;
            this.source = source;
        }

        @Override
        public void accept(T t, Throwable throwable) {
            if (throwable != null) {
                error(throwable);
            } else if (t != null) {
                complete(t);
            } else if (nullMeansEmpty) {
                complete();
            } else {
                error(new NullPointerException("The CompletionStage completed with a null value"));
            }
        }

        @Override
        public void cancel() {
            super.cancel();
            source.toCompletableFuture().cancel(true);
            watcher.getAndSet(null);
        }
    }

    static final class AtomicBiConsumer<T> extends AtomicReference<BiConsumer<T, Throwable>>
    implements BiConsumer<T, Throwable> {

        @Override
        public void accept(T t, Throwable throwable) {
            BiConsumer<T, Throwable> bc = getAndSet(null);
            if (bc != null) {
                bc.accept(t, throwable);
            }
        }
    }
}
