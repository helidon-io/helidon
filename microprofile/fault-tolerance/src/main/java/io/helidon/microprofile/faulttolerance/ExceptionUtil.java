/*
 * Copyright (c) 2018, 2019 Oracle and/or its affiliates. All rights reserved.
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

import com.netflix.hystrix.exception.HystrixRuntimeException;

/**
 * Class ExceptionUtil.
 */
public class ExceptionUtil {

    /**
     * Exception used internally to propagate other exceptions.
     */
    static class WrappedException extends RuntimeException {
        WrappedException(Throwable t) {
            super(t);
        }
    }

    /**
     * Wrap throwable into {@code Exception}.
     *
     * @param throwable The throwable.
     * @return A {@code RuntimeException}.
     */
    public static Exception toException(Throwable throwable) {
        return throwable instanceof Exception ? (Exception) throwable
                : new RuntimeException(throwable);
    }

    /**
     * Wrap throwable into {@code RuntimeException}.
     *
     * @param throwable The throwable.
     * @return A {@code RuntimeException}.
     */
    public static WrappedException toWrappedException(Throwable throwable) {
        return throwable instanceof WrappedException ? (WrappedException) throwable
                : new WrappedException(throwable);
    }

    /**
     * Unwrap an throwable wrapped by {@code HystrixRuntimeException}.
     *
     * @param throwable Throwable to unwrap.
     * @return Unwrapped throwable.
     */
    public static Throwable unwrapHystrix(Throwable throwable) {
        return throwable instanceof HystrixRuntimeException ? throwable.getCause() : throwable;
    }

    private ExceptionUtil() {
    }
}
