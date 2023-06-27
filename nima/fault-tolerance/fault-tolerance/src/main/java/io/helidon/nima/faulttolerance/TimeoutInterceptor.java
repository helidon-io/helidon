/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.nima.faulttolerance;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

import io.helidon.common.Weight;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.TypedElementInfo;
import io.helidon.pico.api.ClassNamed;
import io.helidon.pico.api.PicoServices;

import jakarta.inject.Singleton;

@ClassNamed(FaultTolerance.Timeout.class)
@Weight(FaultTolerance.WEIGHT_TIMEOUT)
@Singleton
class TimeoutInterceptor extends InterceptorBase<Timeout> {
    TimeoutInterceptor() {
        super(PicoServices.realizedServices(), Timeout.class, FaultTolerance.Timeout.class);
    }

    @Override
    Timeout obtainHandler(TypedElementInfo elementInfo, CacheRecord cacheRecord) {
        return namedHandler(elementInfo, this::fromAnnotation);
    }

    private Timeout fromAnnotation(Annotation annotation) {
        String name = annotation.getValue("name").orElse("timeout-") + System.identityHashCode(annotation);
        long timeout = annotation.getValue("time").map(Long::parseLong).orElse(10L);
        ChronoUnit unit = annotation.getValue("timeUnit").map(ChronoUnit::valueOf).orElse(ChronoUnit.SECONDS);
        boolean currentThread = annotation.getValue("currentThread").map(Boolean::parseBoolean).orElse(false);

        return TimeoutConfig.builder()
                .name(name)
                .timeout(Duration.of(timeout, unit))
                .currentThread(currentThread)
                .build();
    }
}
