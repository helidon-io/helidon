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
package io.helidon.metrics.micrometer;

import java.util.Optional;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;

import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.not;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;


public class MicrometerSupportBuilderTest {

    @Test
    public void testValidBuiltInRegistries() {
        MicrometerSupport support = MicrometerSupport.builder()
                .enrollBuiltInRegistry(MicrometerSupport.BuiltInRegistryType.PROMETHEUS, PrometheusConfig.DEFAULT)
                .build();

        assertThat("Did not find expected instance of PrometheusMeterRegistry",
                support.enrolledRegistries()
                    .values()
                    .stream()
                    .anyMatch(PrometheusMeterRegistry.class::isInstance));
    }

    @Test
    public void testValidExplicitlyAddedPrometheusRegistry() {
        MicrometerSupport support = MicrometerSupport.builder()
                .enrollRegistry(new PrometheusMeterRegistry(PrometheusConfig.DEFAULT), req -> Optional.empty())
                .build();

        assertThat("Did not find expected instance of PrometheusMeterRegistry",
                support.enrolledRegistries()
                    .values()
                    .stream()
                    .anyMatch(PrometheusMeterRegistry.class::isInstance));
    }

    @Test
    public void testBuiltInWithExplicitlyAddedPrometheusRegistries() {
        MicrometerSupport support = MicrometerSupport.builder()
                .enrollRegistry(new PrometheusMeterRegistry(PrometheusConfig.DEFAULT), req -> Optional.empty())
                .enrollBuiltInRegistry(MicrometerSupport.BuiltInRegistryType.PROMETHEUS, PrometheusConfig.DEFAULT)
                .build();

        assertThat("Unexpected count of PrometheusMeterRegistries",
                support.enrolledRegistries()
                    .values()
                    .stream()
                    .filter(PrometheusMeterRegistry.class::isInstance)
                    .count(),
                is(2L));
    }

    @Test
    public void testBuiltInWithSingleGoodType() {
        Config config = Config.create(ConfigSources.classpath("/testData.json")).get("singleValue");
        MicrometerSupport.Builder builder = MicrometerSupport.builder()
                .config(config.get("metrics.micrometer"));

        assertThat(builder.logRecords(), is(empty()));
        MicrometerSupport support = builder.build();

        assertThat("Did not find expected instance of PrometheusMeterRegistry",
                support.enrolledRegistries()
                        .values()
                        .stream()
                        .anyMatch(PrometheusMeterRegistry.class::isInstance));
        assertThat(builder.logRecords(), is(empty()));
    }

    @Test
    public void testBuiltInWithOneBadType() {
        Config config = Config.create(ConfigSources.classpath("/testData.json")).get("singleBadValueWithGoodOne");
        MicrometerSupport.Builder builder = MicrometerSupport.builder()
                .config(config.get("metrics.micrometer"));

        MicrometerSupport support = builder.build();

        assertThat("Did not find expected instance of PrometheusMeterRegistry",
                support.enrolledRegistries()
                        .values()
                        .stream()
                        .anyMatch(PrometheusMeterRegistry.class::isInstance));

        assertThat(builder.logRecords(), is(not(empty())));
    }

    @Test
    public void testBuiltInWithConfig() {
        Config config = Config.create(ConfigSources.classpath("/testData.json")).get("structure");

        MicrometerSupport.Builder builder = MicrometerSupport.builder()
                .config(config.get("metrics.micrometer"));

        assertThat(builder.logRecords(), is(empty()));
        MicrometerSupport support = builder.build();

        assertThat("Did not find expected instance of PrometheusMeterRegistry",
                support.enrolledRegistries()
                        .values()
                        .stream()
                        .anyMatch(PrometheusMeterRegistry.class::isInstance));

    }

    @Test
    public void testMultipleNamesOnly() {
        Config config = Config.create(ConfigSources.classpath("/testData.json")).get("listOfValues");
        MicrometerSupport.Builder builder = MicrometerSupport.builder()
                .config(config.get("metrics.micrometer"));

        assertThat(builder.logRecords(), is(empty()));
        MicrometerSupport support = builder.build();

        assertThat("Did not find expected instance of PrometheusMeterRegistry",
                support.enrolledRegistries()
                        .values()
                        .stream()
                        .anyMatch(PrometheusMeterRegistry.class::isInstance));

    }
}
