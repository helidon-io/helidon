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

import java.util.function.Predicate;

/**
 * Processor filtering stream with supplied predicate.
 *
 * @param <T> both input/output type
 */
public class MultiFilterProcessor<T> extends BufferedProcessor<T, T> implements Multi<T> {

    private Predicate<T> predicate;

    private MultiFilterProcessor(Predicate<T> predicate) {
        this.predicate = predicate;
    }

    /**
     * Processor filtering stream with supplied predicate.
     *
     * @param predicate provided predicate to filter stream with
     * @param <T>       both input/output type
     * @return {@link MultiFilterProcessor}
     */
    public static <T> MultiFilterProcessor<T> create(Predicate<T> predicate) {
        return new MultiFilterProcessor<>(predicate);
    }

    @Override
    protected void hookOnNext(T item) {
        if (predicate.test(item)) {
            submit(item);
        } else {
            tryRequest(getSubscription().get());
        }
    }
}
