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
package io.helidon.integrations.micrometer;

import io.micrometer.core.instrument.Counter;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

public class MicrometerTestAddingMeters {

    @Test
    public void addCounter() {
        MeterRegistryFactory factory = MeterRegistryFactory.builder()
                .enrollBuiltInRegistry(MeterRegistryFactory.BuiltInRegistryType.PROMETHEUS, PrometheusConfig.DEFAULT)
                .build();

        MicrometerSupport support = MicrometerSupport.builder()
                .meterRegistryFactorySupplier(factory)
                .build();

        Counter counter1 = support.registry().counter("testCounter", "number", "one");
        Counter counter2 = support.registry().counter("testCounter", "number", "two");

        counter1.increment();
        counter2.increment(2.0);

        assertThat("testCounter/number=one", support.registry().counter("testCounter", "number", "one").count(), is(1.0));
        assertThat("testCounter/number=two", support.registry().counter("testCounter", "number", "two").count(), is(2.0));

        String output =
                PrometheusMeterRegistry.class.cast(
                        factory.enrolledBuiltInRegistries().get(MeterRegistryFactory.BuiltInRegistryType.PROMETHEUS))
                        .scrape();

        assertThat(output, containsString("testCounter_total{number=\"one\",} 1.0"));
        assertThat(output, containsString("testCounter_total{number=\"two\",} 2.0"));
    }
}
