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
package io.helidon.integrations.micrometer.cdi;

import java.util.Optional;
import java.util.stream.IntStream;

import io.helidon.common.testing.junit5.OptionalMatcher;
import io.helidon.microprofile.metrics.MetricsCdiExtension;
import io.helidon.microprofile.testing.junit5.AddBean;
import io.helidon.microprofile.testing.junit5.AddExtension;
import io.helidon.microprofile.testing.junit5.HelidonTest;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.inject.Inject;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

@HelidonTest
@AddBean(MixedAnnotationsResource.class)
class TestMixedAnnotations {

    @Inject
    private WebTarget webTarget;

    @Inject
    private MeterRegistry mMeterRegistry;

    @Inject
    private MetricRegistry mpMetricRegistry;

    @Test
    void testMixedAnnotations() {
        final int iterations = 2;
        IntStream.range(0, iterations).forEach(
                i -> webTarget
                        .path("mixed/withArg/Hans")
                        .request()
                        .accept(MediaType.TEXT_PLAIN_TYPE)
                        .get(String.class));
        Optional<Long> mTimerCount =  mMeterRegistry.getMeters().stream()
                .filter(m -> m instanceof Timer
                        && m.getId().getName().equals(MixedAnnotationsResource.MESSAGE_TIMER)
                        && m.getId().getTag("scope") != null
                        && m.getId().getTag("scope").equals("application"))
                .findFirst()
                        .map(Timer.class::cast)
                .map(Timer::count);

        assertThat("Micrometer timer count", mTimerCount, OptionalMatcher.optionalValue(is((long) iterations)));

        org.eclipse.microprofile.metrics.Timer mpTimer =
                mpMetricRegistry.getTimer(new MetricID(MixedAnnotationsResource.MESSAGE_TIMER));
        assertThat("MicroProfile timer count", mpTimer.getCount(), is((long) iterations));
    }
}
