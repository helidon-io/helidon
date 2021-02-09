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
package io.helidon.integrations.micrometer;

import java.util.Optional;
import java.util.Set;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;

public class MicrometerSupportBuilderTest {

    @Test
    public void testValidBuiltInRegistries() {
        MeterRegistryFactory factory = MeterRegistryFactory.builder()
                .enrollBuiltInRegistry(MeterRegistryFactory.BuiltInRegistryType.PROMETHEUS, PrometheusConfig.DEFAULT)
                .build();
        MicrometerSupport support = MicrometerSupport.builder()
                .meterRegistryFactorySupplier(factory)
                .build();


        support.registry().counter("testCounter3").increment(3.0);

        Counter counter = support.registry().find("testCounter3").counter();

        assertThat("Did not find expected instance of PrometheusMeterRegistry or meter within it",
                counter, is(notNullValue()));
        assertThat("Found counter but with unexpected value", counter.count(), is(3.0));
    }

    @Test
    public void testValidExplicitlyAddedPrometheusRegistry() {
        double inc = 4.0;
        PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        MeterRegistryFactory factory = MeterRegistryFactory.builder()
                .enrollRegistry(registry, r -> Optional.of((req, resp) -> resp.send(registry.scrape())))
                .build();

        MicrometerSupport support = MicrometerSupport.builder()
                .meterRegistryFactorySupplier(factory)
                .build();

        assertThat("Did not find expected explicitly enrolled registry", factory.registries(), Matchers.contains(registry));

        support.registry().counter("testCounter4").increment(inc);
        Counter counter = support.registry().find("testCounter4").counter();

        assertThat("Did not find expected instance of counter", counter, is(notNullValue()));
        assertThat("Found counter but with unexpected value", counter.count(), is(inc));
    }

    @Test
    public void testBuiltInWithExplicitlyAddedPrometheusRegistries() {
        double inc = 5.0;

        PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        MeterRegistryFactory factory = MeterRegistryFactory.builder()
                .enrollRegistry(registry, r -> Optional.of((req, resp) -> resp.send(registry.scrape())))
                .enrollBuiltInRegistry(MeterRegistryFactory.BuiltInRegistryType.PROMETHEUS, PrometheusConfig.DEFAULT)
                .build();

        MicrometerSupport support = MicrometerSupport.builder()
                .meterRegistryFactorySupplier(factory)
                .build();

        assertThat("Did not find expected explicitly enrolled registry", factory.registries().contains(registry));

        support.registry().counter("testCounter5").increment(inc);
        Counter counter = support.registry().find("testCounter5").counter();

        assertThat("Did not find expected instance of counter", counter, is(notNullValue()));
        assertThat("Found counter but with unexpected value", counter.count(), is(inc));
    }

    @Test
    public void testBuiltInWithSingleGoodType() {
        double inc = 6.0;
        Config config = Config.create(ConfigSources.classpath("/micrometerTestData.json")).get("singleValue");
        MeterRegistryFactory.Builder factoryBuilder = MeterRegistryFactory.builder()
                .config(config.get("metrics.micrometer"));

        assertThat(factoryBuilder.logRecords(), is(empty()));

        MeterRegistryFactory factory = factoryBuilder.build();
        MicrometerSupport support = MicrometerSupport.builder()
                .config(config.get("metrics.micrometer"))
                .meterRegistryFactorySupplier(factory)
                .build();

        support.registry().counter("testCounter6").increment(inc);
        Counter counter = support.registry().find("testCounter6").counter();

        assertThat("Did not find expected instance of counter", counter, is(notNullValue()));
        assertThat("Found counter but with unexpected value", counter.count(), is(inc));

        for (MeterRegistry r : factory.registries()) {
            if (r instanceof PrometheusMeterRegistry) {
                PrometheusMeterRegistry prometheusMeterRegistry = (PrometheusMeterRegistry) r;
                prometheusMeterRegistry.scrape();
            }
        }
    }

    @Test
    public void testBuiltInWithOneBadType() {
        Config config = Config.create(ConfigSources.classpath("/micrometerTestData.json")).get("singleBadValueWithGoodOne");
        MeterRegistryFactory.Builder factoryBuilder = MeterRegistryFactory.builder()
                .config(config.get("metrics.micrometer"));
        MeterRegistryFactory factory = factoryBuilder.build();
        MicrometerSupport.Builder builder = MicrometerSupport.builder()
                .meterRegistryFactorySupplier(factory);

        MicrometerSupport support = builder.build();

        assertThat("Too many or too few enrolled registries", factory.registries().size(), is(1));

        assertThat(factoryBuilder.logRecords().size(), is(1));
    }

    @Test
    public void testBuiltInWithConfig() {
        Config config = Config.create(ConfigSources.classpath("/micrometerTestData.json")).get("structure");

        MeterRegistryFactory.Builder factoryBuilder = MeterRegistryFactory.builder()
                .config(config.get("metrics.micrometer"));

        MeterRegistryFactory factory = factoryBuilder.build();

        MicrometerSupport.Builder builder = MicrometerSupport.builder()
                .config(config.get("metrics.micrometer"))
                .meterRegistryFactorySupplier(factory);

        assertThat(factoryBuilder.logRecords(), is(empty()));
        MicrometerSupport support = builder.build();

        Set<MeterRegistry> registries = factory.registries();
        assertThat("Unexpectedly found no enrolled registry", registries, is(not(empty())));

        assertThat("Did not find expected PrometheusRegistry in MicrometerSupport",
                registries.stream()
                    .filter(PrometheusMeterRegistry.class::isInstance)
                    .findAny()
                    .isPresent());
    }

    @Test
    public void testMultipleNamesOnly() {
        Config config = Config.create(ConfigSources.classpath("/micrometerTestData.json")).get("listOfValues");

        MeterRegistryFactory.Builder factoryBuilder = MeterRegistryFactory.builder()
                .config(config.get("metrics.micrometer"));

        MeterRegistryFactory factory = factoryBuilder.build();

        MicrometerSupport.Builder builder = MicrometerSupport.builder()
                .config(config.get("metrics.micrometer"))
                .meterRegistryFactorySupplier(factory);

        assertThat(factoryBuilder.logRecords(), is(empty()));
        MicrometerSupport support = builder.build();

        // Even though the test data defines two Prometheus registries, internally we use a map to store
        // them, keyed by the enum. So the map will contain only one.
        assertThat("Did not find expected Prometheus registry",
            factory.registries().stream()
                    .anyMatch(PrometheusMeterRegistry.class::isInstance));
    }
}
