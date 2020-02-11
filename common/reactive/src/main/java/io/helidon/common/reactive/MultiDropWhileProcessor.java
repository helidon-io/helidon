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
 * Drop the longest prefix of elements from this stream that satisfy the given predicate.
 *
 * @param <T> Item type
 */
public class MultiDropWhileProcessor<T> extends MultiFilterProcessor<T> implements Multi<T> {

    private boolean foundNotMatching = false;

    private MultiDropWhileProcessor(Predicate<T> predicate) {
        super.setPredicate(item -> {
            try {
                if (foundNotMatching) return true;
                foundNotMatching = !predicate.test(item);
                if (foundNotMatching) return true;
                return false;
            } catch (Throwable t) {
                cancel();
                complete(t);
            }
            return false;
        });
    }

    /**
     * Drop the longest prefix of elements from this stream that satisfy the given predicate.
     *
     * @param <T>       Item type
     * @param predicate provided predicate to filter stream with
     * @return {@link MultiDropWhileProcessor}
     */
    public static <T> MultiDropWhileProcessor<T> create(Predicate<T> predicate) {
        return new MultiDropWhileProcessor<>(predicate);
    }
}
