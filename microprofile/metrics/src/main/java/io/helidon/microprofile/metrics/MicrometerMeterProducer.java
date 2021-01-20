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
 *
 */
package io.helidon.microprofile.metrics;

import io.micrometer.core.annotation.Counted;
import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;
import javax.enterprise.inject.spi.InjectionPoint;
import java.lang.annotation.Annotation;

@ApplicationScoped
class MicrometerMeterProducer {

    @Produces
    private Counter produceCounter(MeterRegistry meterRegistry, InjectionPoint ip) {
        Counted counted = getAnnotation(ip, Counted.class);
        if (counted == null) {
            return null;
        }

        boolean recordFailuresOnly = counted.recordFailuresOnly();

        Counter result = builderFromAnnotation(counted)
                .register(meterRegistry);

        // Use recordFailuresOnly in setting up the interceptor

        return result;
    }

    @Produces
    private Timer produceTimer(MeterRegistry meterRegistry, InjectionPoint ip) {
        Timed timed = getAnnotation(ip, Timed.class);
        if (timed == null) {
            return null;
        }
        Timer result = Timer.builder(timed, "method.timer")
                .register(meterRegistry);

        return result;
    }

    private Counter.Builder builderFromAnnotation(Counted annotation) {
        Counter.Builder builder = Counter.builder(annotation.value())
                .description(annotation.description())
                .tags(annotation.extraTags());

        return builder;
    }

    private static <T extends Annotation> T getAnnotation(InjectionPoint ip, Class<T> clazz) {
        T result = ip.getAnnotated().getAnnotation(clazz);
        return clazz.cast(result);
    }
}
