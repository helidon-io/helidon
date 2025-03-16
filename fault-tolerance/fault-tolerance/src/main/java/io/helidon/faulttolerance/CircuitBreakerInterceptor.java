/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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

import io.helidon.common.Weight;
import io.helidon.common.types.TypedElementInfo;
import io.helidon.service.registry.Service;
import io.helidon.service.registry.ServiceRegistry;

@Service.NamedByType(FaultTolerance.CircuitBreaker.class)
@Weight(FaultTolerance.WEIGHT_CIRCUIT_BREAKER)
@Service.Singleton
class CircuitBreakerInterceptor extends InterceptorBase<CircuitBreaker> {
    CircuitBreakerInterceptor(ServiceRegistry registry) {
        super(registry, CircuitBreaker.class, FaultTolerance.CircuitBreaker.class);
    }

    @SuppressWarnings("OptionalGetWithoutIsPresent")
    @Override
    CircuitBreaker obtainHandler(TypedElementInfo elementInfo, InterceptorBase.CacheRecord cacheRecord) {
        return generatedMethod(FaultToleranceGenerated.CircuitBreakerMethod.class, cacheRecord)
                .map(FaultToleranceGenerated.CircuitBreakerMethod::circuitBreaker)
                .orElseGet(() -> lookup(CircuitBreaker.class).get());
    }
}
