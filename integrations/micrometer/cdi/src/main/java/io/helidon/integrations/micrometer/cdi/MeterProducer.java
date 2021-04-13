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
 *
 */
package io.helidon.integrations.micrometer.cdi;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;
import javax.enterprise.inject.spi.InjectionPoint;

import io.micrometer.core.annotation.Counted;
import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.LongTaskTimer;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

@ApplicationScoped
class MeterProducer {

    @Produces
    public Counter produceCounter(MeterRegistry meterRegistry, InjectionPoint ip) {
        return produceCounter(meterRegistry, ip.getAnnotated().getAnnotation(Counted.class));
    }

    static Counter produceCounter(MeterRegistry meterRegistry, Counted counted) {
        return Counter.builder(counted.value().trim())
                .description(counted.description())
                .tags(counted.extraTags())
                .register(meterRegistry);
    }

    @Produces
    public Timer produceTimer(MeterRegistry meterRegistry, InjectionPoint ip) {
        Timed annotation = ip.getAnnotated()
                .getAnnotation(Timed.class);
        return annotation.longTask()
                ? null
                : produceTimer(meterRegistry, annotation);
    }

    static Timer produceTimer(MeterRegistry meterRegistry, Timed timed) {
        return Timer.builder(timed.value())
                .description(timed.description())
                .tags(timed.extraTags())
                .publishPercentiles(timed.percentiles())
                .publishPercentileHistogram(timed.histogram())
                .register(meterRegistry);
    }

    static LongTaskTimer produceLongTaskTimer(MeterRegistry meterRegistry, Timed timed) {
        return LongTaskTimer.builder(timed.value().trim())
                .description(timed.description())
                .tags(timed.extraTags())
                .publishPercentiles(timed.percentiles())
                .publishPercentileHistogram(timed.histogram())
                .register(meterRegistry);
    }

    @Produces
    public LongTaskTimer produceLongTaskTimer(MeterRegistry meterRegistry, InjectionPoint ip) {
        Timed annotation = ip.getAnnotated().getAnnotation(Timed.class);
        return annotation.longTask()
                ? produceLongTaskTimer(meterRegistry, annotation)
                : null;
    }
}
