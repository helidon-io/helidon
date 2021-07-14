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
 */
package io.helidon.metrics.jaeger;

import java.util.Map;

import javax.inject.Inject;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;

import io.helidon.microprofile.tests.junit5.AddBean;
import io.helidon.microprofile.tests.junit5.HelidonTest;

import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.Gauge;
import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.annotation.RegistryType;
import org.junit.jupiter.api.Test;

import static io.helidon.metrics.jaeger.HelidonJaegerMetricsFactory.convertTags;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

@HelidonTest
@AddBean(HelloWorldResource.class)
class TestJaegerMetrics {

    @Inject
    private WebTarget webTarget;

    @Inject
    @RegistryType(type = MetricRegistry.Type.VENDOR)
    private MetricRegistry vendorRegistry;

    @Test
    void checkForJaegerMetrics() throws InterruptedException {
        String hello = webTarget
                .path("/helloworld/hi")
                .request(MediaType.TEXT_PLAIN)
                .get(String.class);

        Map<MetricID, Gauge> gauges = vendorRegistry.getGauges();

        MetricID expectedCounterID = new MetricID("jaeger_tracer_traces",
                convertTags(Map.of("sampled", "n", "state", "started")));
        Map<MetricID, Counter>  counters = vendorRegistry.getCounters((metricID, metric) -> metricID.equals(expectedCounterID));
        Counter expectedCounter = counters.get(expectedCounterID);
        assertThat("jaeger_tracer_traces counter", expectedCounter, is(notNullValue()));
        assertThat("jaeger_tracer_traces counter", expectedCounter.getCount(), is(greaterThan(0L)));
    }
}
