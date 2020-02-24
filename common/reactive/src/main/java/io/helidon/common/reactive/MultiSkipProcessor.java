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

/**
 * Skip first n items, all the others are emitted.
 *
 * @param <T> item type
 */
public class MultiSkipProcessor<T> extends MultiFilterProcessor<T> {

    private long counter = 0;
    private boolean foundNotMatching = false;

    private MultiSkipProcessor(Long skip) {
        super.setPredicate(item -> {
            try {
                if (foundNotMatching) return true;
                foundNotMatching = !(counter++ < skip);
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
     * Create new {@link MultiSkipProcessor}.
     *
     * @param skip number of items to be skipped
     * @param <T>  item type
     * @return {@link MultiSkipProcessor}
     */
    public static <T> MultiSkipProcessor<T> create(Long skip) {
        return new MultiSkipProcessor<T>(skip);
    }
}
