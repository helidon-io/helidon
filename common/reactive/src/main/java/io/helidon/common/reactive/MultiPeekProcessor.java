/*
 * Copyright (c)  2020 Oracle and/or its affiliates. All rights reserved.
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
 *
 */

package io.helidon.common.reactive;

import java.util.function.Consumer;

/**
 * Invoke supplied consumer for every item in the stream.
 *
 * @param <T> both input/output type
 */
public class MultiPeekProcessor<T> extends BaseProcessor<T, T> implements Multi<T> {

    private Consumer<T> consumer;

    private MultiPeekProcessor(Consumer<T> consumer) {
        this.consumer = consumer;
    }

    /**
     * Invoke supplied consumer for every item in the stream.
     *
     * @param consumer supplied consumer to be invoke for every item
     * @param <T>      both input/output type
     * @return {@link MultiPeekProcessor}
     */
    public static <T> MultiPeekProcessor<T> create(Consumer<T> consumer) {
        return new MultiPeekProcessor<>(consumer);
    }

    @Override
    public void onNext(T item) {
        try {
            consumer.accept(item);
            super.onNext(item);
        } catch (Throwable t) {
            cancel();
            complete(t);
        }
    }
}
