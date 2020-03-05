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
 * Take the longest prefix of elements from this stream that satisfy the given predicate.
 *
 * @param <T> Item type
 */
public class MultiTakeWhileProcessor<T> extends MultiFilterProcessor<T> {

    private MultiTakeWhileProcessor(Predicate<T> predicate) {
        super();
        super.setPredicate(item -> {
            if (predicate.test(item)) {
                return true;
            }
            // by design done as part of onNext, so it is thread-safe w.r.t. invocation of any Subscriber methods
            // so complete(...) causes onComplete to be signalled to downstream, and any signals from upstream or
            // downstream during or following this call will be ignored
            cancel();
            complete();
            return false;
        });
    }

    /**
     * Create new {@link MultiTakeWhileProcessor}.
     *
     * @param predicate provided predicate to filter stream with
     * @param <T>       Item type
     * @return {@link MultiTakeWhileProcessor}
     */
    public static <T> MultiTakeWhileProcessor<T> create(Predicate<T> predicate) {
        return new MultiTakeWhileProcessor<>(predicate);
    }

}
