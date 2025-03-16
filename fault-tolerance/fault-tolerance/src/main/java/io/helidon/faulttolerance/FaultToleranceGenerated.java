/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

import io.helidon.service.registry.Service;

/**
 * Types used only in generated code (and of course when reading the generated code).
 */
public final class FaultToleranceGenerated {
    private FaultToleranceGenerated() {
    }

    /**
     * A generated service common interface.
     */
    public interface FtMethod {
    }

    /**
     * A generated service to support fallback without reflection.
     *
     * @param <T> type of the response of the method
     * @param <S> type of the service that hosts the method
     */
    @Service.Contract
    public interface FallbackMethod<T, S> extends FtMethod {
        /**
         * Fallback method generated based on the {@link io.helidon.faulttolerance.FaultTolerance.Fallback} annotation.
         * This generated type will check if the throwable should be handled or not, and either throws it, or executes the fallback.
         *
         * @param service service instance
         * @param throwable throwable thrown by the original code (if check, it is wrapped in a runtime exception)
         * @param arguments original arguments to the method
         * @return result obtained from the fallback method (or throws the throwable if fallback should not be done)
         */
        T fallback(S service, Throwable throwable, Object... arguments) throws Throwable;
    }

    /**
     * A generated service to support retries without resorting to Class.forName() for exception types.
     */
    @Service.Contract
    public interface RetryMethod extends FtMethod {
        /**
         * Provide a retry instance that should be used with this method.
         * If the retry annotation contains a name, we will attempt to obtain the named instance from
         * registry. If such a named instance does not exist a new retry will be created from the annotation.
         *
         * @return retry instance
         */
        Retry retry();
    }

    /**
     * A generated service to support circuit breaker without resorting to Class.forName() for exception types.
     */
    @Service.Contract
    public interface CircuitBreakerMethod extends FtMethod {
        /**
         * Provide a circuit breaker instance that should be used with this method.
         * If the {@link io.helidon.faulttolerance.FaultTolerance.CircuitBreaker} annotation contains a name, we will attempt to obtain the named instance from
         * registry. If such a named instance does not exist a new circuit breaker will be created from the annotation.
         *
         * @return circuit breaker instance
         */
        CircuitBreaker circuitBreaker();
    }
}
