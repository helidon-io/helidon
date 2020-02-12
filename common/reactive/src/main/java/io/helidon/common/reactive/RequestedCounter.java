/*
 * Copyright (c) 2017, 2020 Oracle and/or its affiliates. All rights reserved.
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

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Requested event counter.
 *
 * This utility class helps to safely track and tryProcess the back-pressure of
 * {@link java.util.concurrent.Flow.Subscriber}s.
 */
public class RequestedCounter {

    private final AtomicLong requested = new AtomicLong();

    /**
     * Increments safely a requested event counter to prevent {@code Long.MAX_VALUE} overflow.
     *
     * @param increment amount of additional events to request.
     * @param errorHandler a consumer of {@code IllegalArgumentException} to
     * process errors
     */
    public void increment(long increment, Consumer<? super IllegalArgumentException> errorHandler) {
        if (!StreamValidationUtils.checkRequestParam(increment, errorHandler)) {
            return;
        }

        requested.updateAndGet(original -> {
            if (original == Long.MAX_VALUE) {
                return Long.MAX_VALUE;
            }

            long r = original + increment;
            // HD 2-12 Overflow iff both arguments have the opposite sign of the result; inspired by Math.addExact(long, long)
            if (r == Long.MAX_VALUE || ((original ^ r) & (increment ^ r)) < 0) {
                // unbounded reached
                return Long.MAX_VALUE;
            } else {
                return r;
            }
        });
    }

    /**
     * Tries to safely decrement a <em>positive</em> requested counter value, making sure the value does not drop below zero.
     *
     * @return {@code true} if the initial positive value has been decremented successfully, {@code false} in case the initial
     * counter value was already set to zero.
     */
    public boolean tryDecrement() {
        return requested.getAndUpdate(val -> {
                            if (val == Long.MAX_VALUE) {
                                return val;
                            } else if (val > 0) {
                                return val - 1;
                            } else {
                                return 0;
                            }
                        }) > 0;
    }

    /**
     * Gets the current requested event counter value.
     *
     * @return current value of the requested event counter.
     */
    public long get() {
        return requested.get();
    }
}
