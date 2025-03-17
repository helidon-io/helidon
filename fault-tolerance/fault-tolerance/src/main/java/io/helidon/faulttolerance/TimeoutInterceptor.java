/*
 * Copyright (c) 2023, 2025 Oracle and/or its affiliates.
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

import java.time.Duration;

import io.helidon.common.Weight;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.TypedElementInfo;
import io.helidon.service.registry.Service;
import io.helidon.service.registry.ServiceRegistry;

@Service.NamedByType(FaultTolerance.Timeout.class)
@Weight(FaultTolerance.WEIGHT_TIMEOUT)
@Service.Singleton
class TimeoutInterceptor extends InterceptorBase<Timeout> {
    TimeoutInterceptor(ServiceRegistry registry) {
        super(registry, Timeout.class, FaultTolerance.Timeout.class);
    }

    @Override
    Timeout obtainHandler(TypedElementInfo elementInfo, CacheRecord cacheRecord) {
        return namedHandler(elementInfo, this::fromAnnotation);
    }

    private Timeout fromAnnotation(Annotation annotation) {
        String name = annotation.getValue("name").orElse("timeout-") + System.identityHashCode(annotation);
        Duration timeout = annotation.getValue("time").map(Duration::parse).orElseGet(() -> Duration.ofSeconds(10));
        boolean currentThread = annotation.getValue("currentThread").map(Boolean::parseBoolean).orElse(false);

        return TimeoutConfig.builder()
                .name(name)
                .timeout(timeout)
                .currentThread(currentThread)
                .build();
    }
}
