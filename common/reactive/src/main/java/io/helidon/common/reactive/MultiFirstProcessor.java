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

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Processor of {@code Multi<T>} to {@code Single<T>}.
 *
 * @param <T> item type
 */
final class MultiFirstProcessor<T> extends BaseProcessor<T, T> implements Single<T> {

    private AtomicBoolean nextCalled = new AtomicBoolean(false);

    private MultiFirstProcessor() {
    }

    static <T> MultiFirstProcessor<T> create() {
        return new MultiFirstProcessor<>();
    }

    @Override
    public void onNext(T item) {
        if (!nextCalled.getAndSet(true)) {
            super.onNext(item);
        }
    }

    @Override
    protected void hookOnNext(T item) {
        submit(item);
        onComplete();
    }
}
