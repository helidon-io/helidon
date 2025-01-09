/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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
package io.helidon.faulttolerance;

import static org.junit.jupiter.api.Assertions.assertThrows;

class CircuitBreakerBaseTest {

    protected void breakerOpen(CircuitBreaker breaker) {
        Request good = new Request();
        assertThrows(CircuitBreakerOpenException.class, () -> breaker.invoke(good::invoke));
    }

    protected void bad(CircuitBreaker breaker) {
        Failing failing = new Failing(new IllegalStateException("Fail"));
        assertThrows(IllegalStateException.class, () -> breaker.invoke(failing::invoke));
    }

    protected void good(CircuitBreaker breaker) {
        Request good = new Request();
        breaker.invoke(good::invoke);
    }

    protected static class Failing {
        private final RuntimeException exception;

        Failing(RuntimeException exception) {
            this.exception = exception;
        }

        Integer invoke() {
            throw exception;
        }
    }

    protected static class Request {
        Request() {
        }

        Integer invoke() {
            return 1;
        }
    }
}
