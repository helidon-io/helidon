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
 * Let pass only specified number of items.
 *
 * @param <T> both input/output type
 */
public class MultiLimitProcessor<T> extends MultiFilterProcessor<T> {

    private long counter = 0;
    private Long limit;

    private MultiLimitProcessor(Long limit) {
        super();
        this.limit = limit;
        super.setPredicate(item -> {
            if (limit > counter++) {
                return true;
            }
            // by design done as part of onNext, so it is thread-safe w.r.t. invocation of any Subscriber methods
            // so complete(...) causes onComplete to be signalled to downstream, and any signals from upstream or
            // downstream during or following this call will be ignored
            getSubscription().cancel();
            complete();
            return false;
        });
    }

    /**
     * Processor with specified number of allowed items.
     *
     * @param limit number of items to pass
     * @param <T>   both input/output type
     * @return {@link MultiLimitProcessor}
     */
    public static <T> MultiLimitProcessor<T> create(Long limit) {
        return new MultiLimitProcessor<>(limit);
    }

    @Override
    protected void downstreamSubscribe() {
        super.downstreamSubscribe();
        if (limit == 0) {
            getSubscription().cancel();
            complete();
        }
    }

    @Override
    public void onError(Throwable ex) {
        if (limit > counter) {
            super.onError(ex);
        } else {
            complete();
        }
    }
}
