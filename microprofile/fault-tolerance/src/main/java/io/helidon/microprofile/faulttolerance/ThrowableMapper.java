/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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

package io.helidon.microprofile.faulttolerance;

import java.util.Arrays;
import java.util.concurrent.ExecutionException;

import org.eclipse.microprofile.faulttolerance.exceptions.BulkheadException;
import org.eclipse.microprofile.faulttolerance.exceptions.CircuitBreakerOpenException;
import org.eclipse.microprofile.faulttolerance.exceptions.TimeoutException;

/**
 * Maps Helidon to MP exceptions.
 */
class ThrowableMapper {

    private ThrowableMapper() {
    }

    /**
     * Maps a {@code Throwable} in Helidon to its corresponding type in the MP
     * FT API.
     *
     * @param t throwable to map.
     * @return mapped throwable.
     */
    static Throwable map(Throwable t) {
        if (t instanceof ExecutionException) {
            t = t.getCause();
        }
        if (t instanceof io.helidon.faulttolerance.CircuitBreakerOpenException) {
            return new CircuitBreakerOpenException(t.getMessage(), t.getCause());
        }
        if (t instanceof io.helidon.faulttolerance.BulkheadException) {
            return new BulkheadException(t.getMessage(), t.getCause());
        }
        if (t instanceof io.helidon.faulttolerance.RetryTimeoutException) {
            return t;       // the cause if handled elsewhere
        }
        if (t instanceof java.util.concurrent.TimeoutException) {
            return new TimeoutException(t.getMessage(), t.getCause());
        }
        if (t instanceof java.lang.InterruptedException) {
            return new TimeoutException(t.getMessage(), t.getCause());
        }
        return t;
    }

    /**
     * Maps exception types in MP FT to internal ones used by Helidon. Allocates
     * new array for the purpose of mapping.
     *
     * @param types array of {@code Throwable}'s type to map.
     * @return mapped array.
     */
    static Class<? extends Throwable>[] mapTypes(Class<? extends Throwable>[] types) {
        if (types.length == 0) {
            return types;
        }
        Class<? extends Throwable>[] result = Arrays.copyOf(types, types.length);
        for (int i = 0; i < types.length; i++) {
            Class<? extends Throwable> t = types[i];
            if (t == BulkheadException.class) {
                result[i] = io.helidon.faulttolerance.BulkheadException.class;
            } else if (t == CircuitBreakerOpenException.class) {
                result[i] = io.helidon.faulttolerance.CircuitBreakerOpenException.class;
            } else if (t == TimeoutException.class) {
                result[i] = java.util.concurrent.TimeoutException.class;
            } else {
                result[i] = t;
            }
        }
        return result;
    }
}
