/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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

import org.eclipse.microprofile.faulttolerance.exceptions.BulkheadException;
import org.eclipse.microprofile.faulttolerance.exceptions.CircuitBreakerOpenException;
import org.eclipse.microprofile.faulttolerance.exceptions.TimeoutException;

/**
 * Maps Helidon to MP exceptions.
 */
class ThrowableMapper {

    private ThrowableMapper() {
    }

    static RuntimeException map(Throwable t) {
        if (t instanceof io.helidon.faulttolerance.CircuitBreakerOpenException) {
            return new CircuitBreakerOpenException(t.getMessage(), t.getCause());
        }
        if (t instanceof io.helidon.faulttolerance.BulkheadException) {
            return new BulkheadException(t.getMessage(), t.getCause());
        }
        if (t instanceof java.util.concurrent.TimeoutException) {
            return new TimeoutException(t.getMessage(), t.getCause());
        }
        if (t instanceof java.lang.InterruptedException) {
            return new TimeoutException(t.getMessage(), t.getCause());
        }
        return new RuntimeException(t);
    }
}
